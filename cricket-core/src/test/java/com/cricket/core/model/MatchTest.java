package com.cricket.core.model;

import com.cricket.core.Fixtures;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Covers {@link Match}, {@link MatchState}, {@link Innings}, {@link Over} and {@link Partnership}. */
public class MatchTest {

    private Match match;

    @Before
    public void setUp() {
        match = Fixtures.t20Match();
    }

    @Test
    public void aNewMatchIsScheduled() {
        assertEquals(MatchState.SCHEDULED, match.getState());
    }

    @Test
    public void aNewMatchHasNoInnings() {
        assertTrue(match.getInnings().isEmpty());
        assertNull(match.currentInnings());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aTeamCannotPlayItself() {
        new Match("bad", Fixtures.india(), Fixtures.india(), MatchFormat.T20, "Lord's");
    }

    @Test(expected = IllegalArgumentException.class)
    public void aBlankMatchIdIsRejected() {
        new Match(" ", Fixtures.india(), Fixtures.australia(), MatchFormat.T20, "Lord's");
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNullFormatIsRejected() {
        new Match("m", Fixtures.india(), Fixtures.australia(), null, "Lord's");
    }

    @Test
    public void anUnknownVenueFallsBack() {
        assertEquals("unknown",
                new Match("m", Fixtures.india(), Fixtures.australia(), MatchFormat.T20, null).getVenue());
    }

    @Test
    public void recordingTheTossAdvancesTheState() {
        match.recordToss("IND", true);
        assertEquals(MatchState.TOSS_DONE, match.getState());
        assertEquals("IND", match.getTossWinnerId());
        assertTrue(match.isTossElectedToBat());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aTossWinnerOutsideTheMatchIsRejected() {
        match.recordToss("ENG", true);
    }

    @Test
    public void theOpponentOfEachSideIsTheOther() {
        assertEquals("AUS", match.opponentOf("IND").getId());
        assertEquals("IND", match.opponentOf("AUS").getId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void theOpponentOfAnOutsideTeamIsRejected() {
        match.opponentOf("ENG");
    }

    @Test
    public void bothSidesAreInvolved() {
        assertTrue(match.involves("IND"));
        assertTrue(match.involves("AUS"));
        assertFalse(match.involves("ENG"));
    }

    @Test
    public void aT20InningsInheritsTheTwentyOverLimit() {
        Innings innings = match.startInnings(match.getTeamA(), match.getTeamB());
        assertEquals(20, innings.getOversLimit());
    }

    @Test
    public void aTestInningsHasNoOverLimit() {
        Match test = new Match("t", Fixtures.india(), Fixtures.australia(), MatchFormat.TEST, "Lord's");
        assertEquals(-1, test.startInnings(test.getTeamA(), test.getTeamB()).getOversLimit());
    }

    @Test
    public void inningsAreNumberedFromOne() {
        assertEquals(1, match.startInnings(match.getTeamA(), match.getTeamB()).getNumber());
        assertEquals(2, match.startInnings(match.getTeamB(), match.getTeamA()).getNumber());
    }

    @Test
    public void inningsAreFoundByNumber() {
        match.startInnings(match.getTeamA(), match.getTeamB());
        assertNotNull(match.inningsByNumber(1));
        assertNull(match.inningsByNumber(2));
    }

    @Test
    public void theCurrentInningsIsTheLatest() {
        match.startInnings(match.getTeamA(), match.getTeamB());
        Innings second = match.startInnings(match.getTeamB(), match.getTeamA());
        assertEquals(second, match.currentInnings());
    }

    @Test
    public void aggregateRunsSumEveryInnings() {
        Innings first = match.startInnings(match.getTeamA(), match.getTeamB());
        Innings second = match.startInnings(match.getTeamB(), match.getTeamA());
        first.getScoreCard().addRuns(180);
        second.getScoreCard().addRuns(120);
        assertEquals(300, match.aggregateRuns());
    }

    @Test
    public void matchesAreEqualById() {
        assertEquals(Fixtures.t20Match(), Fixtures.t20Match());
    }

    @Test
    public void theDirtyFlagStartsClear() {
        assertFalse(match.isDirty());
    }

    @Test
    public void theDirtyFlagCanBeSetAndCleared() {
        match.markDirty();
        assertTrue(match.isDirty());
        match.clearDirty();
        assertFalse(match.isDirty());
    }

    @Test
    public void onlyInProgressAcceptsDeliveries() {
        assertTrue(MatchState.IN_PROGRESS.acceptsDeliveries());
        assertFalse(MatchState.SCHEDULED.acceptsDeliveries());
        assertFalse(MatchState.RAIN_DELAY.acceptsDeliveries());
    }

    @Test
    public void completedAndAbandonedAreTerminal() {
        assertTrue(MatchState.COMPLETED.isTerminal());
        assertTrue(MatchState.ABANDONED.isTerminal());
        assertFalse(MatchState.IN_PROGRESS.isTerminal());
    }

    @Test
    public void aTerminalStateAcceptsNoTransition() {
        assertFalse(MatchState.COMPLETED.canTransitionTo(MatchState.IN_PROGRESS));
        assertFalse(MatchState.ABANDONED.canTransitionTo(MatchState.SCHEDULED));
    }

    @Test
    public void aStateCannotTransitionToItself() {
        assertFalse(MatchState.IN_PROGRESS.canTransitionTo(MatchState.IN_PROGRESS));
    }

    @Test
    public void aNullTransitionIsRejected() {
        assertFalse(MatchState.IN_PROGRESS.canTransitionTo(null));
    }

    @Test
    public void rainDelayCanResume() {
        assertTrue(MatchState.RAIN_DELAY.canTransitionTo(MatchState.IN_PROGRESS));
    }

    @Test
    public void anyLiveStateCanBeAbandoned() {
        assertTrue(MatchState.SCHEDULED.canTransitionTo(MatchState.ABANDONED));
        assertTrue(MatchState.IN_PROGRESS.canTransitionTo(MatchState.ABANDONED));
        assertTrue(MatchState.INNINGS_BREAK.canTransitionTo(MatchState.ABANDONED));
    }

    @Test(expected = IllegalStateException.class)
    public void anIllegalTransitionIsRejected() {
        match.transitionTo(MatchState.COMPLETED);
    }

    @Test
    public void aLegalTransitionSequenceIsAccepted() {
        match.recordToss("IND", true);
        match.transitionTo(MatchState.IN_PROGRESS);
        match.transitionTo(MatchState.INNINGS_BREAK);
        match.transitionTo(MatchState.IN_PROGRESS);
        match.transitionTo(MatchState.COMPLETED);
        assertEquals(MatchState.COMPLETED, match.getState());
    }

    @Test
    public void openingAnInningsSeatsBothBatters() {
        Innings innings = Fixtures.openedInnings();
        assertEquals("IND1", innings.getStrikerId());
        assertEquals("IND2", innings.getNonStrikerId());
    }

    @Test
    public void openingCreatesTheFirstPartnership() {
        Innings innings = Fixtures.openedInnings();
        assertEquals(1, innings.getPartnerships().size());
        assertEquals(1, innings.currentPartnership().getWicketNumber());
    }

    @Test(expected = IllegalArgumentException.class)
    public void openingWithTheSameBatterTwiceIsRejected() {
        new Innings("i", 1, Fixtures.india(), Fixtures.australia()).openWith("IND1", "IND1");
    }

    @Test
    public void rotatingStrikeSwapsTheBatters() {
        Innings innings = Fixtures.openedInnings();
        innings.rotateStrike();
        assertEquals("IND2", innings.getStrikerId());
        assertEquals("IND1", innings.getNonStrikerId());
    }

    @Test
    public void aDismissedStrikerIsReplaced() {
        Innings innings = Fixtures.openedInnings();
        innings.replaceBatter("IND1", "IND3");
        assertEquals("IND3", innings.getStrikerId());
    }

    @Test
    public void aDismissedNonStrikerIsReplaced() {
        Innings innings = Fixtures.openedInnings();
        innings.replaceBatter("IND2", "IND3");
        assertEquals("IND3", innings.getNonStrikerId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void replacingABatterWhoIsNotAtTheCreaseIsRejected() {
        Fixtures.openedInnings().replaceBatter("IND7", "IND3");
    }

    @Test
    public void ballsRemainingCountDownFromTheLimit() {
        Innings innings = Fixtures.openedInnings();
        assertEquals(120, innings.ballsRemaining());
        innings.getScoreCard().addLegalBall();
        assertEquals(119, innings.ballsRemaining());
    }

    @Test
    public void anUnlimitedInningsHasNoBallsRemaining() {
        Innings innings = new Innings("i", 1, Fixtures.india(), Fixtures.australia());
        assertEquals(-1, innings.ballsRemaining());
    }

    @Test
    public void runsRequiredAreMinusOneOutsideAChase() {
        assertEquals(-1, Fixtures.openedInnings().runsRequired());
    }

    @Test
    public void runsRequiredCountDownTowardsTheTarget() {
        Innings innings = Fixtures.openedInnings();
        innings.setTarget(181);
        assertEquals(181, innings.runsRequired());
        innings.getScoreCard().addRuns(100);
        assertEquals(81, innings.runsRequired());
    }

    @Test
    public void runsRequiredNeverGoNegative() {
        Innings innings = Fixtures.openedInnings();
        innings.setTarget(100);
        innings.getScoreCard().addRuns(150);
        assertEquals(0, innings.runsRequired());
    }

    @Test
    public void theRequiredRateReflectsTheChase() {
        Innings innings = Fixtures.openedInnings();
        innings.setTarget(121);
        assertEquals(6.05, innings.requiredRunRate(), 1e-9);
    }

    @Test
    public void thereIsNoRequiredRateOutsideAChase() {
        assertEquals(-1.0, Fixtures.openedInnings().requiredRunRate(), 1e-9);
    }

    @Test
    public void anInningsEndsWhenAllOut() {
        Innings innings = Fixtures.openedInnings();
        for (int i = 0; i < 10; i++) {
            innings.getScoreCard().addWicket();
        }
        assertTrue(innings.isComplete());
    }

    @Test
    public void anInningsEndsAtTheOverLimit() {
        Innings innings = Fixtures.openedInnings();
        for (int i = 0; i < 120; i++) {
            innings.getScoreCard().addLegalBall();
        }
        assertTrue(innings.isComplete());
    }

    @Test
    public void aClosedInningsIsComplete() {
        Innings innings = Fixtures.openedInnings();
        innings.close();
        assertTrue(innings.isComplete());
    }

    @Test(expected = IllegalArgumentException.class)
    public void anInningsCannotHaveTheSameTeamBattingAndBowling() {
        new Innings("i", 1, Fixtures.india(), Fixtures.india());
    }

    @Test
    public void anOverTalliesItsRuns() {
        Over over = new Over(0, "AUS8");
        over.record(Ball.builder().over(0).ballInOver(1).bowler("AUS8")
                .striker("IND1").nonStriker("IND2").runsOffBat(4).build());
        assertEquals(4, over.runsConceded());
    }

    @Test
    public void anOverIsCompleteAfterSixLegalBalls() {
        Over over = new Over(0, "AUS8");
        for (int i = 1; i <= 6; i++) {
            over.record(Ball.builder().over(0).ballInOver(i).bowler("AUS8")
                    .striker("IND1").nonStriker("IND2").build());
        }
        assertTrue(over.isComplete());
        assertEquals(6, over.legalBalls());
    }

    @Test
    public void aWideDoesNotAdvanceTheOver() {
        Over over = new Over(0, "AUS8");
        over.record(Ball.builder().over(0).ballInOver(1).bowler("AUS8")
                .striker("IND1").nonStriker("IND2").extra(ExtraType.WIDE, 0).build());
        assertEquals(0, over.legalBalls());
        assertFalse(over.isComplete());
    }

    @Test
    public void theOverSequenceRendersDotsAndRuns() {
        Over over = new Over(0, "AUS8");
        over.record(Ball.builder().over(0).ballInOver(1).bowler("AUS8")
                .striker("IND1").nonStriker("IND2").build());
        over.record(Ball.builder().over(0).ballInOver(2).bowler("AUS8")
                .striker("IND1").nonStriker("IND2").runsOffBat(4).build());
        assertEquals(". 4", over.sequence());
    }

    @Test(expected = IllegalArgumentException.class)
    public void anOverWithoutABowlerIsRejected() {
        new Over(0, " ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNegativeOverNumberIsRejected() {
        new Over(-1, "AUS8");
    }

    @Test
    public void aPartnershipAccumulatesRunsAndBalls() {
        Partnership stand = new Partnership("IND1", "IND2", 1);
        stand.addRuns(4);
        stand.addBall();
        assertEquals(4, stand.getRuns());
        assertEquals(1, stand.getBalls());
    }

    @Test
    public void aPartnershipKnowsItsBatters() {
        Partnership stand = new Partnership("IND1", "IND2", 1);
        assertTrue(stand.involves("IND1"));
        assertFalse(stand.involves("IND3"));
    }

    @Test
    public void aBrokenPartnershipIsMarked() {
        Partnership stand = new Partnership("IND1", "IND2", 1);
        assertFalse(stand.isBroken());
        stand.breakStand();
        assertTrue(stand.isBroken());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aPartnershipNeedsTwoDistinctBatters() {
        new Partnership("IND1", "IND1", 1);
    }

    @Test
    public void aPartnershipRunRateIsPerOver() {
        Partnership stand = new Partnership("IND1", "IND2", 1);
        stand.addRuns(30);
        for (int i = 0; i < 30; i++) {
            stand.addBall();
        }
        assertEquals(6.0, stand.runRate(), 1e-9);
    }
}
