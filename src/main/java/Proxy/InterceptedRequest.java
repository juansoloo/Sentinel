package Proxy;

import java.util.concurrent.CompletableFuture;

public class InterceptedRequest {
    private final ProxyRequest request;
    private final CompletableFuture<ProxyRequest> future = new CompletableFuture<>();

    public InterceptedRequest(ProxyRequest request) {
        this.request = request;
    }

    public ProxyRequest request() {
        return request;
    }

    public void forwardEdited(ProxyRequest edited) {
        future.complete(edited);
    }

    public void drop() {
        future.cancel(false);
    }

    CompletableFuture<ProxyRequest> future() {
        return future;
    }
}
