import javax.swing.JFrame;
import javax.swing.JTabbedPane;

import MVC.Controllers.ProxyController;
import MVC.Controllers.RepeaterController;
import MVC.Models.ProxyModel;
import MVC.Views.FilterView;
import MVC.Views.InterceptView;
import MVC.Views.ProxyTableContextMenu;
import MVC.Views.ProxyView;
import MVC.Views.RepeaterView;
import Proxy.HttpProxy;
import Proxy.HttpResponseReader;
import Proxy.InterceptQueue;
import Proxy.RepeaterClient;

public class Main {
    public static void main(String[] args) throws Exception {
        ProxyModel proxyModel = new ProxyModel();
        InterceptQueue interceptQueue = new InterceptQueue();

        ProxyView proxyView = new ProxyView();
        InterceptView interceptView = new InterceptView(interceptQueue);
        RepeaterView repeaterView = new RepeaterView();
        FilterView filterView = new FilterView(
                proxyView.getSorter(),
                proxyView.getListModel()
        );

        RepeaterClient repeaterClient = new RepeaterClient(new HttpResponseReader());
        RepeaterController repeaterController = new RepeaterController(repeaterView, repeaterClient);
        repeaterView.setOnSend(repeaterController::onSend);

        ProxyController controller = new ProxyController(
                proxyView,
                repeaterController,
                filterView,
                proxyModel
        );

        proxyView.setOnRowSelected(controller::onProxyRowSelected);

        new ProxyTableContextMenu(
                proxyView.getEndpointTable(),
                controller::onAddHostFilter,
                controller::onClearHistory,
                controller::onSendToRepeater
        );

        JTabbedPane interceptTab = new JTabbedPane();
        interceptTab.addTab("Proxy", proxyView.getRoot());
        interceptTab.addTab("Intercept", interceptView.getRoot());
        interceptTab.addTab("Filter", filterView.getRoot());
        interceptTab.addTab("Repeater", repeaterView.getRoot());

        proxyModel.addListener(proxyView);
        
        JFrame frame = new JFrame("Sentinel");
        frame.setContentPane(interceptTab);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    
        HttpProxy httpProxy = new HttpProxy(8080, proxyModel, interceptQueue);
        
        new Thread(() -> {
            try {
                httpProxy.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "proxy-main").start();
    }
}
