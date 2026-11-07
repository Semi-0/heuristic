# Experiment evidence archives

Large evidence and network files are committed as deterministic gzip archives.
Raw local copies remain available and are ignored by Git. Every archive was
round-trip verified; `artifact-archives.json` records original byte counts and
SHA-256 hashes.

After cloning, restore raw files before running an evaluator or integrity checker:

```sh
python3 - <<'PYTHON'
import gzip, json
from pathlib import Path
for entry in json.loads(Path("runtime/runtime-composition-20261107/artifact-archives.json").read_text()):
    Path(entry["path"]).write_bytes(gzip.decompress(Path(entry["archive"]).read_bytes()))
PYTHON
```

Paths are relative to the repository root. Source, prompts, responses, receipts,
reports and small diagnostic files remain directly readable.
