# Running FlakyLens against cricket-tracker


FlakyLens is a fine-tuned CodeBERT classifier that puts a Java test method into one of six
categories (five kinds of flakiness plus non-flaky). cricket-tracker is the demo subject program
in this workspace: 797 tests, 46 of them deliberately flaky, with ground truth in
`cricket-tracker/artifacts/flaky-labels.json`.

The goal is to feed cricket-tracker's 797 test methods to FlakyLens' pre-trained model and compare
its predictions against that ground truth.

The interesting part of the result is not the aggregate F1. It is the `com.cricket.adversarial`
package, which is built to break the paper's own central finding: 8 genuinely flaky tests whose
waits are hidden behind domain-named helpers (`day.drinks()`) and carry no flakiness vocabulary,
and 4 deterministic tests stuffed with concurrency vocabulary that pass 40 runs out of 40.

---

## The machine we tested on

Every change below exists because of something on this list. On different hardware some of them
are unnecessary — each section says which.

| | |
|---|---|
| OS | Windows 11 Home Single Language, 10.0.26200 |
| GPU | **AMD Radeon 610M — no NVIDIA GPU, no CUDA** (`nvidia-smi` absent) |
| Docker | 27.5.1, Docker Desktop with the Linux/WSL2 engine |
| Host Python | 3.13.4 only (no conda, no 3.8–3.11 available via `py --list`) |
| Host packages | pandas 3.0.3, numpy 2.5.1; **torch, transformers, scikit-learn, captum all absent** |
| Java | Temurin OpenJDK 11.0.32+9 |


---

## Changes we made

Nine in total: four edits to files in the upstream FlakyLens clone, five new files that add
nothing to and change nothing in the artifact's own code.

| # | File | Change | Needed because | § |
|---|---|---|---|---|
| 1 | `FlakyLens/src/utils.py:462` | CUDA → auto-detect device | no NVIDIA GPU | 4.1 |
| 2 | `FlakyLens/src/Testing_per_project.py:555` | `torch.load` map_location | GPU-saved checkpoints on a CPU box | 4.2 |
| 3 | `FlakyLens/Dockerfile:41` | conda-forge instead of Anaconda defaults | conda ToS gate breaks the build | 4.3 |
| 4 | `FlakyLens/Dockerfile:62` | CPU-only torch wheel | avoids ~2.5GB of unused CUDA wheels | 4.4 |
| 5 | `FlakyLens/.dockerignore` *(new)* | exclude `models/`, `.git/`, … | build context 2.0GB → ~90MB | 4.5 |
| 6 | `FlakyLens/.gitattributes` *(new)* + the eight `*.sh` | CRLF → LF line endings | bash in Linux rejects `\r` | 4.6 |
| 7 | `FlakyLens/src/Testing_per_project.py:572-599` | record predictions without an attribution file | per-test rows were silently dropped | 4.8 |
| 8 | `FlakyLens/run-cricket-tracker.sh` *(new)* | container entry point | PowerShell mangles inline `bash -c` | Step 5 |
| 9 | `cricket-tracker/tools/{to_flakylens_csv,analyze_flakylens}.py` *(new)* | corpus conversion and result analysis | schemas do not match; output has no test names | 4.7, Step 6 |

Changes 1, 2 and 7 are the only ones that touch the artifact's Python. **None of them alters a
prediction** — verified twice: change 1–2 by the control run in §6, change 7 by a
byte-identical classification report before and after.

### Hardcoded CUDA device

**Symptom:** `RuntimeError: Found no NVIDIA driver on your system.`

**Cause:** `src/utils.py:462`, inside `init_setup()`:

```python
device = torch.device("cuda")
#device = torch.device("cpu")
```

**Fix:**

```python
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
```

Auto-detecting rather than hardcoding CPU keeps the file correct on a GPU machine.

**Not a concern:** `utils.py` has six other `.cuda()` calls, but all of them are inside the
Qwen/Gemma/CodeLlama/DeepSeek model-define functions used only by `Testing_other_LLMs.py` (RQ2).
The BERT path never touches them. `torch.cuda.empty_cache()` is a documented no-op when CUDA was
never initialised, so those calls are harmless too.

### Loading GPU checkpoints on a CPU

**Symptom:** `RuntimeError: Attempting to deserialize object on a CUDA device but
torch.cuda.is_available() is False.`

**Cause:** the published weights were saved on an RTX A5000, and `Testing_per_project.py:555`
loaded them with no `map_location`.

**Fix:**

