package org.lskk.lumen.speech.recognition;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.lskk.lumen.core.LumenCoreConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
@Profile("speechRecognitionApp")
@Import(LumenCoreConfig.class)
public class SpeechRecognitionApp implements CommandLineRunner {

    private static Logger log = LoggerFactory.getLogger(SpeechRecognitionApp.class);

    static {
        log.info("java.library.path = {}", System.getProperty("java.library.path"));
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder(SpeechRecognitionApp.class)
                .profiles("speechRecognitionApp")
                .run(args);
    }

    @Bean(destroyMethod = "close")
    public CloseableHttpClient httpClient() {
        final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(200);
        cm.setDefaultMaxPerRoute(20);
        return HttpClients.custom().useSystemProperties().setConnectionManager(cm).build();
    }

    @Override
    public void run(String... args) throws Exception {
    }
}
