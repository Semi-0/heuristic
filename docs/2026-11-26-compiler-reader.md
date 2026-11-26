# Flash repair using the standalone Lain compiler

The pricing draft lacks the final parenthesis of its outer definition. The
experiment's reader action and submission path call the same standalone
compiler function. The compiler reports the insertion position at column 82
and the opening parenthesis at column 1. Flash receives its diagnostic,
source excerpt and caret in the next request through the Lain turn network.

The guided session supplies the first `CLI lain-read` action. The locally
served `qwen3.8-flash-next` chooses the next action at temperature 1 with
thinking enabled and 20,480 completion tokens per call. Only source submitted
by Flash can pass. The live session admits it and calls the resulting function
at 79, 100, 107 and 143 copies.

[Run the experiment](../experiments/attempt-cli/README.md) ·
[Lain orchestration](../experiments/attempt-cli/runtime.lain) ·
[Standalone compiler source](https://github.com/Semi-0/lain-compiler/blob/343f08d1241b0e23549c0425dcdae9f7a0d8bff0/src/propagators/compiler/language/reader.clj)

On November 26 the actual compiler receipt reached Flash. Its first live
response submitted a balanced definition and the public session admitted it.
All four calls returned the expected values (15,800, 20,000, 21,050 and
26,450 cents). The single provider call returned HTTP 200 and used 9,519
completion tokens, including 9,469 reasoning tokens. The scripted reader
action made no provider call. This demonstrates direct compiler feedback on
one draft. The receipts are in
`/home/lain/reports/lain-compiler-diagnostics-20261126/flash/repair-cli/`.
