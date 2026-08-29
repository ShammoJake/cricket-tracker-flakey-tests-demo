package com.cricket.adversarial;

import com.cricket.core.Fixtures;
import com.cricket.core.model.Ball;
import com.cricket.core.model.Dismissal;
import com.cricket.core.model.Innings;
import com.cricket.core.model.WicketEvent;
import com.cricket.live.IngestPipeline;
import com.cricket.live.LiveFeedBroadcaster;
import com.cricket.live.MilestoneDetector;
import com.cricket.live.PartnershipTracker;
import com.cricket.live.ScorecardUpdater;

import java.util.ArrayList;
import java.util.List;

/**
 * A day's play, driven the way the scoring client drives it.
 *
 * <p>Hides the plumbing behind the vocabulary of the game so the tests that use it read
 * as cricket rather than as machinery: deliveries are bowled, drinks are taken, and the
 * scoreboard is read afterwards.
 */
final class MatchDay {

    static final String MATCH = "IND-AUS-T20";
    static final int OVERS = 20;
    static final int DELIVERIES = OVERS * 6;

    /** How long the players are off for drinks. */
    private static final long DRINKS_MILLIS = 2L;

    /** How long the sides are off between innings. */
    private static final long TEA_MILLIS = 5L;

    private final IngestPipeline pipeline;
    private final ScorecardUpdater scoreboard;
    private final LiveFeedBroadcaster feed;
    private final LiveFeedBroadcaster.RecordingSubscriber viewer;
    private final MilestoneDetector milestones;
    private final PartnershipTracker partnerships;
    private final Innings innings;

    private MatchDay(boolean warm) {
        this.pipeline = new IngestPipeline();
        this.scoreboard = new ScorecardUpdater();
        this.feed = new LiveFeedBroadcaster();
        this.viewer = new LiveFeedBroadcaster.RecordingSubscriber("viewer-1");
        this.milestones = new MilestoneDetector();
        this.partnerships = new PartnershipTracker();
        this.innings = Fixtures.openedInnings();

        feed.subscribe(viewer);
        pipeline.subscribe(scoreboard);
        pipeline.subscribe(feed);
        pipeline.subscribe(milestones);
        pipeline.subscribe(partnerships);

        if (warm) {
            rehearse();
        }
    }

    /** Opens a day's play, with the ground already in use. */
    static MatchDay open() {
        return new MatchDay(true);
    }

    /**
     * Puts an innings through a throwaway ground first, so what the tests see is the
     * behaviour of the scoring itself and not the cost of the first ball of the day.
     */
    private void rehearse() {
        IngestPipeline rehearsal = new IngestPipeline();
        rehearsal.subscribe(new ScorecardUpdater());
        rehearsal.subscribe(new LiveFeedBroadcaster());
        rehearsal.subscribe(new MilestoneDetector());
        rehearsal.subscribe(new PartnershipTracker());
        rehearsal.submitAll("rehearsal", Fixtures.openedInnings(), anInningsOf(DELIVERIES, 4));
        rehearsal.awaitIdle(10000L);
        rehearsal.shutdown(5000L);
    }

    /** Bowls the given number of deliveries, each worth the runs named. */
    void bowl(int deliveries, int runsEach) {
        pipeline.submitAll(MATCH, innings, anInningsOf(deliveries, runsEach));
    }

    /** Bowls a full innings of boundaries. */
    void bowlTheInnings() {
        bowl(DELIVERIES, 4);
    }

    /** Bowls a full innings and takes the last wicket with the final ball. */
    void bowlTheInningsAndTakeTheLastWicket() {
        List<Ball> balls = anInningsOf(DELIVERIES - 1, 4);
        balls.add(Ball.builder().over(OVERS - 1).ballInOver(6).bowler("AUS9")
                .striker("IND1").nonStriker("IND2")
                .wicket(WicketEvent.of(Dismissal.BOWLED, "IND1")).build());
        pipeline.submitAll(MATCH, innings, balls);
    }

    /** The players come off for drinks. */
    void drinks() {
        pause(DRINKS_MILLIS);
    }

    /** The longer break taken between innings. */
    void tea() {
        pause(TEA_MILLIS);
    }

    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The umpires wait for the scorers to catch up before calling play. */
    void waitForTheScorers() {
        pipeline.awaitIdle(5000L);
    }

    ScorecardUpdater.Snapshot scoreboard() {
        return scoreboard.snapshot(MATCH);
    }

    LiveFeedBroadcaster.RecordingSubscriber viewer() {
        return viewer;
    }

    List<String> transcript() {
        return feed.getTranscript();
    }

    int milestonesReached() {
        return milestones.count();
    }

    PartnershipTracker partnerships() {
        return partnerships;
    }

    void close() {
        pipeline.shutdown(5000L);
    }

    private static List<Ball> anInningsOf(int count, int runsEach) {
        List<Ball> balls = new ArrayList<Ball>();
        for (int i = 0; i < count; i++) {
            balls.add(Ball.builder()
                    .over(i / 6)
                    .ballInOver(i % 6 + 1)
                    .bowler(i / 6 % 2 == 0 ? "AUS8" : "AUS9")
                    .striker("IND1")
                    .nonStriker("IND2")
                    .runsOffBat(runsEach)
                    .build());
        }
        return balls;
    }
}
