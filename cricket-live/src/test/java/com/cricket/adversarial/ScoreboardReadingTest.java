package com.cricket.adversarial;

import com.cricket.core.engine.ScoringRules;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Reading the scoreboard after an innings, as the presenters do at the interval.
 */
public class ScoreboardReadingTest {

    private MatchDay day;

    @Before
    public void setUp() {
        ScoringRules.reloadDefaults();
        day = MatchDay.open();
    }

    @After
    public void tearDown() {
        day.close();
        ScoringRules.reloadDefaults();
    }

    @Test
    public void theScoreboardShowsTheWholeInnings() {
        day.bowlTheInnings();
        day.drinks();

        assertEquals(MatchDay.DELIVERIES * 4, day.scoreboard().getRuns());
    }

    @Test
    public void theScoreboardCountsEveryDeliveryBowled() {
        day.bowlTheInnings();
        day.drinks();

        assertEquals(MatchDay.DELIVERIES, day.scoreboard().getLegalBalls());
    }

    @Test
    public void everyBoundaryIsOnTheScoreboard() {
        day.bowlTheInnings();
        day.drinks();

        assertEquals(MatchDay.DELIVERIES, day.scoreboard().getBoundaries());
    }

    @Test
    public void theViewerSeesTheWholeInnings() {
        day.bowlTheInnings();
        day.drinks();

        assertEquals(MatchDay.DELIVERIES, day.viewer().count());
    }

    @Test
    public void theTranscriptRunsToTheEndOfTheInnings() {
        day.bowlTheInnings();
        day.drinks();

        assertEquals(MatchDay.DELIVERIES, day.transcript().size());
    }

    @Test
    public void theFallOfTheLastWicketIsAnnounced() {
        day.bowlTheInningsAndTakeTheLastWicket();
        day.drinks();

        assertTrue(day.viewer().last().contains("OUT"));
    }

    /** The presenters wait for the scorers before reading the total out. */
    @Test
    public void theScoreboardIsCompleteOnceTheScorersHaveCaughtUp() {
        day.bowlTheInnings();
        day.waitForTheScorers();

        assertEquals(MatchDay.DELIVERIES * 4, day.scoreboard().getRuns());
    }
}
