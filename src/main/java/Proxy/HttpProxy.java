package Proxy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class HttpProxy {
    private int port;
    private ServerSocket serverSocket;
    private boolean running;

    public HttpProxy(int port) {
        this.port = port;
    }

    public void start() {
        running = true;

        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Proxy listening on port: " + port);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("client connected: " + clientSocket.getInetAddress() + "\n");

                ClientHandler handler = new ClientHandler(clientSocket);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            if (running) {
                e.printStackTrace();
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
                e.printStackTrace();
            }
        }
    }
}
