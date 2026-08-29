package com.cricket.concurrent;

import com.cricket.core.Fixtures;
import com.cricket.core.engine.ScoringRules;
import com.cricket.core.model.Ball;
import com.cricket.core.model.Innings;
import com.cricket.live.IngestPipeline;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;

/**
 * Ingest under load, with the pipeline configured to use several worker threads
 * so deliveries for one innings are applied in parallel.
 */
public class ConcurrentBallIngestTest {

    private static final long TIMEOUT = 10000L;
    private static final String MATCH = "IND-AUS-T20";

    private static final int WRITERS = 4;
    private static final int BALLS_PER_WRITER = 25;

    private IngestPipeline pipeline;
    private Innings innings;

    @Before
    public void setUp() {
        ScoringRules.reloadDefaults();
        pipeline = new IngestPipeline(4);
        innings = Fixtures.openedInnings();
    }

    @After
    public void tearDown() {
        pipeline.shutdown(TIMEOUT);
        ScoringRules.reloadDefaults();
    }

    /** One delivery worth a single run, addressed legally. */
    private Ball single(int index) {
        return Ball.builder()
                .over(index / 6)
                .ballInOver(index % 6 + 1)
                .bowler("AUS8")
                .striker("IND1")
                .nonStriker("IND2")
                .runsOffBat(1)
                .build();
    }

    /** Submits from several threads at once, released together by a latch. */
    private void ingestInParallel(int writers, int ballsEach) throws InterruptedException {
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(writers);
        for (int w = 0; w < writers; w++) {
            final int base = w * ballsEach;
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                        for (int i = 0; i < BALLS_PER_WRITER; i++) {
                            pipeline.submit(MATCH, innings, single(base + i));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                }
            });
            thread.start();
        }
        start.countDown();
        done.await();
        pipeline.awaitIdle(TIMEOUT);
    }

    @Test
    public void parallelIngestKeepsRunTotalConsistent() throws InterruptedException {
        ingestInParallel(WRITERS, BALLS_PER_WRITER);
        assertEquals(WRITERS * BALLS_PER_WRITER, innings.getScoreCard().getTotalRuns());
    }

    @Test
    public void parallelIngestRecordsEveryLegalBall() throws InterruptedException {
        ingestInParallel(WRITERS, BALLS_PER_WRITER);
        assertEquals(WRITERS * BALLS_PER_WRITER, innings.getScoreCard().getLegalBalls());
    }

    @Test
    public void everySubmittedDeliveryReachesTheInnings() throws InterruptedException {
        ingestInParallel(WRITERS, BALLS_PER_WRITER);
        assertEquals(WRITERS * BALLS_PER_WRITER, innings.ballCount());
    }

    @Test
    public void theBatterIsCreditedWithEveryRun() throws InterruptedException {
        ingestInParallel(WRITERS, BALLS_PER_WRITER);
        assertEquals(WRITERS * BALLS_PER_WRITER,
                innings.getScoreCard().battingLine("IND1").getRuns());
    }
}
