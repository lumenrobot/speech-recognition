package org.lskk.lumen.speech.recognition;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.ooxi.jdatauri.DataUri;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.LoggingErrorHandlerBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rabbitmq.RabbitMQConstants;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.joda.time.DateTime;
import org.lskk.lumen.core.*;
import org.lskk.lumen.core.util.AsError;
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
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@Profile("speechRecognitionApp")
public class SpeechRecognitionRouter extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(SpeechRecognitionRouter.class);
    private static final DefaultExecutor executor = new DefaultExecutor();
    private static final HttpClientContext httpContext = HttpClientContext.create();
    public static final int SAMPLE_RATE = 16000;
    public static final String FLAC_TYPE = "audio/x-flac";

    @Inject
    private Environment env;
    @Inject
    private ToJson toJson;
    @Inject
    private AsError asError;
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
        final String ffmpegExecutable = !new File("/usr/bin/ffmpeg").exists() && new File("/usr/bin/avconv").exists() ? "avconv" : "ffmpeg";
        log.info("libav autodetection result: We will use '{}'", ffmpegExecutable);
        final String googleSpeechKey = env.getRequiredProperty("google-speech.key");

        onException(Exception.class).bean(asError).bean(toJson).handled(true);
        errorHandler(new LoggingErrorHandlerBuilder(log));
        from("rabbitmq://localhost/amq.topic?connectionFactory=#amqpConnFactory&exchangeType=topic&autoDelete=false&queue=" + AvatarChannel.AUDIO_IN.wildcard() + "&routingKey=" + AvatarChannel.AUDIO_IN.wildcard())
                .to("log:IN." + AvatarChannel.AUDIO_IN.wildcard() + "?showHeaders=true&showBody=false&multiline=true")
                .process(exchange -> {
                    final String avatarId = AvatarChannel.getAvatarId((String) exchange.getIn().getHeader(RabbitMQConstants.ROUTING_KEY));
                    final LumenThing thing = toJson.getMapper().readValue(
                            exchange.getIn().getBody(byte[].class), LumenThing.class);
                    if (thing instanceof AudioObject) {
                        final AudioObject audioObject = (AudioObject) thing;
                        log.info("Got audio in {}: {} bytes", audioObject.getContentType(), audioObject.getContentSize());
                        final URI contentUrl = URI.create(audioObject.getContentUrl());
                        if ("data".equals(contentUrl.getScheme())) {
                            final DataUri dataUri = DataUri.parse(audioObject.getContentUrl(), StandardCharsets.UTF_8);
                            final ContentType originalType = ContentType.parse(dataUri.getMime());
                            final boolean conversionRequired = !FLAC_TYPE.equals(originalType.getMimeType()) || originalType.getParameter("rate") == null;

                            final Locale locale = Optional.ofNullable(audioObject.getInLanguage()).orElse(Locale.US);
                            final URI recognizeUri = new URIBuilder("https://www.google.com/speech-api/v2/recognize")
                                    .addParameter("output", "json")
                                    .addParameter("lang", locale.toLanguageTag())
                                    .addParameter("key", googleSpeechKey).build();
                            final HttpPost httpPost = new HttpPost(recognizeUri);

                            // convert everything to FLAC 16000 Hz mono, unless already
                            final byte[] flacContent;
                            final ContentType flacContentType;
                            if (conversionRequired) {
                                final File inFile = File.createTempFile("speech-", ".tmp");
                                final File outFile = File.createTempFile("speech-", ".flac");
                                log.info("Converting {} {} to FLAC {} ...", dataUri.getMime(), inFile, outFile);
                                try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                                    FileUtils.writeByteArrayToFile(inFile, dataUri.getData());
                                    // flac.exe doesn't support mp3, and that's a problem for now (note: mp3 patent is expiring)
                                    final CommandLine cmdLine = new CommandLine(ffmpegExecutable);
                                    cmdLine.addArgument("-i");
                                    cmdLine.addArgument(inFile.toString());
                                    cmdLine.addArgument("-ar");
                                    cmdLine.addArgument(String.valueOf(SAMPLE_RATE));
                                    cmdLine.addArgument("-ac");
                                    cmdLine.addArgument("1");
                                    cmdLine.addArgument("-y"); // happens, weird!
                                    cmdLine.addArgument(outFile.toString());
                                    executor.setStreamHandler(new PumpStreamHandler(bos));
                                    final int executed;
                                    try {
                                        executed = executor.execute(cmdLine);
                                    } finally {
                                        log.info("{}: {}", cmdLine, bos.toString());
                                    }
                                    Preconditions.checkState(outFile.exists(), "Cannot convert %s %s to FLAC %s",
                                            dataUri.getMime(), inFile, outFile);
                                    flacContent = FileUtils.readFileToByteArray(outFile);
                                    flacContentType = ContentType.create(FLAC_TYPE, new BasicNameValuePair("rate", String.valueOf(SAMPLE_RATE)));
                                } finally {
                                    outFile.delete();
                                    inFile.delete();
                                }
                            } else {
                                flacContent = dataUri.getData();
                                flacContentType = originalType;
                            }

                            // Google supports: audio/x-flac
                            // audio/mpeg (mp3) not supported: Your client has issued a malformed or illegal request. Unknown audio encoding: mpeg
                            // audio/vorbis, audio/ogg not supported
                            // ; rate=something is mandatory! 16000 Hz is pretty good
                            httpPost.setEntity(new ByteArrayEntity(flacContent, flacContentType));
                            // Proof below that by using InputStream, data is sent to server almost (after buffer filled) as soon
                            // as available, so if we can get streaming audio as early as possible,
                            // speech recognition is more responsive than waiting the entire audio to arrive then sending
                            // (because of limited bandwidth and latency)
//                            final ByteArrayInputStream origIs = new ByteArrayInputStream(flacContent);
//                            final InputStream mockIs = new InputStream() {
//                                @Override
//                                public int read() throws IOException {
//                                    final int b = origIs.read();
//                                    if (origIs.available() % 20000 == 0) { // sleep every few
//                                        try {
//                                            log.debug("Sleeping...");
//                                            Thread.sleep(500);
//                                        } catch (InterruptedException e) {
//                                        }
//                                    }
//                                    return b;
//                                }
//                            };
//                            httpPost.setEntity(new InputStreamEntity(mockIs, flacContentType));

                            log.info("Recognizing {} bytes {} for language {}...",
                                    flacContent.length, flacContentType, locale.toLanguageTag());
                            final String content;

                            // do NOT close the resp/connection
                            final CloseableHttpResponse resp = httpClient.execute(httpPost, httpContext);
                            if (resp.getStatusLine().getStatusCode() != 200) {
                                final String errorContent = IOUtils.toString(resp.getEntity().getContent());
                                log.error("Recognize error: {} - {}", resp.getStatusLine(), errorContent);
                                throw new SpeechRecognitionException(
                                        String.format("Recognize error: %s - %s", resp.getStatusLine(), errorContent));
                            }
                            content = IOUtils.toString(resp.getEntity().getContent());

                            final List<String> jsons = Splitter.on('\n').omitEmptyStrings().splitToList(content);
                            log.debug("JSON recognized all: {}", jsons);
                            final RecognizedSpeech recognizedSpeech = new RecognizedSpeech();
                            recognizedSpeech.setDateCreated(new DateTime());
                            recognizedSpeech.setAvatarId(avatarId);
                            for (final String json : jsons) {
                                final RecognizedSpeechTmp single = toJson.mapper.readValue(json, RecognizedSpeechTmp.class);
                                log.trace("JSON recognized: {}", single);
                                recognizedSpeech.getResults().addAll(single.getResults());
                            }
                            log.info("Recognized speech: {}", toJson.mapper.writeValueAsString(recognizedSpeech));

                            // lumen.audio.speech.recognition
                            final String speechRecognitionUri = "rabbitmq://localhost/amq.topic?connectionFactory=#amqpConnFactory&exchangeType=topic&autoDelete=false&skipQueueDeclare=true&routingKey=" + LumenChannel.SPEECH_RECOGNITION.key();
                            log.debug("Sending {} to {} ...", recognizedSpeech, speechRecognitionUri);
                            producer.sendBody(speechRecognitionUri, toJson.mapper.writeValueAsBytes(recognizedSpeech));

                            // usedForChat?
                            if (Boolean.TRUE.equals(audioObject.getUsedForChat())) {
                                final Optional<SpeechAlternative> bestAlternative = recognizedSpeech.getResults().stream().findFirst()
                                    .flatMap(it -> it.getAlternatives().stream().findFirst());
                                if (bestAlternative.isPresent() && bestAlternative.get().getConfidence() >= 0.6) {
                                    final CommunicateAction communicateAction = new CommunicateAction();
                                    communicateAction.setAvatarId(avatarId);
                                    communicateAction.setInLanguage(locale);
                                    communicateAction.setObject(bestAlternative.get().getTranscript());
                                    communicateAction.setSpeechTruthValue(new float[] { 1f, bestAlternative.get().getConfidence().floatValue(), 0f });
                                    final String chatInboxUri = "rabbitmq://localhost/amq.topic?connectionFactory=#amqpConnFactory&exchangeType=topic&autoDelete=false&skipQueueDeclare=true&routingKey=" + AvatarChannel.CHAT_INBOX.key(avatarId);
                                    log.debug("Sending {} to {} ...", communicateAction, chatInboxUri);
                                    producer.sendBody(chatInboxUri, toJson.apply(communicateAction));
                                } else if (bestAlternative.isPresent()) {
                                    log.warn("AudioObject wants usedForChat but confidence {} too small for transcript: {}",
                                            bestAlternative.get().getConfidence(), bestAlternative.get().getTranscript());
                                } else {
                                    log.warn("AudioObject wants usedForChat but speech recognition failed, no SpeechResult");
                                }
                            }
                        } else {
                            log.info("Ignoring unknown audio URL: " + contentUrl);
                        }
                    }
                    exchange.getIn().setBody(new Status());
                }).bean(toJson);
    }
}
