package com.cricket.core.scorecard;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ScoreCardTest {

    private ScoreCard card;

    @Before
    public void setUp() {
        card = new ScoreCard("inn1");
    }

    @Test
    public void aNewCardIsBlank() {
        assertEquals(0, card.getTotalRuns());
        assertEquals(0, card.getWickets());
        assertEquals(0, card.getLegalBalls());
    }

    @Test
    public void theInningsIdIsRetained() {
        assertEquals("inn1", card.getInningsId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aBlankInningsIdIsRejected() {
        new ScoreCard(" ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNullInningsIdIsRejected() {
        new ScoreCard(null);
    }

    @Test
    public void runsAccumulate() {
        card.addRuns(4);
        card.addRuns(2);
        assertEquals(6, card.getTotalRuns());
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeRunsAreRejected() {
        card.addRuns(-1);
    }

    @Test
    public void wicketsAccumulate() {
        card.addWicket();
        card.addWicket();
        assertEquals(2, card.getWickets());
    }

    @Test
    public void legalBallsAccumulate() {
        card.addLegalBall();
        assertEquals(1, card.getLegalBalls());
    }

    @Test
    public void tenWicketsIsAllOut() {
        for (int i = 0; i < 10; i++) {
            card.addWicket();
        }
        assertTrue(card.isAllOut());
    }

    @Test
    public void nineWicketsIsNotAllOut() {
        for (int i = 0; i < 9; i++) {
            card.addWicket();
        }
        assertFalse(card.isAllOut());
    }

    @Test
    public void widesAreTallied() {
        card.addWides(1);
        card.addWides(5);
        assertEquals(6, card.getWides());
    }

    @Test
    public void noBallsAreTallied() {
        card.addNoBalls(1);
        assertEquals(1, card.getNoBalls());
    }

    @Test
    public void byesAreTallied() {
        card.addByes(4);
        assertEquals(4, card.getByes());
    }

    @Test
    public void legByesAreTallied() {
        card.addLegByes(2);
        assertEquals(2, card.getLegByes());
    }

    @Test
    public void penaltiesAreTallied() {
        card.addPenalties(5);
        assertEquals(5, card.getPenalties());
    }

    @Test
    public void totalExtrasSumsEveryKind() {
        card.addWides(3);
        card.addNoBalls(1);
        card.addByes(4);
        card.addLegByes(2);
        card.addPenalties(5);
        assertEquals(15, card.totalExtras());
    }

    @Test
    public void extrasAreZeroOnAFreshCard() {
        assertEquals(0, card.totalExtras());
    }

    @Test
    public void sixLegalBallsIsOneOver() {
        for (int i = 0; i < 6; i++) {
            card.addLegalBall();
        }
        assertEquals(1, card.completedOvers());
        assertEquals(0, card.ballsIntoCurrentOver());
    }

    @Test
    public void oversFacedUsesCricketNotation() {
        for (int i = 0; i < 98; i++) {
            card.addLegalBall();
        }
        assertEquals(16, card.completedOvers());
        assertEquals(2, card.ballsIntoCurrentOver());
        assertEquals(16.2, card.oversFaced(), 1e-9);
    }

    @Test
    public void runRateIsZeroBeforeABallIsBowled() {
        card.addRuns(0);
        assertEquals(0.0, card.runRate(), 1e-9);
    }

    @Test
    public void runRateIsRunsPerOver() {
        card.addRuns(60);
        for (int i = 0; i < 60; i++) {
            card.addLegalBall();
        }
        assertEquals(6.0, card.runRate(), 1e-9);
    }

    @Test
    public void aBattingLineIsCreatedOnFirstReference() {
        BattingLine line = card.battingLine("IND3");
        assertNotNull(line);
        assertEquals("IND3", line.getPlayerId());
    }

    @Test
    public void theSameBattingLineIsReturnedEachTime() {
        assertSame(card.battingLine("IND3"), card.battingLine("IND3"));
    }

    @Test
    public void aBowlingLineIsCreatedOnFirstReference() {
        assertNotNull(card.bowlingLine("AUS8"));
    }

    @Test
    public void theSameBowlingLineIsReturnedEachTime() {
        assertSame(card.bowlingLine("AUS8"), card.bowlingLine("AUS8"));
    }

    @Test
    public void hasBattedIsFalseUntilReferenced() {
        assertFalse(card.hasBatted("IND3"));
        card.battingLine("IND3");
        assertTrue(card.hasBatted("IND3"));
    }

    @Test
    public void hasBowledIsFalseUntilReferenced() {
        assertFalse(card.hasBowled("AUS8"));
        card.bowlingLine("AUS8");
        assertTrue(card.hasBowled("AUS8"));
    }

    @Test
    public void battingLinesAreListed() {
        card.battingLine("IND1");
        card.battingLine("IND2");
        assertEquals(2, card.battingLines().size());
    }

    @Test
    public void bowlingLinesAreListed() {
        card.bowlingLine("AUS8");
        assertEquals(1, card.bowlingLines().size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void theBattingViewIsReadOnly() {
        card.getBatting().put("IND1", new BattingLine("IND1"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void theBowlingViewIsReadOnly() {
        card.getBowling().put("AUS8", new BowlingLine("AUS8"));
    }

    @Test
    public void theSummaryUsesTheStandardFormat() {
        card.addRuns(147);
        for (int i = 0; i < 3; i++) {
            card.addWicket();
        }
        for (int i = 0; i < 98; i++) {
            card.addLegalBall();
        }
        assertEquals("147/3 (16.2)", card.summary());
    }

    @Test
    public void aBlankCardSummarisesAsZero() {
        assertEquals("0/0 (0.0)", card.summary());
    }

    @Test
    public void toStringLeadsWithTheInningsId() {
        assertTrue(card.toString().startsWith("inn1"));
    }
}
