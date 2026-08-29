package com.cricket.core.engine;

import com.cricket.core.Fixtures;
import com.cricket.core.model.Ball;
import com.cricket.core.model.Dismissal;
import com.cricket.core.model.ExtraType;
import com.cricket.core.model.Innings;
import com.cricket.core.model.WicketEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BallValidatorTest {

    private BallValidator validator;
    private Innings innings;

    @Before
    public void setUp() {
        ScoringRules.reloadDefaults();
        validator = new BallValidator();
        innings = Fixtures.openedInnings();
    }

    @After
    public void tearDown() {
        ScoringRules.reloadDefaults();
    }

    private Ball.Builder legal() {
        return Ball.builder().over(0).ballInOver(1)
                .bowler("AUS8").striker("IND1").nonStriker("IND2");
    }

    @Test
    public void aPlainDeliveryIsValid() {
        assertTrue(validator.validate(legal().build()).isValid());
    }

    @Test
    public void aNullBallIsRejected() {
        assertFalse(validator.validate(null).isValid());
    }

    @Test
    public void aNullBallReportsAReason() {
        assertNotNull(validator.validate(null).firstError());
    }

    @Test
    public void aNegativeOverIsRejected() {
        assertFalse(validator.validate(legal().over(-1).build()).isValid());
    }

    @Test
    public void ballZeroOfAnOverIsRejected() {
        assertFalse(validator.validate(legal().ballInOver(0).build()).isValid());
    }

    @Test
    public void aSeventhDeliveryIsAllowedAfterAWide() {
        assertTrue(validator.validate(legal().ballInOver(7).build()).isValid());
    }

    @Test
    public void anEighthDeliveryIsRejected() {
        assertFalse(validator.validate(legal().ballInOver(8).build()).isValid());
    }

    @Test
    public void negativeRunsOffBatAreRejected() {
        assertFalse(validator.validate(legal().runsOffBat(-1).build()).isValid());
    }

    @Test
    public void sixRunsOffBatAreAllowed() {
        assertTrue(validator.validate(legal().runsOffBat(6).build()).isValid());
    }

    @Test
    public void sevenRunsOffBatAreRejected() {
        assertFalse(validator.validate(legal().runsOffBat(7).build()).isValid());
    }

    @Test
    public void theSameBatterAtBothEndsIsRejected() {
        Ball ball = Ball.builder().over(0).ballInOver(1)
                .bowler("AUS8").striker("IND1").nonStriker("IND1").build();
        assertTrue(validator.validate(ball).hasError("different players"));
    }

    @Test
    public void aBowlerFacingHisOwnDeliveryIsRejected() {
        Ball ball = Ball.builder().over(0).ballInOver(1)
                .bowler("IND1").striker("IND1").nonStriker("IND2").build();
        assertFalse(validator.validate(ball).isValid());
    }

    @Test
    public void runsOffTheBatFromAWideAreRejected() {
        Ball ball = legal().extra(ExtraType.WIDE, 0).runsOffBat(2).build();
        assertTrue(validator.validate(ball).hasError("off the bat from a wide"));
    }

    @Test
    public void aPlainWideIsValid() {
        assertTrue(validator.validate(legal().extra(ExtraType.WIDE, 0).build()).isValid());
    }

    @Test
    public void aWideWithExtraRunsIsValid() {
        assertTrue(validator.validate(legal().extra(ExtraType.WIDE, 4).build()).isValid());
    }

    @Test
    public void byesCombinedWithRunsOffTheBatAreRejected() {
        Ball ball = legal().extra(ExtraType.BYE, 2).runsOffBat(1).build();
        assertFalse(validator.validate(ball).isValid());
    }

    @Test
    public void legByesCombinedWithRunsOffTheBatAreRejected() {
        Ball ball = legal().extra(ExtraType.LEG_BYE, 1).runsOffBat(1).build();
        assertFalse(validator.validate(ball).isValid());
    }

    @Test
    public void aNoBallHitForRunsIsValid() {
        assertTrue(validator.validate(legal().extra(ExtraType.NO_BALL, 0).runsOffBat(6).build()).isValid());
    }

    @Test
    public void aPenaltyWithoutRunsIsRejected() {
        assertFalse(validator.validate(legal().extra(ExtraType.PENALTY, 0).build()).isValid());
    }

    @Test
    public void aPenaltyWithRunsIsValid() {
        assertTrue(validator.validate(legal().extra(ExtraType.PENALTY, 5).build()).isValid());
    }

    @Test
    public void beingBowledOffAWideIsRejected() {
        Ball ball = legal().extra(ExtraType.WIDE, 0)
                .wicket(WicketEvent.of(Dismissal.BOWLED, "IND1")).build();
        assertTrue(validator.validate(ball).hasError("illegal delivery"));
    }

    @Test
    public void beingStumpedOffAWideIsAllowed() {
        Ball ball = legal().extra(ExtraType.WIDE, 0)
                .wicket(new WicketEvent(Dismissal.STUMPED, "IND1", "AUS5")).build();
        assertTrue(validator.validate(ball).isValid());
    }

    @Test
    public void beingRunOutOffANoBallIsAllowed() {
        Ball ball = legal().extra(ExtraType.NO_BALL, 0)
                .wicket(new WicketEvent(Dismissal.RUN_OUT, "IND1", "AUS5")).build();
        assertTrue(validator.validate(ball).isValid());
    }

    @Test
    public void dismissingABatterWhoIsNotAtTheCreaseIsRejected() {
        Ball ball = legal().wicket(WicketEvent.of(Dismissal.BOWLED, "IND7")).build();
        assertTrue(validator.validate(ball).hasError("not at the crease"));
    }

    @Test
    public void theNonStrikerCannotBeBowled() {
        Ball ball = legal().wicket(WicketEvent.of(Dismissal.BOWLED, "IND2")).build();
        assertTrue(validator.validate(ball).hasError("must dismiss the striker"));
    }

    @Test
    public void theNonStrikerCanBeRunOut() {
        Ball ball = legal().wicket(new WicketEvent(Dismissal.RUN_OUT, "IND2", "AUS5")).build();
        assertTrue(validator.validate(ball).isValid());
    }

    @Test
    public void runsOffTheBatOnACatchAreRejected() {
        Ball ball = legal().runsOffBat(2)
                .wicket(new WicketEvent(Dismissal.CAUGHT, "IND1", "AUS5")).build();
        assertTrue(validator.validate(ball).hasError("credited on a catch"));
    }

    @Test
    public void aCleanCatchIsValid() {
        Ball ball = legal().wicket(new WicketEvent(Dismissal.CAUGHT, "IND1", "AUS5")).build();
        assertTrue(validator.validate(ball).isValid());
    }

    @Test
    public void severalProblemsAreAllReported() {
        Ball ball = Ball.builder().over(-1).ballInOver(0).runsOffBat(9)
                .bowler("AUS8").striker("IND1").nonStriker("IND1").build();
        assertTrue(validator.validate(ball).errorCount() >= 4);
    }

    @Test
    public void aValidDeliveryAgainstTheInningsPasses() {
        assertTrue(validator.validateAgainst(legal().build(), innings).isValid());
    }

    @Test
    public void aNullInningsIsRejected() {
        assertFalse(validator.validateAgainst(legal().build(), null).isValid());
    }

    @Test
    public void aClosedInningsIsRejected() {
        innings.close();
        assertTrue(validator.validateAgainst(legal().build(), innings).hasError("already closed"));
    }

    @Test
    public void aBowlerFromTheBattingSideIsRejected() {
        Ball ball = Ball.builder().over(0).ballInOver(1)
                .bowler("IND9").striker("IND1").nonStriker("IND2").build();
        assertTrue(validator.validateAgainst(ball, innings).hasError("not in the bowling side"));
    }

    @Test
    public void aStrikerFromTheBowlingSideIsRejected() {
        Ball ball = Ball.builder().over(0).ballInOver(1)
                .bowler("AUS8").striker("AUS1").nonStriker("IND2").build();
        assertTrue(validator.validateAgainst(ball, innings).hasError("striker is not in the batting side"));
    }

    @Test
    public void aNonStrikerFromTheBowlingSideIsRejected() {
        Ball ball = Ball.builder().over(0).ballInOver(1)
                .bowler("AUS8").striker("IND1").nonStriker("AUS2").build();
        assertTrue(validator.validateAgainst(ball, innings).hasError("non-striker is not in the batting side"));
    }

    @Test
    public void aFielderFromTheBattingSideIsRejected() {
        Ball ball = legal().wicket(new WicketEvent(Dismissal.CAUGHT, "IND1", "IND7")).build();
        assertTrue(validator.validateAgainst(ball, innings).hasError("fielder is not in the bowling side"));
    }

    @Test
    public void aValidResultHasNoErrors() {
        ValidationResult result = validator.validate(legal().build());
        assertEquals(0, result.errorCount());
        assertNull(result.firstError());
    }

    @Test
    public void throwIfInvalidIsSilentWhenValid() {
        validator.validate(legal().build()).throwIfInvalid();
    }

    @Test(expected = IllegalArgumentException.class)
    public void throwIfInvalidRaisesWhenInvalid() {
        validator.validate(legal().runsOffBat(-1).build()).throwIfInvalid();
    }

    @Test
    public void anUnknownFragmentIsNotMatched() {
        assertFalse(validator.validate(legal().build()).hasError("nonsense"));
    }

    @Test
    public void aNullFragmentIsNotMatched() {
        assertFalse(validator.validate(legal().runsOffBat(-1).build()).hasError(null));
    }
}
