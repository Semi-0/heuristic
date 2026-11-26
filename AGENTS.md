# Heuristic documentation maintenance

The current navigation starts at README.md. Keep docs/STATUS.md, docs/DESIGN.md, docs/PLAN.md and docs/ISSUES.md consistent with the work you perform.

- After changing architecture, environment/prompt construction, or capability boundaries, update DESIGN and link the actual source.
- After starting, completing, failing or cancelling an experiment, update its dated report and STATUS when handling that event. Mark a queued run with a timestamp/checkpoint; do not leave it described as active after observing termination.
- New direct Flash experiments inherit the worker sampling defaults (temperature 1.0, thinking on, 12,288 completion tokens per call). Record explicit sampling overrides for comparison arms.
- Record source/runtime pins, context and generation settings, controlled versus real-provider calls, acceptance criteria, outcomes and evidence locations. Distinguish measured results, code reading and inference.
- Update PLAN acceptance gates and ISSUES when evidence advances or limits a claim. A single successful trial is not a reliability estimate; source restoration is not suspended-state resumption; passing focused checks are not a full-suite result.
- Preserve dated failures and historical reports. Add a supersession pointer instead of rewriting history. Do not include large raw experiment artifacts or databases in documentation changes.
- Keep the README and docs index links valid. For documentation-only changes, check local links and diff whitespace; no JVM/provider run is needed.

This is an update-on-work convention, not an unattended monitoring service. User instructions and existing scope/authorization take precedence. Do not commit or push unless requested.
