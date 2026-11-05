Real-model arithmetic round with parent input, observation and output cells

Result: PASS. ask = "How much is (- 20 (* 100 30))?"
The real model generated the arithmetic expression and connections in generated.lain.
The compiled observed :result slot contains -2980.
user-output contains "The answer is -2980."
The parent's temporal source/scan accumulated the actual user input and output
as ordered role/content records. Generated code and thinking are not messages.

The existing Lain slot syntax tested here is ((slot :result) observed).
There was one provider request, one live model and one lexical child. This is
one arithmetic round, not acceptance of the earlier three-round conversation.
The preceding control probe used a fixed value solely to test cross-scope ports;
only this directory records the real-model acceptance result.

context.txt is the actual environment-derived request text. response.edn stores
provider source and separate thinking. result.edn contains observations, actual
history, admission receipts and the recorded request. source.lain is the parent
orchestration loaded for this run.

Implementation: examples/lain/arithmetic-user-round.lain and
 tools/probe_arithmetic_round.clj.
Reproduction: use a clean isolated checkout with the committed definitions-only
model-refined.lain; copy the example to that checkout's soul-refined.lain.
Set LAIN_HARNESS_ROOT to that checkout and LAIN_OFFLINE_UUID=1. Run the probe with
the companion runtime's Clojure dependencies, adding the checkout's src and the
runtime's editor/src to the classpath. LAIN_CONTROLLED=1 selects the port test.
The probe currently records evidence under the original workspace's runtime.
