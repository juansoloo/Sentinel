package Proxy;

import java.nio.charset.StandardCharsets;

public class HttpMessageFormatter {
    public static String renderRequest(ProxyRequest req) {
        StringBuilder sb = new StringBuilder();

        sb.append(req.method())
                .append(" ")
                .append(req.path())
                .append(" ")
                .append(req.httpVersion())
                .append("\r\n");

        for (String header : req.headers()) {
            sb.append(header).append("\r\n");
        }

        sb.append("\r\n");

        if (req.body() != null && req.body().length > 0) {
            sb.append(new String(req.body(), StandardCharsets.UTF_8));
        }

        return sb.toString();
    }

    public static String renderResponse(ProxyResponse res) {
        StringBuilder sb = new StringBuilder();

        sb.append(res.httpVersion())
                .append(" ")
                .append(res.statusCode())
                .append(" ")
                .append(res.reasonPhrase())
                .append("\r\n");

        for (HttpHeader header : res.headers()) {
            sb.append(header.name())
                    .append(": ")
                    .append(header.value())
                    .append("\r\n");
        }

        sb.append("\r\n");

        if (res.body() != null && res.body().length > 0) {
            sb.append(new String(res.body(), StandardCharsets.UTF_8));
        }

        return sb.toString();
    }
}