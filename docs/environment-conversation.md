# Environment-constructed conversation

`soul-refined.lain` is the local experiment entry point. It imports the existing
`model-refined.lain` definition and creates one model. Importing the model file
alone creates neither a worker nor a request. The runnable display demonstration
is `examples/lain/model-refined-demo.lain`.

The four default bindings are `context-cell`, `language-introduction-cell`,
`observe-cell`, and `memory-cell`. Capabilities are additional lexical binding
contributions. The bootstrap sends a captured environment overview for a numeric
orientation task. After the model writes that network and its observed value and
edge inspection reach parent memory, a second environment-derived request supplies
that observation and the model's saved source for conversation construction. Both
stages share the model, worker, session and lexical child.
There is no separate host system prompt, injected operator catalog, or host
history implementation. The overview lists titles, strongest-value headlines,
references, and explicit unknown/contradictory states. It does not recursively
expand compounds or traverse their connected graphs. `inspect:cell` exposes only
`edge`, `value`, `inputs`, and `outputs`. No tracer is advertised to the model.
The overview walks the declared environment frames, not the compiler's ambient
implementation bindings.

The real provider must produce executable Lain to build history and connect user
input to model requests. The supplied documentation describes independent
operations, not a conversation implementation. The qualified `behavior:*`
operators use the existing temporal reducers and evaluator. `seq:*` primitives
operate on ordinary immutable vectors; Lain decides their composition.
`event:point` materializes each identified occurrence with stable evidence; replacing
its current input does not erase an earlier occurrence. Identity/time/value conflicts
remain contradictions for the existing reducers to expose. This explicit capture
boundary does not retain the full upstream premise chain.
`behavior:latest-value` supplies the missing projection from temporal records to
an ordinary, versioned cell.

The existing model worker accepts either text or an explicit nonempty vector of
role/content messages. The HTTP transport forwards the vector unchanged. It adds
no instructions or earlier messages. Text compatibility still uses a single
user-role message. The probe supports the provider's explicit thinking mode configuration.
The current retry selects medium reasoning with a 2,048-token thinking budget
and completed responses. The installed
provider template was inspected: its default xhigh mode inserts a reasoning
system instruction; medium emits no such instruction. The template snapshot is
recorded locally. Thinking stays in the provider's separate field; the model's
`:actions` slot contains source text.

`guide-core-cell` and `guide-examples-cell` contain source-attributed excerpts from
`docs/how-to-think-in-lain.md` and its worked examples. `operator-examples-cell`
contains independent code snippets and expected observations from
`docs/operator-examples.edn`. These are literal documentation strings, not
running example networks. `tools/build_language_context.py` regenerates
`lib/language-context.lain`; tests execute snippets in separate verification
sessions with controlled IO. They do not satisfy the live conversation criterion.

`construct-concept-of` installs these cells and capabilities as real lexical
binding contributions using `env:reference-slot`. Explicit references preserve
the identity of shared parent cells across the lexical child. The parent binding `history` is a vector of the model's
written source records, retained through the existing reducers. The worker adds
a monotone response sequence so distinct writings, including identical text,
remain distinct entries. This source journal excludes provider thinking and is
separate from the generated conversation's user/assistant history.

`memory-cell` is a plain record of cell references. Read a port with `data:get`,
connect values through `(cell:input port)`, and observe it with `(cell:value port)`.
The supplied example connects the strongest value of a cell to `:feedback-value`,
then constructs a request from that memory value. Verification observed
`memory-score=7` in the request and retained distinct generated source responses
in `history`. The code examples remain literal documentation; only source returned
by the real model is admitted into the experimental child.

`source:admit` rejects failed, truncated, or non-stop responses before reading
source. It uses the existing complete-form reader, admission declaration, lexical
frames, and host extension receipts. Each response/form has a stable identity.
Admissions occur sequentially in the live child; a rejected form stops the
response and retains earlier successful forms. The running worker is neither
restarted nor reconstructed. A stable completion cell records the accepted response and its source reference.
Forms use ordinary scoped CPS compilation: the supported compiler's scalar gates
cannot carry native reducer-cell content correctly. Definitions therefore live
for the lexical child's lifetime and are not withdrawn when output changes.
Completion references and receipts are retained, but definition withdrawal through
response support remains unimplemented. They do not prove the entire
input-to-output causal chain. Exact extension replay is idempotent. Exact source replay likewise preserves existing delivery application identities.

