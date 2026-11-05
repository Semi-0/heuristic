# From arithmetic to reusable, premise-supported networks

The real model generated a three-turn arithmetic chain that reused earlier cells.
It also generated an inventory network whose answers carried the required TTMS
premises and changed correctly when inputs or question premises were revised or
withdrawn. These are narrower successes than the original conversation experiment:
the parent supplied history and orchestration, and replies were observed through
output cells. They do not establish the earlier goal of having the model construct
all conversation infrastructure itself.

## Experiment record

| Experiment | Outcome | Evidence |
|---|---|---|
| Conversation with readable environment descriptions, attempt 14 | Orientation produced 5. Construction invoked the model but omitted user-history ingress; the empty messages vector failed validation. No conversation replies. | [Record](../runtime/soul-attempt-14/README.txt), [generated source](../runtime/soul-attempt-14/soul-response-2.lain) |
| Simple arithmetic | Real model generated `(* (+ 17 25) 3)`; compiled result was 126. | [Source](../runtime/arithmetic-attempt-1/generated.lain), [result](../runtime/arithmetic-attempt-1/result.edn) |
| Arithmetic with input, observation and reply cells | `(- 20 (* 100 30))` produced −2980, a text reply and parent-maintained conversation history. | [Record](../runtime/arithmetic-round-1/README.txt) |
| Three-turn arithmetic reuse | −2980 → −149 → 1 in one model session and lexical child. Later code referenced earlier result cells; compiled input connections reached those cells. | [Record](../runtime/arithmetic-reuse-1/README.txt), [full evidence](../runtime/arithmetic-reuse-1/evidence.edn) |
| Controlled TTMS inventory | Known source verified the adapters, exact premise sets, correction, withdrawal and recovery. This was an operator test, not real-model acceptance. | [Log](../runtime/premise-inventory-control.log), [evidence](../runtime/premise-inventory-control/evidence.edn) |
| First real TTMS attempt | Markdown fences caused rejection before admission. Source was not stripped or repaired by the host. | [Source](../runtime/premise-inventory-live/generated.lain), [log](../runtime/premise-inventory-live.log) |
| TTMS retry with prior source and diagnostic in environment | Real model returned raw Lain; all mutation and support checks passed. | [Record](../runtime/premise-inventory-live-2/README.txt) |
| Fresh TTMS attempt with dedicated output-format context | Valid raw source, but margin omitted order-question/rule premises, and a child definition shadowed the parent output. | [Record](../runtime/premise-inventory-live-3/README.txt) |
| Fresh attempt with general methodology | Correct arithmetic and required premise sets; still shadowed the reply port. | [Source](../runtime/premise-inventory-methodology-1/generated.lain), [log](../runtime/premise-inventory-methodology-1.log) |
| Methodology plus clarified output contract | Passed all required numeric, support and mutation checks. No prior solution or repair-specific diagnostic was supplied. | [Record](../runtime/premise-inventory-methodology-2/README.txt), [readable snapshots](../runtime/premise-inventory-methodology-2/summary.txt) |

The records retain actual provider contexts, unmodified generated code, separate
provider thinking, admission receipts and inspected results. Failed attempts are
included to distinguish numerical success, syntactic admission and semantic
acceptance. All live probes closed their sessions; no service restart was required.

## Reuse across questions

The model produced these definitions in separate rounds:

```lain
(define result-1 (- 20 (* 100 30)))
(define result-2 (/ result-1 20))
(define result-3 (+ result-2 150))
```

Each response also connected its value to its own observation slot and formatted
reply port. The parent used temporal reducers to retain actual user/reply records
and model-written source. Later requests contained those records and existing
child bindings. Acceptance checked values, replies, source references, compiled
input reachability, stable session/run and child identities, and exactly three
requests. This checks computational reuse; graph reachability alone does not
prove a complete causal provenance chain.

Implementation: [parent Lain](../examples/lain/arithmetic-reuse.lain) and
[probe](../tools/probe_arithmetic_reuse.clj).

## Question ownership and input support

The inventory inputs were 20 boxes, 12 items per box, an initially unverified
10% damage estimate, and an order quantity of 200. Premises have distinct roles:

- `question-stock` and `question-order` identify the requests that motivated the computations.
- `definition-stock` and `definition-order` identify the generated rules. Their presence does not independently validate the rules.
- Inventory and order inputs carry their own premises and revisions.
- `delivery-note` is unrelated and must not occur in these answers' support.

