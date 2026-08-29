package com.cricket.adversarial;

import com.cricket.core.engine.ScoringRules;
import com.cricket.live.MilestoneDetector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The report the commentators give at the interval, once the innings has been bowled.
 */
public class IntervalReportTest {

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
    public void theOpeningStandCarriedTheInnings() {
        day.bowlTheInnings();
        day.drinks();

        assertEquals(MatchDay.DELIVERIES * 4,
                day.partnerships().currentStand(MatchDay.MATCH).getRuns());
    }

    @Test
    public void theStandLastedTheWholeInnings() {
        day.bowlTheInnings();
        day.drinks();

        assertEquals(MatchDay.DELIVERIES,
                day.partnerships().currentStand(MatchDay.MATCH).getBalls());
    }

    @Test
    public void everyBattingMilestoneWasPassed() {
        day.bowlTheInnings();
        day.drinks();

        assertEquals(MilestoneDetector.BATTING_MILESTONES.length, day.milestonesReached());
    }

    @Test
    public void theInningsRanToItsFullLength() {
        day.bowlTheInnings();
        day.drinks();

        assertEquals(MatchDay.OVERS, day.scoreboard().getLegalBalls() / 6);
    }

    @Test
    public void theRunningTotalReachedTheClosingScore() {
        day.bowlTheInnings();
        day.drinks();

        assertEquals(MatchDay.DELIVERIES, day.scoreboard().getLastSequence());
    }

    /** The commentators wait for the scorers before giving the closing figures. */
    @Test
    public void theClosingFiguresAreFinalOnceTheScorersHaveCaughtUp() {
        day.bowlTheInnings();
        day.waitForTheScorers();

        assertEquals(MatchDay.DELIVERIES, day.scoreboard().getLastSequence());
    }
}
