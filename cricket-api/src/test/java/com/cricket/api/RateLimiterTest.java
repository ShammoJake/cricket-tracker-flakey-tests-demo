package com.cricket.api;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Driven by a {@link RateLimiter.ManualClock} rather than the wall clock, so the
 * window boundaries are exact and the assertions cannot race.
 */
public class RateLimiterTest {

    private RateLimiter.ManualClock clock;
    private RateLimiter limiter;

    @Before
    public void setUp() {
        clock = new RateLimiter.ManualClock(1_000_000L);
        limiter = new RateLimiter(3, 1000L, clock);
    }

    @Test
    public void theFirstRequestIsAdmitted() {
        assertTrue(limiter.tryAcquire("c1"));
    }

    @Test
    public void requestsUpToTheLimitAreAdmitted() {
        assertTrue(limiter.tryAcquire("c1"));
        assertTrue(limiter.tryAcquire("c1"));
        assertTrue(limiter.tryAcquire("c1"));
    }

    @Test
    public void theRequestPastTheLimitIsRefused() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("c1");
        }
        assertFalse(limiter.tryAcquire("c1"));
    }

    @Test
    public void clientsHaveSeparateAllowances() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("c1");
        }
        assertTrue(limiter.tryAcquire("c2"));
    }

    @Test
    public void theWindowRollsOverAndRestoresTheAllowance() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("c1");
        }
        assertFalse(limiter.tryAcquire("c1"));
        clock.advance(1000L);
        assertTrue(limiter.tryAcquire("c1"));
    }

    @Test
    public void theWindowDoesNotRollOverEarly() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("c1");
        }
        clock.advance(999L);
        assertFalse(limiter.tryAcquire("c1"));
    }

    @Test
    public void remainingStartsAtTheLimit() {
        assertEquals(3, limiter.remaining("c1"));
    }

    @Test
    public void remainingCountsDown() {
        limiter.tryAcquire("c1");
        assertEquals(2, limiter.remaining("c1"));
        limiter.tryAcquire("c1");
        assertEquals(1, limiter.remaining("c1"));
    }

    @Test
    public void remainingBottomsOutAtZero() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("c1");
        }
        assertEquals(0, limiter.remaining("c1"));
    }

    @Test
    public void remainingResetsWithTheWindow() {
        limiter.tryAcquire("c1");
        clock.advance(1000L);
        assertEquals(3, limiter.remaining("c1"));
    }

    @Test
    public void anExhaustedClientIsThrottled() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("c1");
        }
        assertTrue(limiter.isThrottled("c1"));
    }

    @Test
    public void aFreshClientIsNotThrottled() {
        assertFalse(limiter.isThrottled("c1"));
    }

    @Test
    public void timeUntilResetCountsDown() {
        limiter.tryAcquire("c1");
        assertEquals(1000L, limiter.millisUntilReset("c1"));
        clock.advance(400L);
        assertEquals(600L, limiter.millisUntilReset("c1"));
    }

    @Test
    public void timeUntilResetIsZeroForAnUnseenClient() {
        assertEquals(0L, limiter.millisUntilReset("c1"));
    }

    @Test
    public void timeUntilResetBottomsOutAtZero() {
        limiter.tryAcquire("c1");
        clock.advance(5000L);
        assertEquals(0L, limiter.millisUntilReset("c1"));
    }

    @Test
    public void clientsAreTracked() {
        limiter.tryAcquire("c1");
        limiter.tryAcquire("c2");
        assertEquals(2, limiter.trackedClients());
    }

    @Test
    public void resetForgetsEveryClient() {
        limiter.tryAcquire("c1");
        limiter.reset();
        assertEquals(0, limiter.trackedClients());
        assertEquals(3, limiter.remaining("c1"));
    }

    @Test
    public void theConfigurationIsReadable() {
        assertEquals(3, limiter.getLimit());
        assertEquals(1000L, limiter.getWindowMillis());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aZeroLimitIsRejected() {
        new RateLimiter(0, 1000L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aZeroWindowIsRejected() {
        new RateLimiter(3, 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNullClockIsRejected() {
        new RateLimiter(3, 1000L, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aBlankClientIsRejected() {
        limiter.tryAcquire("  ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNullClientIsRejected() {
        limiter.tryAcquire(null);
    }

    @Test
    public void theClockCanBeSetOutright() {
        limiter.tryAcquire("c1");
        clock.set(2_000_000L);
        assertEquals(3, limiter.remaining("c1"));
    }

    @Test
    public void severalWindowsInSuccessionEachAllowTheLimit() {
        for (int window = 0; window < 4; window++) {
            for (int i = 0; i < 3; i++) {
                assertTrue(limiter.tryAcquire("c1"));
            }
            assertFalse(limiter.tryAcquire("c1"));
            clock.advance(1000L);
        }
    }
}
