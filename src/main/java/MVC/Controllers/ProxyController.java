package MVC.Controllers;

import MVC.Models.ProxyModel;
import MVC.Views.FilterView;
import MVC.Views.InterceptView;
import MVC.Views.ProxyView;
import MVC.Views.RepeaterView;
import Proxy.HttpMessageFormatter;
import Proxy.HttpTransaction;
import Proxy.ProxyRequest;

public class ProxyController {
    private ProxyView proxyView;
    private InterceptView interceptView;
    private RepeaterView repeaterView;
    private FilterView filterView;
    private ProxyModel proxyModel;
    private int selectedModelRow = -1;

    public ProxyController(ProxyView proxyView,
                            InterceptView interceptView,
                            RepeaterView repeaterView,
                            FilterView filterView,
                            ProxyModel proxyModel
                        ) {
        this.proxyView = proxyView;
        this.interceptView = interceptView;
        this.repeaterView = repeaterView;
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

        String rawRequest = HttpMessageFormatter.renderRequest(req);
        repeaterView.loadRequest(rawRequest);
    }

    private HttpTransaction getSelectedTransaction() {
        if (selectedModelRow < 0) {
            return null;
        }
        
        return proxyModel.getTransactionSnapshotAt(selectedModelRow);
    }
}
