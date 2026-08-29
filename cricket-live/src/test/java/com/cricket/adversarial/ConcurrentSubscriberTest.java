package com.cricket.adversarial;

import com.cricket.core.Fixtures;
import com.cricket.core.engine.ScoringRules;
import com.cricket.core.model.Ball;
import com.cricket.core.model.Innings;
import com.cricket.live.IngestPipeline;
import com.cricket.live.LiveFeedBroadcaster;
import com.cricket.live.ScorecardUpdater;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Several subscribers on the live feed at once, each reading what it was sent.
 *
 * <p>Every case here waits for the pipeline to drain before it asserts anything, and
 * every shared structure is a concurrent one, so the outcome does not depend on how the
 * threads happen to interleave.
 */
public class ConcurrentSubscriberTest {

    private static final String MATCH = "IND-AUS-T20";
    private static final int DELIVERIES = 120;
    private static final long DRAIN_TIMEOUT_MILLIS = 5000L;
    private static final int SUBSCRIBER_THREADS = 4;

    private IngestPipeline pipeline;
    private LiveFeedBroadcaster feed;
    private ScorecardUpdater updater;
    private Innings innings;

    @Before
    public void setUp() {
        ScoringRules.reloadDefaults();
        pipeline = new IngestPipeline();
        feed = new LiveFeedBroadcaster();
        updater = new ScorecardUpdater();
        pipeline.subscribe(feed);
        pipeline.subscribe(updater);
        innings = Fixtures.openedInnings();
    }

    @After
    public void tearDown() {
        pipeline.shutdown(DRAIN_TIMEOUT_MILLIS);
        ScoringRules.reloadDefaults();
    }

    private static List<Ball> inningsOfSingles(int count) {
        List<Ball> balls = new ArrayList<Ball>();
        for (int i = 0; i < count; i++) {
            balls.add(Ball.builder()
                    .over(i / 6).ballInOver(i % 6 + 1)
                    .bowler(i / 6 % 2 == 0 ? "AUS8" : "AUS9")
                    .striker("IND1").nonStriker("IND2")
                    .runsOffBat(1).build());
        }
        return balls;
    }

    /**
     * Every subscriber on the feed receives the innings, with the counts read back from
     * several threads at once.
     *
     * <p>The subscriber set is not a concurrent structure, so registration is done on
     * one thread before a ball is bowled. The pool only reads, and only after the
     * pipeline has drained.
     */
    @Test
    public void everySubscriberOnTheFeedReceivesTheInnings() throws InterruptedException {
        final List<LiveFeedBroadcaster.RecordingSubscriber> viewers =
                new ArrayList<LiveFeedBroadcaster.RecordingSubscriber>();
        for (int i = 0; i < SUBSCRIBER_THREADS; i++) {
            LiveFeedBroadcaster.RecordingSubscriber viewer =
                    new LiveFeedBroadcaster.RecordingSubscriber("viewer-" + i);
            viewers.add(viewer);
            feed.subscribe(viewer);
        }

        pipeline.submitAll(MATCH, innings, inningsOfSingles(DELIVERIES));
        assertTrue(pipeline.awaitIdle(DRAIN_TIMEOUT_MILLIS));

        final Map<String, Integer> counts = new ConcurrentHashMap<String, Integer>();
        final CountDownLatch read = new CountDownLatch(SUBSCRIBER_THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(SUBSCRIBER_THREADS);

        for (final LiveFeedBroadcaster.RecordingSubscriber viewer : viewers) {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    counts.put(viewer.id(), viewer.count());
                    read.countDown();
                }
            });
        }

        assertTrue(read.await(DRAIN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        pool.shutdown();
        assertTrue(pool.awaitTermination(DRAIN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

        assertEquals(SUBSCRIBER_THREADS, counts.size());
        for (Integer count : counts.values()) {
            assertEquals(Integer.valueOf(DELIVERIES), count);
        }
    }

    /**
     * Counting the deliveries from several threads into a concurrent map, once the
     * pipeline has already drained, so the transcript being counted is complete.
     */
    @Test
    public void theTranscriptTalliesTheSameFromEveryThread() throws InterruptedException {
        pipeline.submitAll(MATCH, innings, inningsOfSingles(DELIVERIES));
        assertTrue(pipeline.awaitIdle(DRAIN_TIMEOUT_MILLIS));

        final Map<String, Integer> tallies = new ConcurrentHashMap<String, Integer>();
        final CountDownLatch counted = new CountDownLatch(SUBSCRIBER_THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(SUBSCRIBER_THREADS);

        for (int i = 0; i < SUBSCRIBER_THREADS; i++) {
            final String key = "counter-" + i;
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    tallies.put(key, feed.getTranscript().size());
                    counted.countDown();
                }
            });
        }

        assertTrue(counted.await(DRAIN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        pool.shutdown();
        assertTrue(pool.awaitTermination(DRAIN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

        assertEquals(SUBSCRIBER_THREADS, tallies.size());
        for (Integer tally : tallies.values()) {
            assertEquals(Integer.valueOf(DELIVERIES), tally);
        }
    }

    /**
     * An atomic counter incremented once per delivery by the ingest thread, read after
     * the queue has drained.
     */
    @Test
    public void theAtomicDeliveryCountMatchesTheInnings() throws InterruptedException {
        final AtomicInteger delivered = new AtomicInteger();
        pipeline.subscribe(new com.cricket.live.BallListener() {
            @Override
            public String name() {
                return "atomic-counter";
            }

            @Override
            public void onBall(com.cricket.live.BallEvent event) {
                delivered.incrementAndGet();
            }
        });

        pipeline.submitAll(MATCH, innings, inningsOfSingles(DELIVERIES));
        assertTrue(pipeline.awaitIdle(DRAIN_TIMEOUT_MILLIS));

        // A pause after the queue has drained changes nothing; the count is already final.
        Thread.sleep(2L);

        assertEquals(DELIVERIES, delivered.get());
    }

    /**
     * The scoreboard read after an explicit drain, with a sleep in front of it that has
     * nothing left to wait for.
     */
    @Test
    public void theScorecardIsSettledOnceTheQueueHasDrained() throws InterruptedException {
        pipeline.submitAll(MATCH, innings, inningsOfSingles(DELIVERIES));
        assertTrue(pipeline.awaitIdle(DRAIN_TIMEOUT_MILLIS));
        Thread.sleep(1L);

        ScorecardUpdater.Snapshot snapshot = updater.snapshot(MATCH);
        assertEquals(DELIVERIES, snapshot.getRuns());
        assertEquals(DELIVERIES, snapshot.getLegalBalls());
        assertEquals(DELIVERIES, snapshot.getLastSequence());
    }
}
