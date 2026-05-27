package Proxy;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public class CertificateManager {
    private static final String MITM_KEYSTORE_PATH = "/Users/juansoloo/Documents/Sentinel/certs/mitm-httpbin.p12";

    public SSLContext createServerContextFor(String host) throws Exception {
        return createMitmServerSslContext();
    }
    private SSLContext createMitmServerSslContext() throws Exception {
        char[] password = "changeit".toCharArray(); // why hard coding this? maybe .env for security reasons

        KeyStore keyStore = KeyStore.getInstance("PKCS12");

        try (InputStream keyStoreInput = new FileInputStream(MITM_KEYSTORE_PATH)) {
            keyStore.load(keyStoreInput, password);
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());

        keyManagerFactory.init(keyStore, password);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(
                keyManagerFactory.getKeyManagers(),
                null,
                null
        );

        return sslContext;
    }
}
