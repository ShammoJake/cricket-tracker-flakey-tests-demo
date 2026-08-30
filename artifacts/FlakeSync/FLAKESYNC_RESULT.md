# FlakeSync on cricket-tracker — results

Findings from running the ICSE'24 FlakeSync artifact end-to-end against the `cricket-tracker`
demo project, 2026-08-29.

Setup, commands and the blockers hit along the way are in `Running_FlakeSync.md`. This file is
the findings.

**Target:** `com.cricket.live.LiveScoreIngestTest#scorecardReflectsAllBallsAfterIngest` —
the primary FlakeSync target in `artifacts/flaky-labels.json`, written to mirror Figure 1 of the
paper.

---

## Headline

FlakeSync **repaired the test**. All six goals ran clean in **8 m 18 s**, far under the paper's
58.77-minute median. The generated barrier is placed exactly where ground truth says it should be.

One things did not go to plan:
1. **The emitted patch was broken as generated.** It hangs forever. One constant had to be
   corrected to make the repair work.

---

## 1. Goal-by-goal

| Goal | Time | Outcome |
|---|---|---|
| `concurrentfind` | 12 s | 90 concurrent methods; `ScorecardUpdater.onBall` present |
| `delaylocs` | 1 m 46 s | 53 locations; baseline passed at delay 0, failed at delay 100 |
| `deltadebug` | 1 m 52 s | minimized 53 → **1**: `WorkerPool#59` |
| `critsearch` | 2 m 36 s | root method `WorkerPool.beforeExecute`; 2 critical points |
| `barrierpointsearch` | 1 m 48 s | barrier `LiveScoreIngestTest#105`, threshold 240 |
| `patch` | 4 s | 2 patch files |

The run was fast because `delaylocs` failed at the very first delay value it tried (100 ms) and
`deltadebug` collapsed 53 locations to a single one. Neither had to explore.

---

## 2. Accuracy against ground truth

`artifacts/flaky-labels.json` records what each artifact is expected to report.

| Expectation | FlakeSync produced | Match |
|---|---|---|
| `expectedRepairable: true` | repaired; test passes under the barrier | **yes** |
| barrier "before the `assertEquals`" | `LiveScoreIngestTest#105` — the line of the `assertEquals` | **yes** |
| critical point `ScorecardUpdater.onBall — final statement` | `WorkerPool.beforeExecute`, ranges `#59-62` and `#64-65` | **no** |

### Why the critical point missed

The signal was there and then got discarded:

- `delaylocs` ranked **`ScorecardUpdater#69` first** of its 53 locations — lines 69–70 are
  `snapshot.lastSequence = ...; applied++;`, precisely the "final statement of `onBall`" that
  ground truth names.
- `deltadebug` then minimized to `WorkerPool#59` — `started.incrementAndGet()` inside
  `beforeExecute`. Delaying there stalls *every* task start (120 tasks × 100 ms), so it is the
  single cheapest location that reproduces the failure. Delta debugging is asked for a minimal
  set, and this is one.
- `critsearch` explored outward from that minimum, so it never revisited the listener.

The consequence is visible in the failure mode: with the delay in `beforeExecute` nothing runs at
all, and the final run threw an **NPE** (`updater.snapshot(MATCH)` returned null) rather than the
assertion failure the flakiness actually produces.

**Reading:** FlakeSync reached a *correct barrier* through a *coarser critical point* than the
subject was written to expose. Minimality and explanatory value are not the same objective. A
global choke point will always win a minimization race against the specific listener whose write
the assertion reads.

---

## 3. The generated patch does not work as emitted

This is the most consequential finding.

`patch/LiveScoreIngestTest.java.patch` produces:

```java
com.cricket.live.WorkerPool.resetExecutions();          // counter -> 0
pipeline.submitAll(MATCH, innings, inningsOfSingles(DELIVERIES));

Thread.sleep(ISOLATED_SETTLE_MILLIS);
while (com.cricket.live.WorkerPool.getExecutedStatus() < 240) {
    Thread.yield();
}

assertEquals(DELIVERIES, updater.snapshot(MATCH).getRuns());
```

with a counter added to `WorkerPool.beforeExecute` by the companion patch.

**The threshold and the reset disagree.** `barrierpointsearch` measured 240 = 120 warmup tasks
from `@Before primePipeline()` + 120 from the test. But the patch resets the counter at the
**start of the test method**, i.e. after the warmup has already run. Only the test's own 120 tasks
can ever be counted, so `< 240` is never satisfied and the barrier spins forever.


### The fix

One constant:

```java
while (com.cricket.live.WorkerPool.getExecutedStatus() < DELIVERIES) {
```

After that the barrier means "every delivery has been dispatched", which is what the assertion
depends on. Results:

| | before patch | patch as generated | patch with threshold fixed |
|---|---|---|---|
| isolated, 10 runs | 10 pass | **10 hang** | **10 pass** |
| `rerun.py`, 50 runs | 0 fail | — (hangs) | **0 fail** |
| full suite | passes | — | passes, no hang |

Because the reset makes the count local to the test, the repaired test is also independent of how
many tests ran before it — it behaves the same in isolation and in the full suite. That is a
better property than the 240 threshold would have had even if it had worked.

---

## 6. What this says about the artifact

**It works.** On a subject it had never seen, FlakeSync found a barrier that turns a flaky
async-wait test into a deterministic one, and put it exactly where the test's author would have.
The end-to-end pipeline ran without manual intervention once the baseline was viable.

Two caveats worth carrying into any evaluation:

1. **The patch needs review before it is applied.** Here it was not merely suboptimal, it
   deadlocked the test. An evaluation that scores FlakeSync on "did it emit a patch" would count
   this as a success; one that runs the patched test would count it as a hang. The distance
   between those two verdicts is one integer.
2. **Critical points can be correct but uninformative.** Minimization pulls toward global choke
   points. `WorkerPool.beforeExecute` is a true answer to "where does a delay break this test" and
   a poor answer to "what is this test racing against".


---

## Files

| Path | Contents |
|---|---|
| `cricket-live/.flakesync/` | all goal output |
| `.flakesync/patch/LiveScoreIngestTest.java.patch` | barrier (threshold 240 — needs the fix above) |
| `.flakesync/patch/WorkerPool.java.patch` | the injected counter |
| `RUNBOOK_FLAKESYNC.md` | setup, commands, blockers, step-by-step log |
| `artifacts/flaky-labels.json` | ground truth |
