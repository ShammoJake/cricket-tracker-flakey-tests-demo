package com.cricket.core.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed JSON value.
 *
 * <p>Wraps the natural Java representation: {@code Map<String, Object>} for an object,
 * {@code List<Object>} for an array, and {@link String}, {@link Double}, {@link Boolean}
 * or {@code null} for the scalars. Objects preserve insertion order so output is stable.
 */
public final class JsonValue {

    private final Object value;

    JsonValue(Object value) {
        this.value = value;
    }

    public static JsonValue of(Object raw) {
        return new JsonValue(raw);
    }

    public static JsonValue nullValue() {
        return new JsonValue(null);
    }

    /** A new, empty, order-preserving object. */
    public static JsonValue object() {
        return new JsonValue(new LinkedHashMap<String, Object>());
    }

    public static JsonValue array() {
        return new JsonValue(new ArrayList<Object>());
    }

    public Object raw() {
        return value;
    }

    public boolean isNull() {
        return value == null;
    }

    @SuppressWarnings("unchecked")
    public boolean isObject() {
        return value instanceof Map;
    }

    public boolean isArray() {
        return value instanceof List;
    }

    public boolean isString() {
        return value instanceof String;
    }

    public boolean isNumber() {
        return value instanceof Number;
    }

    public boolean isBoolean() {
        return value instanceof Boolean;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> asObject() {
        if (!isObject()) {
            throw new JsonException("not an object: " + typeName());
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    public List<Object> asArray() {
        if (!isArray()) {
            throw new JsonException("not an array: " + typeName());
        }
        return (List<Object>) value;
    }

    public String asString() {
        if (!isString()) {
            throw new JsonException("not a string: " + typeName());
        }
        return (String) value;
    }

    public double asDouble() {
        if (!isNumber()) {
            throw new JsonException("not a number: " + typeName());
        }
        return ((Number) value).doubleValue();
    }

    public int asInt() {
        return (int) asDouble();
    }

    public long asLong() {
        return (long) asDouble();
    }

    public boolean asBoolean() {
        if (!isBoolean()) {
            throw new JsonException("not a boolean: " + typeName());
        }
        return (Boolean) value;
    }

    /** Member of an object, wrapped; a missing key yields a null value. */
    public JsonValue get(String key) {
        return new JsonValue(asObject().get(key));
    }

    /** Element of an array, wrapped. */
    public JsonValue get(int index) {
        List<Object> list = asArray();
        if (index < 0 || index >= list.size()) {
            throw new JsonException("index out of range: " + index);
        }
        return new JsonValue(list.get(index));
    }

    public boolean has(String key) {
        return isObject() && asObject().containsKey(key);
    }

    /** Number of members of an object or elements of an array. */
    public int size() {
        if (isObject()) {
            return asObject().size();
        }
        if (isArray()) {
            return asArray().size();
        }
        throw new JsonException("no size for " + typeName());
    }

    /** Adds a member; only valid on an object. */
    public JsonValue put(String key, Object member) {
        if (key == null) {
            throw new JsonException("key must not be null");
        }
        asObject().put(key, member instanceof JsonValue ? ((JsonValue) member).raw() : member);
        return this;
    }

    /** Appends an element; only valid on an array. */
    public JsonValue add(Object element) {
        asArray().add(element instanceof JsonValue ? ((JsonValue) element).raw() : element);
        return this;
    }

    /** A string member, or the fallback when absent or not a string. */
    public String optString(String key, String fallback) {
        if (!has(key)) {
            return fallback;
        }
        Object member = asObject().get(key);
        return member instanceof String ? (String) member : fallback;
    }

    /** An int member, or the fallback when absent or not a number. */
    public int optInt(String key, int fallback) {
        if (!has(key)) {
            return fallback;
        }
        Object member = asObject().get(key);
        return member instanceof Number ? ((Number) member).intValue() : fallback;
    }

    public String typeName() {
        if (value == null) {
            return "null";
        }
        if (isObject()) {
            return "object";
        }
        if (isArray()) {
            return "array";
        }
        if (isString()) {
            return "string";
        }
        if (isNumber()) {
            return "number";
        }
        return "boolean";
    }

    @Override
    public String toString() {
        return JsonWriter.write(this);
    }
}
