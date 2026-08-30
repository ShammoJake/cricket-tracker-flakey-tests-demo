# Running FlakeSync on cricket-tracker

Worked runbook for the ICSE'24 FlakeSync artifact against the `cricket-tracker` demo
project on machine - Windows 11, JDK 11.0.32 Temurin, Maven 3.9.16


---

## Step 1 — build and install FlakeSync  [DONE, verified]

```bash
cd "D:/Projects/Demo/FlakeSync"
mvn clean install -DskipTests
```

Exit 0. Installed four artifacts under `~/.m2/repository/edu/utexas/ece/`:
`flakesync`, `flakesync-core`, `flakesync-maven-plugin`, `flakesync-utils`.
Confirmed `flakesync-maven-plugin-1.0-SNAPSHOT.jar` is present.

`-DskipTests` is ours, not the artifact's — the README says plain `mvn clean install`.
Skipping the plugin's own tests is fine for using it as a tool.

## Step 2 — install cricket-tracker  [DONE, verified]

```powershell
cd "D:\Projects\Demo\cricket-tracker"
mvn clean install "-Dmaven.test.failure.ignore=true"
```

The failure-ignore flag is **required** — this suite is designed to fail, and without it
the reactor stops before `install` and FlakeSync cannot resolve the modules. Note the
quotes (see shell gotcha above).

Result: `BUILD SUCCESS`, 7 failures out of 797 — the expected shape.

| Module | Tests | Failures |
|---|---|---|
| `cricket-core` | — | 0 |
| `cricket-live` | 73 | 4 — `ConcurrentBallIngestTest` (1), `SharedStateRaceTest` (3) |
| `cricket-stats` | 65 | 3 — `UnorderedStandingsTest` (3) |
| `cricket-api` | 154 | 0 (all `com.cricket.od` green, as `runOrder=alphabetical` intends) |

`LiveScoreIngestTest` passed 6/6 on this run. That is normal (labelled 8 failures / 50
runs) and does not affect FlakeSync, which injects its own delays to force the failure
rather than waiting for a natural one.

## Step 3 — target selection

The four tests carrying a `flakesync` block. We are choosing LiveScoreIngestTest#scorecardReflectsAllBallsAfterIngest as Target for this run.

| Test | Module | Expected |
|---|---|---|
| `com.cricket.live.LiveScoreIngestTest#scorecardReflectsAllBallsAfterIngest` | `cricket-live` | repairable — **primary target**, mirrors paper Figure 1 |
| `com.cricket.live.BroadcastLatencyTest#everyBattingMilestoneIsNoticed` | `cricket-live` | repairable — multi-execution threshold path |
| `com.cricket.time.MatchTimingTest#theArchiveIsWrittenInsideTheBudget` | `cricket-stats` | **not** repairable — negative control |
| `com.cricket.time.MatchTimingTest#theOverlayKeepsUpWithTheDeliveries` | `cricket-stats` | **not** repairable — negative control |

Ground truth for the primary target: critical point
`com/cricket/live/ScorecardUpdater.java:onBall` (final statement), barrier point in
`LiveScoreIngestTest.java` before the `assertEquals`.


## Step 4 — the six goals

Goal names come from the `@Mojo(name=...)` annotations in
`FlakeSync/flakesync-maven-plugin/src/main/java/flakesync/`. They are stateful and each
reads the previous one's output file, so they must run in this order with no skips.

```bash
cd "D:/Projects/Demo/cricket-tracker"
export T='com.cricket.live.LiveScoreIngestTest#scorecardReflectsAllBallsAfterIngest'
export P=edu.utexas.ece:flakesync-maven-plugin:1.0-SNAPSHOT

mvn $P:concurrentfind     -Dflakesync.testName=$T -pl cricket-live
mvn $P:delaylocs          -Dflakesync.testName=$T -pl cricket-live
mvn $P:deltadebug         -Dflakesync.testName=$T -pl cricket-live
mvn $P:critsearch         -Dflakesync.testName=$T -pl cricket-live
mvn $P:barrierpointsearch -Dflakesync.testName=$T -pl cricket-live
mvn $P:patch              -Dflakesync.testName=$T -pl cricket-live
```

`flakesync.testName` is the only required parameter (`FlakeSyncAbstractMojo.java:70`).

