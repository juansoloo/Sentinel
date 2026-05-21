package Proxy;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler implements Runnable{
    private final Socket clientSocket;
    private static final int HEADER_TERMINATOR = 0x0D0A0D0A;
    
    private record HostAndPort(String host, int port) {}

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    private String normalizePath(String pathRaw) {
        if (pathRaw.startsWith("http://")) {
            int pathStart = pathRaw.indexOf("/", 7);
            return pathStart == -1 ? "/" : pathRaw.substring(pathStart);
        }

        return pathRaw;
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

    private ProxyRequest readClientRequest(BufferedReader reader) throws IOException {
        int contentLength = 0;

        String requestLine = reader.readLine();

        if (requestLine == null || requestLine.isEmpty()) {
            return null;
        }

        String[] requestsParts = requestLine.split(" ", 3);

        if (requestsParts.length != 3) {
            return null;
        }

        String method = requestsParts[0];
        String pathRaw = requestsParts[1];
        String httpVersion = requestsParts[2];

        String line;
        String host = null;
        List<String> headers = new ArrayList<>();

        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            headers.add(line);

            int colonIndex = line.indexOf(":");

            if (colonIndex == -1) {
                continue;
            }

            String name = line.substring(0, colonIndex).trim();
            String value = line.substring(colonIndex + 1).trim();

            if (name.equalsIgnoreCase("Content-Length")) {
                contentLength = Integer.parseInt(value);
            }

            if (name.equalsIgnoreCase("Host")) {
                host = value;
            }
        }

        if (host == null) {
            return null;
        }

        String path = normalizePath(pathRaw);

        return new ProxyRequest(method, path, httpVersion, host, headers, contentLength);
    }

    private ProxyResponse handleServerResponse(InputStream serverInput,
                                               OutputStream clientOutput)
            throws IOException {
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        
        int bytesRead;
        int lastFourBytes = 0;

        String version = "";
        int statusCode = 0;
        String reasonPhrase = "";

        while(true) {
            int data = serverInput.read();

            if (data == -1) {
                throw new IOException("Server ended unexpectedly.");
            }

            headerBytes.write(data);
            lastFourBytes = ((lastFourBytes << 8) | data) & 0xFFFFFFFF;

            if (lastFourBytes == HEADER_TERMINATOR) {
                break;
            }
        }

        String headerString = headerBytes.toString(StandardCharsets.ISO_8859_1);
        List<HttpHeader> responseHeaders = new ArrayList<>();

        String[] lines = headerString.split("\r\n");

        for (String line : lines ) {
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("HTTP/")) {
                String[] statusParts = line.split(" ", 3);

                if (statusParts.length >= 2) {
                    version = statusParts[0];
                    statusCode = Integer.parseInt(statusParts[1]);
                    reasonPhrase = statusParts.length > 2 ? statusParts[2] : "";
                }

                System.out.println("HTTP Version: " + version);
                System.out.println("Status Code: " + statusCode);
                System.out.println("Reason Phrase: " + reasonPhrase);

            } else {
                int colonIndex = line.indexOf(":");

                if (colonIndex == -1) {
                    continue;
                }

                String name = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex+ 1).trim();

                responseHeaders.add(new HttpHeader(name, value));
                System.out.println(name + ": " + value);
            }
        }

        clientOutput.write(headerBytes.toByteArray());

        while ((bytesRead = serverInput.read(buffer)) != -1) {
            clientOutput.write(buffer, 0, bytesRead);
        }

        clientOutput.flush();

        return new ProxyResponse(version, statusCode, reasonPhrase, responseHeaders);
    }

    private void copyExact(InputStream in, OutputStream out, int byteCount) throws IOException {
        byte[] buffer = new byte[8192];
        int remaining = byteCount;

        while(remaining > 0) {
            int read = in.read(buffer, 0, Math.min(buffer.length, remaining));

            if (read == -1) {
                throw new IOException("client disconnected before full body request was read.");
            }

            out.write(buffer, 0, read);
            remaining -= read;
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
                System.out.println("Forwarding request body bytes: " + clientInput);
                copyExact(clientInput, serverOutput, contentLength);
            }

            serverOutput.flush();

            System.out.println(" ***************** server response ******************");
            System.out.println("\n");
            ProxyResponse response = handleServerResponse(serverInput, clientOutput);
            System.out.println(" ***************** server response ******************");
            System.out.println("\n");

            return response;
        }
    }


    @Override
    public void run() {
        HostAndPort target;
        String host = "unknown";
        int port = -1;

        try (InputStream clientInput = clientSocket.getInputStream();
                OutputStream clientOutput = clientSocket.getOutputStream()) {

            BufferedReader bufferReader = new BufferedReader(new InputStreamReader(clientInput, StandardCharsets.ISO_8859_1));
            ProxyRequest request = readClientRequest(bufferReader);

            if (request == null) {
                sendErrorResponse(clientOutput,
                        400,
                        "Bad Request",
                        "Bad Request");
                return;
            }

            if (request.method().equalsIgnoreCase("CONNECT")) {
                System.out.println("CONNECT not supported yet: " + request.path());
                sendErrorResponse(clientOutput,
                        501,
                        "Not implemented",
                        "CONNECT is not supported yet");
                return;
            }


            try {
                target = parseHost(request.host());
            } catch (IllegalArgumentException e) {
                sendErrorResponse(clientOutput,
                        400,
                        "Bad Request",
                        "Bad Request");
                return;
            }

            System.out.println("========== ABOUT TO FORWARD ==========");
            System.out.println("method=[" + request.method() + "]");
            System.out.println("host=[" + target.host() + "]");
            System.out.println("port=[" + target.port() + "]");
            System.out.println("path=[" + request.path() + "]");
            System.out.println("httpVersion=[" + request.httpVersion() + "]");

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

        output.write(response.getBytes());
        output.write(bodyBytes);
        output.flush();
    }
}

