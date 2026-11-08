# Environment-derived system guidance for repair

The parent environment now provides `repair-policy-cell` and `system-message`. The independent seed is [`environments/repair-guidance.lain`](../environments/repair-guidance.lain). The print-shop environment and recovery fixture load it; `soul-refined.lain`, `model-refined.lain`, and `lib/compiler.lain` remain unchanged.

## What the model receives

Each Lain phase builds explicit role-tagged messages:

```lain
:messages (seq:of
  (data:record :role "system" :content (data:get :text snapshot))
  (data:record :role "user" :content (data:get :ask context)))
```

The system text is the environment description itself, starting with `system-message`, followed by the selected repair policy, phase instructions, capability descriptions and examples. The user message is the current ask. The transport adds neither instructions nor history. The existing `:text` field remains available for recording and compatibility.

The policy tells the model to:

- Repair the same lexical child using actual admission and inspection feedback.
- Read response identity, UTC timestamp, accepted forms, values and input/output connections.
- Reuse accepted definitions; add missing or corrected connections rather than replaying the whole history.
- Treat unknown inputs as unfinished relations, and inspect contradictions and missing premise links.
- Follow the phase's output format; implementation and repair require complete raw Lain forms.
- Respect the repair budget and require observed output success before claiming completion.

A timestamp records when an admission receipt was published. It establishes observation time, not certainty or full causal provenance. The parent `system-message` retains the latest receipt through the existing behavior reducer; a repair phase renders its own immutable attempt feedback, including that receipt and its output inspection. This avoids mixing different attempts while a later response is in flight.

For example, the controlled failing response produced:

```clojure
{:status :rejected
 :response "round-1"
 :timestamp-ms <actual receipt time>
 :timestamp-utc "<the same time in UTC>"
 :accepted-forms [<three successfully installed forms>]
 ...}
```

The test checks that the actual UTC string, response identity, invalid-form diagnostic and retained source reach the repair request's system message. Provider thinking is kept separate.

## Prompt guidance and request triggering

Guidance alone cannot wake a blocked network. The earlier gate used strict Boolean primitives, so a known rejection could still wait for unfinished outputs. `logic:all` and `logic:any` now reuse the existing partial Boolean operators: a deciding false/true input suffices while other inputs stay watched. No kernel change is involved.

Readiness is evaluated from a completed observation. It requires usable values for all eight declared outputs and a zero count of missing-support entries. Rejection independently makes an attempt unsuccessful. This replaces the earlier redundant support-set calculation; it does not weaken the expected-premise requirement. Controlled experiments also exposed a completion stall with the empty-record equality formulation in the larger network. The count formulation passes the end-to-end control and the delayed-observation regression; no general kernel diagnosis is claimed.

The Lain phase network still owns analysis, generalization, implementation, two bounded repairs and termination. Native adapters only observe, render, publish receipt metadata and perform the existing compiler/transport effects. There is no added host retry controller.

## Verification and limits

The controlled recovery supplies these responses through the existing worker transport:

1. `(define retained 7)(define pending (+ missing 4))(-> pending result))` — retains three valid forms and rejects the extra closing parenthesis.
2. `(define extra 1)` — admits, but required output remains unfinished.
3. `(-> retained missing)` — repairs the existing network and produces `11`.

Lain emits five requests: analysis, generalization, implementation and two repairs. The final observation marks the round done. The worker and lexical child remain the same; replaying the external input does not issue another request. Other checks cover exhaustion after two corrections, invalid plans, delayed context, snapshot identity, partial Boolean decisions, unknown/contradictory values and missing premise links.

Verification on the unchanged companion runtime:

| Check | Result |
|---|---|
| `bash tools/test_module.sh experiment-recovery` | 7 tests, 64 assertions, no failures/errors |
| `bash tools/test_module.sh runtime-composition` | 2 tests, 15 assertions, no failures/errors |
| `bash tools/test_module.sh model-io` | 6 tests, 45 assertions, no failures/errors with loopback access |
| Clojure formatting/lint for the three changed Clojure files | 0 errors, 0 warnings |
| Runner shell syntax and whitespace diff checks | Pass |

The first sandboxed model-IO attempt could not create its local HTTP socket (`Operation not permitted`). The loopback-enabled rerun passed, including exact forwarding of role-tagged messages. This was an environment restriction, not a model or network-computation failure. No deployed service was restarted and no backend/kernel source was changed.

These are controlled transport tests, not evidence that a fresh real model chooses the correct repair or completes the five-question print-shop experiment. Earlier real-model and print-shop failures remain historical evidence. Full causal provenance remains unfinished.

Bulk logs and rejected intermediate diagnostic candidates stay local under `runtime/repair-alert-20261108/`, which is ignored. Only implementation, tests and this summary are intended for publication.