### Expected output, under `cricket-tracker/cricket-live/.flakesync/`

| Goal | Produces |
|---|---|
| `concurrentfind` | `<test>-ResultMethods.txt` — concurrent methods |
| `delaylocs` | `<test>-Locations.txt` — locations where an injected delay fails the test |
| `deltadebug` | `<test>-Locations_minimized.txt` — minimal failing subset |
| `critsearch` | `Results-CritSearch/<test>-RootMethods.csv`, `-CriticalPoints.csv` |
| `barrierpointsearch` | `Results-BarrierSearch/<test>-BarrierPoints.csv` |
| `patch` | `patch/<ClassName>.patch` |

### Budget

The paper reports a **median of 58.77 minutes per test** for the full six goals.
`delaylocs` and `deltadebug` dominate — each re-runs the test once per candidate location.
Run one test at a time.

`FlakeSync/testscripts/runAll.sh` drives batches from `testscripts/input/inputs.csv`, but
it `git clone`s each subject from GitHub, so it cannot target a local path without editing.
Invoke the goals directly.

---

## Run log

Primary target: `com.cricket.live.LiveScoreIngestTest#scorecardReflectsAllBallsAfterIngest`

| Goal | Status | Wall time | Result |
|---|---|---|---|
| `concurrentfind` | **PASS** | 12 s | 90 concurrent methods; `ScorecardUpdater.onBall` at line 56; baseline clean 3/3 |
| `delaylocs` | **PASS** | 1 m 46 s | 53 locations at delay 100; baseline passed at delay 0, failed at delay 100 (7/120) |
| `deltadebug` | **PASS** | 1 m 52 s | minimized 53 -> 1: `WorkerPool#59` |
| `critsearch` | **PASS** | 2 m 36 s | root method `WorkerPool.beforeExecute`; 2 critical points |
| `barrierpointsearch` | **PASS** | 1 m 48 s | barrier at `LiveScoreIngestTest#105`, threshold 240 |
| `patch` | **PASS** | 4 s | 2 patch files; sources restored afterwards |

**Total: 8 m 18 s** for all six goals - far under the paper's 58.77-minute median, because
`delaylocs` failed at the very first delay value and `deltadebug` minimized to a single
location.

### `concurrentfind`

`BUILD SUCCESS`, 12 s. 90 methods in `-ResultMethods.txt`, with
`com/cricket/live/ScorecardUpdater.onBall(Lcom/cricket/live/BallEvent;)V` at line 56 — the
`criticalPoint` recorded in `flaky-labels.json`. Baseline verified clean on three consecutive
runs.

### `delaylocs` - PASS, 1 m 46 s

The goal that proves the calibration worked:

```
delay 0   -> test passes        (baseline clean)
delay 100 -> expected:<120> but was:<7>
```

Exactly FlakeSync's required signal: the injected delay, not ambient slowness, causes the
failure. `RunWithDelaysMojo` breaks out of its sweep at the first failing value, so only delay
100 ran and 200 ... 12800 were not needed.

`-Locations.txt`: 53 locations. `ScorecardUpdater` entries cluster inside `onBall` (source lines
59-71) with `#69` ranked first - `snapshot.lastSequence = ...` / `applied++`, the final
statements, matching the expected critical point.

### `deltadebug` - PASS, 1 m 52 s

Minimized 53 locations to **one**:

```
100
com/cricket/live/WorkerPool#59
```

`WorkerPool#59` is `started.incrementAndGet()` inside `beforeExecute` - a pool-wide choke point.
Delaying there stalls every task start (120 tasks x 100 ms), so nothing completes and the final
run threw an NPE rather than an assertion failure (the snapshot was never created).

Legitimate - it is genuinely the smallest set that reproduces the failure - but it is the
*trivially sufficient* location rather than the semantically interesting one. This choice
propagates into the two goals that follow.

### `critsearch` - PASS, 2 m 36 s

```
RootMethods.csv
  com/cricket/live/WorkerPool/beforeExecute [100]

CriticalPoints.csv
  com/cricket/live/WorkerPool#59-com/cricket/live/WorkerPool#62[100]
  com/cricket/live/WorkerPool#64-com/cricket/live/WorkerPool#65[100]
```

