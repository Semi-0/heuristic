"""Reconcile provider usage and report separately on each context policy."""

import json
from pathlib import Path
import re
import sys


def summarize(path):
    calls = [json.loads(line) for line in (path / "calls.jsonl").read_text().splitlines()]
    dispatched = [call for call in calls if "usage" in call]
    result = json.loads((path / "result.json").read_text())
    for call in dispatched:
        name = f"t{call['ticket']}-r{call['turn']}-flash-reply.edn"
        receipt = (path / "packets" / name).read_text()
        for metric in ("completion_tokens", "prompt_tokens"):
            match = re.search(rf":{metric} (\d+)", receipt)
            if not match or int(match[1]) != call["usage"][metric]:
                raise ValueError(f"{path.name}/{name} has inconsistent {metric}")

    usage = [call["usage"] for call in dispatched]
    return {
        "arm": path.name,
        "feature_gates": result["final"],
        "existing_tests_pass": result["regression_exit"] == 0,
        "milestones": result["milestones"],
        "project_commit": result["commit"],
        "calls": len(dispatched),
        "length_limited_calls": sum(call["finish-reason"] == "length" for call in dispatched),
        "seconds": sum(row["seconds"] for row in result["milestones"]),
        "model_seconds": sum(call["seconds"] for call in dispatched),
        "context_seconds": sum(call["context_seconds"] for call in dispatched),
        "prompt_tokens": sum(row["prompt_tokens"] for row in usage),
        "cached_prompt_tokens": sum(row.get("prompt_tokens_details", {}).get("cached_tokens", 0) for row in usage),
        "generated_tokens": sum(row["completion_tokens"] for row in usage),
        "largest_input": max(call["input"] for call in calls),
        "overflow": any(call.get("overflow", False) for call in calls),
        "compacted_dispatches": sum(call["compacted"] for call in dispatched),
    }


if __name__ == "__main__":
    root = Path(sys.argv[1])
    print(json.dumps([summarize(root / arm) for arm in ("full", "editor", "selected")], indent=2))
