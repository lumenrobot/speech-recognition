package org.lskk.lumen.speech.recognition;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.lskk.lumen.core.LumenCoreConfig;
import org.lskk.lumen.core.util.ProxyConfig;
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
@Import({LumenCoreConfig.class, ProxyConfig.class})
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
        // cache.itb.ac.id is not very reliable with pooling connection, better dedicated connection per recognition
//        final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
//        cm.setMaxTotal(200);
//        cm.setDefaultMaxPerRoute(20);
//        cm.setValidateAfterInactivity(100);
        final BasicHttpClientConnectionManager basicCm = new BasicHttpClientConnectionManager();
        return HttpClients.custom().useSystemProperties().setConnectionManager(basicCm)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(10000)
                        .setSocketTimeout(10000)
                        .setConnectionRequestTimeout(10000)
                        .build())
                .build();
    }

    @Override
    public void run(String... args) throws Exception {
    }
}
