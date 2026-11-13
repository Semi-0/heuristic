# Recorded output-repair cases

These small source fixtures are verbatim real-model responses from the preceding
durable accumulation experiment, not handwritten replacement solutions:

- `discover.lain`: `runtime/directed-20261111/live-5/reuse-discover.lain`.
- `choose.lain`: `runtime/directed-20261111/live-5/reuse-choose.lain`.
- `shadowed-execute.lain`: `runtime/directed-20261111/live-5/reuse-execute.lain`.
  It resolves and invokes the saved closure, then defines local `answer` and
  `user-output`, leaving the required parent ports unknown.
- `correct-execute.lain`: `runtime/directed-20261111/reuse-6/reuse-execute.lain`.

The runner labels replayed responses separately from fresh provider calls.
Controlled repair adds only the two missing parent connections. The exhaustion
control deliberately supplies no connections. In `replay` mode the base responses
are fixtures but repair responses come from the real provider. In `fresh` mode
all responses come from the provider. Neither replay nor control is reported as a
fresh end-to-end model run.
