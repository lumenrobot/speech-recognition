package org.lskk.lumen.speech.recognition;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.net.Authenticator;
import java.net.PasswordAuthentication;

/**
 * Configures HTTP & HTTPS proxy with optional authentication from {@code application.properties}.
 *
 * <p>Usage: Edit {@code config/application.properties} as follows (also, make a commented template in {@code config/application.dev.properties}:</p>
 *
 * <pre>
 * # Proxy
 * http.proxyHost=cache.itb.ac.id
 * http.proxyPort=8080
 * https.proxyHost=cache.itb.ac.id
 * https.proxyPort=8080
 * http.proxyUser=hendyirawan
 * http.proxyPassword=
 * </pre>
 *
 * Then {@link org.springframework.context.annotation.Import} this class.
 */
@Configuration
public class ProxyConfig {

    private static final Logger log = LoggerFactory.getLogger(ProxyConfig.class);

    @Inject
    protected Environment env;

    @PostConstruct
    public void init() {
        final String proxyHost = env.getProperty("http.proxyHost");
        if (!Strings.isNullOrEmpty(proxyHost)) {
            System.setProperty("http.proxyHost", proxyHost);
        }

        final Integer proxyPort = env.getProperty("http.proxyPort", Integer.class);
        if (proxyPort != null) {
            System.setProperty("http.proxyPort", proxyPort.toString());
        }

        final String httpsProxyHost = env.getProperty("https.proxyHost");
        if (!Strings.isNullOrEmpty(httpsProxyHost)) {
            System.setProperty("https.proxyHost", httpsProxyHost);
        }

        final Integer httpsProxyPort = env.getProperty("https.proxyPort", Integer.class);
        if (httpsProxyPort != null) {
            System.setProperty("https.proxyPort", httpsProxyPort.toString());
        }

        final String proxyUser = env.getProperty("http.proxyUser");
        if (!Strings.isNullOrEmpty(proxyUser)) {
            System.setProperty("http.proxyUser", proxyUser);
            final String proxyPassword = env.getRequiredProperty("http.proxyPassword");
            System.setProperty("http.proxyPassword", proxyPassword);
            log.info("Using authenticated proxy http://{}:{}@{}:{}", env.getRequiredProperty("http.proxyUser"), "********", proxyHost, proxyPort);
            Authenticator.setDefault(new Authenticator() {
                public PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(proxyUser, proxyPassword.toCharArray());
                }

            });
        } else if (!Strings.isNullOrEmpty(proxyHost)) {
            log.info("Using unauthenticated proxy http://{}:{}", proxyHost, proxyPort);
        }

    }

}
