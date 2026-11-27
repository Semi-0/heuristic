"""Reconcile recorded costs and print result rows grouped by checkpoint and policy."""
import collections
import json
from pathlib import Path
import re
import statistics
import sys

groups = collections.defaultdict(list)
for directory in map(Path, sys.argv[1:]):
    for line in (directory/"scores.jsonl").read_text().splitlines():
        row=json.loads(line)
        tag=f"{row['size']}-{row['arm']}-{row['symbol']}"
        for n,call in enumerate(row["calls"]):
            raw=(directory/"bridge"/f"{tag}-{n}-flash-reply.edn").read_text()
            for key in ("completion_tokens","prompt_tokens"):
                assert int(re.search(":"+key+r" (\d+)",raw)[1])==call["usage"][key]
        if row.get("correct"):
            assert row["value"]==row["expected"]
        groups[row["size"],row["arm"]].append(row)

print("| Checkpoint | Policy | Correct reuse | Exact support + correct reuse | Mean seconds | Initial input tokens | Generated tokens | Flash calls |")
print("|---|---|---:|---:|---:|---:|---:|---:|")
for (size,arm),rows in groups.items():
    count=len(rows)
    generated=sum(c["usage"]["completion_tokens"] for r in rows for c in r["calls"])
    inputs=[r["calls"][0]["usage"]["prompt_tokens"] for r in rows]
    print(f"| {size} | {arm} | {sum(bool(r.get('correct') and r.get('reuse')) for r in rows)}/{count} | "
          f"{sum(bool(r['pass']) for r in rows)}/{count} | {statistics.mean(r['seconds'] for r in rows):.1f} | "
          f"{min(inputs):,}–{max(inputs):,} | {generated:,} | {sum(len(r['calls']) for r in rows)} |")
