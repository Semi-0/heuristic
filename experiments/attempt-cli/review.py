"""Audit paired prompts, feedback delivery and outcome denominators in a completed run."""
import json
import sys
from pathlib import Path


def read(path):
    return json.loads(path.read_text())


def assess(directory):
    # A teardown failure can occur after HTTP completion. Recover only what was recorded.
    if (directory / "result.json").exists():
        result = read(directory / "result.json")
    else:
        assert (directory / "cleanup-error.edn").exists()
        calls = [t for t in ("one", "two", "three", "four") if (directory / f"{t}-response.json").exists()]
        actions = [{"action": read(directory / f"{t}-response.json")["text"].split()[0]} for t in calls]
        for i, t in enumerate(calls[1:]):
            actions[i]["feedback"] = read(directory / f"{t}-request.json")[-1]["content"]
        result = {"calls": calls, "actions": actions, "verified": False}
    calls = result["calls"]
    actions = result["actions"]
    issues = []
    if (directory / "cleanup-error.edn").exists():
        issues.append("unverified cleanup; terminal action receipt unavailable")
    if (directory / "close.json").exists() and read(directory / "close.json").get("status") != "closed":
        issues.append("unverified cleanup")
    if len(calls) != len(set(calls)) or len(calls) > 4:
        issues.append("turn identity or budget")
    input_tokens = output_tokens = 0
    seconds = 0.0
    for i, turn in enumerate(calls):
        req = directory / f"{turn}-request.json"
        res = directory / f"{turn}-response.json"
        if not res.exists():
            issues.append(f"missing response {turn}")
            continue
        reply = read(res)
        if reply.get("http-status") != 200 or reply.get("finish-reason") != "stop" or reply.get("truncated?"):
            issues.append(f"incomplete HTTP response {turn}")
        usage = reply.get("usage", {})
        input_tokens += usage.get("prompt_tokens", 0)
        output_tokens += usage.get("completion_tokens", 0)
        seconds += res.stat().st_mtime - req.stat().st_mtime
        if i + 1 < len(calls):
            following = read(directory / f"{calls[i+1]}-request.json")
            expected = read(req) + [{"role": "assistant", "content": reply["text"]},
                                    {"role": "user", "content": actions[i]["feedback"]}]
            if following != expected:
                issues.append(f"feedback mismatch after {turn}")
    if len(actions) != len(calls) or not calls:
        issues.append("missing action or request")
    if (directory / "exception.json").exists():
        issues.append("driver exception")
    order = [a["action"] for a in actions]
    return {"arm": directory.name, "actions": order, "verified": result["verified"],
            "provider_calls": len(calls), "cli_calls": order.count("CLI"),
            "failed_submissions": sum(a["action"] == "SUBMIT" and not a.get("verified") for a in actions),
            "prompt_tokens": input_tokens, "completion_tokens": output_tokens,
            "transport_seconds_approx": round(seconds, 2), "issues": issues}


def review(folder):
    billing = []
    for fixture in ("price", "repair"):
        base, cli = [folder / (fixture + "-" + arm) for arm in ("base", "cli")]
        a, b = [read(p / "one-request.json") for p in (base, cli)]
        assert a[1:] == b[1:] and b[0]["content"].startswith(a[0]["content"] + " CLI python3 -c")
        if (cli / "result.json").exists() and (base / "result.json").exists():
            assert read(base / "result.json")["settings"] == read(cli / "result.json")["settings"]
        billing.extend(assess(p) for p in (base, cli))
    print(json.dumps(billing, indent=2))


if __name__ == "__main__":
    review(Path(sys.argv[1]))
