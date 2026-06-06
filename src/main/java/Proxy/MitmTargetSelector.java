package Proxy;

public class MitmTargetSelector {
    private final CertificateManager certificateManager;

    public MitmTargetSelector(CertificateManager certificateManager) {
        this.certificateManager = certificateManager;
    }

    public boolean shouldMitm(HostAndPort target) {
        return certificateManager.canMitmHost(target.host());
    }
}
