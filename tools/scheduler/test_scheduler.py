import itertools
import json
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch
import scheduler

CONFIG = {'classpath': '/home/lain/reports/xiaobao-takeover/20261114-r3/classpath.txt',
          'runtime': '/home/lain/reports/xiaobao-takeover/20261114-r3/source-3c3f643'}


class SchedulerTests(unittest.TestCase):
    def test_compiled_lain_policy(self):
        with tempfile.TemporaryDirectory() as temp, open(Path(temp)/'engine.log', 'w') as log:
            policy = scheduler.Policy(CONFIG, log)
            try:
                keys = ['notified', 'terminal', 'expired', 'started', 'admitted', 'idle']
                # All combinations, in a changing live session, including bounded reopening.
                for values in itertools.product([False, True], repeat=6):
                    o = dict(zip(keys, values))
                    expected = ('done' if o['notified'] else 'wake' if o['terminal'] or o['expired']
                                else 'launch' if not o['started'] and o['admitted'] and o['idle'] else 'wait')
                    with self.subTest(o=o):
                        self.assertEqual(expected, policy.decide(o))
                final = dict.fromkeys(keys, False)
                final['terminal'] = True
                self.assertEqual('wake', policy.decide(final))
                self.assertEqual('wake', policy.decide(final))
            finally:
                policy.close()

    def test_real_guarded_launch_and_completion(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            config = dict(CONFIG, deadline=time.time()+120, poll_seconds=0.1,
                          memory_mib=64, command=['/usr/bin/true'],
                          thread='synthetic', name='launch-smoke', evidence=temp)
            # Synthetic idle telemetry; actual Lain, actual watchdog and owned job.
            # The notifier itself is covered separately by the live wake smoke.
            with patch('scheduler.idle', return_value=True), patch('scheduler.notify') as notify:
                scheduler.run(config, root)
                notify.assert_called_once()
                self.assertTrue(json.loads((root/'state.json').read_text())['launch_claimed'])
                status = json.loads((root/'status.json').read_text())
                self.assertEqual('wake', status['action'])
                self.assertEqual(0, status['job_receipt']['launcher_exit'])
                self.assertIn('Running as unit:', (root/'job.log').read_text())

    def test_delivery_receipt_and_resume_warning(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for stderr, expected in [('', 'sent'), ('Warning: resume failed', 'needs-review')]:
                state = {}
                with patch('scheduler.subprocess.run') as run:
                    run.return_value.returncode = 0
                    run.return_value.stdout = 'queued'
                    run.return_value.stderr = stderr
                    scheduler.notify({'thread': 'synthetic'}, state, root, 'test')
                    self.assertIn('--resume', run.call_args.args[0])
                    self.assertEqual(expected, state['notification'])
                    self.assertTrue((root/'wake-receipt.json').exists())

    def test_restart_does_not_repeat_claimed_notification(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for status in ['sending', 'sent', 'needs-review']:
                scheduler.write(root/'state.json', {'notification': status})
                with patch('scheduler.Policy') as policy:
                    scheduler.run({}, root)
                    policy.assert_not_called()


if __name__ == '__main__':
    unittest.main()
