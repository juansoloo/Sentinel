package Proxy;

public class MitmTargetSelector {
    public boolean shouldMitm(HostAndPort target) {
        String host = target.host().toLowerCase();

        return host.equals("example.com")
            || host.equals("httpbin.org");
    }
}
