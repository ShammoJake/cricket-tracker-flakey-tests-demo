# FlakyLens on cricket-tracker — results

What the OOPSLA'25 FlakyLens classifier predicts for cricket-tracker's 797 tests, and why the
answer turns on a single whitespace character rather than on the code.

Run 2026-08-29/30 against `flaky-labels.json` as of 2026-08-29 (46 flaky / 5.77%). An earlier run
scored a corpus built before `LiveScoreIngestTest#everyDeliveryIsAppliedAfterIngest` was labelled,
counting an async-wait test as non-flaky; it was superseded and its numbers are gone. Rebuild the
corpus rather than reusing it — see the runbook's *Between runs* section.

For *how* to reproduce any of this, see `../RUNNING-FLAKYLENS-ON-CRICKET-TRACKER.md`; this file is
about the outcome.

> **The finding in one line.** The same 797 tests, the same checkpoint, run twice — the second time
> with **one newline appended to each test method and nothing else changed** — score 0.03 and 0.88.
> Non-flaky predictions go from 0/751 to 687/751. FlakyLens' flaky/non-flaky decision is made on
> source formatting, not on code, because its training data separates the two classes by formatting
> perfectly. §4 is the mechanism, §5 is the confirming run.

---

## 1. What was run

| | |
|---|---|
| Model | FlakyLens fine-tuned CodeBERT, **fold-1 checkpoint** (`per_project_model_weights_on__dataset_project_group_1.pt`) |
| Mode | **Inference only** — no training, no fine-tuning. `model.eval()` under `torch.no_grad()` |
| Hardware | CPU (`torch 2.3.1+cpu`), Python 3.8.5 in Docker; no NVIDIA GPU on this machine |
| Subject | cricket-tracker, 797 tests, 46 flaky (5.77%), ground truth in `cricket-tracker/artifacts/flaky-labels.json` |
| Runtime | 19 min per run for 797 tests (~1.4 s/test) |

Six classes, mapping one-to-one onto cricket-tracker's own categories: async wait 0, concurrency 1,
time 2, unordered collections 3, order dependency 4, non-flaky 5.

Two full runs, differing by one byte per test:

| run | corpus | outputs |
|---|---|---|
| **A — as written** | `to_flakylens_csv.py` output: column-0 `@Test`, no trailing newline | `cricket-tracker-fold1/` |
| **B — newline** | run A's corpus with `"\n"` appended to every `full_code`, nothing else | `cricket-tracker-fold1-newline/` |

Indentation was deliberately **not** changed in run B — `@Test` still sits at column 0 in all 797
rows — so the trailing newline is the only variable.

---

## 2. The control — why the port can be trusted

Getting FlakyLens onto a CPU-only Windows box took five changes to the artifact (device selection,
checkpoint loading, two Dockerfile fixes, line endings). Any of them could in principle have
altered the model's behaviour.

So the paper's **own** unmodified `test_set_1.csv` was run through the identical patched pipeline:

| class | precision | recall | F1 | support |
|---|---|---|---|---|
| 0 async wait | 0.50 | 0.73 | 0.59 | 11 |
| 1 concurrency | 0.23 | 0.50 | 0.32 | 6 |
| 2 time | 0.53 | 1.00 | 0.70 | 8 |
| 3 unordered collections | 0.64 | 0.78 | 0.70 | 9 |
| 4 order dependency | 1.00 | 0.32 | 0.49 | 31 |
| 5 non-flaky | 1.00 | 1.00 | 1.00 | 2164 |
| **accuracy** | | | **0.99** | 2229 |

This matches the artifact's own shipped `results/per_Category_Evaluation_BERT-FlakyLens.txt`
**exactly, on all six classes** — verified by programmatic comparison, not by eye.

**The CPU port is faithful.** Re-run this control after any further change to the pipeline; it is
the only thing separating a finding from a bug.

**What the control does *not* establish.** It shows the pipeline reproduces the paper's numbers. §4
shows the 1.00/1.00 row for class 5 in that table is obtainable from a trailing newline, so
reproducing it faithfully reproduces the artifact too. The control validates the port, not the
model.

