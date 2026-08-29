package com.cricket.core.engine;

import com.cricket.core.Fixtures;
import com.cricket.core.model.Ball;
import com.cricket.core.model.Innings;
import com.cricket.core.model.MatchFormat;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OverManagerTest {

    private OverManager t20;
    private OverManager test;

    @Before
    public void setUp() {
        ScoringRules.reloadDefaults();
        t20 = new OverManager(MatchFormat.T20);
        test = new OverManager(MatchFormat.TEST);
    }

    @Test
    public void aFreshManagerHasNoLastBowler() {
        assertNull(t20.getLastOverBowlerId());
    }

    @Test
    public void aFreshManagerHasNoOversRecorded() {
        assertEquals(0, t20.totalOvers());
        assertEquals(0, t20.oversBowledBy("AUS8"));
    }

    @Test
    public void anyBowlerMayOpen() {
        assertTrue(t20.canBowlNextOver("AUS8"));
    }

    @Test
    public void recordingAnOverIncrementsTheTally() {
        t20.recordCompletedOver("AUS8");
        assertEquals(1, t20.oversBowledBy("AUS8"));
        assertEquals(1, t20.totalOvers());
    }

    @Test
    public void recordingAnOverSetsTheLastBowler() {
        t20.recordCompletedOver("AUS8");
        assertEquals("AUS8", t20.getLastOverBowlerId());
    }

    @Test
    public void aBowlerMayNotBowlConsecutiveOvers() {
        t20.recordCompletedOver("AUS8");
        assertFalse(t20.canBowlNextOver("AUS8"));
    }

    @Test
    public void anotherBowlerMayFollow() {
        t20.recordCompletedOver("AUS8");
        assertTrue(t20.canBowlNextOver("AUS9"));
    }

    @Test
    public void aBowlerMayReturnAfterOneOverFromTheOtherEnd() {
        t20.recordCompletedOver("AUS8");
        t20.recordCompletedOver("AUS9");
        assertTrue(t20.canBowlNextOver("AUS8"));
    }

    @Test
    public void t20CapsBowlersAtFourOvers() {
        assertEquals(4, t20.maxOversPerBowler());
    }

    @Test
    public void odiCapsBowlersAtTenOvers() {
        assertEquals(10, new OverManager(MatchFormat.ODI).maxOversPerBowler());
    }

    @Test
    public void testCricketHasNoCap() {
        assertEquals(-1, test.maxOversPerBowler());
    }

    @Test
    public void reachingTheCapBlocksFurtherOvers() {
        for (int i = 0; i < 4; i++) {
            t20.recordCompletedOver("AUS8");
            t20.recordCompletedOver("AUS9");
        }
        assertTrue(t20.hasReachedCap("AUS8"));
        assertFalse(t20.canBowlNextOver("AUS8"));
    }

    @Test
    public void aBowlerBelowTheCapIsNotBlocked() {
        t20.recordCompletedOver("AUS8");
        t20.recordCompletedOver("AUS9");
        assertFalse(t20.hasReachedCap("AUS8"));
    }

    @Test
    public void oversRemainingCountsDownToTheCap() {
        assertEquals(4, t20.oversRemainingFor("AUS8"));
        t20.recordCompletedOver("AUS8");
        assertEquals(3, t20.oversRemainingFor("AUS8"));
    }

    @Test
    public void oversRemainingIsUnboundedInTests() {
        assertEquals(-1, test.oversRemainingFor("AUS8"));
    }

    @Test
    public void aTestBowlerIsNeverCapped() {
        for (int i = 0; i < 30; i++) {
            test.recordCompletedOver("AUS8");
            test.recordCompletedOver("AUS9");
        }
        assertFalse(test.hasReachedCap("AUS8"));
    }

    @Test
    public void aValidBowlerHasNoRejectionReason() {
        assertNull(t20.rejectionReason("AUS8"));
    }

    @Test
    public void backToBackOversAreExplained() {
        t20.recordCompletedOver("AUS8");
        assertNotNull(t20.rejectionReason("AUS8"));
        assertTrue(t20.rejectionReason("AUS8").contains("previous over"));
    }

    @Test
    public void reachingTheCapIsExplained() {
        for (int i = 0; i < 4; i++) {
            t20.recordCompletedOver("AUS8");
            t20.recordCompletedOver("AUS9");
        }
        assertTrue(t20.rejectionReason("AUS8").contains("limit"));
    }

    @Test
    public void aBlankBowlerIsExplained() {
        assertNotNull(t20.rejectionReason(""));
        assertNotNull(t20.rejectionReason(null));
    }

    @Test
    public void aBlankBowlerMayNotBowl() {
        assertFalse(t20.canBowlNextOver(null));
        assertFalse(t20.canBowlNextOver("  "));
    }

    @Test(expected = IllegalStateException.class)
    public void recordingAnIllegalOverIsRejected() {
        t20.recordCompletedOver("AUS8");
        t20.recordCompletedOver("AUS8");
    }

    @Test
    public void resetClearsEverything() {
        t20.recordCompletedOver("AUS8");
        t20.reset();
        assertEquals(0, t20.totalOvers());
        assertNull(t20.getLastOverBowlerId());
        assertTrue(t20.canBowlNextOver("AUS8"));
    }

    @Test
    public void replayCountsOnlyCompletedOvers() {
        ScoringEngine engine = new ScoringEngine();
        Innings innings = Fixtures.openedInnings();
        for (int i = 1; i <= 6; i++) {
            engine.apply(innings, Fixtures.delivery(innings, 0, i).runsOffBat(0).build());
        }
        engine.apply(innings, firstBallOfOver(innings, 1));
        t20.replay(innings);
        assertEquals(1, t20.totalOvers());
    }

    /** Opening delivery of a new over from the other end. */
    private Ball firstBallOfOver(Innings innings, int over) {
        return Ball.builder().over(over).ballInOver(1)
                .bowler("AUS9").striker(innings.getStrikerId())
                .nonStriker(innings.getNonStrikerId()).build();
    }

    @Test
    public void replayIsIdempotent() {
        ScoringEngine engine = new ScoringEngine();
        Innings innings = Fixtures.openedInnings();
        for (int i = 1; i <= 6; i++) {
            engine.apply(innings, Fixtures.delivery(innings, 0, i).runsOffBat(0).build());
        }
        t20.replay(innings);
        t20.replay(innings);
        assertEquals(1, t20.totalOvers());
    }

    @Test
    public void replayRecoversTheLastBowler() {
        ScoringEngine engine = new ScoringEngine();
        Innings innings = Fixtures.openedInnings();
        for (int i = 1; i <= 6; i++) {
            engine.apply(innings, Fixtures.delivery(innings, 0, i).runsOffBat(0).build());
        }
        t20.replay(innings);
        assertEquals("AUS8", t20.getLastOverBowlerId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNullFormatIsRejected() {
        new OverManager(null);
    }

    @Test
    public void theFormatIsRetained() {
        assertEquals(MatchFormat.T20, t20.getFormat());
    }

    @Test
    public void bowledLastOverTracksTheMostRecentBowler() {
        t20.recordCompletedOver("AUS8");
        assertTrue(t20.bowledLastOver("AUS8"));
        assertFalse(t20.bowledLastOver("AUS9"));
    }

    @Test
    public void anUnknownBowlerHasNotBowledLastOver() {
        assertFalse(t20.bowledLastOver("AUS8"));
    }
}
