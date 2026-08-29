package com.cricket.core.json;

import java.util.List;
import java.util.Map;

/** Serialises a {@link JsonValue} back to text. */
public final class JsonWriter {

    private JsonWriter() {
    }

    public static String write(JsonValue value) {
        StringBuilder sb = new StringBuilder();
        writeRaw(value == null ? null : value.raw(), sb);
        return sb.toString();
    }

    public static String write(Object raw) {
        StringBuilder sb = new StringBuilder();
        writeRaw(raw, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeRaw(Object raw, StringBuilder sb) {
        if (raw == null) {
            sb.append("null");
        } else if (raw instanceof JsonValue) {
            writeRaw(((JsonValue) raw).raw(), sb);
        } else if (raw instanceof Map) {
            writeObject((Map<String, Object>) raw, sb);
        } else if (raw instanceof List) {
            writeArray((List<Object>) raw, sb);
        } else if (raw instanceof String) {
            writeString((String) raw, sb);
        } else if (raw instanceof Boolean) {
            sb.append(raw.toString());
        } else if (raw instanceof Number) {
            writeNumber((Number) raw, sb);
        } else {
            writeString(raw.toString(), sb);
        }
    }

    private static void writeObject(Map<String, Object> map, StringBuilder sb) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(entry.getKey(), sb);
            sb.append(':');
            writeRaw(entry.getValue(), sb);
        }
        sb.append('}');
    }

    private static void writeArray(List<Object> list, StringBuilder sb) {
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            writeRaw(list.get(i), sb);
        }
        sb.append(']');
    }

    /** Whole doubles are written without a trailing ".0" so ids stay readable. */
    private static void writeNumber(Number number, StringBuilder sb) {
        double d = number.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new JsonException("cannot write a non-finite number: " + number);
        }
        if (number instanceof Integer || number instanceof Long
                || number instanceof Short || number instanceof Byte) {
            sb.append(number.toString());
        } else if (d == Math.floor(d) && Math.abs(d) < 1e15) {
            sb.append((long) d);
        } else {
            sb.append(number.toString());
        }
    }

    static void writeString(String text, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