Both ranges lie inside `beforeExecute`: `#59-62` spans `started.incrementAndGet()` through the
`peakActive` comparison, `#64-65` is `hasExecuted = true; super.beforeExecute(...)`.

### `barrierpointsearch` - PASS, 1 m 48 s

```
#Test-Name,Boundary-Point,Barrier-Point,Threshold
...,WorkerPool#59-WorkerPool#62[100],com.cricket.live.LiveScoreIngestTest#101,2
...,WorkerPool#64-WorkerPool#65[100],com.cricket.live.LiveScoreIngestTest#105,240
```

Line 105 is the `assertEquals`. **The second barrier matches the expected `barrierPoint`
exactly** - "before the assertEquals". Threshold 240 = 120 warmup tasks + 120 test tasks, since
the injected counter is static and `primePipeline()` runs a full innings through its own pipeline
first.

The test passed under the barrier (26.5 s, 0 failures) - the repair works.

### `patch` - PASS, 4 s

Two patch files in `.flakesync/patch/`.

`WorkerPool.java.patch` adds the counter:

```java
private static volatile int numExecutions;
public static void resetExecutions() { numExecutions = 0; }
public static int getExecutedStatus() { return numExecutions; }
// and inside beforeExecute:
numExecutions++;
```

`LiveScoreIngestTest.java.patch` replaces the bare sleep with the barrier:

```java
Thread.sleep(ISOLATED_SETTLE_MILLIS);
while (com.cricket.live.WorkerPool.getExecutedStatus() < 240) {
    Thread.yield();
}
assertEquals(DELIVERIES, updater.snapshot(MATCH).getRuns());
```

**The `patch` goal edits the working tree in place**, writing `<file>.orig` alongside, then
restores the sources once the diffs are generated. Verified afterwards: no `numExecutions` in
`WorkerPool.java`, no injected code in the test, no stray `.orig` files, and
`ISOLATED_SETTLE_MILLIS` intact. The patches are *not* applied - apply them yourself if you want
the repair in the tree.

The test-file patch is a whole-file diff rather than a small hunk, because the mojo rewrites the
file with different line endings.

---

## Verdict against ground truth

| Expectation from `flaky-labels.json` | FlakeSync produced | Match |
|---|---|---|
| `expectedRepairable: true` | repaired; test passes under the generated barrier | **yes** |
| barrier point "LiveScoreIngestTest.java - before the assertEquals" | `LiveScoreIngestTest#105`, threshold 240 | **yes** |
| critical point `ScorecardUpdater.onBall - final statement` | `WorkerPool.beforeExecute`, `#59-62` and `#64-65` | **no** |

The critical-point miss is traceable: `delaylocs` *did* rank `ScorecardUpdater#69` first among 53
locations, but `deltadebug` minimized to `WorkerPool#59`, and `critsearch` explored outward from
that minimum. A delay in the pool's dispatch hook starves every listener at once, so it is the
cheapest single location that reproduces the failure. FlakeSync found a correct barrier via a
coarser critical point than the one the subject was written to expose.

---

## Step 5 — applying the patch

The `patch` goal only *generates* diffs; applying them is a separate act.

### Line endings block `patch(1)`

```
$ patch --dry-run src/main/java/com/cricket/live/WorkerPool.java \
        .flakesync/patch/WorkerPool.java.patch
Hunk #1 FAILED at 14 (different line endings).
Hunk #2 FAILED at 63 (different line endings).
Hunk #3 FAILED at 101 (different line endings).
3 out of 3 hunks FAILED
```

`WorkerPool.java` and its patch are both CRLF, but the mojo writes its *added* lines with LF, so
the patch is internally mixed. `--ignore-whitespace` does not help. What works: normalise both
sides to LF, apply, then restore CRLF.

```bash
python -c "
for src,dst in [('src/main/java/com/cricket/live/WorkerPool.java','/tmp/wp.java'),
                ('.flakesync/patch/WorkerPool.java.patch','/tmp/wp.patch')]:
    open(dst,'wb').write(open(src,'rb').read().replace(b'\r\n',b'\n'))
"
patch /tmp/wp.java /tmp/wp.patch
# then re-write with CRLF, re-adding the trailing newline patch drops
```

`LiveScoreIngestTest.java` is LF already and applies directly:

```bash
patch src/test/java/com/cricket/live/LiveScoreIngestTest.java \
      .flakesync/patch/LiveScoreIngestTest.java.patch
```

