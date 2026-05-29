package Proxy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import MVC.Models.ProxyModel;

public class HttpProxy {
    private int port;
    private ServerSocket serverSocket;
    private boolean running;
    private final ProxyModel proxyModel;
    private final CertificateManager certificateManager;

    public HttpProxy(int port, ProxyModel proxyModel) {
        this.port = port;
        this.proxyModel = proxyModel;
        this.certificateManager = new CertificateManager();
    }

    public void start() throws Exception {
        running = true;

        certificateManager.initializeRootCa();
        
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Proxy listening on port: " + port);

            while (running) {
                Socket clientSocket = serverSocket.accept();

                ClientHandler handler = new ClientHandler(clientSocket, proxyModel, certificateManager);
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
