# Reload the chronological Soul request policy

The native editor runtime selects the current callable revision once per request packet ID. The default request policy passes the chronological packet through, including its existing compaction behavior. Edit the body of `request-policy` and select **Reload definitions in this session** in the editor command palette. The next packet uses the edit. An in-flight packet finishes with its original revision.

The [integration patch](chronological-soul.patch) is for deployments with the chronological editor-chat entry installed. Apply it to that entry before starting its first hot-reloadable session. The patch depends on the editor-chat experiment bundle, which is maintained outside this checkout. Native primitive implementations and edits to installed graph wiring require a new run.

The runtime release is [datalog-research b10de5f](https://github.com/Semi-0/datalog-research/commit/b10de5f1ce82768a258023cf59b2754e7b43b046). The palette release is [lain-editor f7e3cde](https://github.com/Semi-0/lain-editor/commit/f7e3cde).

On 2026-11-27 the side-by-side deployment compiled the chronological Soul entry, reloaded the identity policy as `(network [packet] (reply packet))`, and restored it. Both reloads retained session and run IDs and advanced the generation. Afterward Lain replied to a user message and executed `(reply "READY")`. Chromium reattached after a page refresh and found the reply and the reload command. A separate browser check reattached to the pre-upgrade session and found its earlier clock and rewrite results. These checks cover one live deployment.
