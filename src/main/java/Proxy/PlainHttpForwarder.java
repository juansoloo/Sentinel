package Proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class PlainHttpForwarder {
    private final HttpResponseReader responseReader;

    public PlainHttpForwarder(HttpResponseReader responseReader) {
        this.responseReader = responseReader;
    }

    public HttpTransaction forward(HostAndPort target,
                                ProxyRequest request,
                                InputStream clientInput,
                                OutputStream clientOutput)
            throws IOException {
        try (Socket serverSocket = new Socket()) {

            serverSocket.connect(new InetSocketAddress(target.host(), target.port()), 15000);
            serverSocket.setSoTimeout(15000);

            OutputStream serverOutput = serverSocket.getOutputStream();
            InputStream serverInput = serverSocket.getInputStream();

            StringBuilder forwardedRequest = new StringBuilder();

            forwardedRequest.append(request.method())
                    .append(" ")
                    .append(request.path())
                    .append(" ")
                    .append(request.httpVersion())
                    .append("\r\n");

            for (String header : request.headers()) {
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

            serverOutput.write(forwardedRequest.toString().getBytes(StandardCharsets.ISO_8859_1));

            byte[] requestBody;

            if (request.contentLength() > 0) {
                requestBody = SocketUtils.copyExactCapture(
                        clientInput,
                        serverOutput,
                        request.contentLength());
            } else {
                requestBody = new byte[0];
            }

            serverOutput.flush();

            ProxyResponse response = responseReader.handleServerResponse(serverInput, clientOutput);

            ProxyRequest fullRequest = new ProxyRequest(
                    request.method(),
                    request.path(),
                    request.httpVersion(),
                    request.host(),
                    request.headers(),
                    request.contentLength(),
                    requestBody);

            return new HttpTransaction(fullRequest, response);
        }
    }
}
