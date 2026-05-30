package Proxy;

import MVC.Models.ProxyModel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final ProxyModel proxyModel;
    private final HttpRequestParser requestParser;
    private final HttpResponseReader responseReader;
    private final MitmTunnel mitmTunnel;
    private final PlainHttpForwarder plainHttpForwarder;
    private final ConnectTunnel connectTunnel;
    private final MitmTargetSelector mitmTargetSelector;
    private final InterceptQueue interceptQueue;

    public ClientHandler(Socket clientSocket, ProxyModel proxyModel, CertificateManager certificateManager, InterceptQueue interceptQueue) {
        this.clientSocket = clientSocket;
        this.proxyModel = proxyModel;
        this.interceptQueue = interceptQueue;
    
        this.requestParser = new HttpRequestParser();
        this.responseReader = new HttpResponseReader();
        this.mitmTargetSelector = new MitmTargetSelector(certificateManager);

        this.plainHttpForwarder = new PlainHttpForwarder(responseReader);
        this.mitmTunnel = new MitmTunnel(
                proxyModel,
                requestParser,
                responseReader,
                certificateManager,
                interceptQueue
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

                    if (mitmTargetSelector.shouldMitm(connectTarget)) {
                        mitmTunnel.handle(
                                connectTarget,
                                clientSocket,
                                clientOutput);
                    } else {
                        connectTunnel.handle(
                                connectTarget,
                                clientSocket,
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

            host = target.host();
            port = target.port();

            try {
                request = interceptQueue.intercept(request);
                if (request == null) return;

                ProxyResponse response = plainHttpForwarder.forward(target,
                    request,
                    clientInput,
                    clientOutput);
                
                HttpTransaction transaction = new HttpTransaction(request, response);
                proxyModel.addTransaction(transaction);

                System.out.println(
                        request.method() + " " +
                                request.host() + " " +
                                request.path() + " -> " +
                                response.statusCode());

            } catch (IOException e) {
                System.out.println("Forwarding failed for " + host + ":" + port + " - " + e.getMessage());
                try {
                    sendErrorResponse(clientOutput, 502, "Bad Gateway", "Bad Gateway");
                } catch (IOException ex) {
                    System.out.println("Unable to send error response: " + ex.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Client handling failed: " + e.getMessage());
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