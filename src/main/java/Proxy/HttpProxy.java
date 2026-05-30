package Proxy;

import MVC.Models.ProxyModel;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class HttpProxy {
    private int port;
    private ServerSocket serverSocket;
    private boolean running;
    private final ProxyModel proxyModel;
    private final CertificateManager certificateManager;
    private InterceptQueue interceptQueue;

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
            serverSocket = new ServerSocket(port);
            System.out.println("Proxy listening on port: " + port);

            while (running) {
                Socket clientSocket = serverSocket.accept();

                ClientHandler handler = new ClientHandler(clientSocket, proxyModel, certificateManager, interceptQueue);
                new Thread(handler).start();
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
