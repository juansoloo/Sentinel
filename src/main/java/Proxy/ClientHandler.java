package Proxy;

import MVC.Models.ProxyModel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static Proxy.SocketUtils.closeQuietly;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final ProxyModel proxyModel;
    private final HttpRequestParser requestParser;
    private final HttpResponseReader responseReader;
    private final MitmTunnel mitmTunnel;
    private final ConnectTunnel connectTunnel;


    public ClientHandler(Socket clientSocket, ProxyModel proxyModel) {
        this.clientSocket = clientSocket;
        this.proxyModel = proxyModel;

        this.requestParser = new HttpRequestParser();
        this.responseReader = new HttpResponseReader();
        this.mitmTunnel = new MitmTunnel(
                proxyModel,
                requestParser,
                responseReader
        );
        this.connectTunnel = new ConnectTunnel();
    }

    private HostAndPort parseHost(String hostHeader) {
        if (hostHeader == null || hostHeader.isEmpty()) {
            throw new IllegalArgumentException("Missing host header");
        }

        String targetHost = hostHeader;
        int targetPort = 80;

        if (hostHeader.contains(":")) {
            String[] parts = hostHeader.split(":",2);
            targetHost = parts[0];

            if (targetHost.isEmpty() || parts.length != 2 || parts[1].isEmpty()) {
                throw new IllegalArgumentException("Invalid host header: " + hostHeader);
            }

            try {
                targetPort = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid port in host header: " + hostHeader, e);
            }
        }

        if (targetPort < 1 || targetPort > 65535) {
            throw new IllegalArgumentException("Port out of range: " + targetPort);
        }

        return new HostAndPort(targetHost, targetPort);
    }

    private void handleConnect(HostAndPort target,
                                InputStream clientInput,
                                OutputStream clientOutput)
            throws IOException {

        if (target.port() != 443 && target.port() != 8443) {
            throw new IllegalArgumentException("CONNECT port not allowed: " + target.port());
        }

        try (Socket serverSocket = new Socket()) {
            int timeout = 15000;

            serverSocket.connect(
                    new InetSocketAddress(target.host(), target.port()),
                    timeout);

            serverSocket.setSoTimeout(0);
            clientSocket.setSoTimeout(0);

            System.out.println("Connect tunnel established to " +
                    target.host() + ":" + target.port());

            clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n"
                    .getBytes(StandardCharsets.ISO_8859_1));

            clientOutput.flush();

            InputStream serverInput = serverSocket.getInputStream();
            OutputStream serverOutput = serverSocket.getOutputStream();

            Thread clientToServer = new Thread(() -> {
                try {
                    SocketUtils.copyTunnel(clientInput, serverOutput);
                } catch (IOException e) {
                    if (!"Socket closed".equals(e.getMessage())) {
                        System.out.println("Tunnel client->server closed: " + e.getMessage());
                    }
                } finally {
                    closeQuietly(serverSocket);
                    closeQuietly(clientSocket);
                }
            }, "tunnel-client-to-server-" + target.host());

            Thread serverToClient = new Thread(() -> {
                try {
                    SocketUtils.copyTunnel(serverInput, clientOutput);
                } catch (IOException e) {
                    if (!"Socket closed".equals(e.getMessage())) {
                        System.out.println("Tunnel server->client closed: " + e.getMessage());
                    }
                } finally {
                    closeQuietly(serverSocket);
                    closeQuietly(clientSocket);
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
        }
    }

    private ProxyResponse forwardRequest(String host,
                                        int targetPort,
                                        String path,
                                        List<String> headers,
                                        String HTTPType,
                                        String method,
                                        InputStream clientInput,
                                        int contentLength,
                                        OutputStream clientOutput)
            throws IOException {
        try (Socket serverSocket = new Socket()) {

            serverSocket.connect(new InetSocketAddress(host, targetPort), 15000);
            serverSocket.setSoTimeout(15000);

            System.out.println("\nConnecting to target: " + host + ":" + targetPort);

            OutputStream serverOutput = serverSocket.getOutputStream();
            InputStream serverInput = serverSocket.getInputStream();

            StringBuilder forwardedRequest = new StringBuilder();

            forwardedRequest.append(method)
                    .append(" ")
                    .append(path)
                    .append(" ")
                    .append(HTTPType)
                    .append("\r\n");

            for (String header : headers) {
                int colonIndex = header.indexOf(":");

                if (colonIndex == -1) {
                    continue;
                }

                String name = header.substring(0, colonIndex).trim();

                if (name.equalsIgnoreCase("Proxy-Connection")
                        || name.equalsIgnoreCase("Connection")) {
                    continue;
                }

                forwardedRequest.append(header).append("\r\n");
            }

            forwardedRequest.append("Connection: close\r\n");
            forwardedRequest.append("\r\n");

            System.out.println("\n");
            System.out.println("===== FORWARDED REQUEST START =====");
            System.out.println("Forwarded request length: " + forwardedRequest.length());
            System.out.println("Forwarded request raw:");
            System.out.print(forwardedRequest
                    .toString()
                    .replace("\r", "\\r")
                    .replace("\n", "\\n\n"));
            System.out.println("===== FORWARDED REQUEST END =====");
            System.out.println("\n");

            serverOutput.write(forwardedRequest.toString().getBytes(StandardCharsets.ISO_8859_1));

            if (contentLength > 0) {
                System.out.println("Forwarding request body bytes: " + contentLength);
                SocketUtils.copyExact(clientInput, serverOutput, contentLength);
                System.out.println("Finished forwarding request body");
            }

            serverOutput.flush();

            System.out.println(" ***************** server response ******************");
            System.out.println("\n");
            ProxyResponse response = responseReader.handleServerResponse(serverInput, clientOutput);
            System.out.println(" ***************** server response ******************");
            System.out.println("\n");

            return response;
        }
    }

    private boolean shouldMitm(HostAndPort target) {
        String host = target.host().toLowerCase();

        return host.equals("example.com")
                || host.equals("httpbin.org");
    }

    // only allows targets defined in this function
    private boolean isAllowedTarget(HostAndPort target) {
        String host = target.host().toLowerCase();

        return !host.equals("localhost")
                && !host.equals("127.0.0.1")
                && !host.startsWith("127.")
                && !host.equals("0.0.0.0")
                && !host.equals("::1");
    }

    @Override
    public void run() {
        HostAndPort target;
        String host = "unknown";
        int port = -1;

        try (InputStream clientInput = clientSocket.getInputStream();
                OutputStream clientOutput = clientSocket.getOutputStream()) {

            clientSocket.setSoTimeout(15000);

            ProxyRequest request;

            // parser try/catch block
            try {
                byte[] headerBytes = requestParser.readHeaderBytes(clientInput);
                request = requestParser.parseClientRequest(headerBytes);
            } catch (java.net.SocketTimeoutException e) {
                sendErrorResponse(
                        clientOutput,
                        408,
                        "Request Timeout",
                        "Timed out waiting for request headers");
                return;
            } catch (UnsupportedOperationException e) {
                sendErrorResponse(clientOutput,
                        501,
                        "Not Implemented",
                        e.getMessage());
                return;
            } catch(IllegalArgumentException e) {
                sendErrorResponse(clientOutput,
                        400,
                        "Bad Request",
                        e.getMessage());
                return;
            }

            if (request == null) {
                sendErrorResponse(clientOutput,
                        400,
                        "Bad Request",
                        "Bad Request");
                return;
            }

            if (request.method().equalsIgnoreCase("CONNECT")) {
                System.out.println("CONNECT request: " + request.path());

                try {
                    HostAndPort connectTarget = parseHost(request.path());

                    if (!isAllowedTarget(connectTarget)) {
                        sendErrorResponse(
                                clientOutput,
                                403,
                                "Forbidden",
                                "Target host is allowed"
                        );

                        return;
                    }

                    if (shouldMitm(connectTarget)) {
                        System.out.println("Using MITM for: " + connectTarget.host());

                        mitmTunnel.handle(
                                connectTarget,
                                clientSocket,
                                clientOutput);
                    } else {
                        System.out.println("Using normal tunnel for: " + request.path());

                        handleConnect(
                                connectTarget,
                                clientInput,
                                clientOutput);
                    }
                } catch (IllegalArgumentException e) {
                    sendErrorResponse(clientOutput,
                            400,
                            "Bad Request",
                            e.getMessage());
                } catch (IOException e) {
                    sendErrorResponse(clientOutput,
                            502,
                            "Bad Gateway",
                            "Tunnel Failed");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                return;
            }

            try {
                target = parseHost(request.host());

                if (!isAllowedTarget(target)) {
                    sendErrorResponse(
                        clientOutput,
                        403,
                        "Forbidden",
                        "Target host is not allowed");

                    return;
                }
            } catch (IllegalArgumentException e) {
                sendErrorResponse(clientOutput,
                        400,
                        "Bad Request",
                        e.getMessage());
                return;
            }

            System.out.println("========== ABOUT TO FORWARD ==========");
            System.out.println("\n");
            System.out.println("method= " + request.method());
            System.out.println("host= " + target.host());
            System.out.println("port= " + target.port());
            System.out.println("path= " + request.path());
            System.out.println("httpVersion= " + request.httpVersion());

            System.out.println("Headers:");

            for (String header : request.headers()) {
                System.out.println(header);
            }

            System.out.println("======================================");

            host = target.host();
            port = target.port();

            try {
                ProxyResponse response = forwardRequest(
                        target.host(),
                        target.port(),
                        request.path(),
                        request.headers(),
                        request.httpVersion(),
                        request.method(),
                        clientInput,
                        request.contentLength(),
                        clientOutput);

                HttpTransaction transaction = new HttpTransaction(request, response);
                proxyModel.addTransaction(transaction);

                System.out.println("\n");
                System.out.println(
                        transaction.request().method() + " " +
                                transaction.request().host() + " " +
                                transaction.request().path() + " -> " +
                                transaction.response().statusCode());
                System.out.println("\n");

            } catch (IOException e) {
                System.out.println("Forwarding failed:");
                System.out.println("host: " + host);
                System.out.println("port: " + port);
                System.out.println("error class: " + e.getClass().getName());
                System.out.println("message: " + e.getMessage());

                e.printStackTrace();
                try {
                    sendErrorResponse(clientOutput, 502, "Bad Gateway", "Bad Gateway");
                } catch (IOException ex) {
                    System.out.println("client already disconnected.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendErrorResponse(OutputStream output, int statusCode, String reasonPhrase, String bodyMessage) throws IOException {
        byte[] bodyBytes = bodyMessage.getBytes(StandardCharsets.UTF_8);

        String response = "HTTP/1.1 " + statusCode + " " + reasonPhrase + "\r\n" +
                        "Content-Type: text/plain" + "\r\n" +
                        "Content-Length: " + bodyBytes.length + "\r\n" +
                        "Connection: close" + "\r\n" +
                        "\r\n";

        output.write(response.getBytes(StandardCharsets.ISO_8859_1));
        output.write(bodyBytes);
        output.flush();
    }
}