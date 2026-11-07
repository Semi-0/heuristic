# Experiment intention and next steps

November 5, 2026 · Planning record, not implementation acceptance

The [Lain Agent Harness proposal](lain-agent-harness-proposal.md) defines the
research intention: agent invocations should leave behind computational
structures that later invocations can inspect and reuse. The next experiment
connects the working model IO boundary to `soul-refined.lain` to test a small
part of that intention: a model creates a callable in a lexical child, finishes
its response, and a later request uses the existing callable.

This is executable memory within an ongoing environment. It is a step toward,
but not proof of, durable storage and reopening a terminated invocation.

## What the existing experiment established

[model-refined.lain](../model-refined.lain) connects an input and output to a
session-owned asynchronous model worker. The worker uses core.async queues;
Meander dispatches its effects. Replies return as supported publications, and
`prim-slot` exposes fields of their plain record values. The semantic tracer
can display connected structures and captured values.

The [November 4 journey](2026-11-04-first-model-io-experiment.md) and
[implementation report](model-io.md) record the evidence and limitations.
That success does not yet establish generated-code admission through the full
soul composition, durable invocation recovery, or complete causal provenance
from model input to output.

## What we are connecting

[soul-refined.lain](../soul-refined.lain) describes an environment for an agent
to extend. The parent supplies capabilities and context. Generated definitions
belong in a lexical child, where they can read inherited bindings without
redefining the parent or contaminating siblings. Selected results and diagnostics
can be contributed through explicit feedback connections.

The next path is:

```text
Parent capabilities and context + identified user request
                         ↓
                 Explicit model request
                         ↓
              Completed generated Lain source
                         ↓
             Targeted admission into a child
                         ↓
          Reusable definitions, results, diagnostics
                         ↓
        Explicit reply delivery and selected feedback
```

The same child must remain available across model evaluations. A second
evaluation should reference the first definition, rather than regenerate it.
Model call identity, invocation identity, and lexical environment identity must
not be conflated: one invocation may contain several model evaluations.

## Interface gaps to resolve

- The model currently consumes text or a plain record containing `:text`.
  Environment descriptions are connected structured values. Prompt construction
  needs an explicit boundary that selects and serializes available information
  without replacing the underlying environment with a permanent text snapshot.
- `describe-environment` currently excludes `user-input`. The request needs its
  own explicit connection to prompt construction.
- The old `incremental-compile` consumes the old model's parser and state slots.
  The new model produces a completed reply. Reuse the reader and targeted
  admission facilities behind an interface independent of the old state machine.
- The current `:actions` field is reply text, not established executable source.
  Source generation must be explicitly requested, validated, and admitted;
  failed or truncated replies must not silently count as successful admissions.
- New descriptions, trace observations, and delivery receipts must not each
  trigger another model call. Define request eligibility and feedback policy
  explicitly, with a bound for subsequent inference.
- Keep the instance and demo display separate from the importable model
  definition. Avoid duplicate definitions and competing display connections.

## Reviewable implementation rounds

1. **Extract the reusable model definition.** Keep the existing demo working.
   Importing the library alone must send no request. Materialize the instance
   explicitly through Lain.
2. **Observe the actual soul environment and construct one request.** Use a fake
   transport to inspect the received prompt. Available capabilities and the
   user request must arrive; unrelated unknown fields must not block them.
3. **Admit one generated form.** Use the existing source reader and connected
   session admission, targeting the lexical child. Verify the result, parent
   and sibling isolation, an inspectable receipt, and replay deduplication.
   Per-form atomicity must not be described as rollback of an entire response.
4. **Complete one deterministic conversation.** Return a reusable definition,
   a computation, and a call to `message-to-user`. Verify one model request,
   the computed result, one acknowledged delivery, and no automatic extra
   inference. Expose rejected source as a diagnostic.
5. **Demonstrate executable reuse.** Submit a second identified request in the
   same environment. Invoke the existing definition with new inputs; verify
   that the definition was not reinstalled. Make its availability discoverable
   through a deliberate environment view or feedback contribution, rather than
   assuming the new model call remembers the earlier text.
6. **Exercise controlled feedback and inspect the connections.** Select one
   result or diagnostic for feedback, check the inference bound and replay
   behavior, and show the semantic graph. Record which provenance relationships
   are implemented and which remain missing.
7. **Repeat the passing composition with Blazux in the hosted editor.** Change
   the transport, keeping the same compiled Lain path. Start with arithmetic and
   a reply; add other capabilities after this passes.

Each round should state the proposed change, expose the relevant Lain source,
and report its acceptance result for review before moving to the next round.
Preserve `soul.lain`, unrelated work, and the working model demonstration.

## The first concrete reuse test

A controlled first response defines:

```clojure
(define plus-two (network [x] (+ x 2)))
(define first-answer (plus-two 5))
(message-to-user "Seven")
```

A later response in the same lexical environment uses the existing network:

```clojure
(define second-answer (plus-two 8))
(message-to-user "Ten")
```

Acceptance checks both computed values, 7 and 10, rather than trusting the
literal reply strings. It checks that `plus-two` retains its definition
identity and is not redeclared, that the parent and siblings remain unaffected,
and that each message is delivered once. A pure invocation of the retained
`plus-two` network should require no further model request.

This demonstrates a reusable computational structure surviving a model call.
It does not yet prove retrieval across independent invocations or process loss.

## The persistence milestone that follows

The proposal's Phase 1 milestone remains stronger: an invocation can terminate
and later be reopened with its structure intact. After in-session reuse works,
define and test a persistence contract for identities, lexical ancestry,
supported observations, source versions, and effect receipts.

Distinguish a closed editor view, a completed model evaluation, a dormant
invocation, and a stopped host process. Reattaching a view to a still-running
owner is not durable recovery. Reexecuting source from scratch is not evidence
that the original network identity and state were restored.

On reopening, the default is inspection of dormant computation. Stored effect
descriptions and observations must not automatically resend requests or replay
deliveries. Pure reuse and newly scheduled effects need distinct tests.

## Research goals beyond this wiring experiment

“Every expression shall have a view” guides observability: inputs, outputs,
dependencies, unknowns, conflicts, and failures should be inspectable. It is a
design requirement, not a claim that the current editor covers every case.

Provenance should eventually support tracing a failed conclusion to its
assumptions, withdrawing invalid support, and exploring alternative branches.
Timestamp and cost observations should allow comparisons of reuse against
recomputation. Timestamps alone do not measure CPU cost, retained memory, or
causality; these require explicit measurements and relationships.

Durable storage, hybrid semantic/Datalog retrieval, reusable subroot discovery,
fork/resume semantics, and automatic strategy improvement remain later research
work. Semantic graph visualization is not the same capability as semantic
retrieval over historical invocations. The existing behavior benchmark's
retention and scaling limitations also remain unresolved.

## Relevant records

- [Full research proposal](lain-agent-harness-proposal.md)
- [Executable soul specification](../soul-refined.lain)
- [Working model IO example](../model-refined.lain)
- [Native boundaries and earlier migration contracts](native-boundaries.md)
- [Earlier integration verification](../runtime/verification.json)
- [Behavior verification and practicality limits](../runtime/behavior-verification.edn)
- [Known editor issues](../TODO.md)
