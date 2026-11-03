"""Authenticated UTF-8 source extension and read-only status for the local editor."""
import argparse
import importlib.util
import json
from pathlib import Path
import urllib.error
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--deploy-root', type=Path, default=Path.home() / '.local/share/lain-editor-deploy')
parser.add_argument('--session', required=True)
parser.add_argument('--extend', type=Path)
args = parser.parse_args()
spec = importlib.util.spec_from_file_location('editor_deploy', args.deploy_root / 'current/backend/deploy/gx10_editor.py')
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)
url = 'http://127.0.0.1:4782'

def api(command):
    request = urllib.request.Request(url + '/api', data=json.dumps(command, ensure_ascii=False).encode(),
                                     headers={'Content-Type': 'application/json', **deploy.probe_headers(url)})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            body = json.load(response)
    except urllib.error.HTTPError as error:
        raise RuntimeError(error.read().decode()) from None
    if not body.get('ok'):
        raise RuntimeError(body)
    return body['result']

state = api({'op': 'session-status', 'sessionId': args.session})
if args.extend:
    state = api({'op': 'extend', 'sessionId': args.session, 'generation': state['generation'],
                 'source': args.extend.read_text()})
print(json.dumps({key: state.get(key) for key in ['sessionId', 'runId', 'generation', 'lifecycle']}, indent=2))
