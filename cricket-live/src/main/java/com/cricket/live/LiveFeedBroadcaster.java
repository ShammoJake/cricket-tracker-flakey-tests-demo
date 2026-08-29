package com.cricket.live;

import com.cricket.core.engine.CommentaryGenerator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pushes a commentary line for every delivery to whoever is subscribed to the feed.
 */
public final class LiveFeedBroadcaster implements BallListener {

    /** Receives commentary lines. */
    public interface Subscriber {
        String id();

        void deliver(String line);
    }

    /** A subscriber that keeps what it was sent, for querying and for tests. */
    public static final class RecordingSubscriber implements Subscriber {
        private final String id;
        private final List<String> received = new ArrayList<String>();

        public RecordingSubscriber(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void deliver(String line) {
            received.add(line);
        }

        public List<String> getReceived() {
            return new ArrayList<String>(received);
        }

        public int count() {
            return received.size();
        }

        public String last() {
            return received.isEmpty() ? null : received.get(received.size() - 1);
        }

        public void clear() {
            received.clear();
        }
    }

    private final CommentaryGenerator commentary;
    private final Set<Subscriber> subscribers = new HashSet<Subscriber>();
    private final List<String> transcript = new ArrayList<String>();
    private int broadcasts;

    public LiveFeedBroadcaster() {
        this(new CommentaryGenerator());
    }

    public LiveFeedBroadcaster(CommentaryGenerator commentary) {
        if (commentary == null) {
            throw new IllegalArgumentException("commentary generator must not be null");
        }
        this.commentary = commentary;
    }

    @Override
    public String name() {
        return "live-feed";
    }

    @Override
    public void onBall(BallEvent event) {
        String line = commentary.describe(event.getBall(),
                event.getBowlerId(), event.getStrikerId());
        transcript.add(line);
        for (Subscriber subscriber : subscribers) {
            subscriber.deliver(line);
        }
        broadcasts++;
    }

    public void subscribe(Subscriber subscriber) {
        if (subscriber == null) {
            throw new IllegalArgumentException("subscriber must not be null");
        }
        subscribers.add(subscriber);
    }

    public boolean unsubscribe(Subscriber subscriber) {
        return subscribers.remove(subscriber);
    }

    public int subscriberCount() {
        return subscribers.size();
    }

    /** Every line broadcast so far, in order. */
    public List<String> getTranscript() {
        return new ArrayList<String>(transcript);
    }

    public String lastLine() {
        return transcript.isEmpty() ? null : transcript.get(transcript.size() - 1);
    }

    public int broadcastCount() {
        return broadcasts;
    }

    /** The most recent lines, newest last, capped at {@code limit}. */
    public List<String> recent(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }
        int from = Math.max(0, transcript.size() - limit);
        return new ArrayList<String>(transcript.subList(from, transcript.size()));
    }

    public void reset() {
        transcript.clear();
        broadcasts = 0;
    }
}