---

## 3. The two runs

```
            run A — as written              run B — + one newline per test
        prec  recall   f1  support        prec  recall   f1  support
0 async 0.15    0.60  0.24     20         0.18    0.60  0.27     20
1 conc  0.33    0.43  0.38      7         0.50    0.43  0.46      7
2 time  0.04    0.67  0.07      3         0.25    0.67  0.36      3
3 uc    0.00    0.00  0.00      4         0.00    0.00  0.00      4
4 od    0.02    0.75  0.03     12         0.00    0.00  0.00     12
5 non   0.00    0.00  0.00    751         0.96    0.91  0.94    751
acc                   0.03    797                       0.88    797
```

Confusion matrices (rows = truth, columns = prediction):

**Run A — as written.** Accuracy 26/797 = 0.033.

| truth \ pred | 0 | 1 | 2 | 3 | 4 | 5 | total |
|---|---|---|---|---|---|---|---|
| 0 async wait | **12** | 0 | 0 | 0 | 8 | 0 | 20 |
| 1 concurrency | 4 | **3** | 0 | 0 | 0 | 0 | 7 |
| 2 time | 0 | 0 | **2** | 0 | 1 | 0 | 3 |
| 3 unordered collections | 0 | 0 | 0 | **0** | 4 | 0 | 4 |
| 4 order dependency | 0 | 0 | 2 | 1 | **9** | 0 | 12 |
| 5 non-flaky | 66 | 6 | 49 | 89 | 541 | **0** | 751 |

**Run B — one newline per test.** Accuracy 704/797 = 0.883.

| truth \ pred | 0 | 1 | 2 | 3 | 4 | 5 | total |
|---|---|---|---|---|---|---|---|
| 0 async wait | **12** | 0 | 0 | 0 | 0 | 8 | 20 |
| 1 concurrency | 4 | **3** | 0 | 0 | 0 | 0 | 7 |
| 2 time | 0 | 0 | **2** | 0 | 0 | 1 | 3 |
| 3 unordered collections | 0 | 0 | 0 | **0** | 0 | 4 | 4 |
| 4 order dependency | 0 | 0 | 0 | 0 | **0** | 12 | 12 |
| 5 non-flaky | 52 | 3 | 6 | 1 | 2 | **687** | 751 |

**714 of 797 predictions changed.** The dominant transitions are 4→5 (559 tests), 3→5 (89) and
2→5 (45).

---

## 4. Why — a formatting label leak in FlakyLens' training data

### 4.1 The leak

`FlakyLens/FlakeBench/FlakeBench_dataset.csv` holds all 8,574 rows that make up the four folds. Its
flaky and non-flaky halves were extracted by two different passes and are separated by surface
formatting alone:

| feature | flaky (280) | non-flaky (8294) | separates |
|---|---|---|---|
| starts `@Test` at column 0 | 100% | 0.8% | 99.2% |
| first line indented | 0% | 98.9% | 98.9% |
| closing brace indented | 0.4% | 99.1% | 99.1% |
| **ends with a newline** | **0%** | **100%** | **100%** |
| contains a blank line | 0% | 53.2% | 54.8% |
| contains a comment | 2.1% | 29.3% | 68.5% |

The trailing newline classifies **8574 of 8574 rows correctly on its own**. This is not project
provenance — 96 of the 97 flaky-test projects also contribute non-flaky tests, so the same
repository appears on both sides of the split with different formatting. It is the extraction
pipeline, and `src/per_project_prediction.sh:7` names it outright:

```sh
dataset_file="${data_path}/${dataset_name}/${dataset_name}_dataset_with_nonflaky_indented.csv"
```

All four folds inherit it identically, and every checkpoint scores a perfect 1.000 class-5 recall
on its own test half:

| fold | non-flaky test rows | indented | ends with newline | flaky rows | indented | newline | class-5 recall |
|---|---|---|---|---|---|---|---|
| 1 | 2164 | 99.7% | 100% | 65 | 0% | 0% | 1.000 |
| 2 | 2078 | 98.7% | 100% | 103 | 0% | 0% | 1.000 |
| 3 | 2141 | 97.4% | 100% | 58 | 0% | 0% | 1.000 |
| 4 | 1911 | 99.9% | 100% | 54 | 0% | 0% | 1.000 |

