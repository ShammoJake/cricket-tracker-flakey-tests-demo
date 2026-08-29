package com.cricket.od;

import com.cricket.core.Fixtures;
import com.cricket.core.engine.ScoringEngine;
import com.cricket.core.engine.ScoringRules;
import com.cricket.core.model.ExtraType;
import com.cricket.core.model.Innings;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The eight-ball over and the two-run wide, as trialled in the domestic competition.
 *
 * <p>The trial conditions are loaded once for the class rather than per test, since
 * every case here is played under them.
 */
public class ExperimentalFormatTest {

    private static final int TRIAL_BALLS_PER_OVER = 8;
    private static final int TRIAL_WIDE_PENALTY = 2;

    @BeforeClass
    public static void loadTrialConditions() {
        ScoringRules.setBallsPerOver(TRIAL_BALLS_PER_OVER);
        ScoringRules.setWidePenalty(TRIAL_WIDE_PENALTY);
    }

    @Test
    public void theTrialOverRunsToEightDeliveries() {
        assertEquals(TRIAL_BALLS_PER_OVER, ScoringRules.ballsPerOver());
    }

    @Test
    public void eightLegalDeliveriesCompleteTheOver() {
        Innings innings = Fixtures.openedInnings();
        ScoringEngine engine = new ScoringEngine();
        for (int i = 1; i <= TRIAL_BALLS_PER_OVER; i++) {
            engine.apply(innings, Fixtures.delivery(innings, 0, i).runsOffBat(1).build());
        }
        assertEquals(1, innings.getScoreCard().completedOvers());
    }

    @Test
    public void aWideCostsTwoUnderTheTrialConditions() {
        Innings innings = Fixtures.openedInnings();
        ScoringEngine engine = new ScoringEngine();
        engine.apply(innings, Fixtures.delivery(innings, 0, 1)
                .extra(ExtraType.WIDE, 0).build());
        assertEquals(TRIAL_WIDE_PENALTY, innings.getScoreCard().getWides());
    }

    @Test
    public void theTrialConditionsAreNotTheShippedOnes() {
        assertTrue(ScoringRules.describe().contains("ballsPerOver=" + TRIAL_BALLS_PER_OVER));
    }

    @Test
    public void anOverLengthBeyondTwelveIsRejected() {
        try {
            ScoringRules.setBallsPerOver(13);
            org.junit.Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("balls per over must be between 1 and 12", expected.getMessage());
        }
    }
}
