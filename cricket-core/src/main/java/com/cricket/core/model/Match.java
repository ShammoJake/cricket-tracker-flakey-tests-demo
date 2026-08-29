package com.cricket.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A match between two teams.
 *
 * <p>Mutable: state transitions and innings are added as play progresses. Instances
 * are shared across the ingest and query paths.
 */
public final class Match {

    private final String id;
    private final Team teamA;
    private final Team teamB;
    private final MatchFormat format;
    private final String venue;

    private final List<Innings> innings = new ArrayList<Innings>();
    private MatchState state = MatchState.SCHEDULED;
    private String tossWinnerId;
    private boolean tossElectedToBat;

    /** Written by the ingest path and read by the export path. */
    private boolean dirty;

    public Match(String id, Team teamA, Team teamB, MatchFormat format, String venue) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("match id must not be blank");
        }
        if (teamA == null || teamB == null) {
            throw new IllegalArgumentException("match requires two teams");
        }
        if (teamA.getId().equals(teamB.getId())) {
            throw new IllegalArgumentException("a team cannot play itself");
        }
        if (format == null) {
            throw new IllegalArgumentException("match format must not be null");
        }
        this.id = id;
        this.teamA = teamA;
        this.teamB = teamB;
        this.format = format;
        this.venue = venue == null ? "unknown" : venue;
    }

    public String getId() {
        return id;
    }

    public Team getTeamA() {
        return teamA;
    }

    public Team getTeamB() {
        return teamB;
    }

    public MatchFormat getFormat() {
        return format;
    }

    public String getVenue() {
        return venue;
    }

    public MatchState getState() {
        return state;
    }

    public String getTossWinnerId() {
        return tossWinnerId;
    }

    public boolean isTossElectedToBat() {
        return tossElectedToBat;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public List<Innings> getInnings() {
        return Collections.unmodifiableList(innings);
    }

    public Innings currentInnings() {
        return innings.isEmpty() ? null : innings.get(innings.size() - 1);
    }

    public Innings inningsByNumber(int number) {
        for (Innings i : innings) {
            if (i.getNumber() == number) {
                return i;
            }
        }
        return null;
    }

    public Innings startInnings(Team batting, Team bowling) {
        int number = innings.size() + 1;
        Innings next = new Innings(id + "-inn" + number, number, batting, bowling);
        if (format.isLimitedOvers()) {
            next.setOversLimit(format.getOversPerInnings());
        }
        innings.add(next);
        return next;
    }

    public void recordToss(String winnerTeamId, boolean electedToBat) {
        if (!teamA.getId().equals(winnerTeamId) && !teamB.getId().equals(winnerTeamId)) {
            throw new IllegalArgumentException("toss winner is not playing in this match: " + winnerTeamId);
        }
        this.tossWinnerId = winnerTeamId;
        this.tossElectedToBat = electedToBat;
        transitionTo(MatchState.TOSS_DONE);
    }

    public void transitionTo(MatchState next) {
        if (!state.canTransitionTo(next)) {
            throw new IllegalStateException("illegal transition " + state + " -> " + next);
        }
        this.state = next;
    }

    public Team opponentOf(String teamId) {
        if (teamA.getId().equals(teamId)) {
            return teamB;
        }
        if (teamB.getId().equals(teamId)) {
            return teamA;
        }
        throw new IllegalArgumentException("team is not playing in this match: " + teamId);
    }

    public boolean involves(String teamId) {
        return teamA.getId().equals(teamId) || teamB.getId().equals(teamId);
    }

    /** Total runs across every innings so far. */
    public int aggregateRuns() {
        int total = 0;
        for (Innings i : innings) {
            total += i.getScoreCard().getTotalRuns();
        }
        return total;
    }

    public String describe() {
        return teamA.getName() + " v " + teamB.getName() + " at " + venue + " (" + format + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Match)) {
            return false;
        }
        return id.equals(((Match) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id + ": " + describe() + " [" + state + "]";
    }
}
