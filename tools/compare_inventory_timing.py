"""Compare saved provider-call intervals; makes no provider requests.

Context is written immediately before transport invocation; response is written
immediately after it returns. File mtimes approximate provider round-trip latency,
including transport and small file-write overhead, not pure reasoning time.
"""
import argparse
import json
import statistics
from pathlib import Path


def timings(root):
    rows = []
    for trial in range(1, 4):
        directory = root / f"trial-{trial}"
        rounds = []
        for question in (1, 2):
            start = directory / f"context-{question}.txt"
            end = directory / f"response-{question}.edn"
            start_ns, end_ns = start.stat().st_mtime_ns, end.stat().st_mtime_ns
            if end_ns < start_ns:
                raise ValueError(f"Reversed timestamps: {directory}, question {question}")
            rounds.append({"question": question, "context_path": str(start),
                           "response_path": str(end), "context_mtime_ns": start_ns,
                           "response_mtime_ns": end_ns,
                           "seconds": (end_ns - start_ns) / 1e9,
                           "source_characters": len((directory / f"generated-{question}.lain").read_text())})
        rows.append({"trial": trial, "rounds": rounds,
                     "total_provider_seconds": sum(r["seconds"] for r in rounds)})
    return {"trials": rows,
            "mean_question_seconds": [statistics.mean(r["rounds"][q]["seconds"] for r in rows) for q in (0, 1)],
            "mean_total_seconds": statistics.mean(r["total_provider_seconds"] for r in rows),
            "median_total_seconds": statistics.median(r["total_provider_seconds"] for r in rows)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("previous", type=Path)
    parser.add_argument("lookback", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    previous, lookback = timings(args.previous), timings(args.lookback)
    result = {"measurement": "Approximate provider round-trip latency from original file timestamps; excludes startup, admission, and mutation checks.",
              "limits": "Three trials per condition, not randomized or interleaved. Server load, caching, output length, and other timing variation are not controlled. This is not pure reasoning time or a causal speed estimate.",
              "previous": previous, "lookback": lookback,
              "mean_total_change_percent": 100 * (lookback["mean_total_seconds"] / previous["mean_total_seconds"] - 1)}
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({k: result[k] for k in ("measurement", "mean_total_change_percent")}, indent=2))
    for name in ("previous", "lookback"):
        print(name, {k: v for k, v in result[name].items() if k != "trials"})
