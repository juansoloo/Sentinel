import MVC.Models.ProxyModel;
import Proxy.HttpProxy;

public class Main {
    public static void main(String[] args) {
        ProxyModel proxyModel = new ProxyModel();
        HttpProxy httpProxy = new HttpProxy(8080, proxyModel);
        httpProxy.start();
    }
}