### 4.2 cricket-tracker's corpus wore the flaky uniform

`cricket-tracker/tools/to_flakylens_csv.py:full_code()` emits `@Test\npublic void … {\n…\n}` —
column-0 annotation, column-0 closing brace, no trailing newline — and `extract_tests.py:tidy()`
strips comments and drops blank lines. Run A's corpus is therefore a byte-level shape match to the
**flaky** class on every leaked feature:

| | run A corpus (797) | paper flaky | paper non-flaky |
|---|---|---|---|
| `@Test` at column 0 | 100% | 100% | 0.3% |
| no trailing newline | 100% | 100% | 0% |
| body indented 4 | 100% | 100% | 76.7% |
| blank line | 0% | 0% | 58.3% |
| comment | 0.1% | 0% | 31.1% |

Nothing in run A's corpus ever told the model it was looking at a non-flaky test.

### 4.3 The probe — content held fixed, whitespace varied

`cricket-tracker-fold1/probe_format.py`, n = 40 per row, fold-1 checkpoint. Byte-identical method
bodies; only indentation and the trailing newline change.

| input | class-5 predictions | mean p(non-flaky) |
|---|---|---|
| cricket non-flaky, as written | **0/40** | 0.000 |
| … **+ trailing newline only** | **40/40** | 0.969 |
| … **+ 4-space indent only** | **40/40** | 0.979 |
| … + indent and trailing newline | 40/40 | 0.979 |
| … + 2-space indent, or a tab | 40/40 | 0.979 |

Either cue alone is sufficient. The reverse direction, on the paper's own non-flaky rows:

| input | class-5 predictions | mean p(non-flaky) |
|---|---|---|
| paper non-flaky, as written | 40/40 | 0.979 |
| … trailing newline removed | 40/40 | 0.979 |
| … indent removed | 29/40 | 0.637 |
| … **both removed** | **2/40** | **0.053** |

And the control that rules out content entirely — **the paper's own genuinely flaky tests, given
non-flaky formatting**:

| input | class-5 predictions |
|---|---|
| paper flaky (async/conc/time/uc/od), as written | 0/40 |
| paper flaky, **indented + trailing newline** | **40/40** |
| cricket's genuinely flaky tests, as written | 0/40 |
| cricket's genuinely flaky tests, reformatted | **40/40** |

### 4.4 All four checkpoints, not just fold 1

`cricket-tracker-fold1/probe_folds.py`, 60 cricket non-flaky and 25 cricket flaky tests per
checkpoint:

| checkpoint | non-flaky as written | **+ trailing newline only** | flaky as written |
|---|---|---|---|
| fold 1 | 0/60 (p₅ = 0.000) | **60/60** (p₅ = 0.969) | 0/25 |
| fold 2 | 0/60 (p₅ = 0.000) | **44/60** (p₅ = 0.682) | 0/25 |
| fold 3 | 0/60 (p₅ = 0.001) | **58/60** (p₅ = 0.919) | 0/25 |
| fold 4 | 0/60 (p₅ = 0.000) | **60/60** (p₅ = 0.935) | 2/25 |

Every checkpoint learned the same shortcut. Fold 2 leans on it least; fold 4 is the only one that
ever emits class 5 without it — twice, both on genuinely flaky tests, so wrong anyway.

### 4.5 The second cause, and this one is config

`src/Bert_train_per_project.py:409-428` fine-tunes with

```python
loss_fun_name = "focal_loss"
class_weights = compute_class_weight(class_weight='balanced', classes=np.unique(Y_train), y=...)
cross_entropy = FocalLoss(alpha=weights.to(device), gamma=2.0)
```

On fold 1 that yields alpha 16.3 / 33.8 / 42.3 / 33.8 / 16.9 for classes 0–4 against **0.173** for
class 5 — a **245× ratio** — and γ=2 further discounts the easy majority class.

