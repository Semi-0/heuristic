# Tool reuse under dependency selection

Launched at approximately 22:00 UTC on 2026-11-26. The isolated real-provider experiment uses three fresh Soul workers in succession. In each episode Flash writes three executable definitions. The harness admits them once in a restricted lexical frame and checks each on three inputs. All arms share those definitions and the same immutable order records. Aster's original and revised orders need the same sheet conversion and different tariffs. Eighteen unrelated orders compete for attention. Questions are separately dispatched, with counterbalanced arm order and alternating question order. This tests overlapping subject matter. There are no simultaneous in-flight answers.

Full receives all archive bodies. Selected receives a Datomic index and can request additional IDs with their transitive dependencies. Oracle receives the independently specified order and its dependencies. No arm invokes history compaction. Scores cover the computed cents, executable references to the original functions, exclusive choice of tariff, and exact evidence identification. The benchmark records provider prompt and completion tokens across all requests, elapsed wall time for each challenge, and separate shared setup costs. The local worker configuration is Flash, temperature 1, thinking enabled, xhigh, 32,768 completion tokens per call.

Workspace base 03a95792454233fcdc9aeebdb25c1642af98bce6, runtime c33c2111a43cd2d1a0140a62df8312848e585d5a, compiler 343f08d1241b0e23549c0425dcdae9f7a0d8bff0. Uses current working-tree implementations. [Experiment source](../experiments/dependency-context/reuse.clj).

Run with `experiments/dependency-context/run.sh reuse /absolute/path/to/new-evidence-directory`. Each episode saves setup and challenge packets and receipts, the Datomic archive, and per-question scores. The score denominator includes every scheduled challenge. Setup and challenge timings include compiler checks and worker waits, and exclude JVM startup and archive seeding. Generated tokens are the provider's completion count, including reasoning. The full and oracle arms receive ID, body and parents for each supplied event. The selected arm also sees index labels. The fixture supplies the dependency edges. This experiment tests recognition, retrieval and callable use with a complete index. It does not test learning missing edges or creating the index from raw conversations.

## Measured results

| Context | Correct and supported original-tool reuse | Calls | Total prompt tokens | Total generated tokens | Total seconds | Median seconds per answer |
|---|---:|---:|---:|---:|---:|---:|
| Full | 6/6 | 6 | 7,755 | 13,403 | 385.8 | 50.6 |
| Selected | 6/6 | 12 | 8,205 | 38,089 | 1,128.9 | 155.4 |
| Oracle | 6/6 | 6 | 1,524 | 25,204 | 746.2 | 119.2 |

All three setup generations passed admission and all 27 execution checks. Shared setup took 35.6 seconds, 477 prompt tokens and 1,429 generated tokens across the three episodes. All six selected questions used one archive read before executing the appropriate two original functions. Initial selected packets were 632–635 tokens versus 1,291–1,294 for full history. Selected used 5.8% more cumulative input and 2.84 times the generated tokens. The dataset fits comfortably within the context window. The experiment leaves long-history costs and the need for a production compaction fallback open.

All 18 scheduled scores, independent expected amounts and provider usage sums were reconciled. The live JVM exited with a reader error after writing the last score. The outer launcher had been reindented while that JVM was still reading it. [Per-question scores and setup measurements](../experiments/dependency-context/reuse-measured.json). [Raw evidence](/home/lain/reports/lain-reuse-20261126).

A fresh JVM loaded the saved launcher and completed the separate scripted overlap-selected control with six passing questions and its mutation check, exit code 0.

## Time to first archive request

Elapsed provider-call seconds through receipt of the first valid read request, with generated tokens for that call. These measurements exclude archive resolution.

| Episode | Question | Seconds | Generated tokens |
|---|---|---:|---:|
| 1 | historical | 102.62 | 3,346 |
| 1 | revision | 119.71 | 3,906 |
| 2 | historical | 98.95 | 3,372 |
| 2 | revision | 64.70 | 2,173 |
| 3 | historical | 15.67 | 519 |
| 3 | revision | 251.49 | 8,335 |

Median across six reads: 100.78 seconds.
