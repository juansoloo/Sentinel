package MVC.Models;

import java.util.ArrayList;
import java.util.List;

import MVC.Interfaces.ProxyModelListener;
import Proxy.HttpTransaction;

public class ProxyModel {
    private final List<HttpTransaction> transactions = new ArrayList<>();
    private final List<ProxyModelListener> listeners = new ArrayList<>();

    public void addTransaction(HttpTransaction transaction) {
        List<ProxyModelListener> listenersSnapshot;

        synchronized (this) {
            transactions.add(transaction);
            listenersSnapshot = List.copyOf(listeners);
        }

        for (ProxyModelListener listener: listenersSnapshot) {
            listener.update(transaction);
        }
    }

    public synchronized List<HttpTransaction> getTransactions() {
        return List.copyOf(transactions);
    }

    public synchronized void addListener(ProxyModelListener listener) {
        listeners.add(listener);
    }
}