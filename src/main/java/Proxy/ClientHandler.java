package Proxy;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler implements Runnable{
    private final Socket clientSocket;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    public boolean endsWithHeaderTerminator(byte[] headerInput) {
        if (headerInput.length < 4) {
            return false;
        } else return headerInput[headerInput.length - 4] == 13
                && headerInput[headerInput.length - 3] == 10
                && headerInput[headerInput.length - 2] == 13
                && headerInput[headerInput.length - 1] == 10;
    }

    public void handlerServerResponse(InputStream serverInput, OutputStream clientOutput) throws IOException {
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        boolean headerComplete = false;
        int bytesRead;

        while(!headerComplete) {
            int data = serverInput.read();
            if (data == -1) {
                System.out.println("Server ended unexpectedly.");
                return;
            }

            headerBytes.write(data);
            byte[] byteData = headerBytes.toByteArray();

            if (endsWithHeaderTerminator(byteData)) {
                headerComplete = true;
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

                String version = statusParts[0];
                String statusCode = statusParts[1];
                String reasonPhrase = statusParts.length > 2 ? statusParts[2] : "";

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
    }

    public void forwardRequest(String host, int targetPort, String path, List<String> headers, String HTTPType,
                               String method, OutputStream clientOutput) throws IOException {
        try (Socket serverSocket = new Socket(host, targetPort)) {
            System.out.println("Connecting to target: " + host + ":" + targetPort);

            OutputStream serverOutput = serverSocket.getOutputStream();
            InputStream serverInput = serverSocket.getInputStream();

            // *********************** Request logger ************************
            StringBuilder forwardedRequest = new StringBuilder();
            forwardedRequest.append(method)
                    .append(" ")
                    .append(path)
                    .append(" ")
                    .append(HTTPType)
                    .append("\r\n");

            for (String header : headers) {
                String lower = header.toLowerCase();

                if (lower.startsWith("proxy-connection:")) {
                    continue;
                }
                if (lower.startsWith("connection:")) {
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
            System.out.print(forwardedRequest.toString().replace("\r", "\\r").replace("\n", "\\n\n"));
            System.out.println("===== FORWARDED REQUEST END =====");
            System.out.println("\n");
            // ************************ Request logger *************************

            // write forwarded request to target server
            serverOutput.write(forwardedRequest.toString().getBytes(StandardCharsets.ISO_8859_1));

            // flush server output
            serverOutput.flush();

            System.out.println(" ***************** server resppnse ******************");
            System.out.println("\n");
            handlerServerResponse(serverInput, clientOutput);
            System.out.println("\n");
            System.out.println(" ***************** server resppnse ******************");
            System.out.println("\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try (InputStream clientInput = clientSocket.getInputStream();
                OutputStream clientOutput = clientSocket.getOutputStream()) {
            BufferedReader bufferReader = new BufferedReader(new InputStreamReader(clientInput));

            String requestLine = bufferReader.readLine();

            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            String line;
            String host = null;
            List<String> headers = new ArrayList<>();


            while ((line = bufferReader.readLine()) != null && !line.isEmpty()) {
                headers.add(line);

                if (line.toLowerCase().startsWith("host:")) {
                    host = line.substring(5).trim();
                }
            }

            if (host == null) {
                sendBadRequest(clientOutput);
                return;
            }


            String targetHost = host;
            int targetPort = 80;

            if (host.contains(":")) {
                String[] parts = host.split(":");
                targetHost = parts[0];
                targetPort = Integer.parseInt(parts[1]);
            }

            String[] requestParts = requestLine.split(" ");

            String method = requestParts[0];
            String path = requestParts[1].replace(host, "").replace("http://", "");
            String HTTPType = requestParts[2];

            if (method.equalsIgnoreCase("connect")) {
                return;
            }

            System.out.println(method + " " + path + " " + HTTPType);

            for (String item : headers) {
                System.out.println(item);
            }

            forwardRequest(host, targetPort, path, headers, HTTPType, method, clientOutput);


        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendBadRequest(OutputStream output) throws IOException {
        String response =
                "HTTP/1.1 400 Bad Request\r\n" +
                "Content-Length: 11\r\n" +
                "\r\n" +
                "Bad Request";

        output.write(response.getBytes());
        output.flush();
    }
}


