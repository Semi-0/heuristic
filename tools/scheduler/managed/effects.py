#!/usr/bin/env python3
"""One IO operation per invocation. No polling, scheduling policy or action loop."""
import fcntl
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import urllib.request

WATCHDOG = Path('/home/lain/.local/state/lain-watchdog')
LAUNCHER = '/home/lain/projects/datalog-research/scripts/resource_watchdog.py'


def read(path, default=None):
    try:
        return json.loads(path.read_text())
    except FileNotFoundError:
        return default


def write(path, value):
    tmp = path.with_suffix('.tmp')
    tmp.write_text(json.dumps(value, indent=2) + '\n')
    tmp.replace(path)


def admitted():
    s = read(WATCHDOG / 'status.json', {})
    return (s.get('health') == 'ok' and 0 <= time.time()-s.get('updated', 0) < 35
            and s.get('decision', {}).get('admission') is True)


def idle():
    try:
        client = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with client.open('http://127.0.0.1:18320/metrics', timeout=5) as response:
            raw = response.read().decode()
        counts = [re.findall(r'^vllm:' + k + r'\{[^\n]*\} ([0-9.]+)$', raw, re.M)
                  for k in ['num_requests_running', 'num_requests_waiting']]
        return bool(all(counts) and all(float(v) == 0 for group in counts for v in group))
    except (OSError, ValueError):
        return False


def operate(op, job, tick):
    root = Path(job['state_dir'])
    root.mkdir(parents=True, exist_ok=True)
    with (root/'effects.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        state = read(root/'state.json', {'unit': job.get('unit'), 'notification': None})
        if op == 'observe':
            receipt = None
            unit = state.get('unit')
            # A configured launch has its own durable wrapper exit receipt.
            local_exit = read(root/'job-exit.json')
            if unit:
                if not re.fullmatch(r'lain-test-[0-9a-f]{32}\.service', unit):
                    raise ValueError('Invalid job unit')
                receipt = read(WATCHDOG/'receipts'/(unit[10:-8]+'.json'))
                if not receipt:
                    result = subprocess.run(['systemctl', '--user', 'show', unit,
                                             '--property=ActiveState', '--value'],
                                            capture_output=True, text=True, timeout=10)
                    if result.returncode == 0 and result.stdout.strip() in ['inactive', 'failed']:
                        receipt = {'finished': True, 'error': 'inactive without receipt'}
            elif local_exit:
                receipt = dict(local_exit, finished=True)
            started = bool(unit or state.get('launch_claimed'))
            observation = {'notified': bool(state.get('notification')),
                           'terminal': bool(receipt and receipt.get('finished')),
                           'expired': time.time() >= job['deadline'] or tick >= job['max_ticks'],
                           'started': started, 'admitted': admitted(),
                           'idle': False if started else idle(),
                           'tick': tick, 'job_receipt': receipt, 'at': time.time()}
            write(root/'observation.json', observation)
            return observation
        if op == 'start':
            if state.get('unit') or state.get('launch_claimed'):
                return {'status': 'already-started'}
            # Durable reservation before external work: ambiguous crashes never auto-retry.
            state['launch_claimed'] = True
            write(root/'state.json', state)
            with (root/'job.log').open('a') as output:
                child = subprocess.Popen([sys.executable, str(Path(__file__).resolve()),
                                          'execute', json.dumps(job)], stdout=output,
                                         stderr=subprocess.STDOUT, start_new_session=True)
            state['launcher_pid'] = child.pid
            write(root/'state.json', state)
            return {'status': 'launched', 'pid': child.pid}
        if op == 'wake':
            if state.get('notification'):
                return {'status': 'already-claimed', 'notification': state['notification']}
            state['notification'] = 'sending'
            write(root/'state.json', state)
            observation = tick
            message = (f"[Lain managed scheduler] {job['name']}: terminal/deadline event. "
                       f"Read {root}/observation.json and {job['evidence']}. "
                       "Review actual evidence, report to the user, update heuristic docs. "
                       "Do not restart interrupted jobs, change kernel, commit or push. "
                       "Notification is not an acceptance claim.")
            result = subprocess.run(['codex', 'queue', '--thread', job['thread'],
                                     '--message', message, '--resume'],
                                    capture_output=True, text=True, timeout=60)
            receipt = {'exit': result.returncode, 'stdout': result.stdout,
                       'stderr': result.stderr, 'at': time.time(), 'observation': observation}
            write(root/'wake-receipt.json', receipt)
            state['notification'] = 'sent' if result.returncode == 0 and 'warn' not in result.stderr.lower() else 'needs-review'
            write(root/'state.json', state)
            return {'status': state['notification']}
        raise ValueError('Unknown effect')


if __name__ == '__main__':
    if sys.argv[1] == 'execute':
        job = json.loads(sys.argv[2])
        # A blocking process launcher, not a scheduler: watchdog remains authoritative.
        result = subprocess.run([sys.executable, LAUNCHER, 'run', '--memory-mib',
                                 str(job['memory_mib']), '--cpus', '1', '--']+job['command'])
        write(Path(job['state_dir'])/'job-exit.json', {'exit': result.returncode, 'at': time.time()})
    else:
        request = json.load(sys.stdin)
        print(json.dumps(operate(sys.argv[1], request['job'], request.get('input'))))
