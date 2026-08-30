"""Does surface formatting alone decide FlakyLens' non-flaky class?

Not part of the artifact. Holds a test method's CONTENT fixed and varies only
leading indentation and the trailing newline -- the two surface features that
separate class 5 from classes 0-4 with 100% accuracy in FlakyLens' own splits.

Run inside the container:  python3 -W ignore /app/probe_format.py
"""
import os, sys, json
import numpy as np
import pandas as pd
import torch
import torch.nn.functional as F

sys.path.insert(0, "/app/src")
os.chdir("/app/src")

from utils import codebert_model_define
from codebert_model import BERT_Arch

DATA = "FlakyLens_Categorization_PerProject-Data"
WEIGHTS = "../models/per_project_model_weights_on__dataset_project_group_1.pt"
NAMES = ["async", "conc", "time", "uc", "od", "NONFLAKY"]
N = 40

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print("device", device, flush=True)
_, tokenizer, auto_model = codebert_model_define()
model = BERT_Arch(auto_model, 6)
model.load_state_dict(torch.load(WEIGHTS, map_location=device, weights_only=False))
model.to(device); model.eval()


def predict(code):
    t = tokenizer.batch_encode_plus([code], max_length=512, pad_to_max_length=True, truncation=True)
    seq = torch.tensor(t["input_ids"]).to(device).long()
    mask = torch.tensor(t["attention_mask"]).to(device).long()
    with torch.no_grad():
        out = model(seq, mask)
    p = F.softmax(out.detach().cpu().float(), dim=1).numpy()[0]
    return int(p.argmax()), p


def indent(code, pad="    "):
    return "\n".join(pad + ln if ln.strip() else ln for ln in code.split("\n"))


def dedent(code):
    return "\n".join(ln.lstrip() if ln.strip() else ln for ln in code.split("\n"))


def run(tag, codes, truth):
    hits = {}
    rows = []
    for c in codes:
        k, p = predict(c)
        hits[k] = hits.get(k, 0) + 1
        rows.append((k, float(p[k]), float(p[5])))
    n = len(codes)
    dist = " ".join("%s=%d" % (NAMES[k], v) for k, v in sorted(hits.items()))
    n5 = hits.get(5, 0)
    print("%-42s n=%d  nonflaky=%3d/%d (%5.1f%%)  mean p(nonflaky)=%.3f   %s"
          % (tag, n, n5, n, 100.0 * n5 / n, np.mean([r[2] for r in rows]), dist), flush=True)
    return rows


cricket = pd.read_csv(DATA + "/test_set_1.csv")
paper = pd.read_csv(DATA + "/test_set_1.csv.orig")

cn = cricket[cricket.category == 5].full_code.head(N).tolist()   # cricket non-flaky, col-0, no trailing \n
cf = cricket[cricket.category != 5].full_code.head(N).tolist()   # cricket flaky
pn = paper[paper.category == 5].full_code.head(N).tolist()       # paper non-flaky, indented + trailing \n
pf = paper[paper.category != 5].full_code.head(N).tolist()       # paper flaky, col-0, no trailing \n

print("\n=== BASELINES (content and formatting both authentic) ===")
run("cricket non-flaky, as written", cn, 5)
run("cricket flaky, as written", cf, None)
run("paper non-flaky, as written", pn, 5)
run("paper flaky, as written", pf, None)

print("\n=== CRICKET NON-FLAKY: content fixed, formatting varied ===")
run("A col-0, no trailing NL  (as written)", cn, 5)
run("B + trailing newline only", [c + "\n" for c in cn], 5)
run("C + 4-space indent only", [indent(c) for c in cn], 5)
run("D + indent AND trailing newline", [indent(c) + "\n" for c in cn], 5)
run("E + 2-space indent AND trailing NL", [indent(c, "  ") + "\n" for c in cn], 5)
run("F + tab indent AND trailing NL", [indent(c, "\t") + "\n" for c in cn], 5)

print("\n=== REVERSE: paper non-flaky, formatting stripped ===")
run("A as written (indent + trailing NL)", pn, 5)
run("B trailing newline removed only", [c.rstrip("\n") for c in pn], 5)
run("C indent removed only", [dedent(c) for c in pn], 5)
run("D both removed", [dedent(c).rstrip("\n") for c in pn], 5)

print("\n=== CONTROL: cricket GENUINELY FLAKY given non-flaky formatting ===")
run("A as written", cf, None)
run("D indent + trailing newline", [indent(c) + "\n" for c in cf], None)

print("\n=== CONTROL: paper flaky given non-flaky formatting ===")
run("A as written", pf, None)
run("D indent + trailing newline", [indent(c) + "\n" for c in pf], None)
