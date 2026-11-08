Unchanged-kernel verification. See docs/2026-11-08-unchanged-kernel-cross-check.md for conclusions and scope.

Primary results: native-cross-check.log (existing native suites); native-slot-valid-cross-check.log (valid native slot/closure probes); reconvergence-usage.log (seven four-stage arithmetic comparisons); context-barrier-usage.log (immediate/delayed context comparisons); recovery-context-barrier.log (5 tests, 11 passing assertions, 2 timeout errors); recovery-first-diagnostic.log (actual failed admission and retained-prefix receipts); model-io.log (authorized loopback rerun, all pass); behavior.log; operator-examples.log; runtime-composition.log; harness-suites.log (legacy setup failures/timeouts and sandbox HTTP binding error).

Excluded exploratory logs: closure-slot-cross-check.log omitted combinator bindings; closure-with-library-cross-check.log supplied multiple forms to a single-expression fixture; context-usage.log had missing premise bindings and was edited while loading. They are not evidence of kernel failure. context-valid-usage.log is superseded by context-barrier-usage.log.

No new live provider trial. The controlled full-size recovery attempts are in the sibling unchanged-kernel-printshop directories; both timed out in round one and closed cleanly. No full mutation acceptance is established.
