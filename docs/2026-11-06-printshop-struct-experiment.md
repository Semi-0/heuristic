# Print-shop quotes with structured inputs

One exploratory session asks the five fictional print-shop questions in sequence.
The model receives a shared `pricing` struct and separate `order-1` through
`order-5` structs. Each order contains quantity, rush/member flags, discount cap
and its question premise. Existing `quote-N` output structs expose production,
rush, discount, subtotal, delivery, tax and total. Text ports and optional
`self-check-N` boolean ports permit the model to explain and verify its calculation.

The [parent environment](../examples/lain/printshop-struct.lain) contains rules and
interfaces but no pricing formula. It retains the agreed generalization-and-reuse
methodology. The model can create parameterized operators, return structs, invoke
prior operators and construct tests. None of those strategies is required, and
no solved print-shop example or evaluator answer appears in a model context.

The [probe](../tools/probe_printshop_struct.clj) reuses the model transport,
lexical child, sequential admission, reducers, and supported arithmetic. Generic
supported `min`, `max`, and `round-money` adapters provide missing numeric operations;
`round-money` rounds half up to cents. Rates are supplied as exact decimal values.
The final rounding adapter converts the value to a double-backed decimal; this
is sufficient for these checked amounts, not a universal financial precision claim.

A [controlled quote function](../test/fixtures/printshop/quote.lain) exercises the
same structs and output connections before the live run. It is not included in
model contexts and is not counted as a model result. The larger environment
requires a local propagation allowance of 300000 steps and 180 seconds, instead
of the earlier 100000 steps and 30 seconds. The original 30-second limit expired
while constructing the environment, before any model request. Provider timeout
and generation settings remain unchanged.

The [evaluator](../tools/evaluate_printshop_struct.clj) records initial breakdowns,
new callable invocations, model-written self-check outputs, history preservation,
and mutations without another inference: order 1 quantity becomes 120; order 5's
cap becomes 40 and returns to 75; order 2 quantity is withdrawn and restored;
question 1 is withdrawn and restored; an unrelated note changes.

Expected original totals are $212.76, $533.30, $1,199.88, $249.91 and $1,405.30.
The corrected first order is $277.56. Removing the promotion makes order 5
$1,443.10; restoring it yields $1,405.30. These values belong to the evaluator.

## Live model result

One fresh session completed five provider requests with no retries or feedback.
All sources were admitted unchanged. The model read the order structs and wrote
all seven quote slots, but accessed the pricing rates through their readable scalar
bindings. It created no callable operators and repeated the calculation for every
order. Runtime and source inspection found no reuse of earlier computed operators
or result cells in later quotes.

| Question | Expected total | Model total | Model self-check |
|---|---:|---:|---|
| 1: standard, nonmember, 80 copies | $212.76 | $212.76 | true |
| 2: rush, member, 240 copies | $533.30 | $350.14 | true |
| 3: standard, member, 720 copies | $1,199.88 | $1,199.88 | true |
| 4: rush, member, 90 copies | $249.91 | $172.15 | true |
| 5: rush, member, 720 copies, $75 cap | $1,405.30 | $918.86 | true |

The same formula error appears in every response: the subtotal subtracts the rush
charge instead of adding it. For example, the first response defines:

```lain
(define subtotal (+ (- production rush-charge discount) setup-fee))
```

That error is invisible for the two standard-service orders, but causes the three
rush-order failures. Production, rush charge and discount are individually correct;
the incorrect subtotal then contaminates tax and total. Overall, 26 of 35 initial
breakdown values and 2 of 5 final totals match the oracle.

Each self-check repeats the exact expression used to define the total:

```lain
(define total (round-money (+ subtotal delivery tax)))
(-> (= total (round-money (+ subtotal delivery tax))) self-check-1)
```

Thus all five self-checks return true even though three quotes are wrong. These
are internal consistency checks, not independent tests of the pricing rules or
rush branch. The model did construct checks, but their coverage was insufficient.

Own question and shared-rule premises were absent from all five totals. Promotion
changes stayed isolated to order 5 and unrelated-note changes preserved values
and supports. Changing order 1's quantity produced contradictory downstream
values; withdrawing order 2's quantity also produced contradictory values rather
than unknown. Mutation interpretation is limited by the known-source baseline's
runtime failures described below. The initial rush arithmetic error is directly
visible in generated source and does not depend on those later failures.

[Integrity checks](../runtime/printshop-struct-live-20261106/integrity.edn) verify
one persistent worker/session/child, five real requests, unchanged sources, all ten
conversation messages and both reducers. The first context contained neither the
controlled quote function nor its expected answer. Full
[evidence](../runtime/printshop-struct-live-20261106/evidence.edn),
[per-check evaluation](../runtime/printshop-struct-live-20261106/evaluation.log), and
[first generated source](../runtime/printshop-struct-live-20261106/generated-1.lain)
are preserved. Copies of the probe and evaluator are retained with that evidence.

This run shows that exposing structs and a more complex problem did not elicit
operator abstraction in this session. The controlled implementation demonstrates
that a single callable can solve these five cases through the same interfaces.
It does not establish why the model chose repetition or how reliably it would
behave across more sessions.

## Response times

| Question | Provider response time |
|---|---:|
| 1 | 53.72 s |
| 2 | 30.83 s |
| 3 | 26.05 s |
| 4 | 30.67 s |
| 5 | 32.74 s |

Combined provider time was 174.02 seconds. These are file-timestamp
measurements bracketing each request; startup, admission and mutation checks are
excluded. Later calls were shorter despite no runtime callable reuse. The task
and number of questions differ from the inventory experiment, so their summed
times are not directly comparable. [Raw timing evidence](../runtime/printshop-struct-live-20261106/timing.json).

## Controlled baseline

The [controlled run](../runtime/printshop-struct-control-4/evidence.edn) completed
without provider requests. All 35 initial breakdown values matched the oracle,
all five consistency checks were true, and rounds 2–5 invoked the same `quote-fn`
with its declaration and captured environment preserved. Promotion changes were
isolated, quantity withdrawal/recovery worked for order 2, and unrelated-note
invariance passed.

Two mutation checks failed even with this known source: after order 1 changes
from 80 to 120, its production becomes 232, subtotal 257 and delivery zero, but
tax and total become unknown rather than yielding 277.56. Restoring question 1
later also leaves that total unknown. The evaluator deliberately retains these
failures; the controlled suite does not claim a full pass. The runtime cause has
not been diagnosed. Model failures on those transitions cannot be attributed
solely to model strategy.

The first development control also caught a generic adapter's handling of unknown
values; it was fixed before the completed baseline and before any live request.
No controlled source is supplied to the model. Detailed checks are in the
[control evaluation log](../runtime/printshop-struct-control-4/evaluation.log).

## Reproduction

```sh
LAIN_EVIDENCE_DIR="$PWD/runtime/printshop-control-new" tools/run_printshop_struct.sh control
clojure -M tools/evaluate_printshop_struct.clj runtime/printshop-control-new
LAIN_EVIDENCE_DIR="$PWD/runtime/printshop-live-new" tools/run_printshop_struct.sh live
clojure -M tools/evaluate_printshop_struct.clj runtime/printshop-live-new
```

Use fresh evidence directories. The isolated runner preserves unrelated local
changes and does not change or restart deployed services. One completed live
session makes five provider requests, with no repair prompts, retries or fallback
answers. This is a larger task with new struct and numeric interfaces, not a clean
causal comparison of prompt wording alone with the previous inventory runs.
