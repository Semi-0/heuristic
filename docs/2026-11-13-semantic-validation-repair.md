# Semantic validation and bounded candidate repair

This experiment follows the changed-policy arithmetic failure: the model computed
`base + delivery * surcharge`, omitting the delivery principal. Outputs were known
and supported, so the earlier missing-output contract accepted them as ready.

## Contract and boundaries

For this question's nonnegative delivery charge and surcharge, the revised quote
must not be lower than the historical quote; the historical quote must not be
lower than the event base. The natural-language rule is supplied in the
environment. Lain evaluates the corresponding inequalities after source admission
using actual observed outputs. No exact expected answer or corrected pricing
expression is sent to the model. The independent host evaluator still checks the
complete numerical results and exact premise dependencies.

These inequalities are necessary conditions, not a correctness proof. A formula
can satisfy them and still be wrong. They are checked at candidate selection;
this experiment does not establish continuous semantic monitoring after arbitrary
future input changes. The tested mutations have their own independent checks.

## Candidate isolation

A failed numerical candidate cannot be repaired simply by connecting a different
number to the same already-populated answer cell. This experiment avoids that
problem using existing lexical environments and output ports:

1. Initial generated source is admitted by the existing runtime into a child with
   isolated candidate outputs.
2. `semantic:attempt` observes those outputs after the admission receipt. It
   checks availability, the declared input domain, and the two inequalities.
3. A failed candidate triggers a Lain-built `system-message` containing source,
   admission timestamp, edge observations, actual failed checks, and budget.
4. The repair target is a lexical descendant of the previous candidate, with
   fresh output bindings. Its correct local intermediates are inherited; its
   rejected results remain inspectable evidence.
5. Only the selected candidate connects to public answer/reply and comparison
   ports. Two failed repairs produce a terminal failure with no publication.

Thus this is **repair before public delivery**, not retraction/replacement of an
already-delivered wrong answer. Repairs use inherited candidate children, rather
than the same child used in the earlier missing-connection experiment. This
choice is explicit and implemented entirely through existing lexical operations.
It does not delete or disable the rejected graph, and is not a security sandbox.

The main `soul-refined.lain`, existing model, compiler/admission machinery, native
observers, and kernel remain unchanged. `lib/semantic-repair.lain` and
`experiments/catalog-semantic.lain` own validation, eligibility, the two-repair
bound and publication. `environments/catalog-semantic.lain` supplies instructions.
The host supplies external inputs, records evidence, dispatches controlled test
responses where selected, and evaluates results. It never selects a repair stage
or injects a corrected answer into the live network.

## Verification modes

- `control`: replay recorded discovery/selection/wrong execution, then a
  handwritten repair into an inherited child.
- `clean`: replay an initially correct execution; no repair should be requested.
- `exhausted`: reconnect the wrong retained values twice; no candidate is delivered.
- `replay`: recorded base responses followed by real-provider correction(s).
- `fresh`: real-provider responses for all stages and any corrections.

The fixture README records source origins. Original recorded source is unedited.
These modes separate compiled control-flow verification, real repair of a known
failure, and fully fresh integration. Provider calls and total requests are
recorded separately.

## Results

Controlled trials compile the actual Lain orchestration through the public
session extension path:

| Trial | Requests / provider calls | Result |
| --- | --- | --- |
| `control-1` | 4 / 0 | 47/47 checks; rejects 821 < 859, accepts one correction |
| `clean-1` | 3 / 0 | 43/43 checks; correct candidate needs no repair |
| `exhausted-1` | 5 / 0 | 4/4 checks; two ineffective repairs, terminal failure, no publication |
| `real-repair-1` | 4 / 1 | 47/47 checks; real model corrects the replayed failure in one response |
| `fresh-1` | 3 / 3 | 43/43 checks; entirely fresh discovery, selection and execution, no repair |

The successful controls retain one restored event-call application and compute
revised totals 861 → 882 → 1008 → 1056 as external inputs change. Exact numerical
supports, separate question/policy withdrawal and restoration, unrelated-source
isolation, and no additional inference or admission all pass. Before every
repair request, the evaluator observes unknown public answer and reply ports.
The exhaustion control also verifies all four public ports remain unknown.

Session API tests pass 36 assertions and runtime composition tests pass 15,
with zero failures or errors. The new Clojure probe passes formatter and linter
checks with zero warnings; the shell runner passes `bash -n`. Lain sources are
verified by the compiled controls. These are focused checks, not a claim that
the entire repository test suite was rerun.

All five sessions closed normally. Evidence directories are under
`runtime/semantic-20261113/`; recorded requests, generated source, receipts,
candidate outcomes and public-port snapshots remain local.

### Real correction and fresh integration

Both provider trials use the existing `qwen3.8-flash-next` transport, temperature
0, maximum output 8192, and thinking enabled with medium effort and a 4096-token
thinking budget. No prompt revision or hand editing occurred between these trials.
The repair trial replays three recorded responses unchanged and calls the real
provider only for correction; the fresh trial calls it for every stage.

In `real-repair-1`, Lain detects revised 821 < historical 859, with base 819.
The model receives that failed constraint and the inherited environment. It emits
a new premise-wrapped closure with this body:

```lain
(network [base delivery surcharge] (+ base delivery (* delivery surcharge)))
```

It invokes that closure with the inherited `base-amount`, retains
`historical-amount`, and connects all four fresh candidate outputs. It does not
repeat the restored catalog invocation. One correction passes both the Lain
validator and all independent numerical/support/mutation checks. The incorrect
candidate remains inspectable but was never delivered.

In `fresh-1`, the model independently rejects the stored whole quote because its
delivery-after-surcharge assumption is incompatible with the new policy. It
selects the event-only component, invokes it once, and constructs distinct
historical and revised closures. Its revised body is `(+ b (* d (+ 1 s)))`.
All independent checks pass without a repair request.

These are single successful real trials, not a reliability estimate. The prompt
explicitly guides repair toward inherited correct intermediates and fresh output
ports. General unguided repair, nonnumeric invalid outputs, continuous validation,
and correction after public delivery remain unproved. The next useful experiment
is a wrong formula that passes these inequalities, to test a stronger executable
contract without giving the model a solved answer.

## Reproduction

```bash
LAIN_SEMANTIC_MODE=replay \
LAIN_POLICY_THINKING=1 \
LAIN_ACCUMULATED_TRIAL=/absolute/path/closed-successful-accumulation \
LAIN_EVIDENCE_DIR=/absolute/path/fresh-semantic-trial \
bash tools/run_catalog_semantic.sh
```

Use one of the modes above and a fresh evidence directory per run. The runner
copies the closed database and snapshots source. Large evidence stays local under
ignored `runtime/semantic-20261113/`; no deployed service is restarted.
