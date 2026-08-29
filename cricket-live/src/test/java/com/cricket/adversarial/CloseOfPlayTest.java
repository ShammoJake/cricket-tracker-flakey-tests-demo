package com.cricket.adversarial;

import com.cricket.core.engine.ScoringRules;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The figures put up on the board at the close of play.
 */
public class CloseOfPlayTest {

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
    public void theClosingTotalIsOnTheBoard() {
        day.bowlTheInnings();
        day.tea();

        assertEquals(MatchDay.DELIVERIES * 4, day.scoreboard().getRuns());
    }

    @Test
    public void theBoardShowsEveryDeliveryOfTheInnings() {
        day.bowlTheInnings();
        day.tea();

        assertEquals(MatchDay.DELIVERIES, day.viewer().count());
    }

    @Test
    public void theLastBallOfTheDayIsOnTheFeed() {
        day.bowlTheInningsAndTakeTheLastWicket();
        day.tea();

        assertTrue(day.viewer().last().contains("OUT"));
    }

    /** The board is only put up once the scorers have signed off. */
    @Test
    public void theBoardIsFinalOnceTheScorersHaveSignedOff() {
        day.bowlTheInnings();
        day.waitForTheScorers();

        assertEquals(MatchDay.DELIVERIES * 4, day.scoreboard().getRuns());
    }
}
