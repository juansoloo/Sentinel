package Proxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HttpResponseReader {
    private static final int HEADER_TERMINATOR = 0x0D0A0D0A;
    private static final int MAX_BODY_CAPTURE = 1024 * 1024;
    private static final int MAX_HEADER_SIZE = 1024 * 1024;
    private static final Set<String> ignoredApplicationTypes  = Set.of(
            "application/octet-stream",
            "application/pdf",
            "application/zip",
            "application/x-zip-compressed",
            "application/x-rar-compressed",
            "application/x-7z-compressed",
            "application/gzip",
            "application/x-gzip",
            "application/x-tar",
            "application/x-bzip2",

            "application/vnd.ms-fontobject",
            "application/font-woff",
            "application/font-woff2",
            "application/x-font-ttf",
            "application/x-font-opentype",

            "application/wasm",

            "application/vnd.apple.mpegurl",
            "application/x-mpegURL",
            "application/dash+xml",

            "application/x-shockwave-flash",

            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",

            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/svg+xml",
            "image/x-icon",
            "image/bmp",
            "image/tiff",
            "image/avif",

            "video/mp4",
            "video/webm",
            "video/ogg",
            "video/mpeg",
            "video/quicktime",
            "video/x-msvideo",
            "video/x-matroska",

            "audio/mpeg",
            "audio/ogg",
            "audio/wav",
            "audio/webm",
            "audio/aac",
            "audio/flac",
            "audio/x-wav"
    );

    ProxyResponse handleServerResponse(InputStream serverInput, OutputStream clientOutput)
            throws IOException {
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];

        int bytesRead;
        int lastFourBytes = 0;

        String version = "";
        int statusCode = 0;
        String reasonPhrase = "";
        int contentLength = -1;

        do {
            int data = serverInput.read();

            if (data == -1) {
                throw new IOException("Server ended unexpectedly.");
            }

            headerBytes.write(data);

            if (headerBytes.size() > MAX_HEADER_SIZE) {
                throw new IOException("Header size limit exceeded.");
            }

            lastFourBytes = ((lastFourBytes << 8) | data) & 0xFFFFFFFF;

        } while (lastFourBytes != HEADER_TERMINATOR);

        String headerString = headerBytes.toString(StandardCharsets.ISO_8859_1);
        List<HttpHeader> responseHeaders = new ArrayList<>();

        String[] lines = headerString.split("\r\n");

        for (String line : lines) {
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

            } else {
                int colonIndex = line.indexOf(":");

                if (colonIndex == -1) {
                    continue;
                }

                String name = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();

                responseHeaders.add(new HttpHeader(name, value));

                if (name.equalsIgnoreCase("Content-Length")) {
                    try {
                        contentLength = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid Content-Length: " + value, e);
                    }

                    if (contentLength < 0) {
                        throw new IOException("Invalid Content-Length: " + value);
                    }
                }
            }
        }

        clientOutput.write(headerBytes.toByteArray());

        byte[] capturedBody;
        boolean containsFilter = isContainsFilter(responseHeaders);

        if (containsFilter) {
            capturedBody = new byte[0];

            while ((bytesRead = serverInput.read(buffer)) != -1) {
                clientOutput.write(buffer, 0, bytesRead);

            }
        } else if (contentLength > 0) {
            capturedBody = SocketUtils.copyExactCapture(serverInput, clientOutput, contentLength);
        } else if (contentLength == 0) {
            capturedBody = new byte[0];
        } else {
            int totalCaptured = 0;
            ByteArrayOutputStream capturedArray = new ByteArrayOutputStream();

            while ((bytesRead = serverInput.read(buffer)) != -1) {
                clientOutput.write(buffer, 0, bytesRead);

                if (totalCaptured < MAX_BODY_CAPTURE) {
                    capturedArray.write(buffer, 0, bytesRead);
                    totalCaptured += bytesRead;
                }
            }

            capturedBody = capturedArray.toByteArray();
        }

        clientOutput.flush();

        return new ProxyResponse(version, statusCode, reasonPhrase, responseHeaders, capturedBody);
    }

    private static boolean isContainsFilter(List<HttpHeader> responseHeaders) {
        boolean containsFilter = false;

        for (HttpHeader header : responseHeaders) {
            if (!containsFilter) {
                if (header.name().equalsIgnoreCase("content-type")) {
                    if (ignoredApplicationTypes.contains(header.value().split(";", 2)[0].trim())
                        || header.value().startsWith("image/")
                        || header.value().startsWith("video/")
                        || header.value().startsWith("audio/")) {
                        containsFilter = true;
                    }
                }
            }
        }
        return containsFilter;
    }
}
