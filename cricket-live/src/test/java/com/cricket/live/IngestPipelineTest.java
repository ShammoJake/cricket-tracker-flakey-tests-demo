package com.cricket.live;

import com.cricket.core.Fixtures;
import com.cricket.core.engine.ScoringRules;
import com.cricket.core.model.Ball;
import com.cricket.core.model.Dismissal;
import com.cricket.core.model.ExtraType;
import com.cricket.core.model.Innings;
import com.cricket.core.model.WicketEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The pipeline is asynchronous, so every assertion here is preceded by
 * {@link IngestPipeline#awaitIdle(long)}. That is the correct way to synchronise
 * with it, and is why these tests are deterministic.
 */
public class IngestPipelineTest {

    private static final long TIMEOUT = 5000L;
    private static final String MATCH = "IND-AUS-T20";

    private IngestPipeline pipeline;
    private Innings innings;

    @Before
    public void setUp() {
        ScoringRules.reloadDefaults();
        pipeline = new IngestPipeline();
        innings = Fixtures.openedInnings();
    }

    @After
    public void tearDown() {
        pipeline.shutdown(TIMEOUT);
        ScoringRules.reloadDefaults();
    }

    private Ball delivery(int over, int ballInOver, int runs) {
        return Fixtures.delivery(innings, over, ballInOver).runsOffBat(runs).build();
    }

    private List<Ball> anOverOf(int runsPerBall) {
        List<Ball> balls = new ArrayList<Ball>();
        for (int i = 1; i <= 6; i++) {
            balls.add(Ball.builder().over(0).ballInOver(i).bowler("AUS8")
                    .striker("IND1").nonStriker("IND2").runsOffBat(runsPerBall).build());
        }
        return balls;
    }

    @Test
    public void aFreshPipelineIsIdle() {
        assertTrue(pipeline.isIdle());
        assertEquals(0, pipeline.submitted());
    }

    @Test
    public void submitReturnsAnIncreasingSequence() {
        assertEquals(1L, pipeline.submit(MATCH, innings, delivery(0, 1, 0)));
        assertEquals(2L, pipeline.submit(MATCH, innings, delivery(0, 2, 0)));
    }

    @Test
    public void aSubmittedDeliveryIsApplied() {
        pipeline.submit(MATCH, innings, delivery(0, 1, 4));
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertEquals(4, innings.getScoreCard().getTotalRuns());
    }

    @Test
    public void awaitIdleReturnsTrueOnceDrained() {
        pipeline.submit(MATCH, innings, delivery(0, 1, 1));
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertTrue(pipeline.isIdle());
    }

    @Test
    public void awaitIdleOnAnEmptyPipelineReturnsImmediately() {
        assertTrue(pipeline.awaitIdle(TIMEOUT));
    }

    @Test
    public void everyDeliveryOfAnOverIsApplied() {
        pipeline.submitAll(MATCH, innings, anOverOf(1));
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertEquals(6, innings.getScoreCard().getTotalRuns());
        assertEquals(6, innings.getScoreCard().getLegalBalls());
    }

    @Test
    public void submittedAndCompletedAgreeAfterDraining() {
        pipeline.submitAll(MATCH, innings, anOverOf(0));
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertEquals(pipeline.submitted(), pipeline.completed());
    }

    @Test
    public void acceptedCountsSuccessfulDeliveries() {
        pipeline.submitAll(MATCH, innings, anOverOf(2));
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertEquals(6L, pipeline.acceptedCount());
    }

    @Test
    public void anInvalidDeliveryIsRejectedNotApplied() {
        Ball illegal = Ball.builder().over(0).ballInOver(1)
                .bowler("IND9").striker("IND1").nonStriker("IND2").build();
        pipeline.submit(MATCH, innings, illegal);
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertEquals(1, pipeline.rejectionCount());
        assertEquals(0, innings.getScoreCard().getTotalRuns());
    }

    @Test
    public void aRejectionStillCountsAsCompleted() {
        Ball illegal = Ball.builder().over(0).ballInOver(1)
                .bowler("IND9").striker("IND1").nonStriker("IND2").build();
        pipeline.submit(MATCH, innings, illegal);
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertTrue(pipeline.isIdle());
    }

    @Test
    public void theRejectionNamesTheDelivery() {
        Ball illegal = Ball.builder().over(3).ballInOver(2)
                .bowler("IND9").striker("IND1").nonStriker("IND2").build();
        pipeline.submit(MATCH, innings, illegal);
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertTrue(pipeline.getRejections().get(0).startsWith("3.2"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void submittingNullIsRejected() {
        pipeline.submit(MATCH, innings, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void submittingANullListIsRejected() {
        pipeline.submitAll(MATCH, innings, null);
    }

    @Test
    public void deliveriesReachTheScorecardUpdater() {
        ScorecardUpdater updater = new ScorecardUpdater();
        pipeline.subscribe(updater);
        pipeline.submitAll(MATCH, innings, anOverOf(1));
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertEquals(6, updater.appliedCount());
        assertEquals(6, updater.snapshot(MATCH).getRuns());
    }

    @Test
    public void theUpdaterTracksWicketsAndBalls() {
        ScorecardUpdater updater = new ScorecardUpdater();
        pipeline.subscribe(updater);
        pipeline.submit(MATCH, innings, Ball.builder().over(0).ballInOver(1)
                .bowler("AUS8").striker("IND1").nonStriker("IND2")
                .wicket(WicketEvent.of(Dismissal.BOWLED, "IND1")).build());
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertEquals(1, updater.snapshot(MATCH).getWickets());
        assertEquals(1, updater.snapshot(MATCH).getLegalBalls());
    }

    @Test
    public void theUpdaterCountsBoundaries() {
        ScorecardUpdater updater = new ScorecardUpdater();
        pipeline.subscribe(updater);
        pipeline.submit(MATCH, innings, delivery(0, 1, 4));
        pipeline.submit(MATCH, innings, delivery(0, 2, 6));
        pipeline.submit(MATCH, innings, delivery(0, 3, 2));
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertEquals(2, updater.snapshot(MATCH).getBoundaries());
    }

    @Test
    public void anUntrackedMatchHasNoSnapshot() {
        ScorecardUpdater updater = new ScorecardUpdater();
        assertNull(updater.snapshot("nope"));
        assertFalse(updater.tracks("nope"));
    }

    @Test
    public void theUpdaterSummarisesTheScore() {
        ScorecardUpdater updater = new ScorecardUpdater();
        pipeline.subscribe(updater);
        pipeline.submitAll(MATCH, innings, anOverOf(1));
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertEquals("6/0 (1.0)", updater.snapshot(MATCH).summary());
    }

    @Test
    public void resettingTheUpdaterClearsIt() {
        ScorecardUpdater updater = new ScorecardUpdater();
        pipeline.subscribe(updater);
        pipeline.submit(MATCH, innings, delivery(0, 1, 1));
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        updater.reset();
        assertEquals(0, updater.trackedMatches());
        assertEquals(0, updater.appliedCount());
    }

    @Test
    public void theBroadcasterProducesALineForEveryDelivery() {
        LiveFeedBroadcaster feed = new LiveFeedBroadcaster();
        pipeline.subscribe(feed);
        pipeline.submitAll(MATCH, innings, anOverOf(0));
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertEquals(6, feed.broadcastCount());
        assertEquals(6, feed.getTranscript().size());
    }

    @Test
    public void subscribersReceiveEveryLine() {
        LiveFeedBroadcaster feed = new LiveFeedBroadcaster();
        LiveFeedBroadcaster.RecordingSubscriber sub =
                new LiveFeedBroadcaster.RecordingSubscriber("s1");
        feed.subscribe(sub);
        pipeline.subscribe(feed);
        pipeline.submitAll(MATCH, innings, anOverOf(1));
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertEquals(6, sub.count());
    }

    @Test
    public void anUnsubscribedListenerStopsReceiving() {
        LiveFeedBroadcaster feed = new LiveFeedBroadcaster();
        LiveFeedBroadcaster.RecordingSubscriber sub =
                new LiveFeedBroadcaster.RecordingSubscriber("s1");
        feed.subscribe(sub);
        assertTrue(feed.unsubscribe(sub));
        pipeline.subscribe(feed);
        pipeline.submit(MATCH, innings, delivery(0, 1, 1));
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertEquals(0, sub.count());
    }

    @Test
    public void recentLinesAreCapped() {
        LiveFeedBroadcaster feed = new LiveFeedBroadcaster();
        pipeline.subscribe(feed);
        pipeline.submitAll(MATCH, innings, anOverOf(0));
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertEquals(3, feed.recent(3).size());
    }

    @Test
    public void recentReturnsEverythingWhenTheLimitExceedsTheTranscript() {
        LiveFeedBroadcaster feed = new LiveFeedBroadcaster();
        pipeline.subscribe(feed);
        pipeline.submit(MATCH, innings, delivery(0, 1, 1));
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertEquals(1, feed.recent(50).size());
    }

    /** Maps a flat delivery index to a legal over/ball address. */
    private Ball six(int index) {
        return Ball.builder().over(index / 6).ballInOver(index % 6 + 1)
                .bowler("AUS8").striker("IND1").nonStriker("IND2").runsOffBat(6).build();
    }

    @Test
    public void milestonesFireOnceTheThresholdIsPassed() {
        MilestoneDetector detector = new MilestoneDetector();
        pipeline.subscribe(detector);
        for (int i = 0; i < 9; i++) {
            pipeline.submit(MATCH, innings, six(i));
        }
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertTrue(detector.hasReached("IND1", 50));
        assertTrue(detector.isMilestoneReached());
    }

    @Test
    public void aMilestoneFiresOnlyOnce() {
        MilestoneDetector detector = new MilestoneDetector();
        pipeline.subscribe(detector);
        for (int i = 0; i < 12; i++) {
            pipeline.submit(MATCH, innings, six(i));
        }
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertEquals(1, detector.forPlayer("IND1").size());
    }

    @Test
    public void noMilestoneFiresBelowFifty() {
        MilestoneDetector detector = new MilestoneDetector();
        pipeline.subscribe(detector);
        pipeline.submitAll(MATCH, innings, anOverOf(1));
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertFalse(detector.isMilestoneReached());
        assertEquals(0, detector.count());
    }

    @Test
    public void thePartnershipTrackerFollowsTheStand() {
        PartnershipTracker tracker = new PartnershipTracker();
        pipeline.subscribe(tracker);
        pipeline.submitAll(MATCH, innings, anOverOf(2));
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertNotNull(tracker.currentStand(MATCH));
        assertEquals(12, tracker.currentStand(MATCH).getRuns());
    }

    @Test
    public void severalListenersAllSeeTheDelivery() {
        ScorecardUpdater updater = new ScorecardUpdater();
        LiveFeedBroadcaster feed = new LiveFeedBroadcaster();
        MilestoneDetector detector = new MilestoneDetector();
        pipeline.subscribe(updater);
        pipeline.subscribe(feed);
        pipeline.subscribe(detector);
        pipeline.submitAll(MATCH, innings, anOverOf(1));
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertEquals(6, updater.appliedCount());
        assertEquals(6, feed.broadcastCount());
        assertEquals(3, pipeline.getBus().listenerCount());
    }

    @Test
    public void theWorkerPoolRecordsThatItRan() {
        pipeline.submit(MATCH, innings, delivery(0, 1, 1));
        assertTrue(pipeline.awaitIdle(TIMEOUT));
        assertTrue(pipeline.getPool().hasExecuted());
        assertTrue(pipeline.getPool().startedCount() >= 1);
    }

    @Test
    public void aShutDownPipelineDrains() {
        pipeline.submitAll(MATCH, innings, anOverOf(1));
        assertTrue(pipeline.shutdown(TIMEOUT));
        assertEquals(6, innings.getScoreCard().getTotalRuns());
    }
}
