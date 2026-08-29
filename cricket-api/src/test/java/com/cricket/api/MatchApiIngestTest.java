package com.cricket.api;

import com.cricket.core.engine.ScoringRules;
import com.cricket.core.registry.MatchRegistry;
import com.cricket.core.registry.PlayerDirectory;
import com.cricket.live.IngestPipeline;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Posting deliveries and reading the derived views back, the way a scoring client
 * drives the service.
 */
public class MatchApiIngestTest {

    private static final int DELIVERIES = 120;

    /**
     * The pipeline drains while the deliveries are still being posted, so only the
     * tail of the innings is ever outstanding. The wait is correspondingly short.
     */
    private static final long SETTLE_MILLIS = 1L;

    private IngestPipeline pipeline;
    private CricketApi api;

    @Before
    public void setUp() {
        MatchRegistry.getInstance().reset();
        PlayerDirectory.getInstance().clear();
        ScoringRules.reloadDefaults();
        primeApi();

        pipeline = new IngestPipeline();
        api = new CricketApi(pipeline);
        startMatch("M1");
    }

    /** Drives a throwaway match through the API so the paths are already warm. */
    private void primeApi() {
        IngestPipeline warmup = new IngestPipeline();
        CricketApi warmApi = new CricketApi(warmup);
        warmApi.handle(new ApiRequest("POST", "/matches", "{\"matchId\":\"WARM\"}"));
        warmApi.handle(new ApiRequest("POST", "/admin/start", null,
                Collections.singletonMap("matchId", "WARM"), "warm"));
        for (int i = 0; i < DELIVERIES; i++) {
            warmApi.handle(new ApiRequest("POST", "/matches/WARM/balls", ballBody(i)));
        }
        warmup.awaitIdle(10000L);
        warmup.shutdown(5000L);
        MatchRegistry.getInstance().reset();
        PlayerDirectory.getInstance().clear();
    }

    @After
    public void tearDown() {
        pipeline.shutdown(5000L);
        MatchRegistry.getInstance().reset();
        PlayerDirectory.getInstance().clear();
        ScoringRules.reloadDefaults();
    }

    private static String ballBody(int index) {
        return "{\"over\":" + (index / 6)
                + ",\"ballInOver\":" + (index % 6 + 1)
                + ",\"bowler\":\"" + (index / 6 % 2 == 0 ? "AUS8" : "AUS9")
                + "\",\"striker\":\"IND1\",\"nonStriker\":\"IND2\",\"runsOffBat\":1}";
    }

    private void startMatch(String id) {
        api.handle(new ApiRequest("POST", "/matches", "{\"matchId\":\"" + id + "\"}"));
        api.handle(new ApiRequest("POST", "/admin/start", null,
                Collections.singletonMap("matchId", id), "test"));
    }

    private void postInnings() {
        for (int i = 0; i < DELIVERIES; i++) {
            api.handle(new ApiRequest("POST", "/matches/M1/balls", ballBody(i)));
        }
    }

    @Test
    public void postedDeliveriesShowUpOnTheScorecard() throws InterruptedException {
        postInnings();

        Thread.sleep(SETTLE_MILLIS);

        ApiResponse response = api.handle(new ApiRequest("GET", "/matches/M1/scorecard"));
        assertEquals(DELIVERIES, response.json().get("runs").asInt());
    }

    @Test
    public void commentaryCoversEveryPostedDelivery() throws InterruptedException {
        postInnings();

        Thread.sleep(SETTLE_MILLIS);

        ApiResponse response = api.handle(new ApiRequest("GET", "/matches/M1/commentary", null,
                Collections.singletonMap("limit", "500"), "test"));
        assertEquals(DELIVERIES, response.json().get("count").asInt());
    }

    @Test
    public void theOverCountIsCurrentOnTheScorecard() throws InterruptedException {
        postInnings();

        Thread.sleep(SETTLE_MILLIS);

        ApiResponse response = api.handle(new ApiRequest("GET", "/matches/M1/scorecard"));
        assertEquals(20.0, response.json().get("overs").asDouble(), 1e-9);
    }

    @Test
    public void theScorecardIsCompleteWhenProperlyAwaited() {
        postInnings();
        assertTrue(pipeline.awaitIdle(5000L));
        ApiResponse response = api.handle(new ApiRequest("GET", "/matches/M1/scorecard"));
        assertEquals(DELIVERIES, response.json().get("runs").asInt());
    }
}
