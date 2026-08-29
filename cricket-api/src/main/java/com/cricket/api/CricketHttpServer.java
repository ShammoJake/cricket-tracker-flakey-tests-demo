package com.cricket.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Adapts the JDK http server onto {@link CricketApi}.
 *
 * <p>Binding to port 0 lets the OS pick a free port, which keeps parallel test runs
 * from colliding; {@link #getPort()} reports what was chosen.
 */
public final class CricketHttpServer {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** Bridges an exchange to the transport-independent API. */
    private final class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                ApiRequest request = toRequest(exchange);
                ApiResponse response = api.handle(request);
                send(exchange, response);
            } catch (RuntimeException e) {
                send(exchange, ApiResponse.error(ApiResponse.SERVER_ERROR, String.valueOf(e.getMessage())));
            } finally {
                exchange.close();
            }
        }
    }

    private final CricketApi api;
    private final HttpServer server;
    private final Executor executor;

    public CricketHttpServer(CricketApi api) throws IOException {
        this(api, 0, 4);
    }

    public CricketHttpServer(CricketApi api, int port, int threads) throws IOException {
        if (api == null) {
            throw new IllegalArgumentException("api must not be null");
        }
        this.api = api;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        this.executor = Executors.newFixedThreadPool(threads);
        this.server.createContext("/", new ApiHandler());
        this.server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    /** Stops the server, allowing in-flight exchanges the given grace period. */
    public void stop(int delaySeconds) {
        server.stop(delaySeconds);
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + getPort();
    }

    private ApiRequest toRequest(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String body = readBody(exchange.getRequestBody());
        String clientId = exchange.getRemoteAddress() == null
                ? "anonymous"
                : exchange.getRemoteAddress().getAddress().getHostAddress();
        return new ApiRequest(method, path, body, query, clientId);
    }

    static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new HashMap<String, String>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            try {
                if (eq < 0) {
                    result.put(URLDecoder.decode(pair, "UTF-8"), "");
                } else {
                    result.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"),
                            URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
                }
            } catch (IOException e) {
                // A parameter we cannot decode is simply skipped.
            }
        }
        return result;
    }

    private static String readBody(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), UTF8);
    }

    private static void send(HttpExchange exchange, ApiResponse response) throws IOException {
        byte[] payload = response.getBody().getBytes(UTF8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(response.getStatus(), payload.length);
        OutputStream out = exchange.getResponseBody();
        try {
            out.write(payload);
        } finally {
            out.close();
        }
    }
}
