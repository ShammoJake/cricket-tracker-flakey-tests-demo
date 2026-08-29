package com.cricket.stats;

import com.cricket.core.model.Innings;
import com.cricket.core.scorecard.BattingLine;
import com.cricket.core.scorecard.BowlingLine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * The standings for a series, folded together from the innings played at each venue.
 *
 * <p>A tour runs several fixtures at once, so the innings are absorbed in parallel and
 * each standing is created by whichever worker reached that player first. The set is
 * synchronised, so every standing that goes in comes back out again.
 */
public final class SeriesBoard {

    /** One player's contribution across the series. */
    public static final class Standing {

        private final String playerId;
        private int runs;
        private int wickets;
        private int ballsFaced;

        public Standing(String playerId) {
            if (playerId == null || playerId.trim().isEmpty()) {
                throw new IllegalArgumentException("playerId must not be blank");
            }
            this.playerId = playerId;
        }

        public String getPlayerId() {
            return playerId;
        }

        public int getRuns() {
            return runs;
        }

        public int getWickets() {
            return wickets;
        }

        public int getBallsFaced() {
            return ballsFaced;
        }

        public synchronized void addBatting(int scored, int balls) {
            this.runs += scored;
            this.ballsFaced += balls;
        }

        public synchronized void addBowling(int taken) {
            this.wickets += taken;
        }

        @Override
        public String toString() {
            return playerId + " " + runs + " runs, " + wickets + " wickets";
        }
    }

    /** Most runs first. */
    private static final Comparator<Standing> BY_RUNS = new Comparator<Standing>() {
        @Override
        public int compare(Standing a, Standing b) {
            return Integer.compare(b.getRuns(), a.getRuns());
        }
    };

    /** Most wickets first. */
    private static final Comparator<Standing> BY_WICKETS = new Comparator<Standing>() {
        @Override
        public int compare(Standing a, Standing b) {
            return Integer.compare(b.getWickets(), a.getWickets());
        }
    };

    private final Set<Standing> standings =
            Collections.synchronizedSet(new HashSet<Standing>());

    /** The standing for this player, created on first mention. */
    public Standing standingFor(String playerId) {
        synchronized (standings) {
            for (Standing standing : standings) {
                if (standing.getPlayerId().equals(playerId)) {
                    return standing;
                }
            }
            Standing fresh = new Standing(playerId);
            standings.add(fresh);
            return fresh;
        }
    }

    public int size() {
        return standings.size();
    }

    public boolean isEmpty() {
        return standings.isEmpty();
    }

    /** Folds one innings into the standings. */
    public void absorb(Innings innings) {
        if (innings == null) {
            throw new IllegalArgumentException("innings must not be null");
        }
        for (BattingLine line : innings.getScoreCard().battingLines()) {
            standingFor(line.getPlayerId()).addBatting(line.getRuns(), line.getBallsFaced());
        }
        for (BowlingLine line : innings.getScoreCard().bowlingLines()) {
            standingFor(line.getPlayerId()).addBowling(line.getWickets());
        }
    }

    /** Folds one innings into the standings on its own thread. */
    private final class AbsorbTask implements Runnable {

        private final Innings innings;
        private final CountDownLatch start;
        private final CountDownLatch done;

        AbsorbTask(Innings innings, CountDownLatch start, CountDownLatch done) {
            this.innings = innings;
            this.start = start;
            this.done = done;
        }

        @Override
        public void run() {
            try {
                start.await();
                absorb(innings);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        }
    }

    /**
     * Folds every innings into the standings, one worker per venue, and returns once
     * they have all been counted.
     */
    public void absorbAll(List<Innings> played) {
        if (played == null) {
            throw new IllegalArgumentException("played must not be null");
        }
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(played.size());
        for (Innings innings : played) {
            Thread worker = new Thread(new AbsorbTask(innings, start, done));
            worker.setName("venue-" + innings.getId());
            worker.start();
        }
        start.countDown();
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Every standing on the board. */
    public List<Standing> all() {
        synchronized (standings) {
            return new ArrayList<Standing>(standings);
        }
    }

    private List<Standing> ranked(Comparator<Standing> order) {
        List<Standing> ranked = all();
        Collections.sort(ranked, order);
        return ranked;
    }

    /** The board as the run-scoring table. */
    public List<Standing> byRuns() {
        return ranked(BY_RUNS);
    }

    /** The board as the wicket-taking table. */
    public List<Standing> byWickets() {
        return ranked(BY_WICKETS);
    }

    /** The leading run scorer of the series, or null when nothing has been played. */
    public Standing leadingScorer() {
        List<Standing> ranked = byRuns();
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    /** The leading wicket taker of the series, or null when nothing has been played. */
    public Standing leadingWicketTaker() {
        List<Standing> ranked = byWickets();
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    /** Player ids in the order the board holds them. */
    public List<String> playerIds() {
        List<String> ids = new ArrayList<String>();
        for (Standing standing : all()) {
            ids.add(standing.getPlayerId());
        }
        return ids;
    }

    /** Players who have passed the given number of runs, in board order. */
    public List<String> scorersAbove(int runs) {
        List<String> ids = new ArrayList<String>();
        for (Standing standing : all()) {
            if (standing.getRuns() > runs) {
                ids.add(standing.getPlayerId());
            }
        }
        return ids;
    }

    public int aggregateRuns() {
        int total = 0;
        for (Standing standing : all()) {
            total += standing.getRuns();
        }
        return total;
    }

    public void clear() {
        standings.clear();
    }
}
