package com.cricket.live;

/** Notified once per delivery, after it has been applied to the innings. */
public interface BallListener {

    /** A name for the listener, used in diagnostics. */
    String name();

    /** Handles a delivery. Implementations must not throw. */
    void onBall(BallEvent event);
}
