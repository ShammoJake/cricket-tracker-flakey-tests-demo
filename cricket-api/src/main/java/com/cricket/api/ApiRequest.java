package com.cricket.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** A request to the API, independent of the transport that carried it. */
public final class ApiRequest {

    private final String method;
    private final String path;
    private final String body;
    private final Map<String, String> query;
    private final String clientId;

    public ApiRequest(String method, String path) {
        this(method, path, null, null, "anonymous");
    }

    public ApiRequest(String method, String path, String body) {
        this(method, path, body, null, "anonymous");
    }

    public ApiRequest(String method, String path, String body,
                      Map<String, String> query, String clientId) {
        if (method == null || method.trim().isEmpty()) {
            throw new IllegalArgumentException("method must not be blank");
        }
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("path must start with '/'");
        }
        this.method = method.toUpperCase();
        this.path = path;
        this.body = body;
        this.query = query == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new HashMap<String, String>(query));
        this.clientId = clientId == null || clientId.trim().isEmpty() ? "anonymous" : clientId;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    /** May be null for requests without a body. */
    public String getBody() {
        return body;
    }

    public Map<String, String> getQuery() {
        return query;
    }

    public String getClientId() {
        return clientId;
    }

    public boolean hasBody() {
        return body != null && !body.trim().isEmpty();
    }

    /** A query parameter, or the fallback when absent. */
    public String param(String key, String fallback) {
        String value = query.get(key);
        return value == null ? fallback : value;
    }

    /** A query parameter as an int, or the fallback when absent or unparseable. */
    public int intParam(String key, int fallback) {
        String value = query.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Path split on '/', with empty segments dropped. */
    public String[] segments() {
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        if (trimmed.isEmpty()) {
            return new String[0];
        }
        String[] raw = trimmed.split("/");
        int count = 0;
        for (String s : raw) {
            if (!s.isEmpty()) {
                count++;
            }
        }
        String[] result = new String[count];
        int i = 0;
        for (String s : raw) {
            if (!s.isEmpty()) {
                result[i++] = s;
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return method + " " + path;
    }
}