```python
model.load_state_dict(torch.load(
    model_weights_path+'_project_group_'+str(project_group)+'.pt',
    map_location=device, weights_only=False))
```

`weights_only=False` is belt-and-braces: torch 2.3 (what we pin) still defaults it to `False`, but
torch 2.6+ flipped the default to `True`, which would reject these checkpoints. Stating it keeps
the line working if anyone unpins torch. It does mean the checkpoint is unpickled without
restriction — fine for files you fetched yourself from the authors' UT Box link, not something to
point at an untrusted `.pt`.

### conda Terms-of-Service gate

**Symptom:** build dies at step 4/8:

```
CondaToSNonInteractiveError: Terms of Service have not been accepted for the
following channels. Please accept or remove them before proceeding:
    - https://repo.anaconda.com/pkgs/main
    - https://repo.anaconda.com/pkgs/r
```

**Cause:** not a bug in the artifact. The Dockerfile installs `Miniconda3-latest`, and current
conda releases refuse to use Anaconda's `defaults` channels until their ToS is accepted
interactively. A `docker build` has no way to answer the prompt. The Dockerfile simply aged into a
policy change.

**Fix** — build the environment from conda-forge, which carries Python 3.8.5 and has no such gate:

```dockerfile
RUN conda config --system --remove-key channels || true && \
    conda config --system --add channels conda-forge && \
    conda config --system --set channel_priority strict && \
    conda create -n flakylens python=3.8.5 -y --override-channels -c conda-forge && \
    conda clean --all -f -y
```

We chose this over accepting the ToS because Anaconda's terms require a paid licence for
organisations above 200 people, and that is not a decision to bury in a Dockerfile. Only Python
itself comes from conda; every real dependency still comes from `requirements.txt` via pip, so the
pinned versions are untouched.

**If you would rather keep the upstream channels**, revert the block and insert this before
`conda create`:

```dockerfile
RUN conda tos accept --override-channels --channel https://repo.anaconda.com/pkgs/main && \
    conda tos accept --override-channels --channel https://repo.anaconda.com/pkgs/r
```

### pip pulling the CUDA build of torch

**Symptom:** step 7/8 (`pip install -r requirements.txt`) runs for a very long time.

**Cause:** the default PyPI wheel for `torch>=2.3.0,<2.4.0` on Linux is the CUDA build, which
drags in the whole `nvidia-*` runtime family (cudnn, cublas, cusparse, nccl, …) — roughly 2.5GB
downloaded and then never used on a machine with no NVIDIA GPU.

**Fix** — install torch from PyTorch's CPU index first, so `requirements.txt` afterwards finds its
pin already satisfied:

```dockerfile
RUN /opt/conda/envs/flakylens/bin/pip install --upgrade pip && \
    /opt/conda/envs/flakylens/bin/pip install --no-cache-dir \
        --index-url https://download.pytorch.org/whl/cpu "torch>=2.3.0,<2.4.0" && \
    /opt/conda/envs/flakylens/bin/pip install --no-cache-dir -r requirements.txt
```

**On an NVIDIA machine, delete the middle line.** `requirements.txt` alone then installs the CUDA
build, and change 4.1 picks the GPU up automatically.

### Docker build context

`Dockerfile` does `COPY . .`, and `models/` is 1.9GB of checkpoints, so every rebuild shipped a
2.0GB context. We added `.dockerignore` excluding `models/`, `.git/`, `FlakeBench/`,
`oopsla2025_results/`, `__pycache__/` and `*.pt`, taking it to ~90MB.

This is safe **because we bind-mount the working tree over `/app` at run time**, which shadows
whatever `COPY` baked in. The image only needs to carry the conda environment. If you ever run the
container *without* the bind mount, the excluded files will not be inside it.

### CRLF line endings in the shell scripts

**Symptom:** the run dies in under a second with

