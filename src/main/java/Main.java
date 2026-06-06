import MVC.Models.ProxyModel;
import MVC.Views.FilterView;
import MVC.Views.InterceptView;
import MVC.Views.ProxyTableContextMenu;
import MVC.Views.ProxyView;
import Proxy.HttpProxy;
import Proxy.InterceptQueue;

import javax.swing.*;

public class Main {
    public static void main(String[] args) throws Exception {
        ProxyModel proxyModel = new ProxyModel();
        InterceptQueue interceptQueue = new InterceptQueue();
    
        ProxyView proxyView = new ProxyView();
        FilterView filterView = new FilterView(proxyView.getSorter(), proxyView.getListModel());

        ProxyTableContextMenu actionWindow = new ProxyTableContextMenu(
                proxyView.getEndpointTable(),
                proxyView.getListModel(),
                filterView::applyFilter,
                proxyView::clearHistory
        );

        InterceptView interceptView = new InterceptView(interceptQueue);

        JTabbedPane interceptTab = new JTabbedPane();
        interceptTab.addTab("Proxy", proxyView.getRoot());
        interceptTab.addTab("Intercept", interceptView.getRoot());
        interceptTab.addTab("Filter", filterView.getRoot());

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