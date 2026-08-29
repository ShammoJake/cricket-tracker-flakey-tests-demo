package com.cricket.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A time-stamped log of what happened during a match: the toss, the start of each
 * session, drinks, the fall of a wicket, the close of play.
 *
 * <p>Entries are held against the wall-clock time they were noted at, which is what the
 * broadcast overlay and the match referee's report are both driven from.
 */
public final class MatchTimeline {

    private final Map<Long, String> entries = new LinkedHashMap<Long, String>();
    private final long openedAt;

    public MatchTimeline() {
        this.openedAt = System.currentTimeMillis();
    }

    /** Notes an event against the moment it happened. */
    public void note(String event) {
        if (event == null || event.trim().isEmpty()) {
            throw new IllegalArgumentException("event must not be blank");
        }
        entries.put(System.currentTimeMillis(), event);
    }

    /** Notes an event against a moment the caller supplies. */
    public void noteAt(long timestampMillis, String event) {
        if (event == null || event.trim().isEmpty()) {
            throw new IllegalArgumentException("event must not be blank");
        }
        entries.put(timestampMillis, event);
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** The event noted at this moment, or null when nothing was. */
    public String at(long timestampMillis) {
        return entries.get(timestampMillis);
    }

    /** Every event, oldest first. */
    public List<String> events() {
        return new ArrayList<String>(entries.values());
    }

    /** The moments events were noted at, in order. */
    public List<Long> moments() {
        List<Long> moments = new ArrayList<Long>(entries.keySet());
        Collections.sort(moments);
        return moments;
    }

    public String first() {
        List<String> all = events();
        return all.isEmpty() ? null : all.get(0);
    }

    public String last() {
        List<String> all = events();
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    /** Milliseconds from the first note to the last. */
    public long spanMillis() {
        List<Long> moments = moments();
        if (moments.size() < 2) {
            return 0L;
        }
        return moments.get(moments.size() - 1) - moments.get(0);
    }

    /** Milliseconds since the timeline was opened. */
    public long elapsedMillis() {
        return System.currentTimeMillis() - openedAt;
    }

    public long getOpenedAt() {
        return openedAt;
    }

    /** Events noted within the given window of the timeline opening. */
    public List<String> within(long millis) {
        List<String> result = new ArrayList<String>();
        for (Map.Entry<Long, String> entry : entries.entrySet()) {
            if (entry.getKey() - openedAt <= millis) {
                result.add(entry.getValue());
            }
        }
        return result;
    }

    public void clear() {
        entries.clear();
    }
}