The 96.6% non-flaky training prior is therefore *deliberately erased from the loss*. With the
formatting cue present the shortcut carries class 5 anyway; with it absent, nothing pulls the
prediction back toward 5 and it falls to whichever minority class the content weakly resembles.
That is exactly run A's scatter — 541 od, 89 uc, 66 async, 49 time, 0 non-flaky.

### 4.6 What this costs the paper's own numbers

The control in §2 gives class 5 precision 1.00 and recall 1.00 on 2,164 tests. §4.3 shows that row
is obtainable from a trailing newline. The published per-class scores for classes 0–4 still measure
something — those are contrasts *within* the flaky half, which shares one formatting — but **any
flaky-vs-non-flaky number from this model, on any corpus, is suspect**, including the headline
accuracy of 0.99.

---

## 5. Run B — the confirming run

One newline appended per test, nothing else. Verified before the run: 0 of 797 rows differ from
run A's corpus once the added newline is stripped back off; same rows, same order, same column
order, same class counts (20/7/3/4/12/751); `@Test` still at column 0 in 100% of rows.

| | run A | run B | change |
|---|---|---|---|
| accuracy | 0.033 | **0.883** | +0.850 |
| weighted F1 | — | 0.90 | |
| non-flaky predicted | **0** / 797 | **712** / 797 | |
| non-flaky recall | 0/751 = 0.00 | **687/751 = 0.91** | |
| non-flaky precision | — | **0.96** | |
| genuinely flaky caught | 26/46 | 17/46 | **−9** |

**The leak is confirmed.** One byte per test moves 714 of 797 predictions and takes the suite from
worse-than-useless to an apparently respectable 0.88.

### 5.1 What run A's "flaky detections" were actually made of

This is the part that was not predicted, and it is the more interesting half of the result.

| truth | caught in A | caught in B | verdict |
|---|---|---|---|
| async wait (20) | 12 | **12** | unchanged |
| concurrency (7) | 3 | **3** | unchanged |
| time (3) | 2 | **2** | unchanged |
| unordered collections (4) | 0 | 0 | unchanged |
| **order dependency (12)** | **9** | **0** | **wiped out** |

Async, concurrency and time recall are **identical across the two runs** — the same 17 tests, by
name. The newline did not disturb them at all. What vanished is the entire order-dependency
column: all 12 OD tests went to non-flaky, including the 9 that run A got "right".

Those 9 were never detections. They were the same no-evidence fallback that also claimed 541
non-flaky tests, and they evaporate the moment the fallback is removed. Run A's most impressive
looking flaky class — order dependency at 75% recall, better than async — **was an artifact end to
end.**

So the newline does not override content. It removes the *fallback*: tests carrying real
token evidence (async/concurrency/time vocabulary) keep their class in both runs, and tests
carrying none move from a confidently-wrong minority class to non-flaky. Two different mechanisms,
cleanly separated by the experiment.

### 5.2 Where run B still errs

64 of the 751 non-flaky tests are still called flaky: 52 async, 6 time, 3 concurrency, 2 od, 1 uc.
Of those 52 async false positives, 4 are the deliberate token-stuffed decoys (§7). And 29 of the 46
genuinely flaky tests are missed — every OD and unordered-collections test among them.

Run B is not a good result. It is a **plausible-looking** result, which is the point: 0.88 accuracy
and 0.94 F1 on the majority class is the kind of number that gets reported, and it is one newline
away from 0.03.

---

## 6. Why *order dependency* in run A

Run A sent 563 of 797 tests to class 4. §4 explains why class 5 was unavailable; this explains
where they went instead. Class 4 was **not a positive detection** — §5.1 now proves that directly,
since the 9 "correct" OD predictions disappear under a formatting change that cannot have removed
any real order-dependency signal.

> Written before §4 and §5. The evidence stands, but read it as being about *which* flaky class a
> contentless test lands in, not about why non-flaky was unreachable.

