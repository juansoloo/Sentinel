package Proxy;

import MVC.Models.ProxyModel;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static Proxy.SocketUtils.closeQuietly;

public class MitmTunnel {
    private final ProxyModel proxyModel;
    private final HttpRequestParser requestParser;
    private final HttpResponseReader responseReader;
    private final CertificateManager certificateManager;
    private final InterceptQueue interceptQueue;

    public MitmTunnel(ProxyModel proxyModel,
                    HttpRequestParser requestParser,
                    HttpResponseReader responseReader,
                    CertificateManager certificateManager,
                    InterceptQueue interceptQueue
    ) {
        this.proxyModel = proxyModel;
        this.requestParser = requestParser;
        this.responseReader = responseReader;
        this.certificateManager = certificateManager;
        this.interceptQueue = interceptQueue;
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
            SSLContext mitmServerContext = certificateManager.createServerContextFor(target.host());

            tlsClientSocket = (SSLSocket) mitmServerContext
                    .getSocketFactory()
                    .createSocket(
                            clientSocket,
                            clientSocket.getInetAddress().getHostAddress(),
                            clientSocket.getPort(),
                            false);

            tlsClientSocket.setUseClientMode(false);
            tlsClientSocket.startHandshake();


            // 3. Connect to the real server using TLS as a client
            SSLSocketFactory serverSslFactory =
                    (SSLSocketFactory) SSLSocketFactory.getDefault();

            tlsServerSocket = (SSLSocket) serverSslFactory.createSocket(
                            target.host(),
                            target.port());

            tlsServerSocket.setUseClientMode(true);
            tlsServerSocket.startHandshake();

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
                    return;
                }

                if (httpsRequest == null) {
                    throw new IOException("Invalid HTTPS request inside MITM tunnel");
                }

                httpsRequest = interceptQueue.intercept(httpsRequest);
                if (httpsRequest == null) return;

                forwardMitmHttpsRequest(
                        httpsRequest,
                        decryptedClientInput,
                        serverOutput);

                ProxyResponse response = responseReader.handleServerResponse(
                        serverInput,
                        decryptedClientOutput);

                HttpTransaction transaction = new HttpTransaction(httpsRequest, response);

                proxyModel.addTransaction(transaction);

                System.out.println(
                        "MITM " +
                                transaction.request().method() + " " +
                                transaction.request().host() + " " +
                                transaction.request().path() + " -> " +
                                transaction.response().statusCode());

                if (usesConnectionClose(httpsRequest.headers())) {
                    return;
                }
            }
        } catch (IOException e) {
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

        serverOutput.write(
                forwardedRequest.toString().getBytes(StandardCharsets.ISO_8859_1));

        if (request.contentLength() > 0) {
            SocketUtils.copyExact(
                    decryptedClientInput,
                    serverOutput,
                    request.contentLength()
            );
        }

        serverOutput.flush();
    }
}