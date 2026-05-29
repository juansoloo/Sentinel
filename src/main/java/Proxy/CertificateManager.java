package Proxy;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

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
    private static final char[] GENERATED_CERT_PASSWORD = "changeit".toCharArray();
    
    private final CertificateGenerator certificateGenerator;

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

    private boolean rootCaExists() {
        return Files.exists(Path.of(ROOT_CA_KEYSTORE_PATH));
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
        rootStore.load(null, GENERATED_CERT_PASSWORD);

        rootStore.setKeyEntry(
            ROOT_CA_CERT_ALIAS,
            rootCaKeyPair.getPrivate(),
            GENERATED_CERT_PASSWORD,
            new Certificate[]{rootCaCertificate});

        try (OutputStream output = Files.newOutputStream(
                rootCaPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            rootStore.store(output, GENERATED_CERT_PASSWORD);
        }
    }

    private void ensureRootCaExists() throws Exception {
        if (rootCaExists()) {
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
            cachePathFor(normalizedHost).toString(),
            GENERATED_CERT_PASSWORD);
    }

    private SSLContext loadServerContextFromStore(
            String keyStorePath,
            char[] keyStorePassword)
        throws Exception {

        KeyStore keyStore = KeyStore.getInstance("PKCS12");

        try (InputStream keyStoreInput = new FileInputStream(keyStorePath)) {
            keyStore.load(keyStoreInput, keyStorePassword);
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());

        keyManagerFactory.init(keyStore, keyStorePassword);

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
            rootStore.load(input, GENERATED_CERT_PASSWORD);
        }

        return rootStore;
    }

    private PrivateKey loadRootCaPrivateKey() throws Exception {
        KeyStore rootStore = loadRootCaKeyStore();

        return (PrivateKey) rootStore.getKey(
            ROOT_CA_CERT_ALIAS,
            GENERATED_CERT_PASSWORD
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
        hostStore.load(null, GENERATED_CERT_PASSWORD);

        Certificate[] certificateChain = new Certificate[] {
            hostCertificate,
            caCertificate
        };

        hostStore.setKeyEntry(
            host,
            hostKeyPair.getPrivate(),
            GENERATED_CERT_PASSWORD,
            certificateChain);

        try (OutputStream output = Files.newOutputStream(
                cachePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            hostStore.store(output, GENERATED_CERT_PASSWORD);
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

    public boolean hasCertificateFor(String host) {
        return isValidHost(host);
    }

    public void initializeRootCa() throws Exception {
        ensureRootCaExists();
    }
}
