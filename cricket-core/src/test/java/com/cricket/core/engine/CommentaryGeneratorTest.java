package com.cricket.core.engine;

import com.cricket.core.model.Ball;
import com.cricket.core.model.Dismissal;
import com.cricket.core.model.ExtraType;
import com.cricket.core.model.WicketEvent;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CommentaryGeneratorTest {

    private CommentaryGenerator commentary;

    @Before
    public void setUp() {
        commentary = new CommentaryGenerator();
    }

    private Ball.Builder ball() {
        return Ball.builder().over(12).ballInOver(3)
                .bowler("AUS8").striker("IND3").nonStriker("IND1");
    }

    @Test
    public void describeLeadsWithTheAddress() {
        String line = commentary.describe(ball().runsOffBat(0).build(), "Cummins", "Kohli");
        assertTrue(line.startsWith("12.3 "));
    }

    @Test
    public void describeNamesBothPlayers() {
        String line = commentary.describe(ball().runsOffBat(0).build(), "Cummins", "Kohli");
        assertTrue(line.contains("Cummins to Kohli"));
    }

    @Test
    public void describeEndsWithTheOutcome() {
        String line = commentary.describe(ball().runsOffBat(4).build(), "Cummins", "Kohli");
        assertTrue(line.endsWith("FOUR"));
    }

    @Test
    public void aMissingBowlerNameFallsBack() {
        String line = commentary.describe(ball().runsOffBat(0).build(), null, "Kohli");
        assertTrue(line.contains("unknown to Kohli"));
    }

    @Test
    public void aBlankStrikerNameFallsBack() {
        String line = commentary.describe(ball().runsOffBat(0).build(), "Cummins", "   ");
        assertTrue(line.contains("to unknown"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void describingANullBallIsRejected() {
        commentary.describe(null, "Cummins", "Kohli");
    }

    @Test(expected = IllegalArgumentException.class)
    public void theOutcomeOfANullBallIsRejected() {
        commentary.outcome(null);
    }

    @Test
    public void aDotBallReadsAsNoRun() {
        assertEquals("no run", commentary.outcome(ball().runsOffBat(0).build()));
    }

    @Test
    public void aSingleIsRenderedInTheSingular() {
        assertEquals("1 run", commentary.outcome(ball().runsOffBat(1).build()));
    }

    @Test
    public void twoRunsAreRenderedInThePlural() {
        assertEquals("2 runs", commentary.outcome(ball().runsOffBat(2).build()));
    }

    @Test
    public void threeRunsAreRenderedInThePlural() {
        assertEquals("3 runs", commentary.outcome(ball().runsOffBat(3).build()));
    }

    @Test
    public void aBoundaryIsShouted() {
        assertEquals("FOUR", commentary.outcome(ball().runsOffBat(4).build()));
    }

    @Test
    public void aSixIsShouted() {
        assertEquals("SIX", commentary.outcome(ball().runsOffBat(6).build()));
    }

    @Test
    public void aPlainWideIsNamed() {
        assertEquals("wide", commentary.outcome(ball().extra(ExtraType.WIDE, 0).build()));
    }

    @Test
    public void aWideWithRunsMentionsThem() {
        assertEquals("wide, 4 more", commentary.outcome(ball().extra(ExtraType.WIDE, 4).build()));
    }

    @Test
    public void aPlainNoBallIsNamed() {
        assertEquals("no ball", commentary.outcome(ball().extra(ExtraType.NO_BALL, 0).build()));
    }

    @Test
    public void aNoBallHitForRunsMentionsThem() {
        String line = commentary.outcome(ball().extra(ExtraType.NO_BALL, 0).runsOffBat(6).build());
        assertEquals("no ball, 6 off it", line);
    }

    @Test
    public void oneByeIsRenderedInTheSingular() {
        assertEquals("1 bye", commentary.outcome(ball().extra(ExtraType.BYE, 1).build()));
    }

    @Test
    public void multipleByesAreRenderedInThePlural() {
        assertEquals("3 byes", commentary.outcome(ball().extra(ExtraType.BYE, 3).build()));
    }

    @Test
    public void oneLegByeIsRenderedInTheSingular() {
        assertEquals("1 leg bye", commentary.outcome(ball().extra(ExtraType.LEG_BYE, 1).build()));
    }

    @Test
    public void multipleLegByesAreRenderedInThePlural() {
        assertEquals("2 leg byes", commentary.outcome(ball().extra(ExtraType.LEG_BYE, 2).build()));
    }

    @Test
    public void penaltyRunsAreNamed() {
        assertEquals("5 penalty runs", commentary.outcome(ball().extra(ExtraType.PENALTY, 5).build()));
    }

    @Test
    public void beingBowledIsAnnounced() {
        Ball b = ball().wicket(WicketEvent.of(Dismissal.BOWLED, "IND3")).build();
        assertEquals("OUT, bowled", commentary.outcome(b));
    }

    @Test
    public void anLbwIsAnnounced() {
        Ball b = ball().wicket(WicketEvent.of(Dismissal.LBW, "IND3")).build();
        assertEquals("OUT, lbw", commentary.outcome(b));
    }

    @Test
    public void aCatchNamesTheFielder() {
        Ball b = ball().wicket(new WicketEvent(Dismissal.CAUGHT, "IND3", "Carey")).build();
        assertEquals("OUT, caught by Carey", commentary.outcome(b));
    }

    @Test
    public void aStumpingNamesTheKeeper() {
        Ball b = ball().wicket(new WicketEvent(Dismissal.STUMPED, "IND3", "Carey")).build();
        assertEquals("OUT, stumped by Carey", commentary.outcome(b));
    }

    @Test
    public void aRunOutNamesTheFielder() {
        Ball b = ball().wicket(new WicketEvent(Dismissal.RUN_OUT, "IND3", "Maxwell")).build();
        assertEquals("OUT, run out by Maxwell", commentary.outcome(b));
    }

    @Test
    public void hitWicketIsAnnounced() {
        Ball b = ball().wicket(WicketEvent.of(Dismissal.HIT_WICKET, "IND3")).build();
        assertEquals("OUT, hit wicket", commentary.outcome(b));
    }

    @Test
    public void obstructingTheFieldIsAnnounced() {
        Ball b = ball().wicket(WicketEvent.of(Dismissal.OBSTRUCTING_THE_FIELD, "IND3")).build();
        assertEquals("OUT, obstructing the field", commentary.outcome(b));
    }

    @Test
    public void retiringOutIsNotShouted() {
        Ball b = ball().wicket(WicketEvent.of(Dismissal.RETIRED_OUT, "IND3")).build();
        assertEquals("retired out", commentary.outcome(b));
    }

    @Test
    public void timedOutIsAnnounced() {
        Ball b = ball().wicket(WicketEvent.of(Dismissal.TIMED_OUT, "IND3")).build();
        assertEquals("OUT, timed out", commentary.outcome(b));
    }

    @Test
    public void aWicketOutranksTheRunsOnTheDelivery() {
        Ball b = ball().runsOffBat(1)
                .wicket(new WicketEvent(Dismissal.RUN_OUT, "IND3", "Maxwell")).build();
        assertTrue(commentary.outcome(b).startsWith("OUT"));
    }

    @Test
    public void thereIsNoMilestoneBelowFifty() {
        assertNull(commentary.milestone("Kohli", 49, 40));
    }

    @Test
    public void fiftyIsAMilestone() {
        String line = commentary.milestone("Kohli", 50, 34);
        assertTrue(line.contains("FIFTY"));
        assertTrue(line.contains("34 balls"));
    }

    @Test
    public void ninetyNineIsStillAFifty() {
        assertTrue(commentary.milestone("Kohli", 99, 70).contains("FIFTY"));
    }

    @Test
    public void oneHundredIsAHundred() {
        assertTrue(commentary.milestone("Kohli", 100, 71).contains("HUNDRED"));
    }

    @Test
    public void twoHundredIsADoubleHundred() {
        assertTrue(commentary.milestone("Kohli", 200, 150).contains("DOUBLE HUNDRED"));
    }

    @Test
    public void theMilestoneNamesTheBatter() {
        assertTrue(commentary.milestone("Kohli", 50, 34).startsWith("Kohli"));
    }

    @Test
    public void endOfOverCountsFromOne() {
        assertTrue(commentary.endOfOver(0, 6, 6, 0).startsWith("End of over 1"));
    }

    @Test
    public void endOfOverReportsTheScore() {
        assertTrue(commentary.endOfOver(11, 8, 97, 3).contains("97/3"));
    }

    @Test
    public void oneRunInAnOverIsRenderedInTheSingular() {
        assertTrue(commentary.endOfOver(4, 1, 30, 1).contains("(1 run)"));
    }

    @Test
    public void severalRunsInAnOverAreRenderedInThePlural() {
        assertTrue(commentary.endOfOver(4, 12, 41, 1).contains("(12 runs)"));
    }

    @Test
    public void aMaidenOverReadsAsZeroRuns() {
        assertTrue(commentary.endOfOver(4, 0, 29, 1).contains("(0 runs)"));
    }
}
