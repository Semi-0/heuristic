Real-model TTMS inventory experiment: PASS, after a formatting retry

The first real response (premise-inventory-live) used Markdown fences and was
rejected before admission. This attempt exposed that source and the reader
failure through environment bindings. The model returned raw Lain; its source
was admitted unchanged. The provider was called once in this attempt.

The resulting network passed these checks without further model requests:
- initial estimate 10%: usable=216, margin=16, can-fill=true;
- confirmed damage 25%: usable=180, margin=-20, can-fill=false;
- changing unrelated delivery-note: entire inspected results unchanged;
- withdrawing damage: stock, margin, decision and reply become unknown;
- restoring damage: prior results recover;
- withdrawing only question-order: usable remains 180, order outputs unknown;
- restoring that question: order outputs recover.

Exact support sets include respective question and definition premises plus
used input facts. The unrelated delivery premise is excluded. Reply text reads
supplied damage-status metadata, changing from unverified estimate to confirmed
inspection. This is conditional dependency tracking, not calibrated probability,
and does not independently verify the truth of supplied premises or formulas.
Definitions remain installed; support withdrawal changes result availability.

summary.txt is a readable inspection of the actual snapshots. generated.lain,
context.txt and evidence.edn preserve source, request and verification evidence.
A later fresh run with a dedicated output-format-cell and no prior candidate
source is recorded separately in premise-inventory-live-3; it failed semantic
validation despite producing valid raw Lain.
