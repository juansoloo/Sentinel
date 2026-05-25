package Proxy;

import MVC.Models.ProxyModel;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.List;

import static Proxy.SocketUtils.closeQuietly;

public class MitmTunnel {
    private static final String MITM_KEYSTORE_PATH = "/Users/juansoloo/Documents/Sentinel/certs/mitm-httpbin.p12";
    private final ProxyModel proxyModel;
    private final HttpRequestParser requestParser;
    private final HttpResponseReader responseReader;

    public MitmTunnel(ProxyModel proxyModel,
                      HttpRequestParser requestParser,
                      HttpResponseReader responseReader) {
        this.proxyModel = proxyModel;
        this.requestParser = requestParser;
        this.responseReader = responseReader;
    }

    public void handle(HostAndPort target,
                       Socket clientSocket,
                       OutputStream clientOutput) throws Exception {
        if (target.port() != 443) {
            throw new IllegalArgumentException("MITM only supports port 443 for now");
        }

        SSLSocket tlsClientSocket = null;
        SSLSocket tlsServerSocket = null;

        try {
            // 1. tell  client the CONNECT tunnel is ready
            clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n"
                    .getBytes(StandardCharsets.ISO_8859_1)
            );
            clientOutput.flush();

            // 2. wrap the client socket as a TLS socket
            SSLContext mitmServerContext = createMitmServerSslContext();

            tlsClientSocket = (SSLSocket) mitmServerContext
                    .getSocketFactory()
                    .createSocket(
                            clientSocket,
                            clientSocket.getInetAddress().getHostAddress(),
                            clientSocket.getPort(),
                            false);

            System.out.println("Starting MITM client TLS handshake");
            tlsClientSocket.setUseClientMode(false);
            tlsClientSocket.startHandshake();
            System.out.println("MITM client TLS established for " + target.host());


            // 3. Connect to the real server using TLS as a client
            SSLSocketFactory serverSslFactory =
                    (SSLSocketFactory) SSLSocketFactory.getDefault();

            tlsServerSocket = (SSLSocket) serverSslFactory.createSocket(
                            target.host(),
                            target.port());

            System.out.println("Starting MITM server TLS handshake");
            tlsServerSocket.setUseClientMode(true);
            tlsServerSocket.startHandshake();
            System.out.println("MITM server TLS established to " + target.host());

            // 4. Now both streams are decrypted HTTP request to the TLS system
            InputStream decryptedClientInput = tlsClientSocket.getInputStream();
            OutputStream decryptedClientOutput = tlsClientSocket.getOutputStream();

            InputStream serverInput = tlsServerSocket.getInputStream();
            OutputStream serverOutput = tlsServerSocket.getOutputStream();

            // 5. for first time milestone, handle one HTTP request over the TLS stream
            while (true) {
                ProxyRequest httpsRequest;

                try {
                    byte[] headerBytes = requestParser.readHeaderBytes(decryptedClientInput);
                    httpsRequest = requestParser.parseClientRequest(headerBytes);
                } catch (IOException e) {
                    System.out.println("MITM client closed HTTPS connection or no more requests.");
                    return;
                }

                if (httpsRequest == null) {
                    throw new IOException("Invalid HTTPS request inside MITM tunnel");
                }

                System.out.println("MITM decrypted request: "
                        + httpsRequest.method() + " "
                        + httpsRequest.path());

                forwardMitmHttpsRequest(
                        httpsRequest,
                        decryptedClientInput,
                        serverOutput);

                ProxyResponse response = responseReader.handleServerResponse(
                        serverInput,
                        decryptedClientOutput);

                HttpTransaction transaction = new HttpTransaction(httpsRequest, response);

                proxyModel.addTransaction(transaction);


                if (usesConnectionClose(httpsRequest.headers())) {
                    System.out.println("Client requested Connection close.");
                    return;
                }
            }
        } catch (IOException e) {
            System.out.println("CONNECT handling failed:");
            System.out.println("error class: " + e.getClass().getName());
            System.out.println("message: " + e.getMessage());
            e.printStackTrace();

            closeQuietly(clientSocket);

            throw e;
        } catch (Exception e) {
            throw new IOException("MITM failed", e);
        } finally {
            if (tlsClientSocket != null) {
                closeQuietly(tlsClientSocket);
            }

            if (tlsServerSocket != null) {
                closeQuietly(tlsServerSocket);
            }
        }
    }

    private boolean usesConnectionClose(List<String> headers) {
        for (String header: headers) {
            int colonIndex = header.indexOf(":");

            if (colonIndex == -1) {
                continue;
            }

            String name = header.substring(0, colonIndex).trim();
            String value = header.substring(colonIndex + 1).trim();

            if (name.equalsIgnoreCase("Connection")
                    & value.equalsIgnoreCase("close")) {
                return true;
            }
        }

        return false;
    }

    private void forwardMitmHttpsRequest(ProxyRequest request,
                                         InputStream decryptedClientInput,
                                         OutputStream serverOutput) throws IOException {
        StringBuilder forwardedRequest = new StringBuilder();

        forwardedRequest.append(request.method())
                .append(" ")
                .append(request.path())
                .append(" ")
                .append(request.httpVersion())
                .append("\r\n");

        for (String header: request.headers()) {
            int coloIndex = header.indexOf(":");

            if (coloIndex == -1) {
                continue;
            }

            String name = header.substring(0, coloIndex).trim();

            if (name.equalsIgnoreCase("Proxy-Connection")
                    || name.equalsIgnoreCase("Connection")) {
                continue;
            }

            forwardedRequest.append(header).append("\r\n");
        }

        forwardedRequest.append("Connection: close\r\n");
        forwardedRequest.append("\r\n");

        System.out.println("===== MITM FORWARDED HTTPS REQUEST START =====");
        System.out.println(forwardedRequest
                .toString()
                .replace("\r", "\\r")
                .replace("\n", "\\n"));
        System.out.println("===== MITM FORWARDED HTTPS REQUEST END =====");

        serverOutput.write(
                forwardedRequest.toString().getBytes(StandardCharsets.ISO_8859_1));

        if (request.contentLength() > 0) {
            System.out.println("MITM forwarding HTTPS body bytes: "
                    + request.contentLength());
            SocketUtils.copyExact(
                    decryptedClientInput,
                    serverOutput,
                    request.contentLength()
            );
        }

        serverOutput.flush();
    }

    private SSLContext createMitmServerSslContext() throws Exception {
        char[] password = "changeit".toCharArray(); // why hard coding this? maybe .env for security reasons

        KeyStore keyStore = KeyStore.getInstance("PKCS12");

        try (InputStream keyStoreInput = new FileInputStream(MITM_KEYSTORE_PATH)) {
            keyStore.load(keyStoreInput, password);
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());

        keyManagerFactory.init(keyStore, password);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(
                keyManagerFactory.getKeyManagers(),
                null,
                null
        );

        return sslContext;
    }
}