```
per_project_prediction.sh: line 7: $'\r': command not found
per_project_prediction.sh: line 18: syntax error near unexpected token `elif'
```

**Cause:** this clone was made on Windows with `core.autocrlf=true`, so git rewrote every checked-out
file to CRLF — including the bash scripts. That is invisible on Windows, but the scripts are
bind-mounted into a Linux container, where bash reads the trailing `\r` as part of each command.

**Beware of how you check for this.** Git Bash's `grep` silently normalises CR, so
`grep -q $'\r' file` reports *clean* on a file that is entirely CRLF. Check the bytes instead:

```bash
python -c "d=open('src/per_project_prediction.sh','rb').read(); \
print('CRLF:', d.count(b'\r\n'), 'bare LF:', d.count(b'\n')-d.count(b'\r\n'))"
```

Before the fix this printed `CRLF: 35 bare LF: 0`.

**Fix** — pin the shell scripts to LF via `.gitattributes`, then convert the eight in place:

```bash
cd "D:/New folder/Demo/FlakyLens"
printf '*.sh text eol=lf\n' > .gitattributes
python -c "
import glob
for pat in ('src/*.sh','results/scripts/*.sh','testscripts/*.sh'):
    for f in glob.glob(pat):
        d = open(f,'rb').read()
        if b'\r\n' in d: open(f,'wb').write(d.replace(b'\r\n', b'\n'))
"
```

**Do not fix this by setting `core.autocrlf false` alone** — we tried that first and it makes
things worse. With `autocrlf` off, git stops normalising on read, so every CRLF `.py` file in the
working tree suddenly differs from its LF blob in *every line*: `git diff` reported ~3,000 changed
lines across `utils.py` and `Testing_per_project.py` and became useless for reviewing real edits.
`.gitattributes` scopes the change to `.sh` and leaves `core.autocrlf=true` doing its normal job,
which keeps the diff at the ~80 lines we actually changed.

This affects `src/{per_project_prediction,rq1,rq2,rq3,rq4,runAll}.sh` and
`results/scripts/{parse_result,per_category_parse_result}.sh`. The `.py` files do not need it —
Python reads CRLF fine — and neither do the CSVs.

The same trap applies to **TSVD4J and FlakeSync**, whose `.sh` scripts came from the same kind of
Windows clone. Check them before blaming their tooling.

### The CSV converter

`cricket-tracker/artifacts/tests.json` and FlakyLens' fold files do not have the same shape, so
`cricket-tracker/tools/to_flakylens_csv.py` bridges them.

FlakyLens fold files carry `id,project,test_name,full_code,label,category`, where `category` is the
integer the model predicts and `full_code` is the entire method including its `@Test` annotation,
indented four spaces. `tests.json` stores the signature and the body separately and dedents the
body, so the converter reassembles the method — matching the training-time formatting matters,
because a dedented body tokenises differently from everything the model saw in training.

The six classes map one-to-one, which is why this is a clean conversion and not an approximation:

| cricket-tracker | FlakyLens `label` | `category` |
|---|---|---|
| `async` | async wait | 0 |
| `concurrency` | concurrency | 1 |
| `time` | time | 2 |
| `uc` | unordered collections | 3 |
| `od` | test order dependency | 4 |
| `non-flaky` | non-flaky | 5 |

---

### Per-test predictions are gated on an attribution file

`Finetuned_Result_with_tokens.csv` — the per-test prediction rows, the only output that lets you
inspect individual tests — is written inside

```python
if os.path.exists(attribution_csvfile_name):   # Testing_per_project.py:572
```

where that name resolves to `Attributions_scores/FlakyLens_attributions_project_group_<n>.csv`.
On a fold-1 run that file was absent, so the run produced the classification report and confusion
matrix and silently dropped every per-test row.

**The cause is local, not upstream.** The file *is* tracked in git — `git status` shows it as
` D` (deleted from the working tree). It was already missing when we started; the artifact does
ship it. `git checkout -- src/Attributions_scores/FlakyLens_attributions_project_group_1.csv`
restores it.

**But do not restore it when running your own data.** Those token lists are computed for the
*paper's* 2229-row fold 1. Restore the file while `test_set_1.csv` holds cricket-tracker's 797
tests and the lengths disagree, so the paper's tokens get attached to cricket tests — silently
wrong output rather than a crash.

**What we did instead** — patched the guard so a missing attribution file falls back to blank
token lists and the prediction row is still recorded:

```python
else:
    print("No attribution file at %s -- recording predictions without token lists." % ...)
    token_list = [""] * len(X_test)
