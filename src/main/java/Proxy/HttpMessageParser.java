package Proxy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class HttpMessageParser {
    public static ProxyRequest parseRequest(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        int splitIndex = raw.indexOf("\r\n\r\n");
        
        String separator = "\r\n\r\n";

        if (splitIndex == -1) {
            splitIndex = raw.indexOf("\n\n");
            separator = "\n\n";
        }

        if (splitIndex == -1) {
            return null;
        }

        String headerText = raw.substring(0, splitIndex);
        String bodyText = raw.substring(splitIndex + separator.length());

        String[] lines = headerText.split("\\r?\\n");

        if (lines.length == 0 || lines[0].isBlank()) {
            return null;
        }

        String[] requestParts = lines[0].split(" ", 3);

        if (requestParts.length != 3) {
            return null;
        }

        String method = requestParts[0];
        String path = requestParts[1];
        String httpVersion = requestParts[2];

        String host = null;
        int contentLength = 0;
        List<String> headers = new ArrayList<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];

            if (line.isBlank()) {
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
                    return null;
                }
                
                if (contentLength < 0) {
                    return null;
                }
            }
        }

        if (host == null || host.isBlank()) {
            return null;
        }

        byte[] body = bodyText.getBytes(StandardCharsets.ISO_8859_1);

        return new ProxyRequest(
            method,
            path,
            httpVersion,
            host,
            headers,
            contentLength,
            body
        );
    }
}
