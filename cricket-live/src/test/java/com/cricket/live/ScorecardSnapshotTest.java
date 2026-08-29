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
 * The denormalised snapshot the scorecard endpoint serves, read shortly after the
 * innings has been handed to the pipeline.
 */
public class ScorecardSnapshotTest {

    private static final String MATCH = "IND-AUS-T20";
    private static final int DELIVERIES = 120;
    private static final long SETTLE_MILLIS = 2L;

    private IngestPipeline pipeline;
    private ScorecardUpdater updater;
    private Innings innings;

    @Before
    public void setUp() {
        ScoringRules.reloadDefaults();
        primePipeline();

        pipeline = new IngestPipeline();
        updater = new ScorecardUpdater();
        pipeline.subscribe(updater);
        innings = Fixtures.openedInnings();
    }

    private void primePipeline() {
        IngestPipeline warmup = new IngestPipeline();
        warmup.subscribe(new ScorecardUpdater());
        warmup.submitAll("warmup", Fixtures.openedInnings(), mixedInnings(DELIVERIES));
        warmup.awaitIdle(10000L);
        warmup.shutdown(5000L);
    }

    @After
    public void tearDown() {
        pipeline.shutdown(5000L);
        ScoringRules.reloadDefaults();
    }

    /** A mix of singles and boundaries across a full innings. */
    private List<Ball> mixedInnings(int count) {
        List<Ball> balls = new ArrayList<Ball>();
        for (int i = 0; i < count; i++) {
            balls.add(Ball.builder()
                    .over(i / 6)
                    .ballInOver(i % 6 + 1)
                    .bowler(i / 6 % 2 == 0 ? "AUS8" : "AUS9")
                    .striker("IND1")
                    .nonStriker("IND2")
                    .runsOffBat(i % 5 == 0 ? 4 : 1)
                    .build());
        }
        return balls;
    }

    /** The sequence number only reaches the total once the final event is handled. */
    @Test
    public void theSnapshotReachesTheFinalDelivery() throws InterruptedException {
        pipeline.submitAll(MATCH, innings, mixedInnings(DELIVERIES));

        Thread.sleep(SETTLE_MILLIS);

        assertEquals(DELIVERIES, updater.snapshot(MATCH).getLastSequence());
    }

    @Test
    public void theSnapshotCountsEveryBoundary() throws InterruptedException {
        pipeline.submitAll(MATCH, innings, mixedInnings(DELIVERIES));

        Thread.sleep(SETTLE_MILLIS);

        assertEquals(24, updater.snapshot(MATCH).getBoundaries());
    }

    @Test
    public void theSnapshotIsCompleteWhenProperlyAwaited() {
        pipeline.submitAll(MATCH, innings, mixedInnings(DELIVERIES));
        assertTrue(pipeline.awaitIdle(5000L));
        assertEquals(DELIVERIES, updater.snapshot(MATCH).getLastSequence());
    }
}
