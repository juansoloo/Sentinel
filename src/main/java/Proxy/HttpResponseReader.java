package Proxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class HttpResponseReader {
    private static final int HEADER_TERMINATOR = 0x0D0A0D0A;

    ProxyResponse handleServerResponse(InputStream serverInput, OutputStream clientOutput)
            throws IOException {
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];

        int bytesRead;
        int lastFourBytes = 0;

        String version = "";
        int statusCode = 0;
        String reasonPhrase = "";

        do { // change from while(true) to do {}
            int data = serverInput.read();

            if (data == -1) {
                throw new IOException("Server ended unexpectedly.");
            }

            headerBytes.write(data);
            lastFourBytes = ((lastFourBytes << 8) | data) & 0xFFFFFFFF;;

        } while (lastFourBytes != HEADER_TERMINATOR);

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
}
