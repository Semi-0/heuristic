Fresh environment-only output-format experiment

A dedicated output-format-cell was included immediately after role-cell.
No previous model answer or repair-specific diagnostic was included in this
fresh request. The response was passed unchanged to the source admission reader.

Formatting: PASS. Raw Lain forms were returned, without Markdown fences.
Semantic validation: FAIL. The generated margin cell was computed outside the
order premise closure, so it omitted question-order and definition-order.
can-fill did carry those premises, but margin did not. The source also defined
a child user-output instead of connecting a text reply to the parent output.
The parent's output remained unknown. It connected several different values to
its child output as well. The run stopped at the failed support assertion.

See context.txt, generated.lain, and evidence.edn for the unmodified request,
response, admission receipts and inspected initial result/support values.

Earlier experiments are separate: premise-inventory-control passed all mutation
checks using controlled source; premise-inventory-live-2 passed with real model
source after an environment diagnostic supplied its prior fenced response.
This fresh run removes that prior-solution context and does not reproduce the
full success. No generated source was manually corrected in any attempt.
