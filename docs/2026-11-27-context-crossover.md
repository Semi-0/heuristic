# Direct environment-code lookup and mixed-history crossover

Live experiment started November 27 in an isolated Soul compiler session. Three direct lookups have no prior conversation or order→function edge. The mixed trial uses two composite questions at each of three increasing, nested history checkpoints, with all three context policies receiving identical archived events. A four-category synthetic workload interleaves price amendments, compiler repairs, arithmetic corrections and CLI receipts. Real 0.6B retrieval ranks at most eight FTS5 candidates and expands two roots using Datomic; the direct lookup takes one root. The editor arm evaluates the editor checkout's actual request projection and journal serialization. Flash uses temperature 1, thinking enabled, xhigh and 12,288 completion tokens per call in all arms. Every answer must execute an installed declaration, match the expected integer and cite exactly the required visible evidence. All policies can search and read. [Runner](../experiments/context-crossover/run.py)

The live model is qwen3.8-flash-next. The GPU worker and its session transport use the same model and thinking settings as the preceding experiment with a uniform 12,288-token generation allowance for long packets. Workspace base c306723, compiler 343f08d1241b0e23549c0425dcdae9f7a0d8bff0, runtime c33c2111a43cd2d1a0140a62df8312848e585d5a. The bridge evaluates the verbatim four projection functions from the editor checkout (SHA-256 7dff0c3784502c3faab9178f4a944932ce13727a07ca94e5ce4caf5cea72a67e), using its journal serializer (2eb44148dbd62648aa8840fca6907f315bccf6e47bc27297448e5b2af6421c1f). This is a packet projection experiment rather than a browser/UI test.

Direct lookup completed before an import error in the mixed fixture. The historical and superseding orders were in the same transaction; a lookup reference could not resolve an entity being created. Transaction-local IDs now connect in-batch parents. The mixed test resumed from a fresh archive with the same fixture. Direct answers were not resampled.

## Results

| Checkpoint | Policy | Correct reuse | Exact support + correct reuse | Mean seconds | Initial input tokens | Generated tokens | Flash calls |
|---|---|---:|---:|---:|---:|---:|---:|
| files | lookup | 3/3 | 0/3 | 24.9 | 221–228 | 2,424 | 3 |
| short | full | 2/2 | 1/2 | 88.5 | 1,749–1,756 | 6,205 | 2 |
| short | editor | 2/2 | 2/2 | 53.1 | 1,749–1,756 | 3,712 | 2 |
| short | selected | 2/2 | 1/2 | 126.8 | 308–332 | 8,143 | 2 |
| middle | editor | 2/2 | 2/2 | 83.6 | 20,812–20,819 | 5,741 | 2 |
| middle | selected | 2/2 | 0/2 | 213.6 | 308–332 | 14,219 | 2 |
| middle | full | 2/2 | 1/2 | 49.8 | 20,812–20,819 | 3,766 | 2 |
| long | selected | 2/2 | 1/2 | 97.6 | 308–332 | 6,134 | 2 |
| long | full | 2/2 | 2/2 | 92.9 | 91,366–91,373 | 5,913 | 2 |
| long | editor | 2/2 | 2/2 | 58.4 | 17,407–17,414 | 3,678 | 6 |

The three file-only queries retrieved and invoked the correct live declarations on the first attempt. Variable-price invoicing executed (linear-v1 37 19) for 703 cents; the explicitly superseded tariff executed (legacy-v1 43) for 86 cents; the scanner executed (append-digit 714 3) for 7143. Each response gave a file path or symbol in addition to, or instead of, the exact archive ID. All three failed the prespecified exact-evidence check. The catalog was seeded from four declarations in two existing fixture files, with curated descriptions. There was no prior answer, conversation or dependency edge in this condition.

The mixed checkpoints contained 26, 295 and 1,275 records. At every checkpoint the first-ranked record was the correct root for both questions. Its transitive closure contained all required evidence. Each question's selected provider messages were byte-identical across the three lengths. Selection used 1,920 cumulative input tokens across six answers, versus 227,875 for full and 150,823 for editor including recovery. All 18 arithmetic results and executions were correct. Strict citation passes were 4/6 for full, 6/6 for editor and 2/6 for selection. Selected misses included substituting a callable name or file path for an archive ID. Selected messages show a compact ID, label and body; full/editor use speaker and event headers. One cannot attribute citation differences solely to the selection algorithm.

At the long checkpoint the editor's UTF-16 estimate crossed 98,304 even though the provider counted about 91,370 actual tokens. Both compacted initial requests had lost all target records. Both answers recovered them via the common search/read interface. Editor spent about 0.02 seconds projecting the two long initial packets; the six subsequent and initial Flash calls together are included in its 58.4-second mean per answer. Initial context construction over all six questions took 52.24 seconds for shortlist/rerank/expand, 0.08 for full and 0.04 for editor. These construction timings include bridge IPC. Session startup, one-time ingestion, FTS updates and installing declarations are excluded. Prior history is a fixed replay; this measures cumulative projection/dispatch work on the six sampled questions, not solving every background event.

We observed no mean latency crossover in favor of selection through the longest tested history. It ran with bounded observed input and required zero compactions. The matching full and editor packets below the threshold produced different generation times; stochastic output and prefix caching limit any inference from two questions per checkpoint. For example, one long full-history call reused 18,128 cached tokens. This experiment has no uncached-only or repeated-sampling control. [Per-answer evidence and timing](../experiments/context-crossover/measured.json).

Run the Python driver from the repository root with a new absolute output directory. Set LAIN_EDITOR_ROOT to the editor checkout containing the projection, LAIN_RERANK_MODEL to the pinned Qwen3-Reranker-0.6B directory and LAIN_RERANK_PYTHON to its interpreter as described in the [reranker report](2026-11-27-reranker.md). Use the summarizer with the raw report directories to verify token counts against provider receipts. [Direct packets](/home/lain/reports/lain-crossover-live-20261127/bridge) and [mixed-history packets](/home/lain/reports/lain-crossover-mixed-20261127/bridge).
