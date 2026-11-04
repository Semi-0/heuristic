# Native boundaries in the migration

This inventory describes the current draft, not completed acceptance. Host extensions do not preseed cells or install custom compiler activation/installation hooks; native facilities declare primitive or effectful propagators. `lib/ports.lain` and `lib/model.lain` own the source and model networks. Initialization is an acknowledged immutable session transition requested by Lain. Every exported facility has a distinct extension identity and an explicit import in `soul-refined.lain`. The convenience installer assembles those extensions.

| Boundary | Contract and justification | Mutation |
| --- | --- | --- |
| `empty-env`, `lexical-child`, `env:slot`, `extend-env` | Construct native lexical references and binding-cell contributions; preserve compiler identity and frame declarations. | None |
| `lexical:bindings`, `lexical:parent` | Read one frame and its parent; expose availability, premises, cell and frame identity. The metadata field is `:premises`; `:support` is reserved for a TTMS layer. Lain chooses traversal and shadowing. | Immutable network publication |
| `value:kind`, `value:slots` | Report callable/compound/scalar kind and immediate field metadata. Lain chooses recursive descriptions and cycle handling. | Immutable network publication |
| `data:record`, `data:list`, `data:get`, `data:merge`, `data:empty?`, `data:integer?`, `data:equal?` | Immutable records/lists are TTMS base values. Pure primitives preserve input support; scalar constants stay primitive. Structural data equality shares that publication contract. | None |
| `data:snapshot`, `data:unpack` | Resolve finite connected values and bridge immutable records with open compounds. Snapshot equality controls publication versions, not domain selection. | Immutable network publication |
| `cell:reference`, `cell:value`, `inputs-of` | Connect explicit cell identities and the model's description inlet. They do not select inference or compiler policy. | Immutable declarations |
| `source:read-next` | Parse one form without rewriting admitted source. Explicit stream completion distinguishes final atoms, incomplete suffixes and malformed forms. | Invocation-local reader cursor only |
| `admission:declaration` | Construct a targeted source declaration; never install it directly. Current wiring carries the model premise; round and consumed-context premises remain incomplete. | None |
| `session:initialize`, `session:feedback`, `session:admit` | Register connected declarations. Immutable session advancement initializes once, compares versions, or admits one form and publishes a receipt. | None inside transformations |
| Open-session host | Serialize external publication and commit immutable session snapshots. Validate exported ingress, run identity and message identity. | Authoritative session owner |
| `transport:request` | Execute the request selected by Lain; publish chunks, completion or failure. | One external request thread, cancellation flag and event sequence counter |
| `message-to-user` | Publish generated message content with an invocation identity and acknowledge delivery. | Authoritative publication and dependency lifecycle |
| Editor owner catalog | Existing discovery, attachment and generation machinery; narrow user-message operation for opted-in external owners. | Existing view/owner registry |

The current `resume-model!` helper still directly advances model state through the host publication boundary. Moving resume selection into the Lain policy is outstanding. Event ingress retains immutable logs but does not yet enforce the required end-to-end 256-pending-event backpressure. These are explicit remaining gaps.

Focused tests cover immutable compare-and-advance, selected Lain feedback without a host, parser/admission components, observation withdrawal/recovery, import identities, and shared editor/headless ingress. Full transport lifecycle, supported admission recovery, model independence, and canonical coded conversation remain unverified.

The existing `resource_queue.clj`, `resource_stream.clj`, and `resource_environment.clj` files are unused compatibility remnants. Their removal is deferred until the replacement's acceptance tests pass, as required by the migration plan.

`Frozen` and the observation `Snapshot` wrapper have been removed. Existing lexical reference records (`Environment` and `Contribution`) are carried as TTMS bases. Source identity is the cell actually declared by Lain, not an identity computed by the harness host. The standard primitive declaration installer allocates its call ports; the harness does not supply an alternate installer or allocate additional seeded sampling cells.

Model/user interface declarations identify a Lain binding and, when needed, a reference field in its TTMS base. The session resolves that reference and checks that the cell exists. External IO only publishes to those exported references. Reading or attaching a view does not initialize a second model.
