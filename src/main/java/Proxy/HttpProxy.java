package Proxy;

import MVC.Models.ProxyModel;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpProxy {
    private final int port;
    private ServerSocket serverSocket;
    private boolean running;
    private final ProxyModel proxyModel;
    private final CertificateManager certificateManager;
    private final InterceptQueue interceptQueue;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(20);

    public HttpProxy(int port, ProxyModel proxyModel, InterceptQueue interceptQueue) {
        this.port = port;
        this.proxyModel = proxyModel;
        this.certificateManager = new CertificateManager();
        this.interceptQueue = interceptQueue;
    }

    public void start() throws Exception {
        running = true;

        certificateManager.initializeRootCa();
        
        try {
            serverSocket = new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
            System.out.println("Proxy listening on port: " + port);

            while (running) {
                Socket clientSocket = serverSocket.accept();

                ClientHandler handler = new ClientHandler(clientSocket, proxyModel, certificateManager, interceptQueue);
                threadPool.submit(handler);
            }
        } catch (IOException e) {
            if (running) {
                System.out.println("Proxy server failed: " + e.getMessage());
            }
        } finally {
            serverSocket = null;
        }
    }

    public void stop() {
        running = false;

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.out.println("Failed to stop proxy: " + e.getMessage());
            }
        }
    }
}
