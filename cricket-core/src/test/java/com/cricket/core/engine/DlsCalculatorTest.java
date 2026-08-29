package com.cricket.core.engine;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DlsCalculatorTest {

    private static final double EPS = 1e-9;

    private DlsCalculator dls;

    @Before
    public void setUp() {
        dls = new DlsCalculator();
    }

    @Test
    public void fullFiftyOverInningsIsOneHundredPercent() {
        assertEquals(100.0, dls.resourcePercentage(50, 0), 1e-6);
    }

    @Test
    public void noOversRemainingIsZeroResource() {
        assertEquals(0.0, dls.resourcePercentage(0, 0), EPS);
    }

    @Test
    public void noOversRemainingIsZeroWhateverTheWickets() {
        for (int w = 0; w <= 9; w++) {
            assertEquals(0.0, dls.resourcePercentage(0, w), EPS);
        }
    }

    @Test
    public void resourceIncreasesWithOversRemaining() {
        double previous = -1.0;
        for (int overs = 0; overs <= 50; overs += 5) {
            double current = dls.resourcePercentage(overs, 0);
            assertTrue("resource should rise with overs at " + overs, current > previous);
            previous = current;
        }
    }

    @Test
    public void resourceDecreasesAsWicketsFall() {
        double previous = Double.MAX_VALUE;
        for (int w = 0; w <= 9; w++) {
            double current = dls.resourcePercentage(25, w);
            assertTrue("resource should fall as wickets go at " + w, current < previous);
            previous = current;
        }
    }

    @Test
    public void resourceNeverExceedsOneHundred() {
        for (int overs = 0; overs <= 50; overs++) {
            for (int w = 0; w <= 9; w++) {
                assertTrue(dls.resourcePercentage(overs, w) <= 100.0 + EPS);
            }
        }
    }

    @Test
    public void resourceIsNeverNegative() {
        for (int overs = 0; overs <= 50; overs++) {
            for (int w = 0; w <= 9; w++) {
                assertTrue(dls.resourcePercentage(overs, w) >= -EPS);
            }
        }
    }

    @Test
    public void halfTheOversLeavesMoreThanHalfTheResource() {
        // Early overs are worth less than late ones, so 25 overs is worth over 50%.
        assertTrue(dls.resourcePercentage(25, 0) > 50.0);
    }

    @Test
    public void nineWicketsDownIsALowResourceEvenWithOversLeft() {
        assertTrue(dls.resourcePercentage(50, 9) < 30.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeOversAreRejected() {
        dls.resourcePercentage(-1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void moreThanFiftyOversAreRejected() {
        dls.resourcePercentage(51, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeWicketsAreRejected() {
        dls.resourcePercentage(50, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void tenWicketsIsRejected() {
        dls.resourcePercentage(50, 10);
    }

    @Test
    public void resourceUsedIsTheDifferenceBetweenStartAndEnd() {
        double used = dls.resourceUsed(50, 20, 3);
        double expected = dls.resourcePercentage(50, 0) - dls.resourcePercentage(20, 3);
        assertEquals(expected, used, EPS);
    }

    @Test
    public void anUninterruptedInningsUsesAllItsResource() {
        assertEquals(100.0, dls.resourceUsed(50, 0, 0), 1e-6);
    }

    @Test
    public void equalResourcesLeaveTheTargetOneAboveTheScore() {
        assertEquals(251, dls.revisedTarget(250, 100.0, 100.0));
    }

    @Test
    public void fewerResourcesScaleTheTargetDown() {
        int target = dls.revisedTarget(250, 100.0, 50.0);
        assertEquals(126, target);
    }

    @Test
    public void moreResourcesRaiseTheTargetUsingG50() {
        int target = dls.revisedTarget(250, 50.0, 100.0);
        assertEquals(250 + (int) Math.floor(DlsCalculator.G50 * 0.5) + 1, target);
    }

    @Test
    public void targetIsAlwaysAtLeastOne() {
        assertTrue(dls.revisedTarget(0, 100.0, 100.0) >= 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeFirstInningsScoreIsRejected() {
        dls.revisedTarget(-1, 100.0, 100.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroResourceForTeamOneIsRejected() {
        dls.revisedTarget(250, 0.0, 100.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroResourceForTeamTwoIsRejected() {
        dls.revisedTarget(250, 100.0, 0.0);
    }

    @Test
    public void parScoreIsZeroBeforeAnyResourceIsUsed() {
        assertEquals(0, dls.parScore(251, 100.0, 100.0));
    }

    @Test
    public void parScoreIsTheFullChaseWhenAllResourceIsUsed() {
        assertEquals(250, dls.parScore(251, 100.0, 0.0));
    }

    @Test
    public void parScoreIsHalfTheChaseAtTheHalfwayPoint() {
        assertEquals(125, dls.parScore(251, 100.0, 50.0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void parScoreRejectsATargetBelowOne() {
        dls.parScore(0, 100.0, 50.0);
    }

    @Test
    public void aheadOfParIsTrueWhenScoreExceedsPar() {
        assertTrue(dls.isAheadOfPar(130, 125));
    }

    @Test
    public void levelWithParIsNotAhead() {
        assertFalse(dls.isAheadOfPar(125, 125));
    }

    @Test
    public void behindParIsNotAhead() {
        assertFalse(dls.isAheadOfPar(120, 125));
    }

    @Test
    public void oversRemainingCountsDownFromTheLimit() {
        assertEquals(50.0, dls.oversRemaining(50, 0), EPS);
        assertEquals(40.0, dls.oversRemaining(50, 60), EPS);
        assertEquals(25.0, dls.oversRemaining(50, 150), EPS);
    }

    @Test
    public void oversRemainingHandlesAPartOver() {
        assertEquals(49.5, dls.oversRemaining(50, 3), EPS);
    }

    @Test
    public void oversRemainingNeverGoesNegative() {
        assertEquals(0.0, dls.oversRemaining(20, 200), EPS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void oversRemainingRejectsAZeroLimit() {
        dls.oversRemaining(0, 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void oversRemainingRejectsNegativeBalls() {
        dls.oversRemaining(50, -1);
    }
}
