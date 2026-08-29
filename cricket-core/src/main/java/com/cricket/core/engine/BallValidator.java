package com.cricket.core.engine;

import com.cricket.core.model.Ball;
import com.cricket.core.model.Dismissal;
import com.cricket.core.model.Extra;
import com.cricket.core.model.ExtraType;
import com.cricket.core.model.Innings;
import com.cricket.core.model.Team;
import com.cricket.core.model.WicketEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that a delivery is internally consistent and legal for the innings it is
 * being recorded against.
 */
public final class BallValidator {

    /** Highest number of runs that can be run off the bat without a boundary. */
    public static final int MAX_RUNS_OFF_BAT = 6;

    /** Structural checks that need no innings context. */
    public ValidationResult validate(Ball ball) {
        List<String> errors = new ArrayList<String>();
        if (ball == null) {
            return ValidationResult.invalid("ball must not be null");
        }

        if (ball.getOver() < 0) {
            errors.add("over must not be negative");
        }
        if (ball.getBallInOver() < 1) {
            errors.add("ballInOver starts at 1");
        }
        if (ball.getBallInOver() > ScoringRules.ballsPerOver() + 1) {
            errors.add("ballInOver exceeds the length of an over");
        }
        if (ball.getRunsOffBat() < 0) {
            errors.add("runs off bat must not be negative");
        }
        if (ball.getRunsOffBat() > MAX_RUNS_OFF_BAT) {
            errors.add("runs off bat exceeds " + MAX_RUNS_OFF_BAT);
        }
        if (ball.getStrikerId().equals(ball.getNonStrikerId())) {
            errors.add("striker and non-striker must be different players");
        }
        if (ball.getBowlerId().equals(ball.getStrikerId())) {
            errors.add("bowler cannot also be the striker");
        }

        errors.addAll(validateExtra(ball));
        errors.addAll(validateWicket(ball));

        return ValidationResult.of(errors);
    }

    private List<String> validateExtra(Ball ball) {
        List<String> errors = new ArrayList<String>();
        Extra extra = ball.getExtra();
        if (extra == null) {
            return errors;
        }
        ExtraType type = extra.getType();
        if (type == ExtraType.WIDE && ball.getRunsOffBat() > 0) {
            errors.add("no runs can be scored off the bat from a wide");
        }
        if ((type == ExtraType.BYE || type == ExtraType.LEG_BYE) && ball.getRunsOffBat() > 0) {
            errors.add("byes and leg-byes cannot be combined with runs off the bat");
        }
        if (type == ExtraType.PENALTY && extra.getRuns() == 0) {
            errors.add("penalty extras must carry at least one run");
        }
        return errors;
    }

    private List<String> validateWicket(Ball ball) {
        List<String> errors = new ArrayList<String>();
        WicketEvent wicket = ball.getWicket();
        if (wicket == null) {
            return errors;
        }
        Dismissal how = wicket.getDismissal();
        if (!ball.isLegalDelivery() && !how.isAllowedOffIllegalDelivery()) {
            errors.add(how + " cannot occur off an illegal delivery");
        }
        String out = wicket.getBatterId();
        if (!out.equals(ball.getStrikerId()) && !out.equals(ball.getNonStrikerId())) {
            errors.add("dismissed batter is not at the crease: " + out);
        }
        if (how != Dismissal.RUN_OUT && out.equals(ball.getNonStrikerId())) {
            errors.add(how + " must dismiss the striker");
        }
        if (how.requiresFielder() && wicket.getFielderId() == null) {
            errors.add(how + " requires a fielder");
        }
        if (how == Dismissal.CAUGHT && ball.getRunsOffBat() > 0) {
            errors.add("no runs off the bat are credited on a catch");
        }
        return errors;
    }

    /** Structural checks plus checks against the state of the innings. */
    public ValidationResult validateAgainst(Ball ball, Innings innings) {
        ValidationResult structural = validate(ball);
        List<String> errors = new ArrayList<String>(structural.getErrors());

        if (innings == null) {
            errors.add("innings must not be null");
            return ValidationResult.of(errors);
        }
        if (innings.isClosed()) {
            errors.add("innings is already closed");
        }
        if (innings.getScoreCard().isAllOut()) {
            errors.add("innings is already all out");
        }

        Team bowling = innings.getBowlingTeam();
        Team batting = innings.getBattingTeam();
        if (!bowling.contains(ball.getBowlerId())) {
            errors.add("bowler is not in the bowling side: " + ball.getBowlerId());
        }
        if (!batting.contains(ball.getStrikerId())) {
            errors.add("striker is not in the batting side: " + ball.getStrikerId());
        }
        if (!batting.contains(ball.getNonStrikerId())) {
            errors.add("non-striker is not in the batting side: " + ball.getNonStrikerId());
        }
        if (ball.getWicket() != null) {
            String fielder = ball.getWicket().getFielderId();
            if (fielder != null && !bowling.contains(fielder)) {
                errors.add("fielder is not in the bowling side: " + fielder);
            }
        }

        return ValidationResult.of(errors);
    }
}
