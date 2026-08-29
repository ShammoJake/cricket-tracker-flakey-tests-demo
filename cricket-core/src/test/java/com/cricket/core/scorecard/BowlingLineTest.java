package com.cricket.core.scorecard;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BowlingLineTest {

    private BowlingLine line;

    @Before
    public void setUp() {
        line = new BowlingLine("AUS8");
    }

    private void bowlLegalBalls(int count) {
        for (int i = 0; i < count; i++) {
            line.addLegalBall();
        }
    }

    @Test
    public void aNewLineIsBlank() {
        assertEquals(0, line.getLegalBalls());
        assertEquals(0, line.getRunsConceded());
        assertEquals(0, line.getWickets());
        assertEquals(0, line.getMaidens());
    }

    @Test
    public void thePlayerIdIsRetained() {
        assertEquals("AUS8", line.getPlayerId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aBlankPlayerIdIsRejected() {
        new BowlingLine("");
    }

    @Test
    public void legalBallsAccumulate() {
        bowlLegalBalls(4);
        assertEquals(4, line.getLegalBalls());
    }

    @Test
    public void sixBallsIsOneCompletedOver() {
        bowlLegalBalls(6);
        assertEquals(1, line.completedOvers());
        assertEquals(0, line.ballsIntoCurrentOver());
    }

    @Test
    public void twentyTwoBallsIsThreeOversAndFour() {
        bowlLegalBalls(22);
        assertEquals(3, line.completedOvers());
        assertEquals(4, line.ballsIntoCurrentOver());
    }

    @Test
    public void oversBowledUsesCricketNotation() {
        bowlLegalBalls(22);
        assertEquals(3.4, line.oversBowled(), 1e-9);
    }

    @Test
    public void aCompletedOverHasNoPartOver() {
        bowlLegalBalls(24);
        assertEquals(4.0, line.oversBowled(), 1e-9);
    }

    @Test
    public void runsConcededAccumulate() {
        line.addRunsConceded(4);
        line.addRunsConceded(2);
        assertEquals(6, line.getRunsConceded());
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeRunsConcededAreRejected() {
        line.addRunsConceded(-1);
    }

    @Test
    public void wicketsAccumulate() {
        line.addWicket();
        line.addWicket();
        assertEquals(2, line.getWickets());
    }

    @Test
    public void maidensAccumulate() {
        line.addMaiden();
        assertEquals(1, line.getMaidens());
    }

    @Test
    public void widesAreCounted() {
        line.addWide();
        line.addWide();
        assertEquals(2, line.getWides());
    }

    @Test
    public void noBallsAreCounted() {
        line.addNoBall();
        assertEquals(1, line.getNoBalls());
    }

    @Test
    public void economyIsZeroBeforeBowling() {
        assertEquals(0.0, line.economy(), 1e-9);
    }

    @Test
    public void economyIsRunsPerOver() {
        bowlLegalBalls(24);
        line.addRunsConceded(24);
        assertEquals(6.0, line.economy(), 1e-9);
    }

    @Test
    public void economyAccountsForAPartOver() {
        bowlLegalBalls(3);
        line.addRunsConceded(6);
        assertEquals(12.0, line.economy(), 1e-9);
    }

    @Test
    public void averageIsMinusOneWithoutAWicket() {
        bowlLegalBalls(24);
        line.addRunsConceded(30);
        assertEquals(-1.0, line.average(), 1e-9);
    }

    @Test
    public void averageIsRunsPerWicket() {
        line.addRunsConceded(30);
        line.addWicket();
        line.addWicket();
        assertEquals(15.0, line.average(), 1e-9);
    }

    @Test
    public void strikeRateIsMinusOneWithoutAWicket() {
        bowlLegalBalls(24);
        assertEquals(-1.0, line.strikeRate(), 1e-9);
    }

    @Test
    public void strikeRateIsBallsPerWicket() {
        bowlLegalBalls(24);
        line.addWicket();
        line.addWicket();
        assertEquals(12.0, line.strikeRate(), 1e-9);
    }

    @Test
    public void figuresUseTheStandardFormat() {
        bowlLegalBalls(22);
        line.addRunsConceded(22);
        line.addWicket();
        line.addWicket();
        assertEquals("3.4-0-22-2", line.figures());
    }

    @Test
    public void figuresIncludeMaidens() {
        bowlLegalBalls(24);
        line.addMaiden();
        line.addRunsConceded(18);
        line.addWicket();
        assertEquals("4.0-1-18-1", line.figures());
    }

    @Test
    public void wicketlessFiguresEndInZero() {
        bowlLegalBalls(12);
        line.addRunsConceded(15);
        assertEquals("2.0-0-15-0", line.figures());
    }

    @Test
    public void toStringLeadsWithThePlayerId() {
        assertTrue(line.toString().startsWith("AUS8"));
    }
}
