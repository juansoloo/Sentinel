package MVC.Controllers;

import MVC.Models.ProxyModel;
import MVC.Views.FilterView;
import MVC.Views.ProxyView;
import Proxy.HttpTransaction;
import Proxy.ProxyRequest;

public class ProxyController {
    private final ProxyView proxyView;
    private final RepeaterController repeaterController;
    private final FilterView filterView;
    private final ProxyModel proxyModel;
    private int selectedModelRow = -1;

    public ProxyController(ProxyView proxyView,
                            RepeaterController repeaterController,
                            FilterView filterView,
                            ProxyModel proxyModel) {
        this.proxyView = proxyView;
        this.repeaterController = repeaterController;
        this.filterView = filterView;
        this.proxyModel = proxyModel;

    }

    public void onAddHostFilter() {
        HttpTransaction transaction = getSelectedTransaction();

        if (transaction == null) {
            return;
        }

        String host = transaction.request().host();
        filterView.addHostFilter(host);
    }

    public void onProxyRowSelected(int modelRow) {
        selectedModelRow = modelRow;

        HttpTransaction transaction = proxyModel.getTransactionSnapshotAt(modelRow);
        proxyView.displayTransaction(transaction);
    }

    public void onClearHistory() {
        proxyModel.clearTransactions();
        proxyView.clearHistoryDisplay();

        selectedModelRow = -1;
    }

    public void onSendToRepeater() {
        if (selectedModelRow < 0) {
            return;
        }

        ProxyRequest req = proxyModel.getRequestSnapshotAt(selectedModelRow);

        if (req == null) {
            return;
        }

        repeaterController.loadRequest(req);
    }

    private HttpTransaction getSelectedTransaction() {
        if (selectedModelRow < 0) {
            return null;
        }
        
        return proxyModel.getTransactionSnapshotAt(selectedModelRow);
    }
}
