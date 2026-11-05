Three-round real-model computational reuse experiment: PASS

One live model and one persistent lexical child handled all three rounds.
1. User: How much is (- 20 (* 100 30))?
   Generated: (define result-1 (- 20 (* 100 30)))
   Observed value: -2980; reply: The answer is -2980.
2. User: Divide your previous result by 20.
   Generated: (define result-2 (/ result-1 20))
   Observed value: -149; reply: The answer is -149.
3. User: Add 150 to that.
   Generated: (define result-3 (+ result-2 150))
   Observed value: 1; reply: The answer is 1.

Each generated response also connects its result to its fresh observed-N slot
and a formatted text reply to user-output-N. The parent temporal reducer records
actual question and reply values, in order. Another parent reducer records the
actual model-written source. Later environment descriptions include those
records and previously generated child bindings. There are three provider calls.

Checks: all numeric result cells, observation slots and replies match; later
result expressions reference the previous result symbol; traversal of compiled
input connections reaches the previous result cell; session/run identity and
lexical child identity remain unchanged; history accumulates six messages.
This establishes observed source and network reuse, not full causal provenance.

round-N.lain contains each actual model response. round-N-context.txt contains
the corresponding actual environment-derived request. evidence.edn records
provider requests/responses, receipts, result edges, reuse checks and history.
source.lain is the parent orchestration. The harness submits user records and
triggers sequential evaluation; it does not compute answers or assemble history.

This test used an isolated checkout at 08b0474 with source.lain as its
soul-refined.lain entry point, preserving the main working tree's model edits.
Implementation: examples/lain/arithmetic-reuse.lain and
 tools/probe_arithmetic_reuse.clj.
