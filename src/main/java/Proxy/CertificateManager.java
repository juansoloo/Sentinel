package Proxy;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public class CertificateManager {
    private static final String EXAMPLE_KEYSTORE_PATH = "certs/mitm-example.p12";
    private static final String HTTPBIN_KEYSTORE_PATH = "certs/mitm-httpbin.p12";

    public SSLContext createServerContextFor(String host) throws Exception {
        String normalizedHost = host.toLowerCase();

        if (normalizedHost.equals("httpbin.org")) {
            return createMitmServerSslContext(HTTPBIN_KEYSTORE_PATH);
        }

        if (normalizedHost.equals("example.com")) {
            return createMitmServerSslContext(EXAMPLE_KEYSTORE_PATH);
        }

        throw new IllegalArgumentException("No MITM certificate configured for host: " + host);
    }

    private SSLContext createMitmServerSslContext(String keyStorePath) throws Exception {
        char[] password = "changeit".toCharArray(); // why hard coding this? maybe .env for security reasons

        KeyStore keyStore = KeyStore.getInstance("PKCS12");

        try (InputStream keyStoreInput = new FileInputStream(keyStorePath)) {
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

    public boolean hasCertificateFor(String host) {
        String normalizedHost = host.toLowerCase();

        return normalizedHost.equals("httpbin.org")
            || normalizedHost.equals("example.com");
    }

    
}
