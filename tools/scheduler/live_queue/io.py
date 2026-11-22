#!/usr/bin/env python3
"""Single managed IO call. Lain owns queue traversal, clock and alert selection."""
import fcntl
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import time


def load(path, default=None):
    try:
        return json.loads(path.read_text())
    except FileNotFoundError:
        return default


def save(path, value):
    tmp = path.with_suffix('.tmp')
    tmp.write_text(json.dumps(value, indent=2) + '\n')
    tmp.replace(path)


def receipt(task):
    unit = task['unit']
    if not re.fullmatch(r'lain-test-[0-9a-f]{32}\.service', unit):
        raise ValueError('Invalid managed job identity')
    path = Path('/home/lain/.local/state/lain-watchdog/receipts')/(unit[10:-8]+'.json')
    result = load(path)
    if result is not None:
        if result.get('token') != unit[10:-8]:
            raise ValueError('Receipt identity mismatch')
        return result
    status = subprocess.run(['systemctl', '--user', 'show', unit, '--property=ActiveState', '--value'],
                            capture_output=True, text=True, timeout=10)
    if status.returncode == 0 and status.stdout.strip() in ['inactive', 'failed']:
        return {'finished': True, 'error': 'Inactive job has no completion receipt; outcome unknown'}
    return None


def operate(op, args, root):
    task = args[1]
    key = hashlib.sha256((task['id']+'\0'+task['unit']).encode()).hexdigest()
    folder = root/key
    folder.mkdir(parents=True, exist_ok=True)
    with (folder/'lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        state = load(folder/'state.json', {})
        if op == 'observe':
            raw = receipt(task)
            value = {'terminal': bool(raw and raw.get('finished')),
                     'acknowledged': bool(state.get('acknowledged')),
                     'delivery_state': state.get('delivery', 'none'),
                     'receipt': raw}
            save(folder/'observed.json', {'at': time.time(), 'task': task, 'value': value})
            return value
        if op == 'notify':
            kind = args[2]
            if not state.get('delivery'):
                state = {'delivery': 'sending', 'task': task, 'kind': kind}
                save(folder/'state.json', state)
                message = (f"[Lain live-queue SELF-TEST] {task['id']}: {kind}. "
                           f"This is the shared-clock scheduler test, not a benchmark result. "
                           f"Read {root}/full-result.edn and {folder}/notification.json when available. "
                           "Acknowledge delivery; do not launch or restart experiments, commit or push.")
                result = subprocess.run(['codex', 'queue', '--thread', task['thread'],
                                         '--message', message, '--resume'],
                                        capture_output=True, text=True, timeout=60)
                delivery = 'submitted' if result.returncode == 0 and 'warn' not in result.stderr.lower() else 'needs-review'
                state['delivery'] = delivery
                save(folder/'notification.json', {'exit': result.returncode, 'stdout': result.stdout,
                                                  'stderr': result.stderr, 'at': time.time(),
                                                  'job_receipt': receipt(task)})
                save(folder/'state.json', state)
            # A saved ambiguous claim is returned for review, never blindly repeated.
            return {'task': task['id'], 'unit': task['unit'], 'delivery': state['delivery']}
        if op == 'acknowledge':
            ack = args[2]
            if ack['task'] != task['id'] or ack['unit'] != task['unit']:
                raise ValueError('Wrong task acknowledgment')
            if ack.get('delivery') != 'submitted' or state.get('delivery') != 'submitted':
                raise ValueError('Submission failed or ambiguous; operator review required')
            state['acknowledged'] = True
            save(folder/'state.json', state)
            return ack
        raise ValueError('Unknown IO capability')


if __name__ == '__main__':
    request = json.load(sys.stdin)
    print(json.dumps(operate(request['op'], request['args'], Path(sys.argv[1]))))