The adapter [premise_experiment.clj](../src/lain_harness/premise_experiment.clj)
reuses the existing runtime TTMS scalar operators and `ttms-premise-closure`.
It adds qualified bindings and a finite `inspect:premises` view; it does not
implement a new support algebra, backtracking policy or scheduler.
The parent environment exposes supported arithmetic under ordinary arithmetic
names. Generated functions attach question and definition premises through
closures; ordinary invocation carries these into their outputs.

The successful real model generated a stock function and an order-margin
function. `usable` carried exactly the stock question/rule and relevant inventory
inputs. `margin` and `can-fill` additionally carried the order question/rule and
quantity. The text reply carried the dependencies of its used values.

| Change to the running network | Usable | Margin | Can fulfill? |
|---|---:|---:|---|
| Initial 10% estimate | 216 | 16 | true |
| Confirmed damage revised to 25% | 180 | −20 | false |
| Unrelated delivery note changes | 180 | −20 | false |
| Damage premise withdrawn | unknown | unknown | unknown |
| Damage premise restored | 180 | −20 | false |
| Only order question withdrawn | 180 | unknown | unknown |
| Order question restored | 180 | −20 | false |

The reply became unknown when its required premises were withdrawn, and its
qualification changed with the supplied `damage-status` metadata. The unrelated
change preserved the full inspected snapshots. These transitions used the already
compiled network, without further model requests.

An active premise is not a probability or a guarantee that its claim is true.
The experiment supplies the estimate/inspection labels; it does not demonstrate
that the model independently inferred source reliability. Tests checked the
formulas for these cases. Definition withdrawal, stock-question withdrawal,
conflicting reports and an agent-generated backtracking search are not established
by this probe. Definitions remain installed; the verified withdrawal concerns
result availability. Full response-to-definition causal provenance is still open.

Implementation: [parent Lain](../examples/lain/premise-inventory.lain) and
[probe](../tools/probe_premise_inventory.clj).

## General methodology and output contracts

[problem-methodology.lain](../lib/problem-methodology.lain) contributes a literal
environment binding with four general stages:

1. Identify the problem; separate known conditions, assumptions and unknowns.
2. Abstract quantities, units, relations and dependencies.
3. Reduce unknowns to smaller computations and reuse existing results.
4. Solve, reconstruct and verify against the original task and assumptions.

The role and methodology precede the output-format and task sections. The output
contract now distinguishes **new child result bindings** from the **existing
parent text-output port**. A child definition named `user-output` shadows that
port; the agent must instead connect its text reply to the existing binding.
This clarification describes the interface, not a corrected implementation.

The successful fresh context contained the task, operator documentation,
methodology and interface contract. It contained neither the previous generated
answer nor a repair-specific diagnostic. One success does not establish that the
methodology alone caused improvement: the output contract also changed.
The [actual successful context](../runtime/premise-inventory-methodology-2/context.txt)
and [unmodified response](../runtime/premise-inventory-methodology-2/generated.lain)
make that distinction reviewable.

## Local reproduction

Use a clean export so local model-demo edits cannot instantiate an extra model.
The probes use the companion `lain-harness-runtime` dependencies and the locally
configured Blazux endpoint. These commands make real provider requests.
From this repository root, run the inventory experiment with:

```sh
harness_root=$PWD
experiment_root=$(mktemp -d /tmp/lain-inventory.XXXXXX)
lain_runtime_root=${LAIN_RUNTIME_ROOT:-"$(dirname "$harness_root")/lain-harness-runtime"}
git archive HEAD | tar -x -C "$experiment_root"
cp "$experiment_root/examples/lain/premise-inventory.lain" "$experiment_root/soul-refined.lain"
export LAIN_HARNESS_ROOT="$experiment_root"
export LAIN_EVIDENCE_DIR="$experiment_root/runtime/inventory-new"
export LAIN_OFFLINE_UUID=1
cd "$lain_runtime_root"
clojure -J-Xmx2g -Sdeps "{:aliases {:experiment {:extra-paths [\"$experiment_root/src\" \"editor/src\"]}}}" \
  -M:experiment "$experiment_root/tools/probe_premise_inventory.clj"
```

Set `LAIN_CONTROLLED=1` for the controlled port/support test instead of a provider
call. For three-turn arithmetic, use `examples/lain/arithmetic-reuse.lain` as the
entry point and `tools/probe_arithmetic_reuse.clj` as the probe. Choose a fresh
`LAIN_EVIDENCE_DIR` for each run to retain previous evidence.

The recorded runs used an isolated harness based on `08b0474` plus the respective
experiment source, against the local companion runtime checkout. That runtime
has local changes; these records are not a clean-upstream compatibility claim.
