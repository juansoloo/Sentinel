package Proxy;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * Root CA helpers currently load the root keystore twice if both
 * private key and cert are needed. That is acceptable for now.
 * We can optimize later.
 */
public class CertificateManager {
    private static final String ROOT_CA_KEYSTORE_PATH = "certs/root-ca.p12";
    private static final String ROOT_CA_CERT_PATH = "certs/root-ca.crt";
    private static final String ROOT_CA_CERT_ALIAS = "sentinel-root-ca";
    private static final String CERT_CACHE_DIR = "cert-cache";
    private static final char[] KEYSTORE_PASSWORD = loadPassword();
    private final CertificateGenerator certificateGenerator;

    private static char[] loadPassword() {
        Path passwordPath = Path.of(System.getProperty("user.home"), ".sentinel", "keystore.pwd");
        char[] pwdFile;

        try {
            if (Files.exists(passwordPath)) {
                pwdFile = Files.readString(passwordPath, StandardCharsets.UTF_8).trim().toCharArray();
            } else {
                SecureRandom secureRandom = new SecureRandom();
                byte[] salty = new byte[32];

                secureRandom.nextBytes(salty);
                String pwdStr = Base64.getEncoder().encodeToString(salty);
                pwdFile = pwdStr.toCharArray();

                Files.createDirectories(passwordPath.getParent());
                Files.writeString(
                        passwordPath,
                        pwdStr,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return pwdFile;
    }

    private void clearCertificateCache() throws Exception {
        Path cacheDir = Path.of(CERT_CACHE_DIR);

        if (!Files.exists(cacheDir)) {
            return;
        }

        try (var paths = Files.list(cacheDir)) {
            for (Path path : paths.toList()) {
                if (Files.isRegularFile(path)) {
                    Files.delete(path);
                }
            }
        }
    }

    private void exportRootCaCertificate(X509Certificate rootCaCertificate) throws Exception {
        Path rootCaCertPath = Path.of(ROOT_CA_CERT_PATH);

        Files.createDirectories(rootCaCertPath.getParent());

        String encodedCertificate = Base64.getMimeEncoder(64, "\n".getBytes())
            .encodeToString(rootCaCertificate.getEncoded());
        
        String pem = "-----BEGIN CERTIFICATE-----\n"
            + encodedCertificate
            + "\n-----END CERTIFICATE-----\n";

        Files.writeString(
            rootCaCertPath,
            pem,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING);
    }

    private boolean isValidHost(String host) {
        if (host == null) {
            return false;
        }

        String normalizedHost = normalizeHost(host);

        if (normalizedHost.isEmpty()) {
            return false;
        }

        return normalizedHost.matches("[a-z0-9.-]+")
                && !normalizedHost.startsWith(".")
                && !normalizedHost.endsWith(".")
                && !normalizedHost.contains("..");
    }

    private void saveRootCaKeyStore(
        KeyPair rootCaKeyPair,
        X509Certificate rootCaCertificate
    ) throws Exception {
        Path rootCaPath = Path.of(ROOT_CA_KEYSTORE_PATH);
        Files.createDirectories(rootCaPath.getParent());

        KeyStore rootStore = KeyStore.getInstance("PKCS12");
        rootStore.load(null, KEYSTORE_PASSWORD);

        rootStore.setKeyEntry(
            ROOT_CA_CERT_ALIAS,
            rootCaKeyPair.getPrivate(),
            KEYSTORE_PASSWORD,
            new Certificate[]{rootCaCertificate});

        try (OutputStream output = Files.newOutputStream(
                rootCaPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            rootStore.store(output, KEYSTORE_PASSWORD);
        }
    }

    private void ensureRootCaExists() throws Exception {
        if (Files.exists(Path.of(ROOT_CA_KEYSTORE_PATH))) {
            return;
        }

        KeyPair rootCaKeyPair = certificateGenerator.generateRootCaKeyPair();
        X509Certificate rootCaCertificate = certificateGenerator.generateRootCaCertificate(rootCaKeyPair);

        saveRootCaKeyStore(rootCaKeyPair, rootCaCertificate);
        exportRootCaCertificate(rootCaCertificate);

        System.out.println("Created Sentinel Root CA at " + ROOT_CA_CERT_PATH);
        
        clearCertificateCache();
    }

    public CertificateManager() {
        this.certificateGenerator = new CertificateGenerator();
    }

    public SSLContext createServerContextFor(String host) throws Exception {
        if (!isValidHost(host)) {
            throw new IllegalArgumentException("Invalid host for certificate generation: " + host);
        }
        
        String normalizedHost = normalizeHost(host);

        if (!cachedCertificateExists(normalizedHost)) {
            generateAndCacheCertificateFor(normalizedHost);
        }

        return loadServerContextFromStore(
            cachePathFor(normalizedHost).toString()
        );
    }

    private SSLContext loadServerContextFromStore(
            String keyStorePath)
        throws Exception {

        KeyStore keyStore = KeyStore.getInstance("PKCS12");

        try (InputStream keyStoreInput = new FileInputStream(keyStorePath)) {
            keyStore.load(keyStoreInput, KEYSTORE_PASSWORD);
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());

        keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(
                keyManagerFactory.getKeyManagers(),
                null,
                null
        );

        return sslContext;
    }

    private KeyStore loadRootCaKeyStore() throws Exception {
        ensureRootCaExists();
        
        KeyStore rootStore = KeyStore.getInstance("PKCS12");

        try (InputStream input = new FileInputStream(ROOT_CA_KEYSTORE_PATH)) {
            rootStore.load(input, KEYSTORE_PASSWORD);
        }

        return rootStore;
    }

    private PrivateKey loadRootCaPrivateKey() throws Exception {
        KeyStore rootStore = loadRootCaKeyStore();

        return (PrivateKey) rootStore.getKey(
            ROOT_CA_CERT_ALIAS,
            KEYSTORE_PASSWORD
        );
    }

    private X509Certificate loadRootCaCertificate() throws Exception {
        KeyStore rootStore = loadRootCaKeyStore();

        return (X509Certificate) rootStore.getCertificate(ROOT_CA_CERT_ALIAS);
    }

    private void saveHostKeyStore(
            String host,
            KeyPair hostKeyPair,
            X509Certificate hostCertificate,
            X509Certificate caCertificate
    ) throws Exception {
        Path cachePath = cachePathFor(host);

        Files.createDirectories(cachePath.getParent());

        KeyStore hostStore = KeyStore.getInstance("PKCS12");
        hostStore.load(null, KEYSTORE_PASSWORD);

        Certificate[] certificateChain = new Certificate[] {
            hostCertificate,
            caCertificate
        };

        hostStore.setKeyEntry(
            host,
            hostKeyPair.getPrivate(),
            KEYSTORE_PASSWORD,
            certificateChain);

        try (OutputStream output = Files.newOutputStream(
                cachePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            hostStore.store(output, KEYSTORE_PASSWORD);
        }
    }

    private void generateAndCacheCertificateFor(String host) throws Exception {
        String normalizedHost = normalizeHost(host);

        PrivateKey caPrivateKey = loadRootCaPrivateKey();
        X509Certificate caCertificate = loadRootCaCertificate();
        KeyPair hostKeyPair = certificateGenerator.generateHostKeyPair();

        X509Certificate hostCertificate = certificateGenerator.generateHostCertificate(
            normalizedHost,
            hostKeyPair,
            caPrivateKey,
            caCertificate);
        
        saveHostKeyStore(
            normalizedHost,
            hostKeyPair,
            hostCertificate,
            caCertificate);
    }

    private String normalizeHost(String host) {
        return host.toLowerCase();
    }
    
    private Path cachePathFor(String host) {
        return Path.of(CERT_CACHE_DIR, normalizeHost(host) + ".p12");
    }
    
    private boolean cachedCertificateExists(String host) {
        return Files.exists(cachePathFor(host));
    }

    public boolean canMitmHost(String host) {
        return isValidHost(host);
    }

    public void initializeRootCa() throws Exception {
        ensureRootCaExists();
    }
}
