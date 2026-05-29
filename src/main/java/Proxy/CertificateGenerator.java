package Proxy;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class CertificateGenerator {
    public KeyPair generateHostKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        
        return keyPairGenerator.generateKeyPair();
    }

    public KeyPair generateRootCaKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        
        return keyPairGenerator.generateKeyPair();
    }
    
    public X509Certificate generateHostCertificate(
            String host,
            KeyPair hostKeyPair,
            PrivateKey caPrivateKey,
            X509Certificate caCertificate
    ) throws Exception {
        long now = System.currentTimeMillis();

        Date notBefore = new Date(now - 60_000);
        Date notAfter = new Date(now + 365L * 24 * 60 * 60 * 1000);

        BigInteger serialNumber = new BigInteger(64, new SecureRandom());

        X500Name issuer = new X500Name(caCertificate.getSubjectX500Principal().getName());
        X500Name subject = new X500Name("CN=" + host);

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
            issuer,
            serialNumber,
            notBefore,
            notAfter,
            subject,
            hostKeyPair.getPublic());

        certBuilder.addExtension(
            Extension.basicConstraints,
            true,
            new BasicConstraints(false));

        certBuilder.addExtension(
            Extension.keyUsage,
            true,
            new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));

        certBuilder.addExtension(
            Extension.extendedKeyUsage,
            false,
            new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
        
        certBuilder.addExtension(
            Extension.subjectAlternativeName,
            false,
            new GeneralNames(new GeneralName(GeneralName.dNSName, host)));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
            .build(caPrivateKey);

        X509CertificateHolder certificateHolder = certBuilder.build(signer);

        return new JcaX509CertificateConverter()
            .getCertificate(certificateHolder);
    }

    public X509Certificate generateRootCaCertificate(
        KeyPair rootCaKeyPair
    ) throws Exception {
        // self signed ca cert
        long now = System.currentTimeMillis();

        Date notBefore = new Date(now - 60_000);
        Date notAfter = new Date(now + 10L * 365 * 24 * 60 * 60 * 1000);

        BigInteger serialNumber = new BigInteger(64, new SecureRandom());

        X500Name subject = new X500Name("CN=Sentinel Root CA");

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
            subject,
            serialNumber,
            notBefore,
            notAfter,
            subject,
            rootCaKeyPair.getPublic());

        certBuilder.addExtension(
            Extension.basicConstraints,
            true,
            new BasicConstraints(true));

        certBuilder.addExtension(
            Extension.keyUsage,
            true,
            new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
            .build(rootCaKeyPair.getPrivate());

        X509CertificateHolder certificateHolder = certBuilder.build(signer);

        return new JcaX509CertificateConverter()
            .getCertificate(certificateHolder);
    }
}
