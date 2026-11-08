# November 8 publication and Tsuki delivery receipt

The user requested organization of existing design and experiment results, delivery to the Docker agent responsible for Tsuki, and publication of all current changes. `.lain` source is included in publication and the handoff; it is excluded from Clojure formatting/linting.

## Repositories

| Repository | Revision/status |
|---|---|
| Backend `Semi-0/datalog-research` | `90f74fc`: formatting-only correction. `3c3f643`: companion examples, supported profiler binding in its test, and waiting for committed async result in the managed-session test. Both are pushed to main; the latter follow-up passes 14 tests / 145 assertions and changes no kernel implementation. |
| Editor `Semi-0/lain-editor` | `602eb67`: checkout clean; no new editor change to commit. |
| Heuristic / `Semi-0/lain-harness` | Source, documentation, corrected public-session probes, and evidence being published in this handoff. Its Git remote may redirect to `Semi-0/heuristic`. |
| Companion `lain-harness-runtime` | Test fixture checkout at `2773dff`; no tracked changes or release commit. Local `.env`, `.env.local`, and `.pi` are private/unrelated and are preserved outside Git. |

The backend and editor have no submodule entries. The Docker agent's Tsuki/Xiaobao worktree is separate and is not reset, updated, committed, or deployed by this publication.

## Evidence and verification

The [research index](research-status-2026-11-08.md) is the acceptance summary. The new large evidence artifacts are preserved as deterministic gzip files with hashes of both original and archive in `runtime/evidence-archive-20261108.json`. Originals remain locally available and ignored; `tools/archive_experiment_evidence.py verify` validates the archives and `restore` recreates original paths in a fresh checkout. Historical source and provider responses are not rewritten to pass checks.

Automatic approval review blocked the initial 1,159-file publication because of the breadth and size of runtime evidence. The safer publication is source, documentation, the hash manifest and selected small evidence records (about 0.82 MB before Git compression). The bulk archives remain local and are included in the Docker reference bundle; their remote publication is awaiting the user's scope choice. Restore in a fresh checkout requires obtaining those archives first. No blocked push is retried indirectly.

Focused verified results include session-API contracts (3 tests / 36 assertions), runtime composition (2 / 15), behavior (17 / 292), operator examples (3 / 108), and model IO (6 / 45). The recovery module still has two timeout errors, and the latest full control does not complete round one. This publication does not convert those experimental failures into acceptance.

## On-machine CI/CD

The GX10 pull service on this machine picked up backend `90f74fc` with editor `602eb67`. Formatting and lint completed; production tests reported 9,863 passing assertions, three failures and one error. They identified the companion guide's missing section, an outdated profiling test hook and an async queue-versus-commit race in a managed-session test. The isolated follow-up at `3c3f643` fixes those tests/documentation, passes the three affected suites (14 tests / 145 assertions), and passes formatting/lint. A new pipeline attempt is being monitored. This pipeline gates the backend/editor release pair; it is not a CI gate for the separate heuristic research harness. The completed run receipt and outcome will be appended after inspection. No manual replacement of the live backend/frontend is used.

## Tsuki delivery

The existing recipient was identified in container `friend-workspace`, tmux `han:3.0`, working in `/workspace`. The intended package location is `/workspace/inbox/2026-11-08-heuristic-reference/`. Existing inbox documents and Tsuki task files are preserved. The handoff respects its current test freeze and asks for a separate `TSUKI-REVIEW.md` with observed/proposed/unresolved labels, for joint review in several days. Package hash, exact source revision, notification, and any acknowledgement will be appended after delivery.
