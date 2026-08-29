package com.cricket.api;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Request counters for the HTTP layer.
 *
 * <p>Deliberately built on {@link ConcurrentHashMap} and {@link AtomicLong}: several
 * request threads update these counters at once, so every mutation here has to be
 * safe under concurrency.
 */
public final class RequestMetrics {

    private final ConcurrentMap<String, AtomicLong> byRoute = new ConcurrentHashMap<String, AtomicLong>();
    private final ConcurrentMap<Integer, AtomicLong> byStatus = new ConcurrentHashMap<Integer, AtomicLong>();
    private final AtomicLong total = new AtomicLong();
    private final AtomicLong totalLatencyMicros = new AtomicLong();

    private AtomicLong counterFor(ConcurrentMap<String, AtomicLong> map, String key) {
        AtomicLong counter = map.get(key);
        if (counter == null) {
            AtomicLong created = new AtomicLong();
            counter = map.putIfAbsent(key, created);
            if (counter == null) {
                counter = created;
            }
        }
        return counter;
    }

    private AtomicLong statusCounter(int status) {
        AtomicLong counter = byStatus.get(status);
        if (counter == null) {
            AtomicLong created = new AtomicLong();
            counter = byStatus.putIfAbsent(status, created);
            if (counter == null) {
                counter = created;
            }
        }
        return counter;
    }

    /** Records one completed request. */
    public void record(String route, int status, long latencyMicros) {
        if (route == null) {
            throw new IllegalArgumentException("route must not be null");
        }
        counterFor(byRoute, route).incrementAndGet();
        statusCounter(status).incrementAndGet();
        total.incrementAndGet();
        totalLatencyMicros.addAndGet(Math.max(0L, latencyMicros));
    }

    public long totalRequests() {
        return total.get();
    }

    public long countForRoute(String route) {
        AtomicLong counter = byRoute.get(route);
        return counter == null ? 0L : counter.get();
    }

    public long countForStatus(int status) {
        AtomicLong counter = byStatus.get(status);
        return counter == null ? 0L : counter.get();
    }

    /** Requests that returned a 2xx status. */
    public long successCount() {
        long count = 0;
        for (Map.Entry<Integer, AtomicLong> entry : byStatus.entrySet()) {
            if (entry.getKey() >= 200 && entry.getKey() < 300) {
                count += entry.getValue().get();
            }
        }
        return count;
    }

    /** Requests that returned 4xx or 5xx. */
    public long errorCount() {
        long count = 0;
        for (Map.Entry<Integer, AtomicLong> entry : byStatus.entrySet()) {
            if (entry.getKey() >= 400) {
                count += entry.getValue().get();
            }
        }
        return count;
    }

    /** Mean latency in microseconds, or zero before any request. */
    public double meanLatencyMicros() {
        long requests = total.get();
        return requests == 0 ? 0.0 : (double) totalLatencyMicros.get() / requests;
    }

    /** Route counters, ordered by route name. */
    public Map<String, Long> routeCounts() {
        Map<String, Long> snapshot = new TreeMap<String, Long>();
        for (Map.Entry<String, AtomicLong> entry : byRoute.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().get());
        }
        return snapshot;
    }

    public int trackedRoutes() {
        return byRoute.size();
    }

    public void reset() {
        byRoute.clear();
        byStatus.clear();
        total.set(0);
        totalLatencyMicros.set(0);
    }
}
