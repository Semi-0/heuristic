#!/usr/bin/env python3
"""IO interpreter for policy.lain; one explicitly configured experiment/thread."""
import argparse
import fcntl
import json
import os
from pathlib import Path
import re
import select
import subprocess
import time
import urllib.request

HERE = Path(__file__).resolve().parent
WATCHDOG = Path('/home/lain/.local/state/lain-watchdog')
LAUNCHER = '/home/lain/projects/datalog-research/scripts/resource_watchdog.py'


def write(path, value):
    tmp = path.with_suffix('.tmp')
    tmp.write_text(json.dumps(value, indent=2) + '\n')
    tmp.replace(path)


def read(path, default=None):
    try:
        return json.loads(path.read_text())
    except FileNotFoundError:
        return default


class Policy:
    def __init__(self, config, log):
        cp = Path(config['classpath']).read_text().strip()
        self.proc = subprocess.Popen(
            ['java', '-Xms32m', '-Xmx512m', '-XX:ActiveProcessorCount=1',
             '-cp', cp, 'clojure.main', str(HERE / 'engine.clj'), str(HERE / 'policy.lain')],
            cwd=config['runtime'], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=log, text=True, bufsize=1)

    def decide(self, observation):
        self.proc.stdin.write(json.dumps(observation) + '\n')
        self.proc.stdin.flush()
        if not select.select([self.proc.stdout], [], [], 90)[0]:
            raise TimeoutError('Lain policy did not settle within 90 seconds')
        return json.loads(self.proc.stdout.readline())['action']

    def close(self):
        self.proc.stdin.close()
        try:
            self.proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            self.proc.terminate()
            self.proc.wait(timeout=10)
        finally:
            self.proc.stdout.close()


def health():
    s = read(WATCHDOG / 'status.json', {})
    return (s.get('health') == 'ok' and 0 <= time.time()-s.get('updated', 0) < 35
            and s.get('decision', {}).get('admission') is True)


def idle():
    try:
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open('http://127.0.0.1:18320/metrics', timeout=5) as response:
            raw = response.read().decode()
        counts = [re.findall(r'^vllm:' + k + r'\{[^\n]*\} ([0-9.]+)$', raw, re.M)
                  for k in ['num_requests_running', 'num_requests_waiting']]
        return all(counts) and all(float(v) == 0 for group in counts for v in group)
    except (OSError, ValueError):
        return False


def notify(config, state, root, message):
    # Claim before effect: a crash in this window is visible/ambiguous, never auto-repeated.
    state['notification'] = 'sending'
    write(root / 'state.json', state)
    result = subprocess.run(
        ['codex', 'queue', '--thread', config['thread'], '--message', message, '--resume'],
        capture_output=True, text=True, timeout=60)
    write(root / 'wake-receipt.json', {'exit': result.returncode, 'stdout': result.stdout,
                                     'stderr': result.stderr, 'at': time.time()})
    state['notification'] = 'sent' if result.returncode == 0 and 'warn' not in result.stderr.lower() else 'needs-review'
    write(root / 'state.json', state)


def run(config, root):
    state = read(root / 'state.json', {'unit': config.get('unit'), 'notification': None})
    if state.get('notification'):
        return  # Includes ambiguous delivery; operator reconciliation, not duplicate wakeups.
    log = (root / 'engine.log').open('a')
    policy = Policy(config, log)
    child = None
    quiet = 0
    try:
        while True:
            unit = state.get('unit')
            receipt = None
            if unit:
                token = unit.removeprefix('lain-test-').removesuffix('.service')
                receipt = read(WATCHDOG / 'receipts' / (token + '.json'))
            terminal = bool(receipt and receipt.get('finished'))
            if child is not None and child.poll() is not None:
                terminal = True
                receipt = {'launcher_exit': child.returncode}
            # For attached jobs, missing/inactive units are failures, never successes.
            if unit and not terminal:
                result = subprocess.run(['systemctl', '--user', 'show', unit,
                                         '--property=ActiveState', '--value'],
                                        capture_output=True, text=True, timeout=10)
                if result.returncode == 0 and result.stdout.strip() in ['inactive', 'failed']:
                    terminal = True
                    receipt = {'error': 'unit inactive without completion receipt'}
            quiet = quiet+1 if not unit and child is None and idle() else 0
            observation = {'notified': False, 'terminal': terminal,
                           'expired': time.time() >= config['deadline'],
                           'started': bool(unit or state.get('launch_claimed')),
                           'admitted': health(), 'idle': quiet >= 3}
            action = policy.decide(observation)
            write(root / 'status.json', {'at': time.time(), 'observation': observation,
                                        'action': action, 'job_receipt': receipt})
            if action == 'launch':
                state['launch_claimed'] = True
                write(root / 'state.json', state)
                output = (root / 'job.log').open('a')
                try:
                    child = subprocess.Popen(['python3', LAUNCHER, 'run', '--memory-mib',
                                              str(config['memory_mib']), '--cpus', '1', '--']
                                             + config['command'], stdout=output, stderr=subprocess.STDOUT)
                finally:
                    output.close()
                state['launcher_pid'] = child.pid
                write(root / 'state.json', state)
            elif action == 'wake':
                reason = 'terminal receipt' if terminal else 'monitor deadline (not job success)'
                message = (f"[Lain experiment scheduler] {config['name']}: {reason}. "
                           f"Read {root}/status.json and {config['evidence']}. "
                           "Review actual results, report to the user and update heuristic docs. "
                           "Do not restart interrupted jobs, change kernel, commit or push. "
                           "This is an automated completion/status event, not an acceptance claim.")
                notify(config, state, root, message)
                return
            time.sleep(config.get('poll_seconds', 15))
    finally:
        policy.close()
        log.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('config', type=Path)
    args = parser.parse_args()
    config = read(args.config)
    if config.get('unit') and not re.fullmatch(r'lain-test-[0-9a-f]{32}\.service', config['unit']):
        raise ValueError('Invalid attached unit')
    root = Path(config['state_dir'])
    root.mkdir(parents=True, exist_ok=True)
    with (root / 'lock').open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        run(config, root)


if __name__ == '__main__':
    main()