```

plus two robustness fixes in the same block: the neighbouring `token_list = []` branch (a length
mismatch waiting to fire) now also pads to `len(X_test)`, there is an explicit length guard before
the DataFrame is built, and `test_code` is `reset_index(drop=True)`-ed so a non-trivial Series
index cannot misalign the columns. Predictions never depended on the token list — it is only an
extra column — so this changes no result. Confirmed by the log line
`No attribution file at ... -- recording predictions without token lists.`

The alternative, using fold 2 where the file exists, needs no code change but uses a different
checkpoint and would still pair the paper's tokens with your tests.

## The process

### Step 0 — Weights

Download the four checkpoints from the UT Box link in `FlakyLens/models/README.md` into
`FlakyLens/models/`. The names must be exactly:

```
per_project_model_weights_on__dataset_project_group_1.pt      (~500MB each)
per_project_model_weights_on__dataset_project_group_2.pt
per_project_model_weights_on__dataset_project_group_3.pt
per_project_model_weights_on__dataset_project_group_4.pt
```

Note the **double underscore** in `on__dataset`. That string is assembled from
`per_project_prediction.sh` (which interpolates an unset `${dataset}` variable) plus
`Testing_per_project.py:555`. A rename breaks the load.

### Step 1 — Build the image

```powershell
cd "D:\New folder\Demo\FlakyLens"
docker build -t flakylens:latest .
```

The `nvidia/cuda` base image runs fine without a GPU — it is Ubuntu plus CUDA libraries. **Omit
`--gpus all` when you run it.**

### Step 2 — Generate the corpus

No Maven build is needed; `extract_tests.py` parses test *sources*.

```powershell
cd "D:\New folder\Demo\cricket-tracker"
python tools\extract_tests.py --out artifacts\tests.json
```

Expect `tests: 797  flaky: 46 (5.77%)` — check this against `flaky-labels.json` rather than
assuming, since that file gains entries as measurements come in. Comments are stripped by default and should stay stripped —
several test bodies say outright what makes them flaky, which would hand the classifier the answer.

### Step 3 — Back up the paper's data, then convert

**`test_set_*.csv` are published evaluation data and cannot be regenerated from this tree.**

```powershell
cd "D:\New folder\Demo\FlakyLens\src\FlakyLens_Categorization_PerProject-Data"
Copy-Item test_set_1.csv test_set_1.csv.orig
Copy-Item ..\FlakyLens_Categorization_PerProject-result\Finetuned_Result_with_tokens.csv `
          ..\FlakyLens_Categorization_PerProject-result\Finetuned_Result_with_tokens.csv.orig

cd "D:\New folder\Demo\cricket-tracker"
python tools\to_flakylens_csv.py --out "..\FlakyLens\src\FlakyLens_Categorization_PerProject-Data\test_set_1.csv"
```

To have all four fold models score cricket-tracker instead:

```powershell
python tools\to_flakylens_csv.py --out-dir "..\FlakyLens\src\FlakyLens_Categorization_PerProject-Data" --folds 1,2,3,4
```

### Step 4 — Optionally restrict to one fold

Folds 2–4 would otherwise score the paper's data. `read_data` pulls training data from
`data_split/`, so the top-level `train_set_*.csv` files only determine the fold *count* — moving
them aside reduces the run to a single fold:

```powershell
cd "D:\New folder\Demo\FlakyLens\src\FlakyLens_Categorization_PerProject-Data"
New-Item -ItemType Directory _parked
Move-Item test_set_2.csv,test_set_3.csv,test_set_4.csv,train_set_2.csv,train_set_3.csv,train_set_4.csv _parked
```

### Between runs — reset

**Do this before every run.** Several outputs are append-mode and several are the artifact's own
tracked files, so consecutive runs otherwise stack onto each other and onto the paper's shipped
data. Symptoms are subtle: an old fold's numbers sitting above the new ones in a report you then
misread as current.

```bash
cd "D:/New folder/Demo/FlakyLens"

# 1. restore the artifact's own files that a run overwrites or appends to
git checkout -- results/per_Category_Evaluation_BERT-FlakyLens.txt \
  src/FlakyLens_Categorization_PerProject-Data/X_test_project_group1_Most_important_features.csv \
  src/FlakyLens_Categorization_PerProject-result/Finetuned_Result_with_tokens.csv

# 2. delete the outputs a run creates from scratch
rm -f results/per_Category_Evaluation_BERT.txt \
      results/weighted_avg_for_cv_BERT-FlakyLens.txt \
      src/FlakyLens_Categorization_PerProject-result/BERT-FlakyLens_classification_report.txt \
      src/FlakyLens_Categorization_PerProject-result/BERT-FlakyLens_confusion_matrix_val.txt
```

Then check `git status -- results/ src/FlakyLens_Categorization_PerProject-*/` shows only the
intended fold swap. Do **not** `git checkout` `src/Attributions_scores/` — see §4.8.

