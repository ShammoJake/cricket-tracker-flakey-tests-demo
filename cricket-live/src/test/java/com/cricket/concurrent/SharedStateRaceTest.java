package com.cricket.concurrent;

import com.cricket.core.Fixtures;
import com.cricket.core.engine.ScoringRules;
import com.cricket.core.model.Match;
import com.cricket.core.model.MatchFormat;
import com.cricket.core.registry.MatchRegistry;
import com.cricket.core.registry.SquadCatalog;
import com.cricket.live.LiveFeedBroadcaster;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;

/**
 * Shared structures written from several threads at once: the match registry that
 * every request path reaches through, and the live feed's subscriber set.
 */
public class SharedStateRaceTest {

    private static final int THREADS = 4;
    private static final int PER_THREAD = 40;

    private MatchRegistry registry;

    @Before
    public void setUp() {
        ScoringRules.reloadDefaults();
        registry = MatchRegistry.getInstance();
        registry.reset();
    }

    @After
    public void tearDown() {
        registry.reset();
        ScoringRules.reloadDefaults();
    }

    /** Runs the given task on several threads, released together. */
    private void inParallel(final Runnable body) throws InterruptedException {
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(THREADS);
        for (int t = 0; t < THREADS; t++) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                        body.run();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (RuntimeException e) {
                        // A structure that is not thread safe can throw here; the
                        // assertion on the final count is what reports the problem.
                    } finally {
                        done.countDown();
                    }
                }
            });
            thread.setName("writer-" + t);
            thread.start();
        }
        start.countDown();
        done.await();
    }

    /** Each thread registers its own block of match ids. */
    private final class Registrar implements Runnable {
        private final int base;

        Registrar(int base) {
            this.base = base;
        }

        @Override
        public void run() {
            for (int i = 0; i < PER_THREAD; i++) {
                Match match = new Match("M" + (base + i), SquadCatalog.require("IND"),
                        SquadCatalog.require("AUS"), MatchFormat.T20, "Stadium");
                registry.register(match);
            }
        }
    }

    @Test
    public void concurrentRegistrationsAreAllVisible() throws InterruptedException {
        final int[] next = {0};
        inParallel(new Runnable() {
            @Override
            public void run() {
                int base;
                synchronized (next) {
                    base = next[0];
                    next[0] += PER_THREAD;
                }
                new Registrar(base).run();
            }
        });
        assertEquals(THREADS * PER_THREAD, registry.size());
    }

    @Test
    public void theRegistrationCounterMatchesTheRegistrations() throws InterruptedException {
        final int[] next = {0};
        inParallel(new Runnable() {
            @Override
            public void run() {
                int base;
                synchronized (next) {
                    base = next[0];
                    next[0] += PER_THREAD;
                }
                new Registrar(base).run();
            }
        });
        assertEquals(THREADS * PER_THREAD, registry.getRegistrations());
    }

    @Test
    public void everySubscriberIsRetainedOnTheFeed() throws InterruptedException {
        final LiveFeedBroadcaster feed = new LiveFeedBroadcaster();
        final int[] next = {0};
        inParallel(new Runnable() {
            @Override
            public void run() {
                int base;
                synchronized (next) {
                    base = next[0];
                    next[0] += PER_THREAD;
                }
                for (int i = 0; i < PER_THREAD; i++) {
                    feed.subscribe(new LiveFeedBroadcaster.RecordingSubscriber("s" + (base + i)));
                }
            }
        });
        assertEquals(THREADS * PER_THREAD, feed.subscriberCount());
    }
}
