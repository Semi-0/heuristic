"""Summarize saved call latency; does not make provider requests."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
conditions = {
    'struct_baseline': ('printshop-struct-live-20261106', 5),
    'strict': ('printshop-strict-live-20261106', 10),
    'strict_diagnostics': ('printshop-diagnostics-live-20261106', 10),
}
result = {
    'measurement': 'Approximate provider round-trip seconds from original context/response file timestamps; excludes setup, admission, feedback publication and mutation checks.',
    'limits': 'One nonrandomized session per condition. Different prompts and output lengths; server load and caching uncontrolled. Neither a causal speed estimate nor time to a correct solution.',
    'conditions': {},
}
for name, (directory, count) in conditions.items():
    base = root / 'runtime' / directory
    rows = []
    for i in range(1, count + 1):
        start, end = base / f'context-{i}.txt', base / f'response-{i}.edn'
        if not end.exists():
            break
        delta = (end.stat().st_mtime_ns - start.stat().st_mtime_ns) / 1e9
        assert delta >= 0
        rows.append({'request': i, 'phase': ('analysis' if i % 2 else 'implementation') if count == 10 else 'implementation',
                     'seconds': delta, 'context_bytes': start.stat().st_size})
    result['conditions'][name] = {'requests': len(rows), 'expected_requests': count, 'calls': rows,
                                 'total_provider_seconds': sum(r['seconds'] for r in rows)}
output = root / 'runtime' / conditions['strict_diagnostics'][0] / 'timing-comparison.json'
output.write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps({k: {x: v[x] for x in ['requests', 'total_provider_seconds']} for k, v in result['conditions'].items()}, indent=2))
