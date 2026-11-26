"""Compare the four completed arms. Run with the absolute live evidence directory."""
import hashlib
import json
import sys
from pathlib import Path


def verify_traces(directory, calls):
    count = 0
    size = 0
    for call in calls:
        trace = call.get("thinking-trace")
        if trace is None:
            continue
        contents = (directory / trace["file"]).read_bytes()
        if len(contents) != trace["bytes"] or hashlib.sha256(contents).hexdigest() != trace["sha256"]:
            raise ValueError(f"Trace mismatch for {call['id']} in {directory}")
        count += 1
        size += len(contents)
    return {"files": count, "bytes": size}


def summarize(path):
    data = json.loads(path.read_text())
    calls = data["calls"]
    results = data["results"]

    def tokens(field, group):
        return sum(call["usage"][field] for call in group)

    answers = [c for c in calls if not c["id"].endswith("-recognize")]
    result = {
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

    if any("thinking-trace" in c for c in calls):
        result["traces"] = verify_traces(path.parent, calls)
        result["reasoning_tokens"] = sum(
            c["usage"].get("completion_tokens_details", {}).get("reasoning_tokens", 0) for c in calls
        )
        result["truncated_calls"] = [c["id"] for c in calls if c["finish-reason"] == "length"]
    return result


if __name__ == "__main__":
    base = Path(sys.argv[1])
    print(json.dumps([summarize(base / f"{schedule}-{arm}" / "summary.json")
                      for schedule in ["serial", "overlap"]
                      for arm in ["full", "selected"]], indent=2))