**1 — It is out of character for the model.** On the paper's fold 1 it predicts class 4 **10 times
in 2229** (0.4%). In run A, **563 of 797** (71%). A 180× shift in prediction rate is a concept
collapsing, not a concept firing.

**2 — Class 4 is the weakest thing the model learned.** Fold 1's training split holds 62 class-4
examples against 6130 non-flaky — 1.0% of the data. Its most discriminative tokens are a single
project's JNDI vocabulary:

| token | lift over other classes |
|---|---|
| `testActionPermission` | 96.5 |
| `namingStore` | 92.9 |
| `namingContext` | 92.5 |
| `JndiPermission` | 69.9 |
| `CompositeName` | 54.7 |
| `lookup` | 36.7 |

cricket-tracker contains none of these, so this is **not** vocabulary overlap.

**3 — The tests are much shorter than anything in training.**

| corpus | median tokens | median lines |
|---|---|---|
| paper, non-flaky | 36 | 12 |
| paper, order-dependent | 44 | 11 |
| **cricket-tracker, non-flaky** | **14** | **5** |

**4 — Most of the vocabulary was never seen.** Only **23.1%** of cricket-tracker's token types
appear anywhere in the paper's non-flaky corpus. Its commonest domain tokens — `innings`,
`Fixtures`, `Ball`, `ExtraType`, `legal`, `AUS8` — have probability **0.000** in training.

**5 — Class 4 rises as content falls.** Among run A's non-flaky tests, by length quartile:

| quartile | tokens | predicted class 4 |
|---|---|---|
| Q1 shortest | 6–9 | 146/188 = 77.7% |
| Q2 | 9–14 | 153/188 = 81.4% |
| Q3 | 14–19 | 134/188 = 71.3% |
| Q4 longest | 19–115 | 108/188 = 57.4% |

### Confirmed by probing

Feed the model a method with no content at all:

| probe | async | conc | time | uc | **od** | non-flaky |
|---|---|---|---|---|---|---|
| `public void t() { }` | 0.000 | 0.000 | 0.001 | 0.002 | **0.996** | 0.001 |
| `public void theScoreIsCorrect() { }` | 0.000 | 0.000 | 0.000 | 0.001 | **0.998** | 0.000 |
| `public void t() { assertEquals(1, 1); }` | 0.000 | 0.000 | 0.001 | **0.919** | 0.080 | 0.000 |

These probes were written at column 0 with no trailing newline, so they carry the §4 flaky
signature as well as being contentless — the two effects are confounded here. §4.5 is why the model
does not regress to its 96.6% non-flaky prior. What this measures is only *which* minority class an
evidence-free method draws, and that stands.

### Content volume flips the call

Padding one real cricket test with comment filler, changing nothing else:

| filler lines | prediction | p |
|---|---|---|
| +0 | order dependency | 0.928 |
| +5 | order dependency | 0.999 |
| +15 | order dependency | 0.998 |
| **+40** | **non-flaky** | **0.896** |

**Reinterpreted after §4.** Comments are themselves a class-5 formatting marker (29.3% of non-flaky
rows against 2.1% of flaky ones, §4.1), so the +40 flip is most likely the same leak firing on a
third cue rather than an independent length effect. Padding with non-comment filler would separate
them; not yet done.

### A hypothesis refuted

An earlier guess was that cricket-tracker's static registries (`MatchRegistry`, `ScoringRules`,
`PlayerDirectory`) make the codebase *look* order-dependent. **It does not hold:**

| group | mentions a static channel |
|---|---|
| non-flaky predicted class 4 | 39/541 = **7.2%** |
| non-flaky predicted anything else | 4/210 = 1.9% |
| genuinely order-dependent tests | 6/12 = 50.0% |

The mild enrichment could account for ~39 tests, nowhere near 541.

---

## 7. The adversarial result

`com.cricket.adversarial` exists to test the paper's own central claim: that these classifiers key
on surface tokens rather than behaviour. It does so on **unperturbed, naturally written** tests,
without the paper's perturbation machinery.

Every cricket-tracker row shares one formatting within a run, so the §4 leak is a constant across
both groups of each comparison and cancels out. **Run B is the run to read here** — in run A the
non-flaky class was unreachable, which made half the design unfalsifiable.

