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


    public HttpProxy(int port, ProxyModel proxyModel) {
        this.port = port;
        this.proxyModel = proxyModel;
    }

    public void start() {
        running = true;

        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Proxy listening on port: " + port);

            while (running) {
                Socket clientSocket = serverSocket.accept();

                ClientHandler handler = new ClientHandler(clientSocket, proxyModel);
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
