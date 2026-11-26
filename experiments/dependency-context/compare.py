"""Compare the four completed arms. Run with the absolute live evidence directory."""
import json
import sys
from pathlib import Path


def summarize(path):
    data = json.loads(path.read_text())
    calls = data["calls"]
    results = data["results"]

    def tokens(field, group):
        return sum(call["usage"][field] for call in group)

    answers = [c for c in calls if not c["id"].endswith("-recognize")]
    return {
        "schedule_and_context": path.parent.name,
        "passed": sum(r["pass"] for r in results.values()),
        "checks": len(results),
        "calls": len(calls),
        "answer_input_tokens": tokens("prompt_tokens", answers),
        "input_tokens": tokens("prompt_tokens", calls),
        "completion_tokens": tokens("completion_tokens", calls),
        "total_tokens": tokens("total_tokens", calls),
        "summed_call_seconds": round(sum(c["seconds"] for c in calls), 2),
        "question_seconds": {q: round(r["seconds"], 2) for q, r in results.items()},
        "failed": [q for q, r in results.items() if not r["pass"]],
        "quote_correction": results["price"]["repaired"],
        "mutation": data["mutation"],
        "wake_order": [w["root"] for w in data["wake"]],
        "rejected_stale": data["rejected-stale"],
    }


if __name__ == "__main__":
    base = Path(sys.argv[1])
    print(json.dumps([summarize(base / f"{schedule}-{arm}" / "summary.json")
                      for schedule in ["serial", "overlap"]
                      for arm in ["full", "selected"]], indent=2))
