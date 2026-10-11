# Framework gaps — what Tina4Pascal ships vs. what consumers assume

Live table of drift (see [README.md](README.md) for how to maintain it). Last
audited against `main` on 2026-10-11. Evidence is `file:line` in this repo.

## Shipped (verified in source)

| Capability | Evidence |
|---|---|
| HTTP client (TLS, redirects) + per-platform backends | `src/Tina4Http.pas` |
| Server-Sent Events | `src/Tina4SSE.pas` |
| WebSockets | `src/Tina4WebSocket.pas` |
| Frond templates | `src/Tina4Frond.pas` |
| Syntax-highlighter registry + runtime `.lang` files | `src/Tina4Highlight.pas` (`RegisterLanguage`, `LoadLanguageFromString`, `LoadLanguageFromFile`) — ADR-0008 |
| Editable `<codearea>` (syntax runs, line-number gutter) | ADR-0008; `src/Tina4HTMLLayout.pas` `MakeControl` |
| `<codearea>` code folding (indentation + Pascal routine) | ADR-0010; `src/Tina4CodeFold.pas` |
| `transform: translate()` `%` / `calc()` resolution | ADR-0011; `src/Tina4HTMLDom.pas`, `src/Tina4HTMLLayout.pas` |
| File picker (macOS only) | `src/Tina4ShellCocoa.pas:1669` `PickFile` (`NSOpenPanel`, `canChooseDirectories:False`) |

## Open gaps

### 🔴 Folder picker — and the file picker on Windows/Linux

The headline M0 item, and **larger than first stated**. The truth in source:

- The contract declares only a **file** picker: `TTina4Shell.PickFile`
  (`src/Tina4RenderBackend.pas:261` virtual, `:493` base no-op). There is **no
  `PickFolder`** anywhere.
- Only **macOS** overrides `PickFile` (`src/Tina4ShellCocoa.pas:1669`), and it sets
  `canChooseDirectories:False` — a file picker, not a folder picker.
- **Windows (`src/Tina4ShellWin.pas`) and Linux (`src/Tina4ShellLinux.pas`) do not
  override `PickFile` at all** → `<input type=file>` is already a silent no-op on
  those shells, before folders even enter the picture.

**To close (M0):**
1. Add `PickFolder: string; virtual;` to the `TTina4Shell` contract
   (`Tina4RenderBackend.pas`), base returns `''`.
2. macOS: `NSOpenPanel` with `canChooseDirectories:True`, `canChooseFiles:False`.
3. Windows: `IFileOpenDialog` with `FOS_PICKFOLDER` (fallback `SHBrowseForFolder`).
4. Linux: `zenity --file-selection --directory` (fallback GTK
   `GtkFileChooser` in `SELECT_FOLDER` mode).
5. A trigger + signal: extend the file control (e.g. a `directory` attribute on the
   file element) with a `TINA_PICK_FOLDER` signal, mirroring `TINA_PICK_FILE`
   (`src/Tina4Interact.pas:39`, dispatched at `:2133`).
6. **While here, give Windows + Linux a real `PickFile`** — they need it regardless,
   and the dialog plumbing is shared with the folder case.

Verify: macOS runs here (build + run). Windows/Linux shells cross-compile from the
Mac toolchain (`-Twin64 -Px86_64`, `-Tlinux -Px86_64`) — compile-check there; run
on the real OS before ticking the box.

### 🟡 Richer highlighter string / sigil support (optional)

`.lang` definitions handle keyword/type/comment/string/sigil today; some languages
want multiple string delimiters or a richer sigil grammar. Does not block v1 — add
per language as callers need it. Evidence: `src/Tina4Highlight.pas` `THLLanguage`.

### 🟡 Read-only `<codearea>` with a line-click event (optional)

A read-only code *viewer* with a clickable-line event. Not blocking: a read-only
code pane is built today as plain `<pre>` rows with the highlighter's
`HighlightToHTML`, so no engine change is required for the viewer itself.

## Recently closed

_(none yet — flip 🔴/🟡 rows to ✅ here with their commit/ADR as they land)_