`lain-harness.soul-session` supplies only native adapters, external worker
assembly and admission effects. User publication uses the existing public
`host/publish-user-message!` operation with its exported interface, current run
token, and stable identity. Delivery reuses `runtime/message-extension`; its
session delivery events and acknowledged receipt drive the Lain wrapper. The local
script submits sequential user events and records results; generated Lain owns
conversation history, requests, and delivery calls. The delivery extension's acknowledgements
are local observations, not proof that a remote messaging service delivered them.
No deployment or service restart is part of this experiment.

## Running locally

```sh
LAIN_OFFLINE_UUID=1 tools/test_module.sh soul
LAIN_OFFLINE_UUID=1 tools/test_module.sh model-io
LAIN_OFFLINE_UUID=1 tools/test_module.sh operator-examples
tools/check_soul_quality.sh
tools/probe_soul.sh
```

The live command opens one session, verifies the real model's numeric orientation
and parent-memory feedback, then asks it to construct the conversation network,
then submits `hi`, `how are you?`, and `do you remember my last sentence?`, waiting
for acknowledged delivery after each. It checks recorded conversation requests
against actual earlier inputs and deliveries, checks recall, and checks that only
one worker subscription exists. Construction diagnostics stop the experiment;
there is no handwritten fallback.

Evidence is recorded in `runtime/soul-experiment.edn`, `soul-experiment.log`,
`soul-network.edn`, and the adjacent focused test and quality logs. Provider
thinking belongs in response records, never in delivered conversation history.
Earlier provider-timeout attempts remain in `soul-attempt-1.*` and
`soul-attempt-2.*`. A third attempt reached the 8,192-token limit in provider
thinking and returned no source; it is retained in `soul-attempt-3.*`. All three
attempts admitted no source and delivered no messages. Attempt four returned
markdown-fenced source and was rejected before admission. The review of that
source is itself a visible environment binding in subsequent attempts. Attempt
six compiled disconnected code and produced no conversational request or reply.
Attempt seven exhausted 16,384 reasoning tokens with no source. Attempt eight
uses the guide excerpts, verified examples, code journal, and memory feedback
example; its results are recorded under `runtime/soul-attempt-8/`.

Full causal provenance remains unfinished. Overview snapshots, the bootstrap
latch, and latest-value projections retain references and graph connections,
but do not preserve every upstream TTMS premise. Completion identities and
matching payloads establish the observed sequence, not a full causal proof.
Streaming, concurrent turns, durable reopening, retrieval, and the historical
model FSM are outside this experiment.

Attempt eleven tests this staged contextualization strategy. Its orientation
response produced an observed value of 5 and wrote the matching edge inspection
into parent memory. Conversation acceptance still requires all three real rounds.

Attempt twelve completed both provider responses. Numeric orientation again
produced 5. Conversation construction was rejected at form 15; the accepted
prefix fed the raw ingress record into a temporal source. The provider's separate
reasoning repeatedly treated `event:point` as static despite cell-valued arguments.
The generated code also invented `seq:map`, used constant assistant identities,
and never invoked its request builder. No conversation replies were delivered.
The next context revision adds `dynamic-expressions-cell`: one generic reading
cell feeds field projections into an `event:point` call. Its independent test
replaces the input twice and checks accumulation without recompilation. This
supplies an explicit reactive-expression example, not a conversation solution.

Attempt thirteen supplied the reactive-expression example. The real model now
used field-driven `event:point` calls for both user records and acknowledged
assistant records, feeding one temporal source and scan. All eight construction
forms were admitted. After the first user ingress, no conversational request was
sent: `conv-request` was defined but never invoked. Its body also constructed a
nested message vector and left the observed prior history unused. The script
stopped on its request timeout and closed the session cleanly. No replies were
delivered, so the three-round acceptance remains unmet. Source and request
records are under `runtime/soul-attempt-13/`, including the readable generated
`soul-response-2.lain`. The orientation and construction used the same live
worker and lexical child; examples stayed literal documentation.
