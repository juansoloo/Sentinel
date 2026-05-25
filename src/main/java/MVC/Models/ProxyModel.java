package MVC.Models;

import MVC.Interfaces.ProxyModelListener;
import Proxy.HttpTransaction;

import java.util.ArrayList;
import java.util.List;

public class ProxyModel implements ProxyModelListener {
    private final List<HttpTransaction> transactions = new ArrayList<>();
    private final List<ProxyModelListener> listeners = new ArrayList<>();

    public void addTransaction(HttpTransaction transaction) {
        transactions.add(transaction);
        update(transaction);
    }

    public List<HttpTransaction> getTransactions() {
        return List.copyOf(transactions);
    }

    public void addListener(ProxyModelListener listener) {
        listeners.add(listener);
    }

    public void update(HttpTransaction transaction) {
        for (ProxyModelListener listener : listeners) {
            listener.update(transaction);
        }
    }
}
