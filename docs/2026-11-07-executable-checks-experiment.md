# Requiring executable checks in the implementation stage

The two-stage inventory prompt now explicitly requires executing the checks from
analysis, using the actual answer computation rather than an unused duplicate.
Existing parent ports case-check-N, live-check-N and test-report-N make verdicts
and coverage observable. The context provides generic callable-check syntax and
support-aware =, and and <= operators, without an inventory solution.

A controlled run passed all evaluator checks. Complete cases execute while the
real damage input is missing, live checks become available later, and the known
reusable calculation passes numeric, premise, withdrawal and recovery tests.
The control is synthetic and is never supplied to the real model.

## Real-model outcomes

Three fresh sessions made ten provider requests in total, with no repair prompts,
retries, source stripping, or fallback solution. Both incomplete trials remain in
the evaluation; they are not counted as numerical successes.

| Trial | Outcome |
|---|---|
| 1 | First implementation rejected before admission: an incomplete outer form begins with `(.` followed by the intended definitions. No computation was installed. |
| 2 | Both questions admitted. Numeric answers and incremental completion correct. Executable case verdicts and reports produced, but tests exercise duplicate formulas. No earlier callable reused by the second answer. |
| 3 | First question admitted and its complete cases execute. Second response contains prose mixed with source and is rejected before admission. No successful second answer or mutation acceptance. |

The first-question sources in trials 2 and 3 are identical. They create direct
answer arithmetic plus separate test-usable and test-fulfillment callables. Their
zero, half and full damage cases yield expected values and true verdicts, but the
answer path does not call these test helpers. Passing tests therefore do not
establish correctness of the actual answer path. This violates the explicit
instruction to exercise the computation used for the answer.

The first live check only verifies total against the literal original box counts;
it does not verify usable stock, margin, or fulfillment. In completed trial 2,
the second live check compares the live result against hardcoded initial values
342 and -8. At the initial inputs it is true. After damage changes to 2.5%, the
correct numeric result becomes 351 usable, margin 1, fulfillment true; the check
becomes false because its expected values are stale. This is a failed check, but
not detection of an incorrect numerical answer. The reply also retains hardcoded
5% damage and shortage wording after the numeric correction.

Required question/rule support is absent from the numeric results. Question
withdrawal and damage withdrawal fail in the completed trial, repeating the
previous direct-arithmetic behavior. Its runtime cause remains unresolved.
The model does not call the supplied inspection operators in admitted source.
The two rejected sources are retained unchanged; rejected response text is not
silently repaired or counted as a usable assistant answer.

Thus the prompt caused executable checks to appear in two admitted first-question
responses, compared with none in the earlier planned condition. It did not yield
independent validation of the answer computation, reliable reuse, or a fully
passing session. These small sequential conditions change context and interfaces
together and do not establish a causal or statistical improvement.

## Infrastructure and evidence

The first sandboxed attempt could not reach localhost:18320 and returned no
provider responses. Those attempt records are retained separately under
runtime/checked-inventory-live-20261107. A read-only check outside the sandbox
returned HTTP 200. The actual three-session provider run then used approved local
network access under runtime/checked-inventory-live-network-20261107. No service
restart or deployment occurred. The companion runtime source was unchanged during
the experiment; its previously local changes were committed during repository
synchronization. Root evidence records the starting revision and diff summary.

The evaluator retains all checks, including missing stages in failed trials.
Aggregate completion/identity checks in incomplete trials must not be read as
proof that a worker restarted: some observations were never reached. No further
model requests are made for input changes in the completed trial.

- [Parent](../examples/lain/checked-inventory.lain)
- [Probe](../tools/probe_checked_inventory.clj)
- [Evaluator](../tools/evaluate_checked_inventory.clj)
- [Control evaluation](../runtime/checked-inventory-control-20261107/evaluation.log)
- [Live evaluation](../runtime/checked-inventory-live-network-20261107/evaluation.log)
- [First valid implementation](../runtime/checked-inventory-live-network-20261107/trial-2/generated-1.lain)
- [Second valid implementation](../runtime/checked-inventory-live-network-20261107/trial-2/generated-2.lain)

```sh
LAIN_EVIDENCE_DIR="$PWD/runtime/checked-control-new" tools/run_checked_inventory.sh controls
LAIN_EVIDENCE_DIR="$PWD/runtime/checked-live-new" tools/run_checked_inventory.sh live
```

Use fresh directories and a process permitted to reach the existing provider.
Sources, contexts, separate provider thinking, analysis records, receipts, graphs,
verdicts and mutation snapshots are preserved. Documentation does not reproduce
provider reasoning transcripts; original responses remain in the evidence files.
