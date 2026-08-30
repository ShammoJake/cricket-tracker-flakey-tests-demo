"""Do the fold-2/3/4 checkpoints behave any differently on cricket-tracker?

Answers 'is it worth running folds 2-4' without a 19-minute run each: score a
sample of cricket tests under every checkpoint, as written and with a single
trailing newline added.
"""
import os, sys
import numpy as np, pandas as pd, torch, torch.nn.functional as F

sys.path.insert(0, "/app/src"); os.chdir("/app/src")
from utils import codebert_model_define
from codebert_model import BERT_Arch

DATA = "FlakyLens_Categorization_PerProject-Data"
NAMES = ["async", "conc", "time", "uc", "od", "NONFLAKY"]
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
_, tokenizer, auto_model = codebert_model_define()

c = pd.read_csv(DATA + "/test_set_1.csv")
nf = c[c.category == 5].full_code.head(60).tolist()
fl = c[c.category != 5].full_code.head(25).tolist()

def predict(model, code):
    t = tokenizer.batch_encode_plus([code], max_length=512, pad_to_max_length=True, truncation=True)
    with torch.no_grad():
        out = model(torch.tensor(t["input_ids"]).to(device).long(),
                    torch.tensor(t["attention_mask"]).to(device).long())
    p = F.softmax(out.detach().cpu().float(), dim=1).numpy()[0]
    return int(p.argmax()), float(p[5])

def run(model, tag, codes):
    ks = [predict(model, x) for x in codes]
    hits = {}
    for k, _ in ks: hits[k] = hits.get(k, 0) + 1
    print("   %-38s n=%d nonflaky=%3d (%5.1f%%) mean p5=%.3f  %s"
          % (tag, len(ks), hits.get(5, 0), 100.0*hits.get(5,0)/len(ks),
             np.mean([p for _, p in ks]),
             " ".join("%s=%d" % (NAMES[k], v) for k, v in sorted(hits.items()))), flush=True)

for fold in (1, 2, 3, 4):
    model = BERT_Arch(auto_model, 6)
    model.load_state_dict(torch.load(
        "../models/per_project_model_weights_on__dataset_project_group_%d.pt" % fold,
        map_location=device, weights_only=False))
    model.to(device); model.eval()
    print("FOLD %d" % fold, flush=True)
    run(model, "cricket non-flaky, as written", nf)
    run(model, "cricket non-flaky, + trailing newline", [x + "\n" for x in nf])
    run(model, "cricket flaky, as written", fl)
    del model
