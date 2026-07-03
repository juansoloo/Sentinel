package Proxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class RepeaterClient {
    private final HttpResponseReader responseReader;

    public RepeaterClient(HttpResponseReader responseReader) {
        this.responseReader = responseReader;
    }

    public ProxyResponse send(ProxyRequest request) throws IOException {
        HostAndPort target = parseHost(request.host());

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(target.host(), target.port()), 15000);
            socket.setSoTimeout(15000);

            OutputStream serverOutput = socket.getOutputStream();
            InputStream serverInput = socket.getInputStream();

            serverOutput.write(buildRequestBytes(request));
            serverOutput.flush();

            return responseReader.handleServerResponse(serverInput, new ByteArrayOutputStream());
        }
    }

    private byte[] buildRequestBytes(ProxyRequest request) {
        ByteArrayOutputStream requestBytes = new ByteArrayOutputStream();
        StringBuilder headers = new StringBuilder();

        headers.append(request.method())
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

            headers.append(header).append("\r\n");
        }

        headers.append("Connection: close\r\n");
        headers.append("\r\n");

        requestBytes.writeBytes(headers.toString().getBytes(StandardCharsets.ISO_8859_1));

        if (request.body() != null && request.body().length > 0) {
            requestBytes.writeBytes(request.body());
        }

        return requestBytes.toByteArray();
    }

    private HostAndPort parseHost(String hostHeader) {
        if (hostHeader == null || hostHeader.isEmpty()) {
            throw new IllegalArgumentException("Missing host header");
        }

        String targetHost = hostHeader;
        int targetPort = 80;

        if (hostHeader.contains(":")) {
            String[] parts = hostHeader.split(":", 2);
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
}
