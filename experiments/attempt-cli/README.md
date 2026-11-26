# Flash repair through the Lain compiler

Flash can check a proposed definition with `CLI lain-read` or send it with
`SUBMIT`. Both use `propagators.compiler.language.reader/read-form` directly
from the pinned standalone compiler. Its diagnostic appears in the next
model request through the action receipt. A valid submission continues to the
live Heuristic session for admission and four executable price checks.

With the Heuristic runtime checked out alongside this repository, the normal
launcher resolves the compiler Git dependency. It requires access to the private
Lain repositories and a running local Qwen endpoint. Use a fresh evidence path.

```sh
experiments/attempt-cli/run.sh guided-repair-cli /tmp/flash-guided 20480 900000
```

The guided trial supplies one initial reader action to guarantee that Flash sees
the diagnostic. Subsequent actions come from Flash. Use `repair-cli` to let
Flash also choose its first action. Results, per-call provider responses,
thinking text and the session close receipt are written to the evidence folder.
See the [experiment report](../../docs/2026-11-26-compiler-reader.md).
