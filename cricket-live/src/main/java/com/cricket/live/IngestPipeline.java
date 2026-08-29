package com.cricket.live;

import com.cricket.core.engine.ScoringEngine;
import com.cricket.core.engine.ScoringResult;
import com.cricket.core.model.Ball;
import com.cricket.core.model.Innings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Accepts deliveries and applies them to their innings on a worker thread.
 *
 * <p>Submission is asynchronous: {@link #submit} returns as soon as the delivery is
 * queued. Callers that need to observe the result must synchronise, either with
 * {@link #awaitIdle(long)} or by waiting on their own condition. The pool defaults
 * to a single thread so deliveries for an innings are applied in submission order.
 */
public final class IngestPipeline {

    /** Named rather than a lambda so bytecode tooling sees a real method to work with. */
    private final class IngestTask implements Runnable {
        private final String matchId;
        private final Innings innings;
        private final Ball ball;
        private final long sequence;

        IngestTask(String matchId, Innings innings, Ball ball, long sequence) {
            this.matchId = matchId;
            this.innings = innings;
            this.ball = ball;
            this.sequence = sequence;
        }

        @Override
        public void run() {
            try {
                ScoringResult result = engine.apply(innings, ball);
                bus.publish(new BallEvent(matchId, innings, ball, result, sequence));
                accepted.incrementAndGet();
            } catch (RuntimeException e) {
                rejections.add(ball.address() + ": " + e.getMessage());
            } finally {
                markCompleted();
            }
        }
    }

    private final WorkerPool pool;
    private final EventBus bus;
    private final ScoringEngine engine;

    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong accepted = new AtomicLong();
    private final List<String> rejections = new CopyOnWriteArrayList<String>();

    private final Object idleMonitor = new Object();
    private long submittedCount;
    private long completedCount;

    public IngestPipeline() {
        this(1, new EventBus(), new ScoringEngine());
    }

    public IngestPipeline(int threads) {
        this(threads, new EventBus(), new ScoringEngine());
    }

    public IngestPipeline(int threads, EventBus bus, ScoringEngine engine) {
        if (bus == null) {
            throw new IllegalArgumentException("bus must not be null");
        }
        if (engine == null) {
            throw new IllegalArgumentException("engine must not be null");
        }
        this.pool = new WorkerPool(threads);
        this.bus = bus;
        this.engine = engine;
    }

    public EventBus getBus() {
        return bus;
    }

    public WorkerPool getPool() {
        return pool;
    }

    public void subscribe(BallListener listener) {
        bus.subscribe(listener);
    }

    /** Queues a delivery for application. Returns its sequence number. */
    public long submit(String matchId, Innings innings, Ball ball) {
        if (ball == null) {
            throw new IllegalArgumentException("ball must not be null");
        }
        long seq = sequence.incrementAndGet();
        synchronized (idleMonitor) {
            submittedCount++;
        }
        try {
            pool.execute(new IngestTask(matchId, innings, ball, seq));
        } catch (RejectedExecutionException e) {
            markCompleted();
            throw e;
        }
        return seq;
    }

    /** Queues every delivery in order. */
    public void submitAll(String matchId, Innings innings, List<Ball> balls) {
        if (balls == null) {
            throw new IllegalArgumentException("balls must not be null");
        }
        for (Ball ball : balls) {
            submit(matchId, innings, ball);
        }
    }

    private void markCompleted() {
        synchronized (idleMonitor) {
            completedCount++;
            idleMonitor.notifyAll();
        }
    }

    /**
     * Blocks until every submitted delivery has been applied, or the timeout expires.
     *
     * @return true when the pipeline drained, false on timeout
     */
    public boolean awaitIdle(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        synchronized (idleMonitor) {
            while (completedCount < submittedCount) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    idleMonitor.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    /** Deliveries queued so far. */
    public long submitted() {
        synchronized (idleMonitor) {
            return submittedCount;
        }
    }

    /** Deliveries whose processing has finished, successfully or not. */
    public long completed() {
        synchronized (idleMonitor) {
            return completedCount;
        }
    }

    /** Deliveries applied without error. */
    public long acceptedCount() {
        return accepted.get();
    }

    /** Deliveries the scoring engine refused. */
    public List<String> getRejections() {
        return new ArrayList<String>(rejections);
    }

    public int rejectionCount() {
        return rejections.size();
    }

    public boolean isIdle() {
        synchronized (idleMonitor) {
            return completedCount >= submittedCount;
        }
    }

    /** Drains and stops the pool. */
    public boolean shutdown(long timeoutMillis) {
        return pool.shutdownAndAwait(timeoutMillis);
    }
}
