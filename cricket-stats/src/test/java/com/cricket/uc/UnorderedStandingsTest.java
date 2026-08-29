package com.cricket.uc;

import com.cricket.core.Fixtures;
import com.cricket.core.engine.ScoringEngine;
import com.cricket.core.model.Ball;
import com.cricket.core.model.Dismissal;
import com.cricket.core.model.Innings;
import com.cricket.core.model.Team;
import com.cricket.core.model.WicketEvent;
import com.cricket.stats.SeriesBoard;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The series standings after a round of fixtures played at several venues.
 *
 * <p>The two openers finished the round level, and so did the two opening bowlers,
 * which is the situation the table has to present sensibly.
 */
public class UnorderedStandingsTest {

    private static final int VENUES = 6;

    /** Venues 0 and 1 finish level at the top; the rest tail away. */
    private static int runsAt(int venue) {
        return venue <= 1 ? 60 : 60 - 4 * venue;
    }

    private SeriesBoard board;

    @Before
    public void setUp() {
        board = new SeriesBoard();
        board.absorbAll(round());
    }

    private List<Innings> round() {
        List<Innings> played = new ArrayList<Innings>();
        for (int venue = 0; venue < VENUES; venue++) {
            played.add(inningsAt(venue));
        }
        return played;
    }

    private Innings inningsAt(int venue) {
        Team batting = Fixtures.india();
        Team bowling = Fixtures.australia();
        String striker = "IND" + (venue + 1);
        String nonStriker = "IND" + (venue + 2);

        Innings innings = new Innings("venue-" + venue, 1, batting, bowling);
        innings.setOversLimit(20);
        innings.openWith(striker, nonStriker);

        ScoringEngine engine = new ScoringEngine();
        int runs = runsAt(venue);
        for (int i = 0; i < runs; i++) {
            engine.apply(innings, Ball.builder()
                    .over(i / 6).ballInOver(i % 6 + 1)
                    .bowler("AUS8").striker(striker).nonStriker(nonStriker)
                    .runsOffBat(1).build());
        }

        // The two opening bowlers each took a wicket in the round, one apiece.
        if (venue <= 1) {
            engine.apply(innings, Ball.builder()
                    .over(runs / 6).ballInOver(runs % 6 + 1)
                    .bowler(venue == 0 ? "AUS8" : "AUS9")
                    .striker(striker).nonStriker(nonStriker)
                    .wicket(WicketEvent.of(Dismissal.BOWLED, striker))
                    .build());
        }
        return innings;
    }

    /** Every venue is counted, so the aggregate is the whole round's runs. */
    @Test
    public void everyVenueIsCountedIntoTheBoard() {
        int expected = 0;
        for (int venue = 0; venue < VENUES; venue++) {
            expected += runsAt(venue);
        }
        assertEquals(expected, board.aggregateRuns());
    }

    /** The opener from the first venue heads the run-scoring table. */
    @Test
    public void theOpeningVenueHeadsTheTable() {
        SeriesBoard.Standing leader = board.leadingScorer();
        assertNotNull(leader);
        assertEquals("IND1", leader.getPlayerId());
    }

    /** The opener from the second venue is the runner up. */
    @Test
    public void theRunnerUpIsSecondOnTheTable() {
        List<SeriesBoard.Standing> ranked = board.byRuns();
        assertEquals("IND2", ranked.get(1).getPlayerId());
    }

    /** The bowler who opened the attack heads the wicket-taking table. */
    @Test
    public void theOpeningBowlerHeadsTheWicketTable() {
        SeriesBoard.Standing leader = board.leadingWicketTaker();
        assertNotNull(leader);
        assertEquals("AUS8", leader.getPlayerId());
    }

    /** The table lists the openers in the order they bat. */
    @Test
    public void theBoardPresentsTheOpenersInSquadOrder() {
        List<String> ids = board.playerIds();
        assertTrue("IND1 should be listed ahead of IND2, got " + ids,
                ids.indexOf("IND1") < ids.indexOf("IND2"));
    }

    @Test
    public void theTableIsSortedByRunsDescending() {
        List<SeriesBoard.Standing> ranked = board.byRuns();
        for (int i = 1; i < ranked.size(); i++) {
            assertTrue(ranked.get(i - 1).getRuns() >= ranked.get(i).getRuns());
        }
    }

    @Test
    public void everyOpenerHasAStandingOnTheBoard() {
        for (int venue = 0; venue < VENUES; venue++) {
            assertTrue(board.playerIds().contains("IND" + (venue + 1)));
        }
    }

    @Test
    public void anEmptyBoardHasNoLeader() {
        assertNull(new SeriesBoard().leadingScorer());
    }
}
