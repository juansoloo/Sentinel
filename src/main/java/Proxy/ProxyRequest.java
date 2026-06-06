package Proxy;

import java.util.List;

public record ProxyRequest(String method,
                           String path,
                           String httpVersion,
                           String host,
                           List<String> headers,
                           int contentLength,
                           byte[] body) {}

