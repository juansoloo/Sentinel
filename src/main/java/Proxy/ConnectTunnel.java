package Proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ConnectTunnel {
    public void handle(HostAndPort target,
                       Socket clientSocket,
                       InputStream clientInput,
                       OutputStream clientOutput)
            throws IOException {

        if (target.port() != 443 && target.port() != 8443) {
            throw new IllegalArgumentException("CONNECT port not allowed: " + target.port());
        }

        Socket serverSocket = new Socket();

        try {
            int timeout = 15000;

            serverSocket.connect(
                    new InetSocketAddress(target.host(), target.port()),
                    timeout);

            serverSocket.setSoTimeout(90000);
            clientSocket.setSoTimeout(90000);

            clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n"
                    .getBytes(StandardCharsets.ISO_8859_1));

            clientOutput.flush();

            InputStream serverInput = serverSocket.getInputStream();
            OutputStream serverOutput = serverSocket.getOutputStream();

            Thread clientToServer = new Thread(() -> {
                try {
                    SocketUtils.copyTunnel(clientInput, serverOutput);
                } catch (IOException e) {
                    // Tunnel shutdown is usually initiated by either peer closing the socket.
                } finally {
                    SocketUtils.closeQuietly(serverSocket);
                    SocketUtils.closeQuietly(clientSocket);
                }
            }, "tunnel-client-to-server-" + target.host());

            Thread serverToClient = new Thread(() -> {
                try {
                    SocketUtils.copyTunnel(serverInput, clientOutput);
                } catch (IOException e) {
                    // Tunnel shutdown is usually initiated by either peer closing the socket.
                } finally {
                    SocketUtils.closeQuietly(serverSocket);
                    SocketUtils.closeQuietly(clientSocket);
                }
            }, "tunnel-server-to-client-" + target.host());

            clientToServer.start();
            serverToClient.start();

            try {
                clientToServer.join();
                serverToClient.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

        } catch (IOException e) {
            SocketUtils.closeQuietly(serverSocket);
            throw e;
        }
    }
}
