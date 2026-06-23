package Proxy;

import java.util.ArrayList;
import java.util.List;

public record ProxyRequest(String method,
                            String path,
                            String httpVersion,
                            String host,
                            List<String> headers,
                            int contentLength,
                            byte[] body) {

    public ProxyRequest copy() {
        List<String> copiedHeaders = new ArrayList<>(this.headers());
        byte[] copiedBody;
        
        if (this.body() == null) {
            copiedBody = null;
        } else {
            copiedBody = this.body().clone();
        }

        return new ProxyRequest(
                this.method(),
                this.path(),
                this.httpVersion(),
                this.host(),
                copiedHeaders,
                this.contentLength(),
                copiedBody
        );
    }
}