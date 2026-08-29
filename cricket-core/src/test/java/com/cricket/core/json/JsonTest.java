package com.cricket.core.json;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Covers {@link JsonParser}, {@link JsonWriter} and {@link JsonValue}. */
public class JsonTest {

    @Test
    public void anEmptyObjectParses() {
        assertTrue(JsonParser.parse("{}").isObject());
        assertEquals(0, JsonParser.parse("{}").size());
    }

    @Test
    public void anEmptyArrayParses() {
        assertTrue(JsonParser.parse("[]").isArray());
        assertEquals(0, JsonParser.parse("[]").size());
    }

    @Test
    public void aStringParses() {
        assertEquals("Kohli", JsonParser.parse("\"Kohli\"").asString());
    }

    @Test
    public void anIntegerParses() {
        assertEquals(47, JsonParser.parse("47").asInt());
    }

    @Test
    public void aNegativeNumberParses() {
        assertEquals(-12, JsonParser.parse("-12").asInt());
    }

    @Test
    public void aDecimalParses() {
        assertEquals(6.25, JsonParser.parse("6.25").asDouble(), 1e-9);
    }

    @Test
    public void exponentNotationParses() {
        assertEquals(1200.0, JsonParser.parse("1.2e3").asDouble(), 1e-9);
    }

    @Test
    public void trueParses() {
        assertTrue(JsonParser.parse("true").asBoolean());
    }

    @Test
    public void falseParses() {
        assertFalse(JsonParser.parse("false").asBoolean());
    }

    @Test
    public void nullParses() {
        assertTrue(JsonParser.parse("null").isNull());
    }

    @Test
    public void anObjectMemberIsRead() {
        JsonValue v = JsonParser.parse("{\"runs\":147,\"wickets\":3}");
        assertEquals(147, v.get("runs").asInt());
        assertEquals(3, v.get("wickets").asInt());
    }

    @Test
    public void anArrayElementIsRead() {
        JsonValue v = JsonParser.parse("[1,2,3]");
        assertEquals(2, v.get(1).asInt());
        assertEquals(3, v.size());
    }

    @Test
    public void nestingIsHandled() {
        JsonValue v = JsonParser.parse("{\"innings\":{\"score\":{\"runs\":180}}}");
        assertEquals(180, v.get("innings").get("score").get("runs").asInt());
    }

    @Test
    public void anArrayOfObjectsIsHandled() {
        JsonValue v = JsonParser.parse("[{\"id\":\"IND1\"},{\"id\":\"IND2\"}]");
        assertEquals("IND2", v.get(1).get("id").asString());
    }

    @Test
    public void whitespaceIsIgnored() {
        JsonValue v = JsonParser.parse("  {\n \"a\" : 1 ,\t\"b\" : 2 \r\n}  ");
        assertEquals(2, v.size());
    }

    @Test
    public void memberOrderIsPreserved() {
        JsonValue v = JsonParser.parse("{\"z\":1,\"a\":2,\"m\":3}");
        assertEquals("{\"z\":1,\"a\":2,\"m\":3}", JsonWriter.write(v));
    }

    @Test
    public void hasDetectsAMember() {
        JsonValue v = JsonParser.parse("{\"runs\":1}");
        assertTrue(v.has("runs"));
        assertFalse(v.has("wickets"));
    }

    @Test
    public void aMissingMemberIsNull() {
        assertTrue(JsonParser.parse("{}").get("nope").isNull());
    }

    @Test
    public void escapedQuotesRoundTrip() {
        assertEquals("say \"hi\"", JsonParser.parse("\"say \\\"hi\\\"\"").asString());
    }

    @Test
    public void escapedBackslashesRoundTrip() {
        assertEquals("a\\b", JsonParser.parse("\"a\\\\b\"").asString());
    }

    @Test
    public void escapedNewlinesRoundTrip() {
        assertEquals("a\nb", JsonParser.parse("\"a\\nb\"").asString());
    }

    @Test
    public void escapedTabsRoundTrip() {
        assertEquals("a\tb", JsonParser.parse("\"a\\tb\"").asString());
    }

    @Test
    public void escapedSlashesAreAccepted() {
        assertEquals("a/b", JsonParser.parse("\"a\\/b\"").asString());
    }

    @Test
    public void unicodeEscapesAreDecoded() {
        assertEquals("\u00e9", JsonParser.parse("\"\\u00e9\"").asString());
    }

    @Test
    public void aStringIsWrittenWithQuotes() {
        assertEquals("\"Kohli\"", JsonWriter.write("Kohli"));
    }

    @Test
    public void quotesAreEscapedOnWrite() {
        assertEquals("\"say \\\"hi\\\"\"", JsonWriter.write("say \"hi\""));
    }

    @Test
    public void newlinesAreEscapedOnWrite() {
        assertEquals("\"a\\nb\"", JsonWriter.write("a\nb"));
    }

    @Test
    public void controlCharactersAreEscapedOnWrite() {
        assertEquals("\"\\u0001\"", JsonWriter.write("\u0001"));
    }

    @Test
    public void wholeDoublesLoseTheTrailingZero() {
        assertEquals("6", JsonWriter.write(6.0));
    }

    @Test
    public void fractionalDoublesKeepTheirDecimals() {
        assertEquals("6.25", JsonWriter.write(6.25));
    }

    @Test
    public void integersAreWrittenPlainly() {
        assertEquals("147", JsonWriter.write(147));
    }

    @Test
    public void nullIsWritten() {
        assertEquals("null", JsonWriter.write((Object) null));
    }

    @Test
    public void booleansAreWritten() {
        assertEquals("true", JsonWriter.write(true));
        assertEquals("false", JsonWriter.write(false));
    }

