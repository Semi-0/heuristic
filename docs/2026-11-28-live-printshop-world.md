# Five print-shop attempts in the active Soul editor

Started at 00:01:13 UTC on November 28, 2026, in the existing live editor run. The supervisor sent five progressively varied order batches through the Codex channel, one at a time. No run reset, alternate worker or reference solution was supplied between attempts. The first prompt provided the pricing rules and hinted that Lain could discover her running main loop. Later prompts rely on those earlier rules and give the previous score, missed order indices and elapsed time.

Each batch contains five orders in the same sequence of pricing branches. Quantities increase between batches so repeating the previous answers fails the check. The first batch uses the original print-shop quantities. The scorer uses decimal arithmetic, applies progressive tiers and discounts, and rounds the final invoice half up to cents. [Scorer and order fixtures](../experiments/printshop-live-world/score.py)

The saved acceptance measures are the five exact totals, wall time from journal acceptance to the final executed reply, observed provider usage, policy source or tool calls that Lain submitted, the runtime's dispatch world and route, and the automatic-compaction marker. A proposal to change a policy counts as a proposal until the runtime executes it. Later correct answers alone cannot establish that a policy change caused the improvement.

## Results

Lain returned all 25 invoices correctly. Every observed packet used the original world `w0` and its `direct` route. The run produced no receipt for a child, rewritten policy, child model, or automatic compaction. It provides no evidence of recursive improvement. [Prompts, replies, dispatches, and provider usage](../experiments/printshop-live-world/results.json)

| Attempt | Exact prices | Elapsed seconds | Dispatches | Prompt tokens | Generated tokens |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1 | 5/5 | 229.71 | 1 | 2,241 | 7,860 |
| 2 | 5/5 | 215.07 | 1 | 2,869 | 7,979 |
| 3 | 5/5 | 586.22 | 1 | 3,577 | 21,905 |
| 4 | 5/5 | 713.26 | 2 | 9,235 | 26,846 |
| 5 | 5/5 | 537.75 | 1 | 5,533 | 19,200 |

Attempts 1, 2, and 5 combined inspection and a terminal reply. No inspection result reached a subsequent model packet on those turns. Attempt 3 returned only the totals. Starting with attempt 3 the supervisor asked for a standalone call. Attempts 4 and 5 also included a worked example of inspecting, spawning, rewriting, comparing, and selecting a world. On attempt 4, a standalone inspection returned the actual parent heuristic, `(network [feedback timed-check] "direct")`, in the continuation. Lain then returned the totals. Token counts sum all dispatches in each attempt, including that continuation.

The live editor's rewrite accepts a replacement routing network over two flags. Its lexical children inherit the parent's heuristic. A child model requires a separate worker dispatch. There was no timed execution under a revised heuristic to compare with the parent. The prompts and quantities changed across attempts, correctness was already at ceiling, and the session never reached its automatic compaction threshold. These measurements cannot establish the effect of the example on speed or the quality of compaction.
