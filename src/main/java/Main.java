import javax.swing.JFrame;

import MVC.Models.ProxyModel;
import MVC.Views.ProxyView;
import Proxy.HttpProxy;

public class Main {
    public static void main(String[] args) throws Exception {
        ProxyModel proxyModel = new ProxyModel();
    
        ProxyView proxyView = new ProxyView();
        proxyModel.addListener(proxyView);
        
        JFrame frame = new JFrame("Sentinel");
        frame.setContentPane(proxyView.getRoot());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    
        HttpProxy httpProxy = new HttpProxy(8080, proxyModel);
        
        new Thread(() -> {
            try {
                httpProxy.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "proxy-main").start();
    }
}