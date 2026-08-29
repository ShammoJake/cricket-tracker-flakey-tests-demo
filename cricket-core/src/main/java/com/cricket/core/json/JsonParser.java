package com.cricket.core.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Recursive-descent JSON parser. */
public final class JsonParser {

    private final String input;
    private int pos;

    private JsonParser(String input) {
        this.input = input;
    }

    /** Parses a complete JSON document. */
    public static JsonValue parse(String text) {
        if (text == null) {
            throw new JsonException("input must not be null");
        }
        JsonParser parser = new JsonParser(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new JsonException("trailing content", parser.pos);
        }
        return JsonValue.of(value);
    }

    private boolean atEnd() {
        return pos >= input.length();
    }

    private char peek() {
        if (atEnd()) {
            throw new JsonException("unexpected end of input", pos);
        }
        return input.charAt(pos);
    }

    private char next() {
        char c = peek();
        pos++;
        return c;
    }

    private void expect(char expected) {
        char c = next();
        if (c != expected) {
            throw new JsonException("expected '" + expected + "' but found '" + c + "'", pos - 1);
        }
    }

    private void skipWhitespace() {
        while (!atEnd()) {
            char c = input.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    private Object readValue() {
        char c = peek();
        switch (c) {
            case '{':
                return readObject();
            case '[':
                return readArray();
            case '"':
                return readString();
            case 't':
                readLiteral("true");
                return Boolean.TRUE;
            case 'f':
                readLiteral("false");
                return Boolean.FALSE;
            case 'n':
                readLiteral("null");
                return null;
            default:
                return readNumber();
        }
    }

    private void readLiteral(String literal) {
        if (!input.startsWith(literal, pos)) {
            throw new JsonException("invalid literal", pos);
        }
        pos += literal.length();
    }

    private Map<String, Object> readObject() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw new JsonException("expected ',' or '}' but found '" + c + "'", pos - 1);
            }
        }
    }

    private List<Object> readArray() {
        expect('[');
        List<Object> list = new ArrayList<Object>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw new JsonException("expected ',' or ']' but found '" + c + "'", pos - 1);
            }
        }
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char escape = next();
            switch (escape) {
                case '"':
                    sb.append('"');
                    break;
                case '\\':
                    sb.append('\\');
                    break;
                case '/':
                    sb.append('/');
                    break;
                case 'n':
                    sb.append('\n');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'b':
                    sb.append('\b');
                    break;
                case 'f':
                    sb.append('\f');
                    break;
                case 'u':
                    sb.append(readUnicodeEscape());
                    break;
                default:
                    throw new JsonException("invalid escape '\\" + escape + "'", pos - 1);
            }
        }
    }

    private char readUnicodeEscape() {
        if (pos + 4 > input.length()) {
            throw new JsonException("truncated unicode escape", pos);
        }
        String hex = input.substring(pos, pos + 4);
        pos += 4;
        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            throw new JsonException("invalid unicode escape '" + hex + "'", pos - 4);
        }
    }

    private Double readNumber() {
        int start = pos;
        if (!atEnd() && peek() == '-') {
            pos++;
        }
        while (!atEnd() && isNumberChar(input.charAt(pos))) {
            pos++;
        }
        if (start == pos) {
            throw new JsonException("expected a value but found '" + peek() + "'", pos);
        }
        String text = input.substring(start, pos);
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException e) {
            throw new JsonException("invalid number '" + text + "'", start);
        }
    }

    private static boolean isNumberChar(char c) {
        return (c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-';
    }
}
