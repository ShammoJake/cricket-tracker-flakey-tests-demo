package com.cricket.core.engine;

import com.cricket.core.model.Ball;
import com.cricket.core.model.Dismissal;
import com.cricket.core.model.Extra;
import com.cricket.core.model.ExtraType;
import com.cricket.core.model.Innings;
import com.cricket.core.model.Over;
import com.cricket.core.model.Partnership;
import com.cricket.core.model.WicketEvent;
import com.cricket.core.scorecard.BattingLine;
import com.cricket.core.scorecard.BowlingLine;
import com.cricket.core.scorecard.ScoreCard;

/**
 * Applies deliveries to an innings, updating the scorecard, the batting and bowling
 * lines, the partnership, and the live strike state.
 *
 * <p>Stateless apart from the free-hit flag, which spans exactly one delivery.
 */
public final class ScoringEngine {

    private final BallValidator validator;
    private boolean freeHitPending;

    public ScoringEngine() {
        this(new BallValidator());
    }

    public ScoringEngine(BallValidator validator) {
        if (validator == null) {
            throw new IllegalArgumentException("validator must not be null");
        }
        this.validator = validator;
    }

    public boolean isFreeHitPending() {
        return freeHitPending;
    }

    public void clearFreeHit() {
        this.freeHitPending = false;
    }

    /**
     * Records a delivery against the innings.
     *
     * @throws IllegalArgumentException when the delivery is not valid for the innings
     */
    public ScoringResult apply(Innings innings, Ball ball) {
        validator.validateAgainst(ball, innings).throwIfInvalid();

        ScoreCard card = innings.getScoreCard();
        Extra extra = ball.getExtra();
        boolean legal = ball.isLegalDelivery();

        int runsRun = runsRun(ball);
        int totalRuns = ball.totalRuns();

        innings.recordBall(ball);
        recordInOver(innings, ball);

        card.addRuns(totalRuns);
        if (legal) {
            card.addLegalBall();
        }
        applyExtrasToCard(card, extra);

        BattingLine batting = card.battingLine(ball.getStrikerId());
        if (creditsRunsToBatter(extra)) {
            batting.addRuns(ball.getRunsOffBat());
        }
        if (facesDelivery(extra)) {
            batting.addBallFaced();
        }

        BowlingLine bowling = card.bowlingLine(ball.getBowlerId());
        if (legal) {
            bowling.addLegalBall();
        }
        bowling.addRunsConceded(ball.runsChargedToBowler());
        if (extra != null && extra.getType() == ExtraType.WIDE) {
            bowling.addWide();
        }
        if (extra != null && extra.getType() == ExtraType.NO_BALL) {
            bowling.addNoBall();
        }

        Partnership stand = innings.currentPartnership();
        if (stand != null) {
            stand.addRuns(totalRuns);
            if (legal) {
                stand.addBall();
            }
        }

        boolean wicketFell = applyWicket(innings, ball, card, bowling, stand);
        boolean rotated = applyStrikeRotation(innings, runsRun, wicketFell);
        boolean overCompleted = applyOverCompletion(innings, card);

        boolean freeHitNext = extra != null
                && extra.getType() == ExtraType.NO_BALL
                && ScoringRules.freeHitAfterNoBall();
        this.freeHitPending = freeHitNext;

        return new ScoringResult(ball, totalRuns, runsRun, legal, wicketFell,
                rotated, overCompleted, freeHitNext);
    }

    /** Runs physically run or hit to the boundary, excluding automatic penalties. */
    static int runsRun(Ball ball) {
        int run = ball.getRunsOffBat();
        Extra extra = ball.getExtra();
        if (extra != null && extra.getType() != ExtraType.PENALTY) {
            run += extra.getRuns();
        }
        return run;
    }

    /** A batter is credited runs off the bat unless the delivery was a bye or wide. */
    private static boolean creditsRunsToBatter(Extra extra) {
        if (extra == null) {
            return true;
        }
        ExtraType type = extra.getType();
        return type == ExtraType.NO_BALL;
    }

    /** A batter faces every delivery except a wide, which must be re-bowled. */
    private static boolean facesDelivery(Extra extra) {
        return extra == null || extra.getType() != ExtraType.WIDE;
    }

    private static void applyExtrasToCard(ScoreCard card, Extra extra) {
        if (extra == null) {
            return;
        }
        int runs = extra.getRuns();
        switch (extra.getType()) {
            case WIDE:
                card.addWides(ScoringRules.widePenalty() + runs);
                break;
            case NO_BALL:
                card.addNoBalls(ScoringRules.noBallPenalty() + runs);
                break;
            case BYE:
                card.addByes(runs);
                break;
            case LEG_BYE:
                card.addLegByes(runs);
                break;
            case PENALTY:
                card.addPenalties(runs);
                break;
            default:
                break;
        }
    }

    private static void recordInOver(Innings innings, Ball ball) {
        Over current = innings.currentOver();
        if (current == null || current.getNumber() != ball.getOver()) {
            current = innings.startOver(ball.getOver(), ball.getBowlerId());
        }
        current.record(ball);
    }

    private boolean applyWicket(Innings innings, Ball ball, ScoreCard card,
                                BowlingLine bowling, Partnership stand) {
        WicketEvent wicket = ball.getWicket();
        if (wicket == null) {
            return false;
        }
        Dismissal how = wicket.getDismissal();
        card.addWicket();
        card.battingLine(wicket.getBatterId()).markOut(how, ball.getBowlerId());
        if (how.isCreditedToBowler()) {
            bowling.addWicket();
        }
        if (stand != null) {
            stand.breakStand();
        }
        return true;
    }

    private static boolean applyStrikeRotation(Innings innings, int runsRun, boolean wicketFell) {
        // A dismissed striker is replaced separately; the incoming batter takes strike.
        if (wicketFell) {
            return false;
        }
        if (runsRun % 2 == 1) {
            innings.rotateStrike();
            return true;
        }
        return false;
    }

    private static boolean applyOverCompletion(Innings innings, ScoreCard card) {
        Over current = innings.currentOver();
        if (current == null) {
            return false;
        }
        if (current.legalBalls() < ScoringRules.ballsPerOver()) {
            return false;
        }
        if (current.isMaiden()) {
            card.bowlingLine(current.getBowlerId()).addMaiden();
        }
        innings.rotateStrike();
        return true;
    }
}
