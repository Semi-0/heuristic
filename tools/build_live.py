"""Build the opt-in v4 source extension; documents are declared environment inputs."""
import json
from pathlib import Path
root = Path(__file__).resolve().parents[1]
source = '(load-primitive-environment "lain_harness/live_v4.clj" :lain-harness.live-v4/environment 4)\n'
for name, path in [('h4:guide', 'docs/how-to-think-in-lain.md'), ('h4:design', 'soul-refined.lain')]:
    source += '(define ' + name + ' ' + json.dumps((root / path).read_text(), ensure_ascii=False) + ')\n'
source += (root / 'experiments/live/scaffold-v4.lain').read_text()
(root / 'experiments/live/bootstrap-v4.lain').write_text(source)
print(root / 'experiments/live/bootstrap-v4.lain')
