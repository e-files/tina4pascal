# OS capability parity — feature × platform

Audited against `main` on 2026-10-11 (see [README.md](README.md) for the method:
the reliable signal is the precise `T…Shell.<method>` override scan plus the native
source per platform — macOS `Tina4ShellCocoa.pas`, iOS `ios/app/*.m`, Android
`android/.../*.java`; a broad text grep false-positives on things like Windows'
`FILE_SHARE_READ`).

Legend: ✅ implemented · ❌ absent · ⏳ in progress · ❓ unconfirmed.

| Feature | macOS | Windows | Linux | iOS | Android |
|---|:--:|:--:|:--:|:--:|:--:|
| Rendering (canvas) | ✅ Cocoa | ✅ GDI+ | ✅ sw raster | ✅ | ✅ |
| Window + input | ✅ | ✅ | ⚠️ minimal | ✅ | ✅ |
| Remote images / HTTP | ✅ | ✅ WinHTTP | ❓ | ✅ | ✅ |
| Notifications | ✅ | ❌ | ❌ | ✅ | ✅ |
| File picker | ✅ | ❌ | ❌ | ✅ | ✅ |
| Folder picker | ⏳ PR #37 | ⏳ PR #37 | ⏳ PR #37 | ❌ | ❌ |
| Camera capture | ✅ | ❌ | ❌ | ✅ | ✅ |
| Audio record/play | ✅ | ❌ | ❌ | ✅ | ✅ |
| Location / GPS | ✅ | ❌ | ❌ | ✅ | ✅ |
| Share sheet | ❌ | ❌ | ❌ | ✅ | ✅ |
| Push notifications | ❓ | ❌ | ❌ | ❓ | ✅ |
| Barcode scan | ✅ | ❌ | ❌ | ✅ | ✅ |

## Where capabilities live (why the matrix looks the way it does)

- **macOS** → `src/Tina4ShellCocoa.pas` (a `TTina4Shell` subclass with the native
  methods). **iOS** → `ios/app/*.m`. **Android** → `android/.../*.java` (richest set:
  Http, ImageLoader, Camera, Notify, Push, Scanner, Share, Location, Capture).
- **Windows and Linux are NOT `TTina4Shell` subclasses.** They are procedural
  WinAPI / Xlib paths in `src/Tina4App.pas` (`WWndProc`, the `XNextEvent` loop) that
  draw via `TWinCanvas` / the software raster and override **zero** device-capability
  methods. That is why desktop = "renders everything, does nothing native".

## The headline drift

**Windows and Linux are rendering-complete but capability-barren.** Bringing desktop
up to capability parity (pickers, notifications, camera, audio, …) is the real
backlog; the folder picker (M0, PR #37) is the first domino.

## Drift found while auditing

1. **Desktop pick dispatch was dead.** `TinaTouch` returns a code
   (`TINA_PICK_FILE`…) for the host to act on, but the desktop `RunApp` paths
   (`TAppDriver.Up`, `WWndProc` `WM_LBUTTONUP`, the Xlib `ButtonRelease`) ignored the
   return — so `<input type=file>` was a silent no-op on desktop outside `htmlviewer`.
   PR #37 wires the dispatch for folders; the **file** case is still unwired on
   Windows/Linux (they have no `PickFile`), tracked in `framework-gaps.md`.
2. **Linux cross-RTL is missing from the vendored toolchain.** `~/fpc` has no
   `units/x86_64-linux/system.ppu`, so `tina4pascal build linux` fails at
   "Can't find unit system" before any project code compiles. Linux targets cannot
   be built or verified on the current Mac toolchain (ADR-0003 says they should).
3. **Share is iOS/Android only**; Location is macOS/iOS/Android (PR #21 shipped
   explicit unsupported placeholders for desktop).

## To confirm (❓ cells)

- Linux remote-image/HTTP path (does the Xlib shell fetch images, or only local?).
- macOS / iOS push notifications.
