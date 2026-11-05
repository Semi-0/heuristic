Real-model inventory with general problem-solving methodology: PASS

The context supplies four general stages: identify knowns/unknowns; abstract
quantities, relations and dependencies; reduce to simpler computations; solve,
reconstruct and verify. It supplies no previous generated solution or
repair-specific diagnostic. The output contract distinguishes new child result
cells from the existing parent text-output port.

The first methodology attempt produced correct arithmetic and premise sets but
shadowed the output port. This second fresh attempt adds only the clarified
port contract to that methodology context. Its raw source was admitted unchanged.
This is a successful example, not proof that methodology alone caused success.

Checks passed: initial usable stock 216, margin 16, fulfillment true; after
confirmed 25% damage, stock 180, margin -20, fulfillment false. Damage withdrawal
makes dependent values unknown; restoration recovers them. Withdrawing only the
order question leaves stock available while invalidating order outputs. An
unrelated delivery-note change has no effect. Exact required premise sets were
checked. Replies read the changing supplied damage-status metadata.

One real provider call constructed the network in this attempt. Subsequent
corrections and withdrawals used that network without model requests.
The source-status label expresses an input's declared status, not a statistical
confidence estimate. Formula validity was checked for the tested cases; premise
support itself is not proof that arbitrary model-generated rules are correct.

See context.txt for the actual request, generated.lain for the unmodified model
source, summary.txt for readable observed states and supports, evidence.edn for
full requests, receipts and inspection records. source.lain and methodology.lain
preserve the parent orchestration and imported methodology text.
