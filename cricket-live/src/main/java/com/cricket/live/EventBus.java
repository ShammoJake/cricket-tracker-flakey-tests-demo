package com.cricket.live;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fans a {@link BallEvent} out to the registered listeners.
 *
 * <p>Listeners are held in a copy-on-write list so publishing never blocks on
 * registration. A listener that throws is recorded and skipped rather than being
 * allowed to abort the fan-out.
 */
public final class EventBus {

    private final List<BallListener> listeners = new CopyOnWriteArrayList<BallListener>();
    private final List<String> failures = new CopyOnWriteArrayList<String>();

    public void subscribe(BallListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        listeners.add(listener);
    }

    public boolean unsubscribe(BallListener listener) {
        return listeners.remove(listener);
    }

    public int listenerCount() {
        return listeners.size();
    }

    public List<BallListener> getListeners() {
        return new ArrayList<BallListener>(listeners);
    }

    /** Delivers the event to every listener in registration order. */
    public void publish(BallEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        for (BallListener listener : listeners) {
            try {
                listener.onBall(event);
            } catch (RuntimeException e) {
                failures.add(listener.name() + ": " + e);
            }
        }
    }

    /** Listener failures recorded since the last clear. */
    public List<String> getFailures() {
        return new ArrayList<String>(failures);
    }

    public int failureCount() {
        return failures.size();
    }

    public void clearFailures() {
        failures.clear();
    }

    public void clear() {
        listeners.clear();
        failures.clear();
    }
}
