import Proxy.HttpProxy;

public class Main {
    public static void main(String[] args) {
        HttpProxy httpProxy = new HttpProxy(8080);
        httpProxy.start();
    }
}