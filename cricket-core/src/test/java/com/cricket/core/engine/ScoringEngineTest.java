package com.cricket.core.engine;

import com.cricket.core.Fixtures;
import com.cricket.core.model.Ball;
import com.cricket.core.model.Dismissal;
import com.cricket.core.model.ExtraType;
import com.cricket.core.model.Innings;
import com.cricket.core.model.WicketEvent;
import com.cricket.core.scorecard.ScoreCard;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScoringEngineTest {

    private ScoringEngine engine;
    private Innings innings;

    @Before
    public void setUp() {
        ScoringRules.reloadDefaults();
        engine = new ScoringEngine();
        innings = Fixtures.openedInnings();
    }

    @After
    public void tearDown() {
        ScoringRules.reloadDefaults();
    }

    private ScoringResult bowl(int over, int ballInOver, int runsOffBat) {
        return engine.apply(innings, Fixtures.delivery(innings, over, ballInOver)
                .runsOffBat(runsOffBat).build());
    }

    @Test
    public void dotBallLeavesScoreUnchanged() {
        ScoringResult result = bowl(0, 1, 0);
        assertEquals(0, innings.getScoreCard().getTotalRuns());
        assertTrue(result.isDot());
    }

    @Test
    public void singleAddsOneRunToTheTeamTotal() {
        bowl(0, 1, 1);
        assertEquals(1, innings.getScoreCard().getTotalRuns());
    }

    @Test
    public void boundaryAddsFourRuns() {
        bowl(0, 1, 4);
        assertEquals(4, innings.getScoreCard().getTotalRuns());
    }

    @Test
    public void sixAddsSixRuns() {
        bowl(0, 1, 6);
        assertEquals(6, innings.getScoreCard().getTotalRuns());
    }

    @Test
    public void runsOffBatAreCreditedToTheStriker() {
        String striker = innings.getStrikerId();
        bowl(0, 1, 4);
        assertEquals(4, innings.getScoreCard().battingLine(striker).getRuns());
    }

    @Test
    public void boundaryIsCountedAsAFour() {
        String striker = innings.getStrikerId();
        bowl(0, 1, 4);
        assertEquals(1, innings.getScoreCard().battingLine(striker).getFours());
    }

    @Test
    public void sixIsCountedAsASix() {
        String striker = innings.getStrikerId();
        bowl(0, 1, 6);
        assertEquals(1, innings.getScoreCard().battingLine(striker).getSixes());
    }

    @Test
    public void legalDeliveryAdvancesTheBallCount() {
        bowl(0, 1, 0);
        assertEquals(1, innings.getScoreCard().getLegalBalls());
    }

    @Test
    public void oddRunRotatesTheStrike() {
        String striker = innings.getStrikerId();
        ScoringResult result = bowl(0, 1, 1);
        assertTrue(result.isStrikeRotated());
        assertEquals(striker, innings.getNonStrikerId());
    }

    @Test
    public void evenRunKeepsTheStrike() {
        String striker = innings.getStrikerId();
        ScoringResult result = bowl(0, 1, 2);
        assertFalse(result.isStrikeRotated());
        assertEquals(striker, innings.getStrikerId());
    }

    @Test
    public void wideAddsOnePenaltyRun() {
        engine.apply(innings, Fixtures.delivery(innings, 0, 1)
                .extra(ExtraType.WIDE, 0).build());
        assertEquals(1, innings.getScoreCard().getTotalRuns());
    }

    @Test
    public void wideDoesNotAdvanceTheBallCount() {
        engine.apply(innings, Fixtures.delivery(innings, 0, 1)
                .extra(ExtraType.WIDE, 0).build());
        assertEquals(0, innings.getScoreCard().getLegalBalls());
    }

    @Test
    public void wideIsRecordedInTheExtras() {
        engine.apply(innings, Fixtures.delivery(innings, 0, 1)
                .extra(ExtraType.WIDE, 0).build());
        assertEquals(1, innings.getScoreCard().getWides());
    }

    @Test
    public void wideDoesNotCountAsABallFaced() {
        String striker = innings.getStrikerId();
        engine.apply(innings, Fixtures.delivery(innings, 0, 1)
                .extra(ExtraType.WIDE, 0).build());
        assertEquals(0, innings.getScoreCard().battingLine(striker).getBallsFaced());
    }

    @Test
    public void noBallAddsOnePenaltyRun() {
        engine.apply(innings, Fixtures.delivery(innings, 0, 1)
                .extra(ExtraType.NO_BALL, 0).build());
        assertEquals(1, innings.getScoreCard().getTotalRuns());
    }

    @Test
    public void runsOffBatOnANoBallAreCreditedToTheStriker() {
        String striker = innings.getStrikerId();
        engine.apply(innings, Fixtures.delivery(innings, 0, 1)
                .extra(ExtraType.NO_BALL, 0).runsOffBat(4).build());
        assertEquals(4, innings.getScoreCard().battingLine(striker).getRuns());
        assertEquals(5, innings.getScoreCard().getTotalRuns());
    }

    @Test
    public void noBallGrantsAFreeHit() {
        ScoringResult result = engine.apply(innings, Fixtures.delivery(innings, 0, 1)
                .extra(ExtraType.NO_BALL, 0).build());
        assertTrue(result.isFreeHitNext());
        assertTrue(engine.isFreeHitPending());
    }

    @Test
    public void byesAreNotCreditedToTheStriker() {
        String striker = innings.getStrikerId();
        engine.apply(innings, Fixtures.delivery(innings, 0, 1)
                .extra(ExtraType.BYE, 2).build());
        assertEquals(0, innings.getScoreCard().battingLine(striker).getRuns());
        assertEquals(2, innings.getScoreCard().getTotalRuns());
        assertEquals(2, innings.getScoreCard().getByes());
    }

    @Test
    public void legByesAreNotChargedToTheBowler() {
        engine.apply(innings, Fixtures.delivery(innings, 0, 1)
                .extra(ExtraType.LEG_BYE, 1).build());
        assertEquals(0, innings.getScoreCard().bowlingLine("AUS8").getRunsConceded());
        assertEquals(1, innings.getScoreCard().getLegByes());
    }

    @Test
    public void byesStillCountAsALegalDelivery() {
        engine.apply(innings, Fixtures.delivery(innings, 0, 1)
                .extra(ExtraType.BYE, 1).build());
        assertEquals(1, innings.getScoreCard().getLegalBalls());
    }

    @Test
    public void wicketIncrementsTheWicketCount() {
        Ball ball = Fixtures.delivery(innings, 0, 1)
                .wicket(WicketEvent.of(Dismissal.BOWLED, innings.getStrikerId()))
                .build();
        engine.apply(innings, ball);
        assertEquals(1, innings.getScoreCard().getWickets());
    }

    @Test
    public void bowledIsCreditedToTheBowler() {
        Ball ball = Fixtures.delivery(innings, 0, 1)
                .wicket(WicketEvent.of(Dismissal.BOWLED, innings.getStrikerId()))
                .build();
        engine.apply(innings, ball);
        assertEquals(1, innings.getScoreCard().bowlingLine("AUS8").getWickets());
    }

    @Test
    public void runOutIsNotCreditedToTheBowler() {
        Ball ball = Fixtures.delivery(innings, 0, 1)
                .wicket(new WicketEvent(Dismissal.RUN_OUT, innings.getStrikerId(), "AUS5"))
                .build();
        engine.apply(innings, ball);
        assertEquals(1, innings.getScoreCard().getWickets());
        assertEquals(0, innings.getScoreCard().bowlingLine("AUS8").getWickets());
    }

    @Test
    public void dismissedBatterIsMarkedOut() {
        String striker = innings.getStrikerId();
        Ball ball = Fixtures.delivery(innings, 0, 1)
                .wicket(WicketEvent.of(Dismissal.LBW, striker))
                .build();
        engine.apply(innings, ball);
        assertTrue(innings.getScoreCard().battingLine(striker).isOut());
    }

    @Test
    public void wicketBreaksThePartnership() {
        Ball ball = Fixtures.delivery(innings, 0, 1)
                .wicket(WicketEvent.of(Dismissal.BOWLED, innings.getStrikerId()))
                .build();
        engine.apply(innings, ball);
        assertTrue(innings.getPartnerships().get(0).isBroken());
    }

    @Test
    public void runsAreChargedToTheBowler() {
        bowl(0, 1, 4);
        assertEquals(4, innings.getScoreCard().bowlingLine("AUS8").getRunsConceded());
    }

    @Test
    public void wideIsChargedToTheBowler() {
        engine.apply(innings, Fixtures.delivery(innings, 0, 1)
                .extra(ExtraType.WIDE, 0).build());
        assertEquals(1, innings.getScoreCard().bowlingLine("AUS8").getRunsConceded());
    }

    @Test
    public void sixLegalDeliveriesCompleteTheOver() {
        ScoringResult last = null;
        for (int i = 1; i <= 6; i++) {
            last = bowl(0, i, 0);
        }
        assertTrue(last.isOverCompleted());
        assertEquals(1, innings.getScoreCard().completedOvers());
    }

    @Test
    public void strikeRotatesAtTheEndOfTheOver() {
        String opener = innings.getStrikerId();
        for (int i = 1; i <= 6; i++) {
            bowl(0, i, 0);
        }
        assertEquals(opener, innings.getNonStrikerId());
    }

    @Test
    public void wicketlessRunlessOverIsAMaiden() {
        for (int i = 1; i <= 6; i++) {
            bowl(0, i, 0);
        }
        assertEquals(1, innings.getScoreCard().bowlingLine("AUS8").getMaidens());
    }

    @Test
    public void overWithARunIsNotAMaiden() {
        for (int i = 1; i <= 5; i++) {
            bowl(0, i, 0);
        }
        bowl(0, 6, 1);
        assertEquals(0, innings.getScoreCard().bowlingLine("AUS8").getMaidens());
    }

    @Test
    public void byesDoNotSpoilAMaiden() {
        for (int i = 1; i <= 5; i++) {
            bowl(0, i, 0);
        }
        engine.apply(innings, Fixtures.delivery(innings, 0, 6)
                .extra(ExtraType.BYE, 2).build());
        assertEquals(1, innings.getScoreCard().bowlingLine("AUS8").getMaidens());
    }

    @Test
    public void ballIsAppendedToTheInningsRecord() {
        bowl(0, 1, 1);
        assertEquals(1, innings.ballCount());
    }

    @Test
    public void partnershipAccumulatesRuns() {
        bowl(0, 1, 2);
        bowl(0, 2, 4);
        assertEquals(6, innings.currentPartnership().getRuns());
    }

    @Test
    public void teamTotalMatchesTheSumOfEveryDelivery() {
        bowl(0, 1, 1);
        bowl(0, 2, 4);
        engine.apply(innings, Fixtures.delivery(innings, 0, 3).extra(ExtraType.WIDE, 0).build());
        bowl(0, 3, 6);
        ScoreCard card = innings.getScoreCard();
        assertEquals(12, card.getTotalRuns());
        assertEquals(1, card.totalExtras());
    }

    @Test(expected = IllegalArgumentException.class)
    public void bowlerFromTheBattingSideIsRejected() {
        engine.apply(innings, Ball.builder().over(0).ballInOver(1)
                .bowler("IND9").striker("IND1").nonStriker("IND2").build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void deliveryToAClosedInningsIsRejected() {
        innings.close();
        bowl(0, 1, 1);
    }
}
