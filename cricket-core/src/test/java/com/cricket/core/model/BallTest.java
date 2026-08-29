package com.cricket.core.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BallTest {

    private Ball.Builder legal() {
        return Ball.builder().over(12).ballInOver(3)
                .bowler("AUS8").striker("IND3").nonStriker("IND1");
    }

    @Test
    public void theAddressCombinesOverAndBall() {
        assertEquals("12.3", legal().build().address());
    }

    @Test
    public void theFirstDeliveryOfAMatchIsZeroOne() {
        assertEquals("0.1", legal().over(0).ballInOver(1).build().address());
    }

    @Test
    public void aPlainDeliveryHasNoExtra() {
        assertFalse(legal().build().hasExtra());
        assertNull(legal().build().getExtra());
    }

    @Test
    public void aPlainDeliveryIsNotAWicket() {
        assertFalse(legal().build().isWicket());
    }

    @Test
    public void aPlainDeliveryIsLegal() {
        assertTrue(legal().build().isLegalDelivery());
    }

    @Test
    public void aWideIsNotALegalDelivery() {
        assertFalse(legal().extra(ExtraType.WIDE, 0).build().isLegalDelivery());
    }

    @Test
    public void aNoBallIsNotALegalDelivery() {
        assertFalse(legal().extra(ExtraType.NO_BALL, 0).build().isLegalDelivery());
    }

    @Test
    public void aByeIsALegalDelivery() {
        assertTrue(legal().extra(ExtraType.BYE, 1).build().isLegalDelivery());
    }

    @Test
    public void totalRunsAreRunsOffTheBatWhenThereAreNoExtras() {
        assertEquals(4, legal().runsOffBat(4).build().totalRuns());
    }

    @Test
    public void aWideCarriesOnePenaltyRun() {
        assertEquals(1, legal().extra(ExtraType.WIDE, 0).build().totalRuns());
    }

    @Test
    public void aWideToTheBoundaryCarriesFivePenaltyRuns() {
        assertEquals(5, legal().extra(ExtraType.WIDE, 4).build().totalRuns());
    }

    @Test
    public void aNoBallHitForSixIsSevenRuns() {
        assertEquals(7, legal().extra(ExtraType.NO_BALL, 0).runsOffBat(6).build().totalRuns());
    }

    @Test
    public void byesCarryNoPenalty() {
        assertEquals(2, legal().extra(ExtraType.BYE, 2).build().totalRuns());
    }

    @Test
    public void extraRunsAreZeroWithoutAnExtra() {
        assertEquals(0, legal().runsOffBat(4).build().extraRuns());
    }

    @Test
    public void extraRunsIncludeThePenalty() {
        assertEquals(3, legal().extra(ExtraType.WIDE, 2).build().extraRuns());
    }

    @Test
    public void runsOffTheBatAreChargedToTheBowler() {
        assertEquals(4, legal().runsOffBat(4).build().runsChargedToBowler());
    }

    @Test
    public void aWideIsChargedToTheBowler() {
        assertEquals(1, legal().extra(ExtraType.WIDE, 0).build().runsChargedToBowler());
    }

    @Test
    public void byesAreNotChargedToTheBowler() {
        assertEquals(0, legal().extra(ExtraType.BYE, 4).build().runsChargedToBowler());
    }

    @Test
    public void legByesAreNotChargedToTheBowler() {
        assertEquals(0, legal().extra(ExtraType.LEG_BYE, 2).build().runsChargedToBowler());
    }

    @Test
    public void aNoBallHitForFourChargesFiveToTheBowler() {
        assertEquals(5, legal().extra(ExtraType.NO_BALL, 0).runsOffBat(4).build().runsChargedToBowler());
    }

    @Test
    public void aWicketIsCarried() {
        Ball ball = legal().wicket(WicketEvent.of(Dismissal.BOWLED, "IND3")).build();
        assertTrue(ball.isWicket());
        assertEquals(Dismissal.BOWLED, ball.getWicket().getDismissal());
    }

    @Test
    public void theBuilderRoundTrips() {
        Ball original = legal().runsOffBat(4).extra(ExtraType.NO_BALL, 0).build();
        assertEquals(original, original.toBuilder().build());
    }

    @Test
    public void theBuilderCanAmendASingleField() {
        Ball original = legal().runsOffBat(4).build();
        Ball amended = original.toBuilder().runsOffBat(6).build();
        assertEquals(6, amended.getRunsOffBat());
        assertEquals(4, original.getRunsOffBat());
    }

    @Test
    public void identicalDeliveriesAreEqual() {
        assertEquals(legal().runsOffBat(2).build(), legal().runsOffBat(2).build());
    }

    @Test
    public void identicalDeliveriesShareAHashCode() {
        assertEquals(legal().runsOffBat(2).build().hashCode(),
                legal().runsOffBat(2).build().hashCode());
    }

    @Test
    public void deliveriesWithDifferentRunsAreNotEqual() {
        assertNotEquals(legal().runsOffBat(2).build(), legal().runsOffBat(4).build());
    }

    @Test
    public void aBallIsNotEqualToAnUnrelatedObject() {
        assertNotEquals(legal().build(), "12.3");
    }

    @Test
    public void aBallEqualsItself() {
        Ball ball = legal().build();
        assertEquals(ball, ball);
    }

    @Test
    public void theTimestampIsCarried() {
        assertEquals(1700000000000L, legal().timestampMillis(1700000000000L).build().getTimestampMillis());
    }

    @Test
    public void toStringLeadsWithTheAddress() {
        assertTrue(legal().build().toString().startsWith("12.3"));
    }

    @Test
    public void toStringMarksAWicket() {
        Ball ball = legal().wicket(WicketEvent.of(Dismissal.BOWLED, "IND3")).build();
        assertTrue(ball.toString().endsWith("W"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aBallWithoutABowlerIsRejected() {
        Ball.builder().striker("IND3").nonStriker("IND1").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void aBallWithoutAStrikerIsRejected() {
        Ball.builder().bowler("AUS8").nonStriker("IND1").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void aBallWithoutANonStrikerIsRejected() {
        Ball.builder().bowler("AUS8").striker("IND3").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNegativeExtraIsRejected() {
        new Extra(ExtraType.WIDE, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNullExtraTypeIsRejected() {
        new Extra(null, 1);
    }

    @Test
    public void extrasOfTheSameKindAreEqual() {
        assertEquals(new Extra(ExtraType.WIDE, 2), new Extra(ExtraType.WIDE, 2));
    }

    @Test
    public void extrasOfDifferentKindsAreNotEqual() {
        assertNotEquals(new Extra(ExtraType.WIDE, 2), new Extra(ExtraType.BYE, 2));
    }

    @Test
    public void anExtraCanBeBuiltWithoutRuns() {
        assertEquals(0, Extra.of(ExtraType.WIDE).getRuns());
    }
}
