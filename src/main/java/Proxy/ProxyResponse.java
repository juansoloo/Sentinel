package Proxy;

import java.util.List;

public record ProxyResponse(
        String httpVersion,
        int statusCode,
        String reasonPhrase,
        List<HttpHeader> headers,
        byte[] body) {}
