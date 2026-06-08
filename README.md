# Sentinel

A Java-based HTTP/HTTPS intercepting proxy with a Swing GUI. Sentinel sits between your browser and the internet, decrypts TLS traffic using dynamically generated certificates, and lets you inspect and intercept requests and responses in real time.

## Features

- **HTTPS interception** via dynamic per-host certificate generation (MITM)
- **Proxy history** — tabular log of all intercepted transactions with method, host, path, and status
- **Request/response viewer** — inspect headers and bodies side by side; JSON bodies are automatically pretty-printed
- **Traffic filtering** — filter history by host or regex; right-click a row to add a host to the filter
- **Request interception** — pause outbound requests and modify them before forwarding
- **Memory-safe capture** — binary responses (images, video, audio, archives, etc.) are forwarded to the client without being buffered in the heap

## Requirements

- Java 17+
- Maven

## Build & Run

```bash
mvn compile exec:java
```

## Setup

To intercept HTTPS traffic, your browser must trust Sentinel's root CA:

1. Start Sentinel and begin proxying
2. Export the root CA certificate from the `cert-cache/` directory
3. Install it as a trusted root CA in your browser or OS certificate store
4. Configure your browser to use `127.0.0.1:8080` as an HTTP/HTTPS proxy

## Architecture

Sentinel follows an MVC structure:

- `Proxy/` — core proxy engine: client handling, MITM tunneling, certificate generation, HTTP parsing
- `MVC/Models/` — proxy data model and listener interface
- `MVC/Views/` — Swing UI: proxy history table, request/response viewer, filter panel, intercept panel
- `MVC/Controllers/` — wires models to views

## Dependencies

- [BouncyCastle](https://www.bouncycastle.org/) — TLS certificate generation
- [Gson](https://github.com/google/gson) — JSON pretty-printing

## Disclaimer

Sentinel is intended for use on networks and systems you own or have explicit written permission to test. Intercepting traffic without authorization is illegal. The author is not responsible for misuse.
