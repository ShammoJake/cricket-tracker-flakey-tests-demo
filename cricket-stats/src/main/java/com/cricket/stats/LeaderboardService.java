package com.cricket.stats;

import com.cricket.core.model.Innings;
import com.cricket.core.scorecard.BattingLine;
import com.cricket.core.scorecard.BowlingLine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the run-scorer, wicket-taker and strike-rate leaderboards.
 *
 * <p>Tallies are keyed by player id and ranked on demand.
 */
public final class LeaderboardService {

    /** Highest runs first. */
    private static final Comparator<PlayerTally> BY_RUNS = new Comparator<PlayerTally>() {
        @Override
        public int compare(PlayerTally a, PlayerTally b) {
            return Integer.compare(b.getRuns(), a.getRuns());
        }
    };

    /** Most wickets first. */
    private static final Comparator<PlayerTally> BY_WICKETS = new Comparator<PlayerTally>() {
        @Override
        public int compare(PlayerTally a, PlayerTally b) {
            return Integer.compare(b.getWickets(), a.getWickets());
        }
    };

    /** Best strike rate first. */
    private static final Comparator<PlayerTally> BY_STRIKE_RATE = new Comparator<PlayerTally>() {
        @Override
        public int compare(PlayerTally a, PlayerTally b) {
            return Double.compare(b.strikeRate(), a.strikeRate());
        }
    };

    /** Most economical first. */
    private static final Comparator<PlayerTally> BY_ECONOMY = new Comparator<PlayerTally>() {
        @Override
        public int compare(PlayerTally a, PlayerTally b) {
            return Double.compare(a.economy(), b.economy());
        }
    };

    private final Map<String, PlayerTally> tallies = new HashMap<String, PlayerTally>();

    public PlayerTally tallyFor(String playerId) {
        PlayerTally tally = tallies.get(playerId);
        if (tally == null) {
            tally = new PlayerTally(playerId);
            tallies.put(playerId, tally);
        }
        return tally;
    }

    public boolean tracks(String playerId) {
        return tallies.containsKey(playerId);
    }

    public int size() {
        return tallies.size();
    }

    /** Folds an innings' scorecard into the running tallies. */
    public void absorb(Innings innings) {
        if (innings == null) {
            throw new IllegalArgumentException("innings must not be null");
        }
        for (BattingLine line : innings.getScoreCard().battingLines()) {
            PlayerTally tally = tallyFor(line.getPlayerId());
            tally.addBatting(line.getRuns(), line.getBallsFaced(), line.getFours(), line.getSixes());
            if (line.isOut()) {
                tally.addDismissal();
            }
        }
        for (BowlingLine line : innings.getScoreCard().bowlingLines()) {
            tallyFor(line.getPlayerId()).addBowling(
                    line.getWickets(), line.getRunsConceded(), line.getLegalBalls());
        }
    }

    private List<PlayerTally> ranked(Comparator<PlayerTally> order, int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }
        List<PlayerTally> all = new ArrayList<PlayerTally>(tallies.values());
        Collections.sort(all, order);
        return all.subList(0, Math.min(limit, all.size()));
    }

    public List<PlayerTally> topRunScorers(int limit) {
        return ranked(BY_RUNS, limit);
    }

    public List<PlayerTally> topWicketTakers(int limit) {
        return ranked(BY_WICKETS, limit);
    }

    public List<PlayerTally> bestStrikeRates(int limit) {
        return ranked(BY_STRIKE_RATE, limit);
    }

    public List<PlayerTally> mostEconomical(int limit) {
        List<PlayerTally> bowlers = new ArrayList<PlayerTally>();
        for (PlayerTally tally : tallies.values()) {
            if (tally.getLegalBallsBowled() > 0) {
                bowlers.add(tally);
            }
        }
        Collections.sort(bowlers, BY_ECONOMY);
        return bowlers.subList(0, Math.min(limit, bowlers.size()));
    }

    /** The single highest run scorer, or null when nothing is tracked. */
    public PlayerTally leadingRunScorer() {
        List<PlayerTally> top = topRunScorers(1);
        return top.isEmpty() ? null : top.get(0);
    }

    /** The single leading wicket taker, or null when nothing is tracked. */
    public PlayerTally leadingWicketTaker() {
        List<PlayerTally> top = topWicketTakers(1);
        return top.isEmpty() ? null : top.get(0);
    }

    /** Total runs across every tracked player. */
    public int aggregateRuns() {
        int total = 0;
        for (PlayerTally tally : tallies.values()) {
            total += tally.getRuns();
        }
        return total;
    }

    /** Players who have passed the given score. */
    public List<String> playersAbove(int runs) {
        List<String> result = new ArrayList<String>();
        for (PlayerTally tally : tallies.values()) {
            if (tally.getRuns() > runs) {
                result.add(tally.getPlayerId());
            }
        }
        return result;
    }

    public void reset() {
        tallies.clear();
    }
}
