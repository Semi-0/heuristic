Real-model experiment with environment:describe
Source commit: 6ebea4e; isolated checkout /tmp/lain-describe-live-6ebea4e.
Same configured provider, one model worker and lexical child across both stages.

Actual environment descriptions: provider-context-1.txt (orientation) and
provider-context-2.txt (construction). Both begin with role-cell. These are actual
request texts, not previews.

Orientation succeeded: seven forms admitted; observed value 5 and edge inspection
written into parent memory. Construction completed: sixteen forms admitted.
After the first user input, model request validation failed:
"Model input requires text or explicit role-tagged messages".

Generated construction source: soul-response-2.lain.
It invokes model:request but never feeds user-ingress occurrences into conv:source.
conv:latest-msgs therefore supplies its empty-vector fallback, which fails request
validation. It also references seq:map, which is not an advertised capability,
and advertises conv:reply using a constant assistant identity/time while defining
a different reply function with varying identity/time.

No conversation request reached the provider. No delivered replies. Three-round
acceptance remains unmet. The session closed cleanly. Exact requests, responses,
admission receipts and inspected connections are retained in adjacent EDN files.
