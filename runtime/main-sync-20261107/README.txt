Local project synchronization, 2026-11-07.

Runtime work was merged without conflicts into current datalog-research/main.
Focused runtime tests passed: 16 tests / 300 assertions.
Merged-main tests passed: 45 tests / 488 assertions (temporal, TTMS, stream and immutable sessions).
Harness model-behavior tests passed: 5 tests / 30 assertions.
Broader harness checks are NOT fully passing:
- legacy model: 1 test / 2 assertions; one pending-state timeout at settled turn 1.
- soul: 9 tests / 46 assertions; one 30-second propagation timeout in reducer-source-in-live-admission-target.
These are retained failures, not release acceptance. No deployed service was restarted.

The edited interactive model-refined source is preserved under
examples/lain/saved/model-refined-session-20261107.lain; definitions-only import
was restored to preserve the existing experiment contract.
The legacy model module test now excludes the separate environment-only soul bootstrap;
it still exposes the pending-state timeout documented above.

The editor had no uncommitted changes and was fast-forwarded to remote main.
The two runtime working copies share the datalog-research remote.
No registered Git submodules were found in these project roots.
The benchmark node_modules absolute symlink is a local dependency and is ignored.
Two old network dumps above 100 MB were losslessly archived as .edn.gz; raw local copies remain ignored.
