package Proxy;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

public class InterceptQueue {
    private volatile boolean intercepting = false;
    private volatile InterceptListener listener;
    private List<InterceptedRequest> pending = new CopyOnWriteArrayList<>();

    public void setIntercepting(boolean on) {
        this.intercepting = on;
    }

    public void setListener(InterceptListener listener) {
        this.listener = listener;
    }

    public interface InterceptListener {
        void onQueueChange(List<InterceptedRequest> pending);
    }

    public ProxyRequest intercept(ProxyRequest request) {
        System.out.println("intercept() called, intercepting=" + intercepting);

        if (!intercepting || listener == null) {
            return request;
        }

        InterceptedRequest intercepted = new InterceptedRequest(request);
        pending.add(intercepted);
        listener.onQueueChange(List.copyOf(pending));

        try {
            return intercepted.future().get();
        } catch (CancellationException e) {
            return null;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            pending.remove(intercepted);
            listener.onQueueChange(List.copyOf(pending));
        }
    }
}
