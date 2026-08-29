package com.cricket.core.engine;

import com.cricket.core.model.Innings;
import com.cricket.core.model.MatchFormat;
import com.cricket.core.model.Over;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks who is allowed to bowl the next over.
 *
 * <p>Enforces the two standing constraints of limited-overs cricket: a bowler may not
 * bowl consecutive overs, and may not exceed the per-bowler cap for the format.
 */
public final class OverManager {

    private final MatchFormat format;
    private final Map<String, Integer> oversPerBowler = new HashMap<String, Integer>();
    private String lastOverBowlerId;

    public OverManager(MatchFormat format) {
        if (format == null) {
            throw new IllegalArgumentException("format must not be null");
        }
        this.format = format;
    }

    public MatchFormat getFormat() {
        return format;
    }

    public String getLastOverBowlerId() {
        return lastOverBowlerId;
    }

    public int oversBowledBy(String bowlerId) {
        Integer count = oversPerBowler.get(bowlerId);
        return count == null ? 0 : count;
    }

    /** Per-bowler cap for the format, or -1 when uncapped. */
    public int maxOversPerBowler() {
        return format.getMaxOversPerBowler();
    }

    public int oversRemainingFor(String bowlerId) {
        int cap = maxOversPerBowler();
        if (cap < 0) {
            return -1;
        }
        return Math.max(0, cap - oversBowledBy(bowlerId));
    }

    public boolean hasReachedCap(String bowlerId) {
        int cap = maxOversPerBowler();
        return cap >= 0 && oversBowledBy(bowlerId) >= cap;
    }

    public boolean bowledLastOver(String bowlerId) {
        return lastOverBowlerId != null && lastOverBowlerId.equals(bowlerId);
    }

    /** True when this bowler may start the next over. */
    public boolean canBowlNextOver(String bowlerId) {
        if (bowlerId == null || bowlerId.trim().isEmpty()) {
            return false;
        }
        return !bowledLastOver(bowlerId) && !hasReachedCap(bowlerId);
    }

    /** Explains why a bowler may not bowl, or null when they may. */
    public String rejectionReason(String bowlerId) {
        if (bowlerId == null || bowlerId.trim().isEmpty()) {
            return "bowler must be named";
        }
        if (bowledLastOver(bowlerId)) {
            return bowlerId + " bowled the previous over";
        }
        if (hasReachedCap(bowlerId)) {
            return bowlerId + " has reached the limit of " + maxOversPerBowler() + " overs";
        }
        return null;
    }

    /** Records that a bowler completed an over. */
    public void recordCompletedOver(String bowlerId) {
        String reason = rejectionReason(bowlerId);
        if (reason != null) {
            throw new IllegalStateException(reason);
        }
        Integer count = oversPerBowler.get(bowlerId);
        oversPerBowler.put(bowlerId, count == null ? 1 : count + 1);
        lastOverBowlerId = bowlerId;
    }

    /** Rebuilds the tally from an innings, e.g. after a restart. */
    public void replay(Innings innings) {
        oversPerBowler.clear();
        lastOverBowlerId = null;
        for (Over over : innings.getOvers()) {
            if (!over.isComplete()) {
                continue;
            }
            String bowler = over.getBowlerId();
            Integer count = oversPerBowler.get(bowler);
            oversPerBowler.put(bowler, count == null ? 1 : count + 1);
            lastOverBowlerId = bowler;
        }
    }

    public void reset() {
        oversPerBowler.clear();
        lastOverBowlerId = null;
    }

    /** Total overs recorded across every bowler. */
    public int totalOvers() {
        int total = 0;
        for (Integer count : oversPerBowler.values()) {
            total += count;
        }
        return total;
    }
}