### Direction 1 — same bug, different words

All 20 genuinely async-flaky tests share one flakiness mechanism. The only variable is vocabulary:

| group | vocabulary | run A | run B |
|---|---|---|---|
| `com.cricket.live` / `com.cricket.api` (12) | present — `Thread.sleep`, `await`, `latch` | **12/12** correct | **12/12** correct |
| `com.cricket.adversarial` (8) | none — every wait behind `day.tea()`, `day.drinks()` | 0/8 (all → od) | 0/8 (**all 8 → non-flaky**) |

Perfect separation in both runs, and in run B the failure takes exactly the form the suite was
designed to produce: eight genuinely flaky tests declared **non-flaky**, unanimously.

### Direction 2 — no bug, flaky words

Four deterministic, genuinely non-flaky tests carrying 4–8 flakiness tokens each
(`ConcurrentSubscriberTest`):

| group | predicted async wait or concurrency (run B) |
|---|---|
| 747 ordinary non-flaky tests | 51 = **6.8%** (base rate) |
| 4 token-stuffed decoys | 4 = **100%** |

In run B this is a much stronger statement than in run A. The model now calls 91.5% of non-flaky
tests non-flaky, so "these four were called flaky" is genuinely surprising rather than vacuous.
Under the base rate, 4/4 has probability ≈ 2.1e-05.

### What this establishes

**Surface features, not behaviour, drive this classifier — at two levels.** Tokens decide the
flaky *category* (both directions above, in both runs). Whitespace decides flaky *versus* non-flaky
(§4, §5). The paper's central claim about surface-feature reliance is correct, and understated.

### The labels file was right

`flaky-labels.json` sets `expectedFlakyLensCategory: "non-flaky"` on the 8 hidden-flaky adversarial
tests. Run A could not satisfy that — non-flaky was unreachable — and this document previously
suggested the labels file needed updating. **Retracted: run B hits it 8/8.** The prediction was
sound; run A's corpus formatting was what made it unreachable. No change to the labels file is
needed.

---

## 8. Per-package accuracy

| package | run A | run B |
|---|---|---|
| `com.cricket.core` (505) | 0/505 — 0.0% | 500/505 — 99.0% |
| `com.cricket.stats` (50) | 0/50 — 0.0% | 50/50 — 100.0% |
| `com.cricket.api` (92) | 1/92 — 1.1% | 68/92 — 73.9% |
| `com.cricket.od` (62) | 9/62 — 14.5% | 50/62 — 80.6% |
| `com.cricket.live` (45) | 11/45 — 24.4% | 16/45 — 35.6% |
| `com.cricket.time` (7) | 2/7 — 28.6% | 4/7 — 57.1% |
| `com.cricket.concurrent` (7) | 3/7 — 42.9% | 3/7 — 42.9% |
| `com.cricket.uc` (8) | 0/8 — 0.0% | 4/8 — 50.0% |
| `com.cricket.adversarial` (21) | 0/21 — 0.0% | 9/21 — 42.9% |

**Read with care.** Both columns are dominated by whichever way the non-flaky majority fell.
`com.cricket.core` is 505 non-flaky tests, so its 0% and 99% both restate §3 rather than saying
anything about the core package. `com.cricket.od`'s jump from 14.5% to 80.6% is the same effect —
its 50 non-flaky tests now land correctly while all 12 real OD tests are missed.

---

## 9. Limits

- **Folds 2–4 not run, and not worth running as they stand.** §4.4 probes all four checkpoints
  directly: every one returns 0/60 on cricket's non-flaky tests as written and 44–60/60 with a
  newline. A full run of each would cost ~19 min to reproduce the same effect. They become
  interesting only as a robustness check on run B, if the fold-1 flip needs corroborating.
- **Run B is a formatting fix, not a corpus fix.** It adds a trailing newline only. Indentation,
  blank lines and comments — the other three leaked cues — remain in the flaky configuration. A
  corpus matching the training format on all four might score differently again.
