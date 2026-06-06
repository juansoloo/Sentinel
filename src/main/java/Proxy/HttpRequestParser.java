package Proxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class HttpRequestParser {
    private static final int HEADER_TERMINATOR = 0x0D0A0D0A;
    private static final int MAX_HEADER_SIZE = 1024 * 1024;

    public HttpRequestParser() {

    }

    byte[] readHeaderBytes(InputStream input) throws IOException {
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();

        int b;
        int lastFourBytes = 0;

        while ((b = input.read()) != -1) {
            headerBytes.write(b);

            if (headerBytes.size() > MAX_HEADER_SIZE) {
                throw new IOException("Header size limit exceeded");
            }

            lastFourBytes = ((lastFourBytes << 8) | b) & 0xFFFFFFFF;

            if (lastFourBytes == HEADER_TERMINATOR) {
                return headerBytes.toByteArray();
            }
        }

        throw new IOException("Client disconnected before sending full headers");
    }

    private String normalizePath(String pathRaw) {
        if (pathRaw.startsWith("http://")) {
            int pathStart = pathRaw.indexOf("/", 7);
            return pathStart == -1 ? "/" : pathRaw.substring(pathStart);
        }

        return pathRaw;
    }

    public ProxyRequest parseClientRequest(byte[] headerBytes) {
        String headerText = new String(headerBytes, StandardCharsets.ISO_8859_1);

        String[] lines = headerText.split("\r\n");

        // maybe create function to do extract request line
        if (lines.length == 0 || lines[0].isEmpty()) {
            return null;
        }

        String[] requestsParts = lines[0].split(" ", 3);

        if (requestsParts.length != 3) {
            return null;
        }

        String method = requestsParts[0];
        String pathRaw = requestsParts[1];
        String httpVersion = requestsParts[2];

        // ___________________________________________________________

        String host = null;
        int contentLength = 0;
        List<String> headers = new ArrayList<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];

            if (line.isEmpty()) {
                continue;
            }

            headers.add(line);

            int colonIndex = line.indexOf(":");

            if (colonIndex == -1) {
                continue;
            }

            String name = line.substring(0, colonIndex).trim();
            String value = line.substring(colonIndex + 1).trim();

            if (name.equalsIgnoreCase("Host")) {
                host = value;
            }

            if (name.equalsIgnoreCase("Content-Length")) {
                try {
                    contentLength = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid Content-Length: " + value, e);
                }

                if (contentLength < 0) {
                    throw new IllegalArgumentException("Invalid Content-Length: " + value);
                }
            }

            if (name.equalsIgnoreCase("Transfer-Encoding")
                    && value.equalsIgnoreCase("chunked")) {
                throw new UnsupportedOperationException("Chunked request bodies are not supported yet");
            }
        }

        if (host == null) {
            return null;
        }

        String path = normalizePath(pathRaw);

        return new ProxyRequest(method, path, httpVersion, host, headers, contentLength, new byte[0]);
    }
}