### The patch hangs as generated

Both patches applied cleanly, and the test then **spun forever**. Ten isolated runs all hit the
timeout; one run was still going at 180 s.

```java
com.cricket.live.WorkerPool.resetExecutions();   // counter -> 0
pipeline.submitAll(MATCH, innings, inningsOfSingles(DELIVERIES));

Thread.sleep(ISOLATED_SETTLE_MILLIS);
while (com.cricket.live.WorkerPool.getExecutedStatus() < 240) {
    Thread.yield();
}
```

`barrierpointsearch` measured the threshold as 240 = 120 warmup tasks from `primePipeline()` plus
120 from the test. But the emitted patch resets the counter at the **start of the test method**,
after `@Before` has already run the warmup. Only the test's own 120 tasks can ever be counted, so
`< 240` is never satisfied.

`jstack` on the hung JVM:

```
"main" #1 prio=5 ... cpu=21218.75ms elapsed=22.09s runnable
   java.lang.Thread.State: RUNNABLE
        at java.lang.Thread.yield(java.base@11.0.32/Native Method)
        at com.cricket.live.LiveScoreIngestTest.scorecardReflectsAllBallsAfterIngest(LiveScoreIngestTest.java:106)
```

A separate hypothesis — that `numExecutions++` on a `volatile int` loses increments under
contention — was tested with a probe over 10 rounds of 240 tasks and **rejected**: no increments
were lost. The threshold is the bug.

### Fix: one constant

```java
while (com.cricket.live.WorkerPool.getExecutedStatus() < DELIVERIES) {
```

| | pre-patch | patch as generated | threshold fixed |
|---|---|---|---|
| isolated, 10 runs | 10 pass | **10 hang** | **10 pass** |
| `rerun.py`, 50 runs | 0 fail | — (hangs) | **0 fail** |
| full suite | passes | — | passes, no hang |

Because the reset makes the count local to the test, the repaired test behaves identically in
isolation and in the full suite.

### Verification after applying

```
mvn clean install -Dmaven.test.failure.ignore=true    -> BUILD SUCCESS, 8 failures / 797
```

Normal shape for this suite. `LiveScoreIngestTest` fully green, no hangs. The control
`pipelineDrainsWhenProperlyAwaited` passed 50/50.

---

## Step 6 — labelling gap fixed

`com.cricket.live.LiveScoreIngestTest#everyDeliveryIsAppliedAfterIngest` fails 43/50 and was in
neither `tests` nor `controls` in `artifacts/flaky-labels.json`. It sleeps `SETTLE_MILLIS - 1`
(2 ms) and asserts on `appliedCount()` — the same async-wait shape as its siblings, with the
tightest wait of the five, which is why it fails most often. Added as `async`, 43/50, with a note
recording that the omission was a gap rather than a deliberate exclusion.

`tests` is now **46**, not 45. **`CLAUDE.md` still says 45** and should be corrected.

---

## Files changed

| File | Change |
|---|---|
| `cricket-live/src/test/java/com/cricket/live/LiveScoreIngestTest.java` | `ISOLATED_SETTLE_MILLIS = 100L` for the target; FlakeSync barrier applied, threshold corrected 240 → `DELIVERIES`, with a comment recording why. Other four methods untouched. |
| `cricket-live/src/main/java/com/cricket/live/WorkerPool.java` | FlakeSync counter applied: `numExecutions`, `resetExecutions()`, `getExecutedStatus()`, increment in `beforeExecute` |
| `artifacts/flaky-labels.json` | target updated; missing `everyDeliveryIsAppliedAfterIngest` entry added (46 tests, 13 controls) |
| `artifacts/rerun-results.json` | regenerated by `rerun.py` |
| `FlakeSync_results.md` | new — the findings |

### Backups

In the session scratchpad:

| File | State |
|---|---|
| `LiveScoreIngestTest.java.bak` | original, before any change |
| `flaky-labels.json.bak` | original |
| `WorkerPool.java.prepatch.bak` | before the patch |
| `LiveScoreIngestTest.java.prepatch.bak` | after recalibration, before the patch |
| `flaky-labels.json.prepatch.bak` | before the gap fix |

To restore the subject fully: copy back `*.bak`, then delete `cricket-live/.flakesync/`.
