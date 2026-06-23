package MVC.Models;

import java.util.ArrayList;
import java.util.List;

import MVC.Interfaces.ProxyModelListener;
import Proxy.HttpTransaction;
import Proxy.ProxyRequest;

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

    public synchronized void clearTransactions() {
        transactions.clear();
    }

    public synchronized List<HttpTransaction> getTransactions() {
        return List.copyOf(transactions);
    }

    public synchronized ProxyRequest getRequestSnapshotAt(int index) {
        if (index < 0 || index >= transactions.size()) {
            return null;
        }

        return transactions.get(index).request().copy();
    }

    public synchronized HttpTransaction getTransactionSnapshotAt(int index) {
        if (index < 0 || index >= transactions.size()) {
            return null;
        }

        return transactions.get(index);
    }

    public synchronized void addListener(ProxyModelListener listener) {
        listeners.add(listener);
    }
}