import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location(
    "boundary", Path(__file__).with_name("io.py")
)
IO = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(IO)


class DeliveryTests(unittest.TestCase):
    def test_ack_and_replay(self):
        with (
            tempfile.TemporaryDirectory() as d,
            patch.object(IO, "receipt", return_value={"finished": True}),
        ):
            task = {"id": "test", "unit": "synthetic", "thread": "thread"}
            calls = []

            def send(*args, **kwargs):
                calls.append(args)
                return subprocess.CompletedProcess(
                    args, 0, "Queued message abc for thread thread.\n", ""
                )

            reply = IO.operate("notify", [task, "completed"], Path(d), send)
            ack = IO.operate("acknowledge", [task, reply], Path(d), send)
            self.assertTrue(ack["acknowledged"])
            IO.operate("notify", [task, "completed"], Path(d), send)
            self.assertEqual(len(calls), 1)
            self.assertTrue(IO.operate("reconcile", [task], Path(d))["safe"])

    def test_ambiguous_never_resends(self):
        with (
            tempfile.TemporaryDirectory() as d,
            patch.object(IO, "receipt", return_value=None),
        ):
            task = {"id": "test", "unit": "synthetic", "thread": "thread"}

            def send(*args, **kwargs):
                raise subprocess.TimeoutExpired("codex", 60)

            with self.assertRaises(subprocess.TimeoutExpired):
                IO.operate("notify", [task, "completed"], Path(d), send)
            reply = IO.operate(
                "notify",
                [task, "completed"],
                Path(d),
                lambda *_a, **_k: self.fail("resend"),
            )
            self.assertEqual(reply["delivery"], "needs-review")
            self.assertFalse(IO.operate("reconcile", [task], Path(d))["safe"])
            with self.assertRaises(ValueError):
                IO.operate("acknowledge", [task, reply], Path(d))

    def test_atomic_json(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "value.json"
            IO.save(path, {"complete": True})
            self.assertEqual(json.loads(path.read_text()), {"complete": True})
            self.assertEqual(len(list(Path(d).iterdir())), 1)


if __name__ == "__main__":
    unittest.main()
