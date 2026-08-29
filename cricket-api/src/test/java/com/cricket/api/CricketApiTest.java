package com.cricket.api;

import com.cricket.core.engine.ScoringRules;
import com.cricket.core.json.JsonValue;
import com.cricket.core.registry.MatchRegistry;
import com.cricket.core.registry.PlayerDirectory;
import com.cricket.live.IngestPipeline;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CricketApiTest {

    private static final long TIMEOUT = 5000L;

    private IngestPipeline pipeline;
    private CricketApi api;

    @Before
    public void setUp() {
        MatchRegistry.getInstance().reset();
        PlayerDirectory.getInstance().clear();
        ScoringRules.reloadDefaults();
        pipeline = new IngestPipeline();
        api = new CricketApi(pipeline);
    }

    @After
    public void tearDown() {
        pipeline.shutdown(TIMEOUT);
        MatchRegistry.getInstance().reset();
        PlayerDirectory.getInstance().clear();
        ScoringRules.reloadDefaults();
    }

    private ApiResponse get(String path) {
        return api.handle(new ApiRequest("GET", path));
    }

    private ApiResponse post(String path, String body) {
        return api.handle(new ApiRequest("POST", path, body));
    }

    /** Creates a match and puts it in progress with openers at the crease. */
    private void startMatch(String id) {
        post("/matches", "{\"matchId\":\"" + id + "\"}");
        api.handle(new ApiRequest("POST", "/admin/start", null,
                java.util.Collections.singletonMap("matchId", id), "test"));
    }

    @Test
    public void healthReportsOk() {
        ApiResponse response = get("/health");
        assertEquals(ApiResponse.OK, response.getStatus());
        assertEquals("ok", response.json().get("status").asString());
    }

    @Test
    public void healthReportsTheMatchCount() {
        post("/matches", "{\"matchId\":\"M1\"}");
        assertEquals(1, get("/health").json().get("matches").asInt());
    }

    @Test
    public void healthRejectsPost() {
        assertEquals(ApiResponse.METHOD_NOT_ALLOWED, post("/health", null).getStatus());
    }

    @Test
    public void anUnknownRouteIsNotFound() {
        assertEquals(ApiResponse.NOT_FOUND, get("/nonsense").getStatus());
    }

    @Test
    public void theRootIsNotFound() {
        assertEquals(ApiResponse.NOT_FOUND, get("/").getStatus());
    }

    @Test
    public void aMatchIsCreated() {
        ApiResponse response = post("/matches", "{\"matchId\":\"M1\"}");
        assertEquals(ApiResponse.CREATED, response.getStatus());
        assertEquals("M1", response.json().get("matchId").asString());
    }

    @Test
    public void aCreatedMatchIsRegistered() {
        post("/matches", "{\"matchId\":\"M1\"}");
        assertTrue(MatchRegistry.getInstance().contains("M1"));
    }

    @Test
    public void creatingAMatchImportsBothSquads() {
        post("/matches", "{\"matchId\":\"M1\"}");
        assertEquals(22, PlayerDirectory.getInstance().size());
    }

    @Test
    public void theFormatDefaultsToT20() {
        assertEquals("T20", post("/matches", "{\"matchId\":\"M1\"}").json().get("format").asString());
    }

    @Test
    public void anExplicitFormatIsHonoured() {
        ApiResponse response = post("/matches", "{\"matchId\":\"M1\",\"format\":\"ODI\"}");
        assertEquals("ODI", response.json().get("format").asString());
    }

    @Test
    public void anUnknownFormatIsRejected() {
        ApiResponse response = post("/matches", "{\"matchId\":\"M1\",\"format\":\"HUNDRED\"}");
        assertEquals(ApiResponse.BAD_REQUEST, response.getStatus());
        assertTrue(response.errorMessage().contains("unknown format"));
    }

    @Test
    public void explicitTeamsAreHonoured() {
        ApiResponse response = post("/matches",
                "{\"matchId\":\"M1\",\"homeTeam\":\"ENG\",\"awayTeam\":\"SA\"}");
        assertEquals(ApiResponse.CREATED, response.getStatus());
    }

    @Test
    public void anUnknownTeamIsRejected() {
        ApiResponse response = post("/matches", "{\"matchId\":\"M1\",\"homeTeam\":\"NZ\"}");
        assertEquals(ApiResponse.BAD_REQUEST, response.getStatus());
        assertTrue(response.errorMessage().contains("unknown team"));
    }

    @Test
    public void aTeamCannotPlayItself() {
        ApiResponse response = post("/matches",
                "{\"matchId\":\"M1\",\"homeTeam\":\"IND\",\"awayTeam\":\"IND\"}");
        assertEquals(ApiResponse.BAD_REQUEST, response.getStatus());
    }

    @Test
    public void aDuplicateMatchIdIsRejected() {
        post("/matches", "{\"matchId\":\"M1\"}");
        ApiResponse response = post("/matches", "{\"matchId\":\"M1\"}");
        assertEquals(ApiResponse.BAD_REQUEST, response.getStatus());
        assertTrue(response.errorMessage().contains("already registered"));
    }

    @Test
    public void aMissingMatchIdIsRejected() {
        assertEquals(ApiResponse.BAD_REQUEST, post("/matches", "{}").getStatus());
    }

    @Test
    public void aMissingBodyIsRejected() {
        assertEquals(ApiResponse.BAD_REQUEST, post("/matches", null).getStatus());
    }

    @Test
    public void malformedJsonIsRejected() {
        ApiResponse response = post("/matches", "{not json");
        assertEquals(ApiResponse.BAD_REQUEST, response.getStatus());
        assertTrue(response.errorMessage().contains("malformed JSON"));
    }

    @Test
    public void matchesAreListed() {
        post("/matches", "{\"matchId\":\"M1\"}");
        post("/matches", "{\"matchId\":\"M2\"}");
        ApiResponse response = get("/matches");
        assertEquals(2, response.json().get("count").asInt());
        assertEquals(2, response.json().get("matchIds").size());
    }

    @Test
    public void anEmptyRegistryListsNothing() {
        assertEquals(0, get("/matches").json().get("count").asInt());
    }

    @Test
    public void aMatchDetailIsServed() {
        post("/matches", "{\"matchId\":\"M1\",\"venue\":\"Lord's\"}");
        ApiResponse response = get("/matches/M1");
        assertEquals("Lord's", response.json().get("venue").asString());
        assertEquals("SCHEDULED", response.json().get("state").asString());
    }

    @Test
    public void anUnknownMatchIsNotFound() {
        assertEquals(ApiResponse.NOT_FOUND, get("/matches/nope").getStatus());
    }

    @Test
    public void startingAMatchOpensAnInnings() {
        startMatch("M1");
        ApiResponse response = get("/matches/M1");
        assertEquals("IN_PROGRESS", response.json().get("state").asString());
        assertEquals(1, response.json().get("innings").asInt());
    }

    @Test
    public void aDeliveryIsAccepted() {
        startMatch("M1");
        ApiResponse response = post("/matches/M1/balls",
                "{\"over\":0,\"ballInOver\":1,\"bowler\":\"AUS8\",\"runsOffBat\":4}");
        assertEquals(ApiResponse.CREATED, response.getStatus());
        assertEquals("0.1", response.json().get("address").asString());
    }

    @Test
    public void aDeliveryReachesTheScorecard() {
        startMatch("M1");
        post("/matches/M1/balls",
                "{\"over\":0,\"ballInOver\":1,\"bowler\":\"AUS8\",\"runsOffBat\":4}");
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertEquals(4, get("/matches/M1/scorecard").json().get("runs").asInt());
    }

    @Test
    public void severalDeliveriesAccumulate() {
        startMatch("M1");
        for (int i = 1; i <= 6; i++) {
            post("/matches/M1/balls",
                    "{\"over\":0,\"ballInOver\":" + i + ",\"bowler\":\"AUS8\",\"runsOffBat\":1}");
        }
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        JsonValue card = get("/matches/M1/scorecard").json();
        assertEquals(6, card.get("runs").asInt());
        assertEquals("6/0 (1.0)", card.get("summary").asString());
    }

    @Test
    public void anExtraIsRecorded() {
        startMatch("M1");
        post("/matches/M1/balls",
                "{\"over\":0,\"ballInOver\":1,\"bowler\":\"AUS8\",\"extraType\":\"WIDE\"}");
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        JsonValue card = get("/matches/M1/scorecard").json();
        assertEquals(1, card.get("runs").asInt());
        assertEquals(1, card.get("extras").asInt());
    }

    @Test
    public void aWicketIsRecorded() {
        startMatch("M1");
        post("/matches/M1/balls",
                "{\"over\":0,\"ballInOver\":1,\"bowler\":\"AUS8\",\"dismissal\":\"BOWLED\"}");
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertEquals(1, get("/matches/M1/scorecard").json().get("wickets").asInt());
    }

    @Test
    public void aDeliveryToAnUnstartedMatchIsRejected() {
        post("/matches", "{\"matchId\":\"M1\"}");
        ApiResponse response = post("/matches/M1/balls",
                "{\"over\":0,\"ballInOver\":1,\"bowler\":\"AUS8\"}");
        assertEquals(ApiResponse.BAD_REQUEST, response.getStatus());
        assertTrue(response.errorMessage().contains("no innings"));
    }

    @Test
    public void aDeliveryToAnUnknownMatchIsNotFound() {
        assertEquals(ApiResponse.NOT_FOUND,
                post("/matches/nope/balls", "{\"bowler\":\"AUS8\"}").getStatus());
    }

    @Test
    public void aDeliveryWithoutABodyIsRejected() {
        startMatch("M1");
        assertEquals(ApiResponse.BAD_REQUEST, post("/matches/M1/balls", null).getStatus());
    }

    @Test
    public void aDeliveryWithoutABowlerIsRejected() {
        startMatch("M1");
        assertEquals(ApiResponse.BAD_REQUEST,
                post("/matches/M1/balls", "{\"over\":0,\"ballInOver\":1}").getStatus());
    }

    @Test
    public void gettingBallsIsNotAllowed() {
        startMatch("M1");
        assertEquals(ApiResponse.METHOD_NOT_ALLOWED, get("/matches/M1/balls").getStatus());
    }

    @Test
    public void commentaryIsServed() {
        startMatch("M1");
        post("/matches/M1/balls",
                "{\"over\":0,\"ballInOver\":1,\"bowler\":\"AUS8\",\"runsOffBat\":4}");
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        ApiResponse response = get("/matches/M1/commentary");
        assertEquals(1, response.json().get("count").asInt());
        assertTrue(response.json().get("lines").get(0).asString().contains("FOUR"));
    }

    @Test
    public void commentaryRespectsTheLimit() {
        startMatch("M1");
        for (int i = 1; i <= 6; i++) {
            post("/matches/M1/balls",
                    "{\"over\":0,\"ballInOver\":" + i + ",\"bowler\":\"AUS8\",\"runsOffBat\":1}");
        }
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        ApiResponse response = api.handle(new ApiRequest("GET", "/matches/M1/commentary", null,
                java.util.Collections.singletonMap("limit", "2"), "test"));
        assertEquals(2, response.json().get("count").asInt());
    }

    @Test
    public void aNegativeCommentaryLimitIsRejected() {
        startMatch("M1");
        ApiResponse response = api.handle(new ApiRequest("GET", "/matches/M1/commentary", null,
                java.util.Collections.singletonMap("limit", "-1"), "test"));
        assertEquals(ApiResponse.BAD_REQUEST, response.getStatus());
    }

    @Test
    public void theLeaderboardIsServed() {
        api.getLeaderboard().tallyFor("IND1").addBatting(80, 50, 8, 2);
        ApiResponse response = get("/stats/leaderboard");
        assertEquals("runs", response.json().get("metric").asString());
        assertEquals(1, response.json().get("count").asInt());
    }

    @Test
    public void theWicketTableIsServed() {
        api.getLeaderboard().tallyFor("AUS8").addBowling(4, 30, 24);
        assertEquals("wickets", get("/stats/wickets").json().get("metric").asString());
    }

    @Test
    public void metricsAreServed() {
        get("/health");
        get("/health");
        ApiResponse response = get("/stats/metrics");
        assertTrue(response.json().get("totalRequests").asInt() >= 2);
    }

    @Test
    public void anUnknownStatsRouteIsNotFound() {
        assertEquals(ApiResponse.NOT_FOUND, get("/stats/nonsense").getStatus());
    }

    @Test
    public void postingToStatsIsNotAllowed() {
        assertEquals(ApiResponse.METHOD_NOT_ALLOWED, post("/stats/leaderboard", null).getStatus());
    }

    @Test
    public void shutdownClearsTheRegistry() {
        post("/matches", "{\"matchId\":\"M1\"}");
        ApiResponse response = post("/admin/shutdown", null);
        assertEquals(1, response.json().get("cleared").asInt());
        assertTrue(MatchRegistry.getInstance().isEmpty());
    }

    @Test
    public void startingAnUnknownMatchIsNotFound() {
        ApiResponse response = api.handle(new ApiRequest("POST", "/admin/start", null,
                java.util.Collections.singletonMap("matchId", "nope"), "test"));
        assertEquals(ApiResponse.NOT_FOUND, response.getStatus());
    }

    @Test
    public void startingWithoutAMatchIdIsRejected() {
        assertEquals(ApiResponse.BAD_REQUEST, post("/admin/start", null).getStatus());
    }

    @Test
    public void anUnknownAdminRouteIsNotFound() {
        assertEquals(ApiResponse.NOT_FOUND, post("/admin/nonsense", null).getStatus());
    }

    @Test
    public void metricsCountEveryRequest() {
        get("/health");
        get("/nonsense");
        assertEquals(2, api.getMetrics().totalRequests());
        assertEquals(1, api.getMetrics().successCount());
        assertEquals(1, api.getMetrics().errorCount());
    }

    @Test
    public void metricsCollapseTheMatchIdIntoOneRoute() {
        post("/matches", "{\"matchId\":\"M1\"}");
        post("/matches", "{\"matchId\":\"M2\"}");
        get("/matches/M1");
        get("/matches/M2");
        assertEquals(2, api.getMetrics().countForRoute("GET/matches/{id}"));
    }

    @Test
    public void aRateLimitedClientIsRefused() {
        RateLimiter limiter = new RateLimiter(2, 60000L);
        CricketApi limited = new CricketApi(new IngestPipeline(), new com.cricket.live.ScorecardUpdater(),
                new com.cricket.live.LiveFeedBroadcaster(),
                new com.cricket.stats.LeaderboardService(), limiter);
        assertTrue(limited.handle(new ApiRequest("GET", "/health")).isSuccess());
        assertTrue(limited.handle(new ApiRequest("GET", "/health")).isSuccess());
        ApiResponse third = limited.handle(new ApiRequest("GET", "/health"));
        assertEquals(ApiResponse.TOO_MANY_REQUESTS, third.getStatus());
    }

    @Test(expected = IllegalArgumentException.class)
    public void handlingNullIsRejected() {
        api.handle(null);
    }

    @Test
    public void theRequestPathMustBeAbsolute() {
        try {
            new ApiRequest("GET", "health");
            assertFalse("expected rejection", true);
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }
}
