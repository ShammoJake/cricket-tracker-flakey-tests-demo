package com.cricket.od;

import com.cricket.core.Fixtures;
import com.cricket.core.engine.ScoringEngine;
import com.cricket.core.engine.ScoringRules;
import com.cricket.core.model.Ball;
import com.cricket.core.model.ExtraType;
import com.cricket.core.model.Innings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The playing conditions the scoring engine applies to an ordinary international.
 */
public class DeliveryRulesTest {

    /**
     * A standard fixture is played under the shipped conditions.
     */
    @Test
    public void theShippedConditionsAreInEffect() {
        assertTrue("playing conditions were not the shipped ones: " + ScoringRules.describe(),
                ScoringRules.isDefault());
    }

    /**
     * Six legal deliveries complete an over, so a full over of singles leaves the
     * scorecard reading one over bowled.
     */
    @Test
    public void anOverIsSixLegalDeliveries() {
        Innings innings = Fixtures.openedInnings();
        ScoringEngine engine = new ScoringEngine();
        for (int i = 1; i <= 6; i++) {
            engine.apply(innings, Fixtures.delivery(innings, 0, i).runsOffBat(1).build());
        }
        assertEquals(1, innings.getScoreCard().completedOvers());
        assertEquals(0, innings.getScoreCard().ballsIntoCurrentOver());
    }

    @Test
    public void aWideCostsTheBowlingSideOneRun() {
        Innings innings = Fixtures.openedInnings();
        ScoringEngine engine = new ScoringEngine();
        engine.apply(innings, Fixtures.delivery(innings, 0, 1)
                .extra(ExtraType.WIDE, 0).build());
        assertEquals(1, innings.getScoreCard().getWides());
    }

    @Test
    public void aWideDoesNotCountTowardsTheOver() {
        Innings innings = Fixtures.openedInnings();
        ScoringEngine engine = new ScoringEngine();
        engine.apply(innings, Fixtures.delivery(innings, 0, 1)
                .extra(ExtraType.WIDE, 0).build());
        assertEquals(0, innings.getScoreCard().getLegalBalls());
    }

    @Test
    public void runsOffTheBatGoToTheStriker() {
        Innings innings = Fixtures.openedInnings();
        ScoringEngine engine = new ScoringEngine();
        Ball ball = Fixtures.delivery(innings, 0, 1).runsOffBat(4).build();
        engine.apply(innings, ball);
        assertEquals(4, innings.getScoreCard().battingLine(ball.getStrikerId()).getRuns());
    }

    @Test
    public void aFreshEngineHasNoFreeHitPending() {
        assertFalse(new ScoringEngine().isFreeHitPending());
    }
}
