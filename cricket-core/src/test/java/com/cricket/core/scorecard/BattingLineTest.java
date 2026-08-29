package com.cricket.core.scorecard;

import com.cricket.core.model.Dismissal;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BattingLineTest {

    private BattingLine line;

    @Before
    public void setUp() {
        line = new BattingLine("IND3");
    }

    @Test
    public void aNewLineIsBlank() {
        assertEquals(0, line.getRuns());
        assertEquals(0, line.getBallsFaced());
        assertEquals(0, line.getFours());
        assertEquals(0, line.getSixes());
        assertFalse(line.isOut());
    }

    @Test
    public void thePlayerIdIsRetained() {
        assertEquals("IND3", line.getPlayerId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aBlankPlayerIdIsRejected() {
        new BattingLine("  ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNullPlayerIdIsRejected() {
        new BattingLine(null);
    }

    @Test
    public void runsAccumulate() {
        line.addRuns(1);
        line.addRuns(2);
        assertEquals(3, line.getRuns());
    }

    @Test
    public void aFourIsCounted() {
        line.addRuns(4);
        assertEquals(1, line.getFours());
        assertEquals(0, line.getSixes());
    }

    @Test
    public void aSixIsCounted() {
        line.addRuns(6);
        assertEquals(1, line.getSixes());
        assertEquals(0, line.getFours());
    }

    @Test
    public void threeRunsRunAreNotABoundary() {
        line.addRuns(3);
        assertEquals(0, line.getFours());
        assertEquals(0, line.getSixes());
    }

    @Test
    public void addingZeroRunsIsAllowed() {
        line.addRuns(0);
        assertEquals(0, line.getRuns());
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeRunsAreRejected() {
        line.addRuns(-1);
    }

    @Test
    public void ballsFacedAccumulate() {
        line.addBallFaced();
        line.addBallFaced();
        assertEquals(2, line.getBallsFaced());
    }

    @Test
    public void strikeRateIsZeroBeforeFacingABall() {
        assertEquals(0.0, line.strikeRate(), 1e-9);
    }

    @Test
    public void strikeRateIsRunsPerHundredBalls() {
        line.addRuns(50);
        for (int i = 0; i < 25; i++) {
            line.addBallFaced();
        }
        assertEquals(200.0, line.strikeRate(), 1e-9);
    }

    @Test
    public void aRunABallIsAStrikeRateOfOneHundred() {
        for (int i = 0; i < 30; i++) {
            line.addRuns(1);
            line.addBallFaced();
        }
        assertEquals(100.0, line.strikeRate(), 1e-9);
    }

    @Test
    public void boundaryRunsCountFoursAndSixes() {
        line.addRuns(4);
        line.addRuns(6);
        line.addRuns(1);
        assertEquals(10, line.boundaryRuns());
    }

    @Test
    public void boundaryRunsAreZeroWithoutBoundaries() {
        line.addRuns(3);
        assertEquals(0, line.boundaryRuns());
    }

    @Test
    public void markingOutRecordsTheDismissal() {
        line.markOut(Dismissal.BOWLED, "AUS8");
        assertTrue(line.isOut());
        assertEquals(Dismissal.BOWLED, line.getDismissal());
    }

    @Test
    public void aBowlerIsCreditedForABowledDismissal() {
        line.markOut(Dismissal.BOWLED, "AUS8");
        assertEquals("AUS8", line.getDismissedBy());
    }

    @Test
    public void noBowlerIsCreditedForARunOut() {
        line.markOut(Dismissal.RUN_OUT, "AUS8");
        assertTrue(line.isOut());
        assertNull(line.getDismissedBy());
    }

    @Test
    public void aMilestoneIsReachedAtTheThreshold() {
        line.addRuns(50);
        assertTrue(line.isMilestone(50));
    }

    @Test
    public void aMilestoneIsNotReachedBelowTheThreshold() {
        line.addRuns(49);
        assertFalse(line.isMilestone(50));
    }

    @Test
    public void aMilestoneStillCountsWhenExceeded() {
        line.addRuns(120);
        assertTrue(line.isMilestone(100));
    }

    @Test
    public void anUnbeatenSummaryCarriesAnAsterisk() {
        line.addRuns(47);
        for (int i = 0; i < 32; i++) {
            line.addBallFaced();
        }
        assertEquals("47* (32)", line.summary());
    }

    @Test
    public void aDismissedSummaryHasNoAsterisk() {
        line.addRuns(47);
        for (int i = 0; i < 32; i++) {
            line.addBallFaced();
        }
        line.markOut(Dismissal.CAUGHT, "AUS8");
        assertEquals("47 (32)", line.summary());
    }

    @Test
    public void aDuckIsRenderedAsZero() {
        line.addBallFaced();
        line.markOut(Dismissal.BOWLED, "AUS8");
        assertEquals("0 (1)", line.summary());
    }

    @Test
    public void toStringLeadsWithThePlayerId() {
        line.addRuns(10);
        assertTrue(line.toString().startsWith("IND3"));
    }
}
