package com.cricket.api;

import com.cricket.core.json.JsonParser;
import com.cricket.core.json.JsonValue;
import com.cricket.core.json.JsonWriter;

/** A response from the API: a status code and a JSON body. */
public final class ApiResponse {

    public static final int OK = 200;
    public static final int CREATED = 201;
    public static final int BAD_REQUEST = 400;
    public static final int NOT_FOUND = 404;
    public static final int METHOD_NOT_ALLOWED = 405;
    public static final int TOO_MANY_REQUESTS = 429;
    public static final int SERVER_ERROR = 500;

    private final int status;
    private final String body;

    public ApiResponse(int status, String body) {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("status out of range: " + status);
        }
        this.status = status;
        this.body = body == null ? "" : body;
    }

    public static ApiResponse ok(JsonValue payload) {
        return new ApiResponse(OK, JsonWriter.write(payload));
    }

    public static ApiResponse created(JsonValue payload) {
        return new ApiResponse(CREATED, JsonWriter.write(payload));
    }

    public static ApiResponse error(int status, String message) {
        return new ApiResponse(status, JsonWriter.write(
                JsonValue.object().put("error", message)));
    }

    public static ApiResponse badRequest(String message) {
        return error(BAD_REQUEST, message);
    }

    public static ApiResponse notFound(String message) {
        return error(NOT_FOUND, message);
    }

    public int getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    public boolean isError() {
        return status >= 400;
    }

    /** The body parsed as JSON. */
    public JsonValue json() {
        return JsonParser.parse(body);
    }

    /** The "error" member of the body, or null when there is none. */
    public String errorMessage() {
        JsonValue parsed = json();
        return parsed.has("error") ? parsed.get("error").asString() : null;
    }

    @Override
    public String toString() {
        return status + " " + body;
    }
}