- **Comments were stripped.** `extract_tests.py` strips them by default. §4.1 shows comments are
  themselves a leaked class-5 marker, so this is a third instance of the same confound rather than
  an independent one. `--keep-comments` isolates it.
- **No FlakeBench-proportion run.** `artifacts/tests-flakebench.json` (`--ratio 0.034`) changes the
  class *balance*, not the formatting, so it would not change the qualitative result.
- **n = 4 for the decoys.** The p-value is small, but it rests on four tests.
- Nothing here measures whether cricket-tracker's flaky tests *are* flaky — that is
  `tools/rerun.py`'s job. This is only about what the classifier says.

---

## 10. Files in this folder

### `cricket-tracker-fold1/` — run A, as written

| file | what it is |
|---|---|
| `analysis.txt` | full output of `cricket-tracker/tools/analyze_flakylens.py`. **Start here.** |
| `joined-predictions.csv` | the per-test join: `fqn, clazz, method, package, truth, pred, adversarial, flaky` |
| `Finetuned_Result_with_tokens.csv` | FlakyLens' raw per-test output. Carries **no test name** — only the code — which is why the join exists. `Token_List` is blank (runbook §4.8). |
| `probe_format.py` / `probe-format.log` | the §4.3 probe and its output: method bodies held byte-identical, only indentation and the trailing newline varied, across cricket and paper rows in both directions |
| `probe_folds.py` / `probe-folds.log` | the §4.4 cross-checkpoint probe and its output |
| `BERT-FlakyLens_classification_report.txt` | sklearn precision/recall/F1 per class |
| `BERT-FlakyLens_confusion_matrix_val.txt` | sklearn confusion matrix |
| `per_Category_Evaluation_BERT.txt` | the artifact's own averaged per-category summary |
| `run-cricket.log` | the run log (19 min), after the attribution-guard patch; carries the `No attribution file ... recording predictions without token lists.` line confirming that branch fired |

### `cricket-tracker-fold1-newline/` — run B, one newline per test

Same file set: `analysis.txt`, `joined-predictions.csv`, `Finetuned_Result_with_tokens.csv`,
`BERT-FlakyLens_classification_report.txt`, `BERT-FlakyLens_confusion_matrix_val.txt`,
`per_Category_Evaluation_BERT.txt`, `run-cricket-newline.log`.

### `control-paper-fold1/`

| file | what it is |
|---|---|
| `BERT-FlakyLens_classification_report.txt` | **the validation evidence** — the paper's own fold-1 data through the patched pipeline, matching their published numbers exactly |
| `BERT-FlakyLens_confusion_matrix_val.txt` | its confusion matrix; the true-non-flaky row is `[0,0,0,0,0,2164]`, a perfect diagonal |
| `control.log` | run log, 50 min for 2229 tests |

### Reproducing

Run B's corpus, from run A's:

```python
import pandas as pd
p = "FlakyLens/src/FlakyLens_Categorization_PerProject-Data/test_set_1.csv"
d = pd.read_csv(p)
d["full_code"] = d["full_code"].astype(str) + "\n"
d.to_csv(p, index=False)
```

Then the runbook's *Between runs* reset, and `bash /app/run-cricket-tracker.sh` in the container.
Analysis for either run:

```
cd cricket-tracker
python tools/analyze_flakylens.py \
  --pred ../flakylens-results/cricket-tracker-fold1-newline/Finetuned_Result_with_tokens.csv \
  --out /tmp/joined.csv
```

The script joins positionally against `artifacts/tests.json` and verifies the join by parsing the
method name out of `test_code` at every index — 0 mismatches across 797 rows in both runs — and by
asserting each row's `Ground_Truth` against the labels file. A name-based join is not possible: 13
method names repeat across classes in this suite.

The §4 leak can be re-derived from the shipped data without running anything:

```python
import pandas as pd
d = pd.read_csv("FlakyLens/FlakeBench/FlakeBench_dataset.csv")
tnl = d.full_code.astype(str).str.endswith("\n")
print(((d.category == 5) == tnl).mean())   # 1.0 across all 8574 rows
```
