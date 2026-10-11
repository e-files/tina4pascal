# Task: iOS safe-area CSS values and footer placement

**Outcome:** expose the native iOS safe-area insets through Tina4Pascal's existing
`env(safe-area-inset-*)` CSS function so a fixed mobile footer can extend into the
home-indicator area without moving its controls into that unsafe region.

## Scope
- [x] Add portable safe-area state with a zero default.
- [x] Resolve the four safe-area CSS environment names in `calc()` expressions.
- [x] Pass iOS `UIView.safeAreaInsets` into the engine before each frame.
- [x] Document consumer fixed-footer and content-clearance usage of the bottom inset.
- [x] Deploy and visually verify on a physical iOS device.

## Parity
| Surface | macOS/iOS | Android | Windows/Linux |
|---|---|---|---|
| `env(safe-area-inset-*)` | iOS values; macOS zero | zero default | zero default |
| Fixed footer safe-area clearance | Consumer iOS shells | unchanged | unchanged |

## Tests (real)
- [x] `tools/tina4pascal test` — all suites pass.
- [x] iOS device build, install, and launch.
- [x] Device screenshot inspected for safe-area and footer placement.

## Bugs
- [x] A fixed footer stopped at the safe-area boundary, leaving the home-indicator
  strip visually detached and allowing content to appear directly behind it.

## Commits
- Working tree change; no commit created.

## Status: Complete
