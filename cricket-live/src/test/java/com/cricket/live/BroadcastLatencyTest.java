package com.cricket.live;

import com.cricket.core.Fixtures;
import com.cricket.core.engine.ScoringRules;
import com.cricket.core.model.Ball;
import com.cricket.core.model.Dismissal;
import com.cricket.core.model.Innings;
import com.cricket.core.model.WicketEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Fan-out to live feed subscribers and to the milestone notices that follow it.
 */
public class BroadcastLatencyTest {

    private static final String MATCH = "IND-AUS-T20";
    private static final int DELIVERIES = 120;
    private static final long SETTLE_MILLIS = 3L;

    private IngestPipeline pipeline;
    private LiveFeedBroadcaster feed;
    private LiveFeedBroadcaster.RecordingSubscriber viewer;
    private MilestoneDetector milestones;
    private Innings innings;

    @Before
    public void setUp() {
        ScoringRules.reloadDefaults();
        primePipeline();

        pipeline = new IngestPipeline();
        feed = new LiveFeedBroadcaster();
        viewer = new LiveFeedBroadcaster.RecordingSubscriber("viewer-1");
        feed.subscribe(viewer);
        milestones = new MilestoneDetector();
        pipeline.subscribe(feed);
        pipeline.subscribe(milestones);
        innings = Fixtures.openedInnings();
    }

    /** Warms the ingest path so the measurement reflects the pipeline, not class loading. */
    private void primePipeline() {
        IngestPipeline warmup = new IngestPipeline();
        LiveFeedBroadcaster warmFeed = new LiveFeedBroadcaster();
        warmFeed.subscribe(new LiveFeedBroadcaster.RecordingSubscriber("warm"));
        warmup.subscribe(warmFeed);
        warmup.subscribe(new MilestoneDetector());
        warmup.submitAll("warmup", Fixtures.openedInnings(), boundaries(DELIVERIES));
        warmup.awaitIdle(10000L);
        warmup.shutdown(5000L);
    }

    @After
    public void tearDown() {
        pipeline.shutdown(5000L);
        ScoringRules.reloadDefaults();
    }

    /** Deliveries worth four each, so milestones accumulate quickly. */
    private List<Ball> boundaries(int count) {
        List<Ball> balls = new ArrayList<Ball>();
        for (int i = 0; i < count; i++) {
            balls.add(Ball.builder()
                    .over(i / 6)
                    .ballInOver(i % 6 + 1)
                    .bowler(i / 6 % 2 == 0 ? "AUS8" : "AUS9")
                    .striker("IND1")
                    .nonStriker("IND2")
                    .runsOffBat(4)
                    .build());
        }
        return balls;
    }

    @Test
    public void subscriberReceivesEveryDelivery() throws InterruptedException {
        pipeline.submitAll(MATCH, innings, boundaries(DELIVERIES));

        Thread.sleep(SETTLE_MILLIS);

        assertEquals(DELIVERIES, viewer.count());
    }

    @Test
    public void theTranscriptCoversTheWholeInnings() throws InterruptedException {
        pipeline.submitAll(MATCH, innings, boundaries(DELIVERIES));

        Thread.sleep(SETTLE_MILLIS - 1L);

        assertEquals(DELIVERIES, feed.getTranscript().size());
    }

    /**
     * Every batting milestone should have fired by the end of the innings. The
     * detector has to run several times over before the last one is reached, so the
     * wait has to cover repeated execution rather than a single pass.
     */
    @Test
    public void everyBattingMilestoneIsNoticed() throws InterruptedException {
        pipeline.submitAll(MATCH, innings, boundaries(DELIVERIES));

        Thread.sleep(SETTLE_MILLIS);

        assertEquals(MilestoneDetector.BATTING_MILESTONES.length, milestones.count());
    }

    @Test
    public void aWicketNoticeReachesSubscribers() throws InterruptedException {
        List<Ball> balls = boundaries(DELIVERIES - 1);
        balls.add(Ball.builder().over(19).ballInOver(6).bowler("AUS9")
                .striker("IND1").nonStriker("IND2")
                .wicket(WicketEvent.of(Dismissal.BOWLED, "IND1")).build());
        pipeline.submitAll(MATCH, innings, balls);

        Thread.sleep(SETTLE_MILLIS);

        assertTrue(viewer.last().contains("OUT"));
    }

    @Test
    public void everySubscriberSeesTheSameCountWhenAwaited() {
        pipeline.submitAll(MATCH, innings, boundaries(DELIVERIES));
        assertTrue(pipeline.awaitIdle(5000L));
        assertEquals(DELIVERIES, viewer.count());
    }
}
