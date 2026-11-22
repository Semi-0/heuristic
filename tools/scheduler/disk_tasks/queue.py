#!/usr/bin/env python3
"""Immutable inbox producer. Only the Lain-managed runner mutates queue state."""

import argparse
import importlib.util
import json
import time
import uuid
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "scheduler_io", Path(__file__).with_name("io.py")
)
boundary = importlib.util.module_from_spec(spec)
spec.loader.exec_module(boundary)


def submit(root, task, op="enqueue", timeout=60, confirmed_message_id=None):
    root = Path(root)
    (root / "inbox").mkdir(parents=True, exist_ok=True)
    (root / "replies").mkdir(exist_ok=True)
    command = uuid.uuid4().hex
    boundary.save(
        root / "inbox" / f"{command}.json",
        {"op": op, "task": task, "confirmed-message-id": confirmed_message_id},
    )
    reply = root / "replies" / f"{command}.json"
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        if reply.exists():
            result = json.loads(reply.read_text())
            reply.unlink()
            return dict(result, command=command)
        time.sleep(0.05)
    return {
        "status": "pending",
        "command": command,
        "note": "Request remains on disk. Inspect its reply; do not invent a new task identity.",
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root")
    parser.add_argument(
        "task_file",
        help="JSON task: id, unit, deadline, thread, evidence; optional priority/message",
    )
    parser.add_argument(
        "--operation", choices=["enqueue", "reconcile"], default="enqueue"
    )
    parser.add_argument(
        "--confirmed-message-id",
        help="Reconcile only: message UUID independently verified as submitted",
    )
    args = parser.parse_args()
    if args.confirmed_message_id and args.operation != "reconcile":
        parser.error("--confirmed-message-id requires --operation reconcile")
    result = submit(
        args.root,
        json.loads(Path(args.task_file).read_text()),
        args.operation,
        confirmed_message_id=args.confirmed_message_id,
    )
    print(json.dumps(result))
    raise SystemExit(1 if result.get("error") else 0)
