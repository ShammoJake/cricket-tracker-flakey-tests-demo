package com.cricket.live;

import com.cricket.core.Fixtures;
import com.cricket.core.engine.ScoringRules;
import com.cricket.core.model.Ball;
import com.cricket.core.model.Innings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Ingest of a full innings, read back once the workers have had time to catch up.
 */
public class LiveScoreIngestTest {

    private static final String MATCH = "IND-AUS-T20";

    /** A full T20 innings. */
    private static final int DELIVERIES = 120;

    /** How long the workers are given before the score is read. */
    private static final long SETTLE_MILLIS = 3L;

    private IngestPipeline pipeline;
    private ScorecardUpdater updater;
    private PartnershipTracker partnerships;
    private LiveFeedBroadcaster feed;
    private Innings innings;

    @Before
    public void setUp() {
        ScoringRules.reloadDefaults();
        primePipeline();

        pipeline = new IngestPipeline();
        updater = new ScorecardUpdater();
        partnerships = new PartnershipTracker();
        feed = new LiveFeedBroadcaster();
        pipeline.subscribe(updater);
        pipeline.subscribe(partnerships);
        pipeline.subscribe(feed);
        innings = Fixtures.openedInnings();
    }

    /**
     * Runs a throwaway innings through a separate pipeline first. Without this the
     * measurement is dominated by class loading and JIT on whichever test happens
     * to run first, rather than by the pipeline itself.
     */
    private void primePipeline() {
        IngestPipeline warmup = new IngestPipeline();
        warmup.subscribe(new ScorecardUpdater());
        warmup.subscribe(new PartnershipTracker());
        warmup.subscribe(new LiveFeedBroadcaster());
        Innings scratch = Fixtures.openedInnings();
        warmup.submitAll("warmup", scratch, inningsOfSingles(DELIVERIES));
        warmup.awaitIdle(10000L);
        warmup.shutdown(5000L);
    }

    @After
    public void tearDown() {
        pipeline.shutdown(5000L);
        ScoringRules.reloadDefaults();
    }

    /** One single off each delivery, addressed legally through the innings. */
    private List<Ball> inningsOfSingles(int count) {
        List<Ball> balls = new ArrayList<Ball>();
        for (int i = 0; i < count; i++) {
            balls.add(Ball.builder()
                    .over(i / 6)
                    .ballInOver(i % 6 + 1)
                    .bowler(i / 6 % 2 == 0 ? "AUS8" : "AUS9")
                    .striker("IND1")
                    .nonStriker("IND2")
                    .runsOffBat(1)
                    .build());
        }
        return balls;
    }

    @Test
    public void scorecardReflectsAllBallsAfterIngest() throws InterruptedException {
        pipeline.submitAll(MATCH, innings, inningsOfSingles(DELIVERIES));

        Thread.sleep(SETTLE_MILLIS);

        assertEquals(DELIVERIES, updater.snapshot(MATCH).getRuns());
    }

    @Test
    public void everyDeliveryIsAppliedAfterIngest() throws InterruptedException {
        pipeline.submitAll(MATCH, innings, inningsOfSingles(DELIVERIES));

        Thread.sleep(SETTLE_MILLIS - 1L);

        assertEquals(DELIVERIES, updater.appliedCount());
    }

    @Test
    public void theInningsTotalIsVisibleAfterIngest() throws InterruptedException {
        pipeline.submitAll(MATCH, innings, inningsOfSingles(DELIVERIES));

        Thread.sleep(SETTLE_MILLIS + 1L);

        assertEquals(DELIVERIES, innings.getScoreCard().getTotalRuns());
    }

    @Test
    public void commentaryCoversTheWholeInnings() throws InterruptedException {
        pipeline.submitAll(MATCH, innings, inningsOfSingles(DELIVERIES));

        Thread.sleep(SETTLE_MILLIS + 1L);

        assertEquals(DELIVERIES, feed.broadcastCount());
    }

    @Test
    public void thePartnershipIsVisibleAfterIngest() throws InterruptedException {
        pipeline.submitAll(MATCH, innings, inningsOfSingles(DELIVERIES));

        Thread.sleep(SETTLE_MILLIS - 1L);

        assertEquals(DELIVERIES, partnerships.currentStand(MATCH).getRuns());
    }

    @Test
    public void pipelineDrainsWhenProperlyAwaited() {
        pipeline.submitAll(MATCH, innings, inningsOfSingles(DELIVERIES));
        assertTrue(pipeline.awaitIdle(5000L));
        assertEquals(DELIVERIES, updater.snapshot(MATCH).getRuns());
    }
}
