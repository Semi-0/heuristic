#!/usr/bin/env python3
"""Single managed IO call. Lain owns queue traversal, clock and alert selection."""

import fcntl
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import uuid
from pathlib import Path


def load(path, default=None):
    try:
        return json.loads(path.read_text())
    except FileNotFoundError:
        return default


def save(path, value):
    tmp = path.with_name("." + path.name + "." + uuid.uuid4().hex)
    try:
        with tmp.open("w") as out:
            json.dump(value, out, indent=2)
            out.write("\n")
            out.flush()
            os.fsync(out.fileno())
        os.replace(tmp, path)
        fd = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)
    finally:
        tmp.unlink(missing_ok=True)


def receipt(task):
    unit = task["unit"]
    if not re.fullmatch(r"lain-test-[0-9a-f]{32}\.service", unit):
        raise ValueError("Invalid managed job identity")
    path = Path("/home/lain/.local/state/lain-watchdog/receipts") / (
        unit[10:-8] + ".json"
    )
    result = load(path)
    if result is not None:
        if result.get("token") != unit[10:-8]:
            raise ValueError("Receipt identity mismatch")
        return result
    status = subprocess.run(
        ["systemctl", "--user", "show", unit, "--property=ActiveState", "--value"],
        check=False,
        capture_output=True,
        text=True,
        timeout=10,
    )
    if status.returncode == 0 and status.stdout.strip() in ["inactive", "failed"]:
        return {
            "finished": True,
            "error": "Inactive job has no completion receipt; outcome unknown",
        }
    return None


def operate(op, args, root, send=subprocess.run):
    task = args[0]
    key = hashlib.sha256((task["id"] + "\0" + task["unit"]).encode()).hexdigest()
    folder = root / key
    folder.mkdir(parents=True, exist_ok=True)
    with (folder / "lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        state = load(folder / "state.json", {})
        if op == "confirm":
            message_id = str(uuid.UUID(args[1]))
            if state.get("delivery") not in ["sending", "needs-review", "submitted"]:
                raise ValueError("No delivery claim to reconcile")
            state.update(
                delivery="submitted",
                operator_confirmed_message_id=message_id,
                confirmed_at=time.time(),
            )
            save(folder / "state.json", state)
            return {"safe": True}
        if op == "reconcile":
            return {
                "safe": state.get("delivery") in [None, "submitted"]
                or bool(state.get("acknowledged")),
                "delivery": state.get("delivery", "none"),
            }
        if op == "observe":
            raw = receipt(task)
            value = {
                "terminal": bool(raw and raw.get("finished")),
                "acknowledged": bool(state.get("acknowledged")),
                "delivery_state": state.get("delivery", "none"),
                "receipt": raw,
            }
            save(
                folder / "observed.json",
                {"at": time.time(), "task": task, "value": value},
            )
            return value
        if op == "notify":
            kind = args[1]
            if not state.get("delivery"):
                state = {"delivery": "sending", "task": task, "kind": kind}
                save(folder / "state.json", state)
                message = task.get("message") or (
                    f"[Lain experiment scheduler] {task['id']}: {kind}. "
                    f"Read {task.get('evidence', str(folder))} and {folder}/notification.json. "
                    "Review the actual results and update heuristic docs. Do not restart jobs, "
                    "change kernel, commit or push. This is a status event, not an acceptance claim."
                )
                try:
                    result = send(
                        [
                            "codex",
                            "queue",
                            "--thread",
                            task["thread"],
                            "--message",
                            message,
                            "--resume",
                        ],
                        check=False,
                        capture_output=True,
                        text=True,
                        timeout=60,
                    )
                except Exception as error:
                    state["delivery"] = "needs-review"
                    state["error"] = str(error)
                    save(folder / "state.json", state)
                    raise
                delivery = (
                    "submitted"
                    if result.returncode == 0
                    and "warn" not in result.stderr.lower()
                    and re.search(
                        r"Queued message [0-9a-f-]+ for thread ", result.stdout
                    )
                    else "needs-review"
                )
                state["delivery"] = delivery
                save(
                    folder / "notification.json",
                    {
                        "exit": result.returncode,
                        "stdout": result.stdout,
                        "stderr": result.stderr,
                        "at": time.time(),
                        "job_receipt": receipt(task),
                    },
                )
                save(folder / "state.json", state)
            # A saved ambiguous claim is returned for review, never blindly repeated.
            return {
                "task": task["id"],
                "unit": task["unit"],
                "delivery": state["delivery"],
            }
        if op == "acknowledge":
            ack = args[1]
            if ack["task"] != task["id"] or ack["unit"] != task["unit"]:
                raise ValueError("Wrong task acknowledgment")
            if (
                ack.get("delivery") != "submitted"
                or state.get("delivery") != "submitted"
            ):
                raise ValueError(
                    "Submission failed or ambiguous; operator review required"
                )
            state["acknowledged"] = True
            save(folder / "state.json", state)
            return dict(ack, acknowledged=True)
        raise ValueError("Unknown IO capability")


if __name__ == "__main__":
    request = json.load(sys.stdin)
    print(json.dumps(operate(request["op"], request["args"], Path(sys.argv[1]))))
