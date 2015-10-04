package org.lskk.lumen.speech.recognition;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.ooxi.jdatauri.DataUri;
import com.google.common.base.Splitter;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.lskk.lumen.core.AudioObject;
import org.lskk.lumen.core.LumenThing;
import org.lskk.lumen.core.RecognizedSpeech;
import org.lskk.lumen.core.SpeechResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Profile("speechRecognitionApp")
public class AudioRouter extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(AudioRouter.class);

    @Inject
    private Environment env;
    @Inject
    private ToJson toJson;
    @Inject
    private ProducerTemplate producer;
    @Inject
    private CloseableHttpClient httpClient;

//    public interface DataRecording {
//        void sendAudio(@Body AudioObject audioObject, @Header("avatarId") String avatarId);
//    }
//
//    @Produce(uri = "seda:data-recording")
//    private DataRecording dataRecording;

    protected static class RecognizedSpeechTmp {
        @JsonProperty("result")
        private List<SpeechResult> results = new ArrayList<>();
        @JsonProperty("result_index")
        private Integer resultIndex;

        public List<SpeechResult> getResults() {
            return results;
        }

        @Override
        public String toString() {
            return "RecognizedSpeechTmp{" +
                    "results=" + results +
                    ", resultIndex=" + resultIndex +
                    '}';
        }
    }

    public static class AudioData {
        private final AudioFormat format;
        private final byte[] data;
        private final AudioFormat sourceFormat;

        public AudioData(AudioFormat format, byte[] data) {
            this.format = format;
            this.data = data;
            this.sourceFormat = null;
        }

        public AudioData(AudioFormat format, byte[] data, AudioFormat sourceFormat) {
            this.format = format;
            this.data = data;
            this.sourceFormat = sourceFormat;
        }

        public AudioFormat getFormat() {
            return format;
        }

        public byte[] getData() {
            return data;
        }

        public AudioFormat getSourceFormat() {
            return sourceFormat;
        }
    }

    public static AudioData convertAudioData(byte[] sourceBytes, AudioFormat audioFormat) throws IOException, UnsupportedAudioFileException {
        if (sourceBytes == null || sourceBytes.length == 0 || audioFormat == null) {
            throw new IllegalArgumentException("Illegal Argument passed to this method");
        }

        try (final ByteArrayInputStream bais = new ByteArrayInputStream(sourceBytes);
             final AudioInputStream sourceAIS = AudioSystem.getAudioInputStream(bais)) {
            final AudioFormat sourceFormat = sourceAIS.getFormat();
            final AudioFormat convertFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED, sourceFormat.getSampleRate(), 16,
                    sourceFormat.getChannels(), sourceFormat.getChannels() * 2, sourceFormat.getSampleRate(), false);
            try (final AudioInputStream convert1AIS = AudioSystem.getAudioInputStream(convertFormat, sourceAIS);
                 final AudioInputStream convert2AIS = AudioSystem.getAudioInputStream(audioFormat, convert1AIS);
                 final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                while (true) {
                    int readCount = convert2AIS.read(buffer, 0, buffer.length);
                    if (readCount == -1) {
                        break;
                    }
                    baos.write(buffer, 0, readCount);
                }
                return new AudioData(audioFormat, baos.toByteArray(), sourceFormat);
            }
        }
    }

    public static AudioData getAudioDataPcmSigned(byte[] sourceBytes) throws IOException, UnsupportedAudioFileException {
        if (sourceBytes == null || sourceBytes.length == 0) {
            throw new IllegalArgumentException("Illegal Argument passed to this method");
        }

        try (final ByteArrayInputStream bais = new ByteArrayInputStream(sourceBytes);
             final AudioInputStream sourceAIS = AudioSystem.getAudioInputStream(bais)) {
            final AudioFormat sourceFormat = sourceAIS.getFormat();
            final AudioFormat convertFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED, sourceFormat.getSampleRate(), 16,
                    sourceFormat.getChannels(), sourceFormat.getChannels() * 2, sourceFormat.getSampleRate(), false);
            try (final AudioInputStream convert1AIS = AudioSystem.getAudioInputStream(convertFormat, sourceAIS);
                 final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                while (true) {
                    int readCount = convert1AIS.read(buffer, 0, buffer.length);
                    if (readCount == -1) {
                        break;
                    }
                    baos.write(buffer, 0, readCount);
                }
                return new AudioData(convertFormat, baos.toByteArray(), sourceFormat);
            }
        }
    }

    @Override
    public void configure() throws Exception {
        final String googleSpeechKey = env.getRequiredProperty("google-speech.key");
        // NAO only supports limited sample rates, so we limit to 22050 Hz (default)
        final int sampleRate = 22050;
        final int channelCount = 2;
        final AudioFormat naoFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16,
                channelCount, channelCount * 2, sampleRate, false);
        from("rabbitmq://localhost/amq.topic?connectionFactory=#amqpConnFactory&exchangeType=topic&autoDelete=false&routingKey=avatar.nao1.audio.in")
                .to("log:IN.avatar.nao1.audio.in?showHeaders=true&showAll=true&multiline=true")
                .process(exchange -> {
                    final LumenThing thing = toJson.getMapper().readValue(
                            exchange.getIn().getBody(byte[].class), LumenThing.class);
                    if (thing instanceof AudioObject) {
                        final AudioObject audioObject = (AudioObject) thing;
                        log.info("Got audio in {}: {} bytes", audioObject.getContentType(), audioObject.getContentSize());
                        final URI contentUrl = URI.create(audioObject.getContentUrl());
                        if ("data".equals(contentUrl.getScheme())) {
                            final DataUri dataUri = DataUri.parse(audioObject.getContentUrl(), StandardCharsets.UTF_8);
                            final Locale locale = Locale.ENGLISH; // TODO: support this in AudioObject
                            final URI recognizeUri = new URIBuilder("https://www.google.com/speech-api/v2/recognize")
                                    .addParameter("output", "json")
                                    .addParameter("lang", locale.toLanguageTag())
                                    .addParameter("key", googleSpeechKey).build();
                            final HttpPost httpPost = new HttpPost(recognizeUri);

                            // TODO: convert everything to FLAC 16000 Hz mono, unless already

                            // Google supports: audio/x-flac
                            // audio/mpeg (mp3) not supported: Your client has issued a malformed or illegal request. Unknown audio encoding: mpeg
                            // audio/vorbis, audio/ogg not supported
                            // ; rate=something is mandatory! 16000 Hz is pretty good
                            httpPost.setEntity(new ByteArrayEntity(dataUri.getData(), ContentType.parse(dataUri.getMime() + "; rate=16000")));
                            log.info("Recognizing {} bytes {} for language {}...",
                                    dataUri.getData().length, dataUri.getMime(), locale.toLanguageTag());
                            final String content;
                            try (final CloseableHttpResponse resp = httpClient.execute(httpPost)) {
                                if (resp.getStatusLine().getStatusCode() != 200) {
                                    final String errorContent = IOUtils.toString(resp.getEntity().getContent());
                                    log.error("Recognize error: {} - {}", resp.getStatusLine(), errorContent);
                                    throw new SpeechRecognitionException(
                                            String.format("Recognize error: %s - %s", resp.getStatusLine(), errorContent));
                                }
                                content = IOUtils.toString(resp.getEntity().getContent());
                            }
                            final List<String> jsons = Splitter.on('\n').omitEmptyStrings().splitToList(content);
                            log.debug("JSON recognized all: {}", jsons);
                            final RecognizedSpeech recognizedSpeech = new RecognizedSpeech();
                            for (final String json : jsons) {
                                final RecognizedSpeechTmp single = toJson.mapper.readValue(json, RecognizedSpeechTmp.class);
                                log.trace("JSON recognized: {}", single);
                                recognizedSpeech.getResults().addAll(single.getResults());
                            }
                            log.info("Recognized speech: {}", toJson.mapper.writeValueAsString(recognizedSpeech));

                            // lumen.audio.speech.recognition
                            final String speechRecognitionUri = "rabbitmq://localhost/amq.topic?connectionFactory=#amqpConnFactory&exchangeType=topic&autoDelete=false&routingKey=lumen.audio.speech.recognition";
                            log.debug("Sending {} to {} ...", recognizedSpeech, speechRecognitionUri);
                            producer.sendBody(speechRecognitionUri, toJson.mapper.writeValueAsBytes(recognizedSpeech));
                        } else {
                            log.info("Ignoring unknown audio URL: " + contentUrl);
                        }
                    }
                });
    }
}
