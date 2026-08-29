"""Convert artifacts/tests.json into the CSV FlakyLens' inference loop reads.

FlakyLens does not read the dataset path it is handed on the command line. Its
Testing_per_project.py reads pre-split folds out of
src/FlakyLens_Categorization_PerProject-Data/, so pointing it at this suite means
writing test_set_<fold>.csv in that directory.

Columns, in the order the fold files use them:

    id,project,test_name,full_code,label,category

`category` is the integer the model predicts, `label` is its human-readable name.
`full_code` is the whole method -- @Test annotation, signature and body -- because
that is what the model was fine-tuned on. artifacts/tests.json keeps the signature
and the body apart, so they are reassembled here.

    python tools/to_flakylens_csv.py --out /path/to/FlakyLens/src/FlakyLens_Categorization_PerProject-Data/test_set_1.csv
    python tools/to_flakylens_csv.py --in artifacts/tests-flakebench.json --folds 1,2,3,4 --out-dir <that directory>
"""

import argparse
import csv
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

# cricket-tracker's category names -> FlakyLens' (label, category) pair.
# The six classes line up exactly; FlakyLens' integers come from its own fold files.
CATEGORY_MAP = {
    "async":       ("async wait", 0),
    "concurrency": ("concurrency", 1),
    "time":        ("time", 2),
    "uc":          ("unordered collections", 3),
    "od":          ("test order dependency", 4),
    "non-flaky":   ("non-flaky", 5),
}


def full_code(record):
    """Reassemble the method as FlakyLens expects to see it.

    The fold files carry the annotation and a four-space indented body, so match
    that: a body that arrives dedented would otherwise tokenise differently from
    everything the model was trained on.
    """
    body = record["body"].strip("\n")
    indented = "\n".join(
        ("    " + line) if line.strip() else line
        for line in body.splitlines()
    )
    return "@Test\n{sig} {{\n{body}\n}}".format(sig=record["signature"], body=indented)


def to_rows(tests, project):
    rows = []
    for index, record in enumerate(tests, start=1):
        category = record["category"]
        if category not in CATEGORY_MAP:
            sys.exit("unknown category %r on %s" % (category, record["fqn"]))
        label, code = CATEGORY_MAP[category]
        rows.append({
            "id": index,
            "project": project,
            # FlakyLens' own files use Class.method, not the fully qualified name.
            "test_name": "%s.%s" % (record["class"].rsplit(".", 1)[-1], record["method"]),
            "full_code": full_code(record),
            "label": label,
            "category": code,
        })
    return rows


def write(rows, path):
    os.makedirs(os.path.dirname(os.path.abspath(path)) or ".", exist_ok=True)
    with open(path, "w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(
            handle, fieldnames=["id", "project", "test_name", "full_code", "label", "category"])
        writer.writeheader()
        writer.writerows(rows)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="source", default=os.path.join(ROOT, "artifacts", "tests.json"))
    ap.add_argument("--out", help="write a single CSV here")
    ap.add_argument("--out-dir", help="write test_set_<fold>.csv here, for each --folds")
    ap.add_argument("--folds", default="1",
                    help="comma-separated fold numbers to write when --out-dir is used")
    ap.add_argument("--project", default="cricket-tracker",
                    help="value for the project column; FlakyLens groups folds by it")
    args = ap.parse_args()

    if not args.out and not args.out_dir:
        ap.error("give --out or --out-dir")

    with open(args.source, encoding="utf-8") as handle:
        payload = json.load(handle)
    tests = payload["tests"]
    rows = to_rows(tests, args.project)

    counts = {}
    for row in rows:
        counts[row["label"]] = counts.get(row["label"], 0) + 1

    targets = []
    if args.out:
        targets.append(args.out)
    if args.out_dir:
        for fold in args.folds.split(","):
            targets.append(os.path.join(args.out_dir, "test_set_%s.csv" % fold.strip()))

    for target in targets:
        write(rows, target)
        print("wrote %d tests -> %s" % (len(rows), target))

    for label in sorted(counts, key=lambda name: CATEGORY_MAP_BY_LABEL[name]):
        print("  %-24s %s -> %d" % (label, CATEGORY_MAP_BY_LABEL[label], counts[label]))


CATEGORY_MAP_BY_LABEL = {label: code for label, code in CATEGORY_MAP.values()}


if __name__ == "__main__":
    main()
