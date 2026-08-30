package com.cricket.live;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The pool that runs ingest work.
 *
 * <p>A {@link ThreadPoolExecutor} subclass rather than a bare {@code ExecutorService}
 * so the before/after hooks and the counters they maintain live in this codebase,
 * where they can be inspected and instrumented.
 */
public final class WorkerPool extends ThreadPoolExecutor {


    /** Deliberately not lambdas: bytecode tooling reads named classes more reliably. */
    private static volatile int numExecutions;

    public static void resetExecutions() { numExecutions = 0; }
    public static int getExecutedStatus() { return numExecutions; }
    private static final class PoolThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger();

        PoolThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }

    private final AtomicLong started = new AtomicLong();
    private final AtomicLong finished = new AtomicLong();
    private final AtomicInteger peakActive = new AtomicInteger();

    /** Set once the first task has run; used to observe pool start-up. */
    private volatile boolean hasExecuted;

    public WorkerPool(int threads) {
        this(threads, "ingest");
    }

    public WorkerPool(int threads, String namePrefix) {
        super(threads, threads, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(4096),
                new PoolThreadFactory(namePrefix));
        if (threads < 1) {
            throw new IllegalArgumentException("pool needs at least one thread");
        }
        allowCoreThreadTimeOut(false);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        started.incrementAndGet();
        int active = getActiveCount();
        if (active > peakActive.get()) {
            peakActive.set(active);
        }
        hasExecuted = true;
        super.beforeExecute(t, r);
    numExecutions++;
    }

    @Override
    protected void afterExecute(Runnable r, Throwable thrown) {
        super.afterExecute(r, thrown);
        finished.incrementAndGet();
    }

    /** Tasks that have begun running. */
    public long startedCount() {
        return started.get();
    }

    /** Tasks that have finished running. */
    public long finishedCount() {
        return finished.get();
    }

    /** Highest number of simultaneously active threads observed. */
    public int peakActiveThreads() {
        return peakActive.get();
    }

    /** True once at least one task has started. */
    public boolean hasExecuted() {
        return hasExecuted;
    }

    /** Shuts the pool down and waits for it to drain. */
    public boolean shutdownAndAwait(long timeoutMillis) {
        shutdown();
        try {
            return awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
