# Task: Fixed-element hit testing after scrolling

**Outcome:** Fixed controls remain tappable after document scrolling on mobile.

## Scope
- [x] Restore viewport-coordinate hit testing for fixed subtrees, including nested scrollers.
- [x] Pass the document scroll offset from interaction call sites.
- [x] Add a real drag-and-tap regression test.

## Parity
| Surface | iOS | Android | Desktop |
|---|---|---|---|
| Fixed-element hit testing | Shared core | Shared core | Shared core |

## Tests (real)
- [x] `test_interact`: 40/40 assertions, including drag then tap on fixed footer.
- [x] `tools/tina4pascal test`: all listed suites pass.
- [x] Native-shell smoke verification: fixed controls remain tappable after scrolling.
- [x] iOS device and Simulator link/build checks complete.

## Bugs
- [x] A fixed footer painted at the viewport bottom but was hit-tested at its document Y after scrolling.

## Commits
- None recorded.

## Status: Complete
