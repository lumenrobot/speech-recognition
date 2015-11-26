package org.lskk.lumen.speech.recognition;

import com.google.common.base.Preconditions;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.joda.time.DateTime;
import org.lskk.lumen.core.AudioObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.spi.FileTypeDetector;

@SpringBootApplication
@Profile("audioCatApp")
public class AudioCatApp implements CommandLineRunner {

    private static Logger log = LoggerFactory.getLogger(AudioCatApp.class);

    static {
        log.info("java.library.path = {}", System.getProperty("java.library.path"));
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder(AudioCatApp.class)
                .profiles("audioCatApp")
                .run(args);
    }

    @Inject
    private ProducerTemplate producer;
    @Inject
    private ToJson toJson;
    @Inject
    private ApplicationContext appCtx;

    @Override
    public void run(String... args) throws Exception {
        Preconditions.checkArgument(args.length >= 1, "Usage: audiocat AUDIO_FILE");
        final File audioFile = new File(args[0]);
        final String extension = FilenameUtils.getExtension(args[0]).toLowerCase();
        final byte[] content = FileUtils.readFileToByteArray(audioFile);
        String contentType = Files.probeContentType(audioFile.toPath());
        if (contentType == null && "ogg".equals(extension)) {
            contentType = "audio/vorbis";
        } else if (contentType == null && "flac".equals(extension)) {
            contentType = "audio/x-flac";
        }
        Preconditions.checkArgument(contentType != null, "Cannot guess content type for %s", audioFile);
        final AudioObject audioObject = new AudioObject();
        audioObject.setContentType(contentType);
        audioObject.setContentUrl("data:" + contentType + ";base64," + Base64.encodeBase64String(content));
        audioObject.setContentSize((long) content.length);
        audioObject.setName(FilenameUtils.getName(args[0]));
        audioObject.setDateCreated(new DateTime());
        audioObject.setDatePublished(audioObject.getDateCreated());
        audioObject.setDateModified(audioObject.getDateCreated());
        audioObject.setUploadDate(audioObject.getDateCreated());
        final String audioInUri = "rabbitmq://localhost/amq.topic?connectionFactory=#amqpConnFactory&exchangeType=topic&autoDelete=false&skipQueueDeclare=true&routingKey=avatar.nao1.audio.in";
        log.info("Sending {} to {} ...", audioObject, audioInUri);
        producer.sendBody(audioInUri, toJson.mapper.writeValueAsBytes(audioObject));
        SpringApplication.exit(appCtx);
    }

}
