package com.cricket.api;

import com.cricket.core.engine.ScoringRules;
import com.cricket.core.json.JsonParser;
import com.cricket.core.json.JsonValue;
import com.cricket.core.registry.MatchRegistry;
import com.cricket.core.registry.PlayerDirectory;
import com.cricket.live.IngestPipeline;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Exercises the real socket path through {@link CricketHttpServer}. */
public class HttpServerTest {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final long TIMEOUT = 5000L;

    private IngestPipeline pipeline;
    private CricketApi api;
    private CricketHttpServer server;

    @Before
    public void setUp() throws IOException {
        MatchRegistry.getInstance().reset();
        PlayerDirectory.getInstance().clear();
        ScoringRules.reloadDefaults();
        pipeline = new IngestPipeline();
        api = new CricketApi(pipeline);
        server = new CricketHttpServer(api);
        server.start();
    }

    @After
    public void tearDown() {
        server.stop(0);
        pipeline.shutdown(TIMEOUT);
        MatchRegistry.getInstance().reset();
        PlayerDirectory.getInstance().clear();
        ScoringRules.reloadDefaults();
    }

    /** Response status paired with its body. */
    private static final class Result {
        final int status;
        final String body;

        Result(int status, String body) {
            this.status = status;
            this.body = body;
        }

        JsonValue json() {
            return JsonParser.parse(body);
        }
    }

    private Result call(String method, String path, String body) throws IOException {
        URL url = new URL(server.baseUrl() + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            OutputStream out = connection.getOutputStream();
            try {
                out.write(body.getBytes(UTF8));
            } finally {
                out.close();
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        return new Result(status, stream == null ? "" : read(stream));
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), UTF8);
    }

    @Test
    public void theServerBindsToAFreePort() {
        assertTrue(server.getPort() > 0);
        assertTrue(server.baseUrl().startsWith("http://127.0.0.1:"));
    }

    @Test
    public void healthIsServedOverHttp() throws IOException {
        Result result = call("GET", "/health", null);
        assertEquals(200, result.status);
        assertEquals("ok", result.json().get("status").asString());
    }

    @Test
    public void theContentTypeIsJson() throws IOException {
        URL url = new URL(server.baseUrl() + "/health");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        assertTrue(connection.getHeaderField("Content-Type").contains("application/json"));
        connection.disconnect();
    }

    @Test
    public void aMatchIsCreatedOverHttp() throws IOException {
        Result result = call("POST", "/matches", "{\"matchId\":\"H1\"}");
        assertEquals(201, result.status);
        assertEquals("H1", result.json().get("matchId").asString());
    }

    @Test
    public void anUnknownRouteReturnsFourOhFour() throws IOException {
        assertEquals(404, call("GET", "/nonsense", null).status);
    }

    @Test
    public void aBadRequestReturnsFourHundred() throws IOException {
        assertEquals(400, call("POST", "/matches", "{oops").status);
    }

    @Test
    public void aFullDeliveryFlowWorksOverHttp() throws IOException {
        call("POST", "/matches", "{\"matchId\":\"H1\"}");
        call("POST", "/admin/start?matchId=H1", null);
        Result posted = call("POST", "/matches/H1/balls",
                "{\"over\":0,\"ballInOver\":1,\"bowler\":\"AUS8\",\"runsOffBat\":6}");
        assertEquals(201, posted.status);
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        Result card = call("GET", "/matches/H1/scorecard", null);
        assertEquals(6, card.json().get("runs").asInt());
    }

    @Test
    public void queryParametersReachTheHandler() throws IOException {
        call("POST", "/matches", "{\"matchId\":\"H1\"}");
        call("POST", "/admin/start?matchId=H1", null);
        for (int i = 1; i <= 4; i++) {
            call("POST", "/matches/H1/balls",
                    "{\"over\":0,\"ballInOver\":" + i + ",\"bowler\":\"AUS8\",\"runsOffBat\":1}");
        }
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        Result result = call("GET", "/matches/H1/commentary?limit=2", null);
        assertEquals(2, result.json().get("count").asInt());
    }

    @Test
    public void aQueryStringIsParsed() {
        Map<String, String> query = CricketHttpServer.parseQuery("a=1&b=two");
        assertEquals("1", query.get("a"));
        assertEquals("two", query.get("b"));
    }

    @Test
    public void anEmptyQueryStringYieldsNothing() {
        assertTrue(CricketHttpServer.parseQuery(null).isEmpty());
        assertTrue(CricketHttpServer.parseQuery("").isEmpty());
    }

    @Test
    public void aValuelessParameterBecomesEmpty() {
        assertEquals("", CricketHttpServer.parseQuery("flag").get("flag"));
    }

    @Test
    public void percentEncodingIsDecoded() {
        assertEquals("Lord's", CricketHttpServer.parseQuery("venue=Lord%27s").get("venue"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNullApiIsRejected() throws IOException {
        new CricketHttpServer(null);
    }
}
