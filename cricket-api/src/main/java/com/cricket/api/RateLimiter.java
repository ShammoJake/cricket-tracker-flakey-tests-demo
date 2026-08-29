package com.cricket.api;

import java.util.HashMap;
import java.util.Map;

/**
 * Fixed-window rate limiter, keyed by client.
 *
 * <p>Each client may make up to {@code limit} requests per window. The window advances
 * on the wall clock, so a client that exhausts its allowance is admitted again once
 * the window rolls over.
 */
public final class RateLimiter {

    /** Per-client state: the window it belongs to and how much has been used. */
    private static final class Window {
        long windowStart;
        int used;
    }

    /** Supplies the current time, so tests can drive the clock. */
    public interface Clock {
        long currentTimeMillis();
    }

    private static final class SystemClock implements Clock {
        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }
    }

    /** A clock the caller advances by hand. */
    public static final class ManualClock implements Clock {
        private long now;

        public ManualClock(long start) {
            this.now = start;
        }

        @Override
        public long currentTimeMillis() {
            return now;
        }

        public void advance(long millis) {
            this.now += millis;
        }

        public void set(long millis) {
            this.now = millis;
        }
    }

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<String, Window>();

    public RateLimiter(int limit, long windowMillis) {
        this(limit, windowMillis, new SystemClock());
    }

    public RateLimiter(int limit, long windowMillis, Clock clock) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        if (windowMillis < 1) {
            throw new IllegalArgumentException("window must be at least 1ms");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    public int getLimit() {
        return limit;
    }

    public long getWindowMillis() {
        return windowMillis;
    }

    /** Admits the request and consumes one unit of allowance, or refuses it. */
    public boolean tryAcquire(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        long now = clock.currentTimeMillis();
        Window window = windows.get(clientId);
        if (window == null) {
            window = new Window();
            window.windowStart = now;
            windows.put(clientId, window);
        }
        if (now - window.windowStart >= windowMillis) {
            window.windowStart = now;
            window.used = 0;
        }
        if (window.used >= limit) {
            return false;
        }
        window.used++;
        return true;
    }

    /** Allowance left in the current window. */
    public int remaining(String clientId) {
        Window window = windows.get(clientId);
        if (window == null) {
            return limit;
        }
        if (clock.currentTimeMillis() - window.windowStart >= windowMillis) {
            return limit;
        }
        return Math.max(0, limit - window.used);
    }

    /** Millis until the client's window rolls over; zero when it already has. */
    public long millisUntilReset(String clientId) {
        Window window = windows.get(clientId);
        if (window == null) {
            return 0L;
        }
        long elapsed = clock.currentTimeMillis() - window.windowStart;
        return Math.max(0L, windowMillis - elapsed);
    }

    public boolean isThrottled(String clientId) {
        return remaining(clientId) == 0;
    }

    public int trackedClients() {
        return windows.size();
    }

    public void reset() {
        windows.clear();
    }
}
