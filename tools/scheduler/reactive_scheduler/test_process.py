"""Owned child-process crash/reopen test. Codex is a recording executable here."""

import json
import os
import signal
import subprocess
import sys
import time
from pathlib import Path

BASE = Path(__file__).parent
ROOT = Path(sys.argv[1]).resolve()
ROOT.mkdir(parents=True, exist_ok=True)
BIN = ROOT / "bin"
BIN.mkdir(exist_ok=True)
FAKE = BIN / "codex"
FAKE.write_text("""#!/usr/bin/python3
import json, os, sys, time
with open(os.environ['SEND_LOG'], 'a') as f:
    f.write(json.dumps(sys.argv)+"\\n")
if 'CRASH' in sys.argv[sys.argv.index('--message')+1]:
    time.sleep(60)
print('Queued message abc for thread synthetic.')
""")
FAKE.chmod(0o755)
ENV = dict(
    os.environ,
    PATH=str(BIN) + ":" + os.environ["PATH"],
    SEND_LOG=str(ROOT / "sends.jsonl"),
)
CONFIG = ROOT / "config.json"
CONFIG.write_text(
    json.dumps(
        {
            "queue-root": str(ROOT / "queue"),
            "io-root": str(ROOT / "io"),
            "poll-seconds": 0.2,
            "renew-seconds": 60,
            "max-active": 1,
        }
    )
)
UNIT = "lain-test-04863c7227364b97ba1cf778ed693b68.service"
PROCESSES = []


def wait(predicate, label, seconds=60):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if predicate():
            return
        time.sleep(0.1)
    raise AssertionError("Timeout: " + label)


def start(n):
    p = subprocess.Popen(
        ["bash", str(BASE / "run.sh"), str(CONFIG)],
        env=ENV,
        stdout=(ROOT / f"daemon-{n}.log").open("w"),
        stderr=subprocess.STDOUT,
        start_new_session=True,
    )
    PROCESSES.append(p)
    return p


def stop(p, crash=False):
    if p.poll() is None:
        os.killpg(p.pid, signal.SIGKILL if crash else signal.SIGTERM)
        p.wait(timeout=100)


def sends():
    path = ROOT / "sends.jsonl"
    return (
        [json.loads(line) for line in path.read_text().splitlines()]
        if path.exists()
        else []
    )


def count(area):
    return len(list((ROOT / "queue" / area).glob("*.json")))


def producer(task, n):
    file = ROOT / f"task-{n}.json"
    file.write_text(json.dumps(task))
    p = subprocess.Popen(
        [sys.executable, str(BASE / "queue.py"), str(ROOT / "queue"), str(file)],
        stdout=(ROOT / f"producer-{n}.json").open("w"),
        stderr=subprocess.STDOUT,
        start_new_session=True,
    )
    PROCESSES.append(p)
    return p


report = {}
try:
    tasks = [
        {
            "id": f"task-{n}",
            "unit": UNIT,
            "deadline": 9999999999,
            "thread": "synthetic",
            "priority": n,
            "message": f"SYNTHETIC {n}",
        }
        for n in range(6)
    ]
    writers = [producer(t, n) for n, t in enumerate(tasks + [tasks[0]] * 4)]
    wait(lambda: count("inbox") == 10, "producers published")
    daemon = start(1)
    for writer in writers:
        assert writer.wait(timeout=90) == 0
    wait(lambda: count("completed") == 6, "six completions", 90)
    assert len(sends()) == 6
    assert {argv[argv.index("--message") + 1] for argv in sends()} == {
        f"SYNTHETIC {n}" for n in range(6)
    }
    status = json.loads((ROOT / "queue" / "status.json").read_text())
    assert status["parent-receipts"] >= 5
    report["concurrent_producers"] = 10
    report["unique_deliveries"] = 6
    report["lain_ingress_deliveries"] = "passed"
    report["parent"] = {k: status[k] for k in ["parent-nodes", "parent-receipts"]}
    report["completed_bytes"] = sum(
        p.stat().st_size
        for area in ["completed", "archive"]
        for p in (ROOT / "queue" / area).glob("*.json")
    )
    stop(daemon)
    started = time.time() * 1000
    daemon = start(2)
    wait(
        lambda: (
            json.loads((ROOT / "queue" / "status.json").read_text())["at"] > started
        ),
        "fresh process",
    )
    time.sleep(1)
    assert len(sends()) == 6
    crash = dict(tasks[0], id="crash", message="CRASH synthetic boundary test")
    writer = producer(crash, "crash")
    assert writer.wait(timeout=30) == 0
    wait(lambda: len(sends()) == 7, "ambiguous send started")
    stop(daemon, crash=True)
    daemon = start(3)
    wait(lambda: count("blocked") == 1, "ambiguous delivery quarantined")
    time.sleep(1)
    assert len(sends()) == 7
    assert count("completed") == 6
    # Explicit operator reconciliation of the synthetic ambiguous send; never resend.
    reconcile = subprocess.run(
        [
            sys.executable,
            str(BASE / "queue.py"),
            str(ROOT / "queue"),
            str(ROOT / "task-crash.json"),
            "--operation",
            "reconcile",
            "--confirmed-message-id",
            "00000000-0000-0000-0000-000000000001",
        ],
        check=False,
        capture_output=True,
        text=True,
        timeout=60,
    )
    assert reconcile.returncode == 0, reconcile.stdout + reconcile.stderr
    wait(lambda: count("completed") == 7, "reconciled completion")
    assert len(sends()) == 7
    report["explicit_reconciliation_no_resend"] = "passed"
    report["fresh_process_replay"] = "passed"
    report["crash_during_send_no_resend"] = "passed"
    report["status"] = "passed"
except BaseException as error:
    report.update(status="failed", error=repr(error))
    raise
finally:
    for process in PROCESSES:
        stop(process)
    (ROOT / "result.json").write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps(report))
