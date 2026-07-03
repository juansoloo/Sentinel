package Proxy;

import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

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
            String bodyText = new String(res.body(), StandardCharsets.UTF_8);

            if (isJsonResponse(res)) {
                sb.append(prettyPrintJson(bodyText));
            } else {
                sb.append(bodyText);
            }
        }

        return sb.toString();
    }

    private static boolean isJsonResponse(ProxyResponse res) {
        for (HttpHeader header : res.headers()) {
            if (!header.name().equalsIgnoreCase("Content-Type")) {
                continue;
            }

            String contentType = header.value().toLowerCase();

            return contentType.contains("application/json") || contentType.contains("+json");
        }

        return false;
    }

    private static String prettyPrintJson(String bodyText) {
        try {
            JsonElement json = JsonParser.parseString(bodyText);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            return gson.toJson(json);
        } catch (JsonSyntaxException e) {
            return bodyText;
        }
    }
}