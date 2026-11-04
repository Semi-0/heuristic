# TODO

- [ ] Show declared but unknown struct slots in the editor's value display as
  `the-nothing`. For `model-refined.lain`, display `:output`, `:thinking`, and
  `:actions` even before they have values. Preserve the distinction between an
  undeclared field and a connected field awaiting a value; do not seed the cells
  merely to make them visible. Verify an empty struct, partial input, and later
  output publication.

- [ ] Fix whole-record inspection of a connected model after a reply. The hosted
  session reports `Compound projection found ambiguous slot parents` for `m`,
  while `(model:output m)` is ready and contains the successful Blazux reply.
  Reproduce with `model-refined.lain` and the `"hi"` source extension; preserve
  the independently supported slot connections when projecting the record.
  The guard in `compiler_2/runtime/one_time_network.clj:sole-parent-id` rejects
  more than one present parent without comparing their values. Whether the
  model's parent values differ remains unverified. `prim-slot` reads plain
  reply fields directly; it does not fix this whole-record projection guard.

- [ ] Report conflicting top-level definitions clearly in the hosted editor.
  Defining a user network named `focus` conflicts with the editor's existing
  visualization primitive and leaves calls waiting on a contradictory operator.
  The same network works under `model-view`; nested slot/struct composition is
  not the cause. Include the conflicting name in the diagnostic.

- [ ] Make replacing the display through a session extension explicit. A second
  `(display ...)` currently adds a connection to the same sink and can produce
  a contradiction. Reloading source with one display works.