    @Test
    public void anObjectIsBuiltAndWritten() {
        JsonValue v = JsonValue.object().put("runs", 147).put("wickets", 3);
        assertEquals("{\"runs\":147,\"wickets\":3}", JsonWriter.write(v));
    }

    @Test
    public void anArrayIsBuiltAndWritten() {
        JsonValue v = JsonValue.array().add("a").add("b");
        assertEquals("[\"a\",\"b\"]", JsonWriter.write(v));
    }

    @Test
    public void nestedValuesAreUnwrappedOnPut() {
        JsonValue inner = JsonValue.object().put("runs", 10);
        JsonValue outer = JsonValue.object().put("score", inner);
        assertEquals("{\"score\":{\"runs\":10}}", JsonWriter.write(outer));
    }

    @Test
    public void aBuiltDocumentRoundTrips() {
        JsonValue v = JsonValue.object()
                .put("match", "IND-AUS")
                .put("runs", 147)
                .put("chasing", true);
        assertEquals(v.toString(), JsonParser.parse(v.toString()).toString());
    }

    @Test
    public void optStringFallsBackWhenAbsent() {
        assertEquals("none", JsonParser.parse("{}").optString("venue", "none"));
    }

    @Test
    public void optStringFallsBackOnTheWrongType() {
        assertEquals("none", JsonParser.parse("{\"venue\":5}").optString("venue", "none"));
    }

    @Test
    public void optStringReadsAPresentMember() {
        assertEquals("Lord's", JsonParser.parse("{\"venue\":\"Lord's\"}").optString("venue", "none"));
    }

    @Test
    public void optIntFallsBackWhenAbsent() {
        assertEquals(-1, JsonParser.parse("{}").optInt("runs", -1));
    }

    @Test
    public void optIntReadsAPresentMember() {
        assertEquals(147, JsonParser.parse("{\"runs\":147}").optInt("runs", -1));
    }

    @Test
    public void theTypeNameIsReported() {
        assertEquals("object", JsonParser.parse("{}").typeName());
        assertEquals("array", JsonParser.parse("[]").typeName());
        assertEquals("string", JsonParser.parse("\"a\"").typeName());
        assertEquals("number", JsonParser.parse("1").typeName());
        assertEquals("boolean", JsonParser.parse("true").typeName());
        assertEquals("null", JsonParser.parse("null").typeName());
    }

    @Test(expected = JsonException.class)
    public void nullInputIsRejected() {
        JsonParser.parse(null);
    }

    @Test(expected = JsonException.class)
    public void trailingContentIsRejected() {
        JsonParser.parse("{} junk");
    }

    @Test(expected = JsonException.class)
    public void anUnterminatedObjectIsRejected() {
        JsonParser.parse("{\"a\":1");
    }

    @Test(expected = JsonException.class)
    public void anUnterminatedArrayIsRejected() {
        JsonParser.parse("[1,2");
    }

    @Test(expected = JsonException.class)
    public void anUnterminatedStringIsRejected() {
        JsonParser.parse("\"abc");
    }

    @Test(expected = JsonException.class)
    public void aMissingColonIsRejected() {
        JsonParser.parse("{\"a\" 1}");
    }

    @Test(expected = JsonException.class)
    public void aBadLiteralIsRejected() {
        JsonParser.parse("tru");
    }

    @Test(expected = JsonException.class)
    public void anInvalidEscapeIsRejected() {
        JsonParser.parse("\"a\\qb\"");
    }

    @Test(expected = JsonException.class)
    public void aTruncatedUnicodeEscapeIsRejected() {
        JsonParser.parse("\"\\u00\"");
    }

    @Test(expected = JsonException.class)
    public void anInvalidUnicodeEscapeIsRejected() {
        JsonParser.parse("\"\\uZZZZ\"");
    }

    @Test(expected = JsonException.class)
    public void readingAStringAsANumberIsRejected() {
        JsonParser.parse("\"abc\"").asInt();
    }

    @Test(expected = JsonException.class)
    public void readingANumberAsAnObjectIsRejected() {
        JsonParser.parse("1").asObject();
    }

    @Test(expected = JsonException.class)
    public void readingAnObjectAsAnArrayIsRejected() {
        JsonParser.parse("{}").asArray();
    }

    @Test(expected = JsonException.class)
    public void anOutOfRangeIndexIsRejected() {
        JsonParser.parse("[1]").get(5);
    }

    @Test(expected = JsonException.class)
    public void aScalarHasNoSize() {
        JsonParser.parse("1").size();
    }

    @Test(expected = JsonException.class)
    public void nonFiniteNumbersCannotBeWritten() {
        JsonWriter.write(Double.NaN);
    }

    @Test
    public void theExceptionCarriesAPosition() {
        try {
            JsonParser.parse("{\"a\" 1}");
        } catch (JsonException e) {
            assertTrue(e.getPosition() >= 0);
            assertTrue(e.getMessage().contains("position"));
        }
    }

    @Test
    public void aRealisticScorecardRoundTrips() {
        String doc = "{\"matchId\":\"IND-AUS-T20\",\"innings\":[{\"team\":\"India\","
                + "\"runs\":186,\"wickets\":4,\"overs\":20},{\"team\":\"Australia\","
                + "\"runs\":179,\"wickets\":8,\"overs\":20}],\"result\":\"India won by 7 runs\"}";
        JsonValue v = JsonParser.parse(doc);
        assertEquals("IND-AUS-T20", v.get("matchId").asString());
        assertEquals(2, v.get("innings").size());
        assertEquals(186, v.get("innings").get(0).get("runs").asInt());
        assertEquals(doc, JsonWriter.write(v));
    }
}