**And rebuild the corpus, do not reuse it.** `artifacts/flaky-labels.json` is edited as new
measurements come in, and a `tests.json` built before such an edit silently carries stale ground
truth. This bit us: a run was scored against a corpus in which
`LiveScoreIngestTest#everyDeliveryIsAppliedAfterIngest` was still unlabelled, so an async-wait test
counted as non-flaky. Always re-run both steps together:

```powershell
cd "D:\New folder\Demo\cricket-tracker"
python tools\extract_tests.py --out artifacts\tests.json
python tools\to_flakylens_csv.py --out "..\FlakyLens\src\FlakyLens_Categorization_PerProject-Data\test_set_1.csv"
```

and check the printed class counts against `flaky-labels.json` before trusting a result.

### Step 5 — Run

First, once per machine, create the HuggingFace cache volume:

```powershell
docker volume create flakylens-hf
```

FlakyLens downloads `microsoft/codebert-base` (~477MB) at startup. Because the container runs
`--rm`, that download is discarded on exit and **re-fetched on every run** unless it is cached
outside the container. Use a *named volume*, not a bind mount: the HF cache links `snapshots/` to
`blobs/` through symlinks (6 of them for this model), which Windows bind mounts handle poorly.

Then run:

```powershell
docker run --rm `
  -v "D:\New folder\Demo\FlakyLens:/app" `
  -v flakylens-hf:/root/.cache/huggingface `
  -v "<somewhere for logs>:/logs" `
  flakylens:latest bash /app/run-cricket-tracker.sh
```

`run-cricket-tracker.sh` is ours, not upstream. It activates the conda env, sets
`PYTHONUNBUFFERED=1`, runs the pipeline and tees to `/logs/run.log`. Three reasons it exists:

- **Inline `bash -c '...'` from PowerShell does not survive argument mangling.** Our first attempt
  exited immediately having run only the first `echo`. Keep the body in a file.
- **Without `PYTHONUNBUFFERED=1` the log stays empty until the run ends**, because python
  block-buffers stdout when redirected. A half-hour run looks hung.
- It records the resolved device and fold list at the top of the log, which is what you want when
  a result looks wrong.

To run it interactively instead:

```powershell
docker run -it --rm -v "D:\New folder\Demo\FlakyLens:/app" `
  -v flakylens-hf:/root/.cache/huggingface flakylens:latest
# then, inside:
cd /app/src && bash per_project_prediction.sh FlakyLens "BERT"
```

Two cautions:

- **Do not use `rq1.sh`.** It parses the per-category output and then `rm`s it.
- **Do not pass `calculate_gradient`** (RQ3 attributions). That path calls
  `torch.cuda.amp.autocast()`, which is not viable here.

### Step 6 — Read the results

In `src/FlakyLens_Categorization_PerProject-result/`:

| File | Contents |
|---|---|
| `Finetuned_Result_with_tokens.csv` | per-test predictions — **the file you want** |
| `BERT-FlakyLens_classification_report.txt` | precision/recall/F1 per class, per fold |
| `BERT-FlakyLens_confusion_matrix_val.txt` | confusion matrix per fold |

In `results/`: `per_Category_Evaluation_BERT-FlakyLens.txt` (per fold) and
`per_Category_Evaluation_BERT.txt` (averaged). The two names differ because `run_experiment` writes
the averaged file using `ml_technique` (`"BERT"`) while `parse_cr` uses the full technique string
(`"BERT-FlakyLens"`).

**Both `BERT-FlakyLens_*` files and `per_Category_Evaluation_BERT-FlakyLens.txt` are opened in
append mode.** A second run adds to them rather than replacing them, so an old run's numbers sit
above the new ones and are easy to misread as the current result. Delete them between runs.

### Joining predictions to ground truth

`Finetuned_Result_with_tokens.csv` has columns
`project_group, test_code, Prediction, Ground_Truth, Token_List` — **no test name**, only the code.
Use the analysis script, which handles the join and reports the adversarial breakdown:

```powershell
cd "D:\New folder\Demo\cricket-tracker"
python tools\analyze_flakylens.py `
  --pred "..\FlakyLens\src\FlakyLens_Categorization_PerProject-result\Finetuned_Result_with_tokens.csv" `
  --out joined-predictions.csv
```

It joins **positionally** against `artifacts/tests.json` — exact, because `to_flakylens_csv.py`
writes the fold file in that order and the model preserves row order — and verifies the join two
ways: parsing the method name out of `test_code` at every index, and asserting each row's
`Ground_Truth` against the labels file. It refuses to report if either check fails.

Do not attempt a join on method name: **13 method names repeat across classes** in this suite.

---

