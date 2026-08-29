package com.cricket.core.json;

/** Raised when JSON cannot be parsed, or when a value is read as the wrong type. */
public class JsonException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int position;

    public JsonException(String message) {
        this(message, -1);
    }

    public JsonException(String message, int position) {
        super(position >= 0 ? message + " at position " + position : message);
        this.position = position;
    }

    /** Offset into the input where the problem was found, or -1. */
    public int getPosition() {
        return position;
    }
}
