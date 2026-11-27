# Autocomplete project experiment

Started 04:06 UTC on November 27. Three isolated project agents each receive the same committed compiler and editor sources, four successive tickets and private writable files. The committed editor uses textarea completion; the newer uncommitted CodeMirror work in the main checkout is outside the snapshots. The benchmark extends the existing editing module. All four hidden behavioral gates initially fail; the original editor editing suite must pass unchanged on a clean final checkout.

The four tickets cover prefix completion from actual definitions, lexical scope in incomplete networks, fresh candidates across panes and child environments, and rejecting stale async completions after child/buffer changes. The UI is exercised through its real install/keyboard/fetch code against a deterministic DOM and session gateway fixture. This isolates client behavior: it does not test deployment or the server implementation of live binding enumeration. The model can inspect the pinned compiler. Hidden checks score observable results, with no citation requirement.

The full, editor and selected arms run sequentially on Flash xhigh, temperature 1, at most 12,288 completion tokens per dispatch. Each ticket gets up to eight dispatches with batched actions, including submission, with the same tools in every arm. Tickets unlock in order even if an earlier one used its budget. Strict full history stops on actual tokenized input plus allowance above 131,072. Editor uses its existing automatic projection. Selected uses FTS5, 0.6B reranking, graph expansion of self-produced events, and the latest tool feedback, with a bounded evidence packet. Sources are never changed in the live editor. Timing includes model generations, context assembly and file/test actions. Startup and final independent verification are separate. [Driver](../experiments/autocomplete-project/run.py) and [frozen behavioral checks](../experiments/autocomplete-project/gates.mjs).

## Pilot corrections

Three transport issues surfaced before the scored comparison. A large read exceeded Datomic Local’s item size, the bridge rejected escaped Unicode, and a journal display label caused valid action arrays to be rejected. The driver now archives descriptions and edges in Datomic, resolves exact payloads from its journal, transmits UTF-8 directly, and accepts the known action envelope. Read-only calls were replayed during pilot debugging. The scored comparison started from fresh snapshots and fresh model calls for every policy. All pilot costs are excluded. A separate private positive control passed the two asynchronous gates.

## Measurement

The context window applies to each request individually. Cumulative prompt tokens count repeated input on every dispatch. The driver checks the actual tokenizer count plus the reserved 12,288 output tokens before dispatch. The editor’s own projection triggers above 98,304 estimated tokens and budgets 20,000 estimated tokens for its projected packet. The provider reports cached prompt tokens. The arms run in a fixed order with shared provider infrastructure, and cache state is uncontrolled.

Full history completed all four tickets and the immutable original editing tests on a clean checkout. The largest request was 59,933 input tokens. The run took 50.5 minutes, generated 102,166 tokens and consumed 1,153,416 cumulative prompt tokens, including 1,015,168 reported cached tokens. Its patch includes regression tests for changing the child and editing the buffer during an outstanding request.

The editor policy passed prefix completion at the first checkpoint. The lexical change in ticket two regressed it. Independent reruns on the clean final checkout reproduced both failures. The editor offered `azStray` from an ordinary call and `azExpired` from a closed network. Fresh pane/child candidates and stale-reply rejection both passed. The policy never triggered compaction. Full and editor are independent generations with the same below-threshold context policy, so their different outcomes illustrate run-to-run variation.

## Final results

Each run used 32 real Flash dispatches. On separate clean checkouts, every patch passed the immutable original editing suite. Each also passed its own editing suite including agent-written race regressions (18, 14 and 10 tests respectively).

| Context policy | Final tickets passed | Wall minutes | Model minutes | Context assembly minutes | Total input tokens | Peak input | Generated tokens |
|---|---|---:|---:|---:|---:|---:|---:|
| Full history | 1, 2, 3, 4 | 50.5 | 50.1 | <0.01 | 1,153,416 | 59,933 | 102,166 |
| Editor auto-compaction | 3, 4 | 52.9 | 52.7 | <0.01 | 984,457 | 53,615 | 108,134 |
| Dependency selection | 1, 4 | 82.1 | 48.9 | 33.0 | 397,990 | 17,845 | 87,553 |

Selection cut cumulative input by 65.5% and generated tokens by 14.3% against full history. Its context assembly took 1,981 seconds across 32 requests, including retrieval, CPU reranking and graph expansion. This combined timer cannot attribute the overhead to an individual component. Prefix caching also differed substantially. The provider reported 1,015,168 cached prompt tokens for full, 847,072 for editor and 49,440 for selected.

On ticket two the selected agent spent seven calls on discovery and retrieval. The last call reached its generation limit before an edit. On ticket three it again exhausted the last generation after discovery. The final implementation exposed a closed sibling binding (`azGone`) and omitted the current child (`red`) in a completion request. The late-reply safety tests still passed. The final score concerns four narrowly specified behaviors, with one trajectory per strategy and no hard overflow or automatic compaction. Whether selection helps after repeated compactions remains open.

The reconciled [per-ticket timings and measurements](../experiments/autocomplete-project/measured.json) include output-limit counts. The compiler pin was `343f08d` and the editor pin was `bf181c4`. On the experiment host, `/home/lain/reports/lain-autocomplete-project-scored-20261127/{full,editor,selected}/` contains the exact request packets, call receipts, ordered tool events, git patch, isolated source tree and clean verification checkout. The experimental changes were confined to those private projects.
