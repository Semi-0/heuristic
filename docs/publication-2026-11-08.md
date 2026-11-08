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

Automatic approval review blocked the initial 1,159-file publication because of the breadth and size of runtime evidence. The user then explicitly chose not to upload large experiments, only their documentation. Source, documentation, the hash manifest and selected small evidence records were pushed in heuristic commit `c3ba1a1` (116 files, about 0.82 MB before Git compression). Bulk archives stay local and are included in the Docker reference bundle; local-only artifact directories are ignored to prevent accidental bulk staging. Restore in a fresh checkout requires obtaining those archives first. No blocked push was retried indirectly.

Focused verified results include session-API contracts (3 tests / 36 assertions), runtime composition (2 / 15), behavior (17 / 292), operator examples (3 / 108), and model IO (6 / 45). The recovery module still has two timeout errors, and the latest full control does not complete round one. This publication does not convert those experimental failures into acceptance.

## On-machine CI/CD

The GX10 pull service on this machine picked up backend `90f74fc` with editor `602eb67`. Formatting and lint completed; production tests reported 9,863 passing assertions, three failures and one error. They identified the companion guide's missing section, an outdated profiling test hook and an async queue-versus-commit race in a managed-session test. The isolated follow-up at `3c3f643` fixes those tests/documentation and passes the three affected suites (14 tests / 145 assertions), formatting and lint.

The subsequent full on-machine pipeline **passed and activated** backend `3c3f643b694aa2ef2ec85f239412a8d5627f717a` with editor `602eb67f70020982ae56a6e1522fa4d119889c64`. Production runtime tests report **9,867 passing assertions, zero failures and zero errors**. The later ClojureScript, Rust, Python, Node, frontend and browser acceptance stages completed under the pipeline's policy. One optional Caddy test was skipped; it is not reported as a tested authentication deployment. Browser acceptance reports no browser errors. The deployment controller's last-attempt status is `active`, `current/release.json` says `verified`, and both backend/frontend user services are active/running. Release directory: `ee325c115d3d41d09b8d981d6e928871`.

GitHub confirms `gx10/ci=success` and `gx10/deploy=success` for backend `3c3f643`. Updating the unchanged editor commit's deployment status hit GitHub's maximum number of statuses for that SHA/context; its GitHub display may therefore be stale. This reporting limitation did not block the on-machine verified activation. Detailed journal and JSON receipts remain local under `runtime/unchanged-kernel-audit-20261108/publication-ci-final*`.

This pipeline gates the backend/editor release pair; it is not a CI gate for the separate heuristic research harness, whose recovery limitation is still documented. No manual replacement of live services was used; activation and retention were performed by the existing deployment controller.

## Tsuki delivery

The existing recipient was identified in container `friend-workspace`, tmux `han:3.0`, working in `/workspace`. The complete package was delivered to `/workspace/inbox/2026-11-08-heuristic-reference/`; existing inbox documents and Tsuki task files were preserved. It contains 1,442 payload files, including 307 `.lain` files, and a 122,167,444-byte compressed package. Package SHA-256: `2e0b440c39bb6ecbd8fb41533e61b291ca5680526e50be412f23064eac1301bc`. Source snapshot: heuristic `c3ba1a1b7a675f0f515c9968592c25025ac66659`; backend reference `3c3f643b694aa2ef2ec85f239412a8d5627f717a`.

The notification was submitted to the existing agent. It acknowledged reading the reference and returned [TSUKI-REVIEW.md](handoffs/2026-11-08-tsuki-review.md), distinguishing observed, proposed and unresolved items. It reported no test execution, project changes, deployment or scheduled tasks. The original review stays beside the local package; its content and hash are preserved in the linked document for joint review in several days. In particular, it proposes a support-only/state-aware publication comparison and a receipt-to-repair contract, without claiming either has been executed.

Concurrent type-checking/formatting edits appeared in the canonical backend checkout during publication. They were left untouched; the CI follow-up was committed from an isolated worktree. They are not silently included in this publication or in the backend revision being tested.
