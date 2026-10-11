# ADR-0015: Native safe-area insets flow through CSS environment values

- **Status:** Accepted
- **Date:** 2026-10-08

## Context

The iOS shell renders the Tina4Pascal document in the view's safe rectangle so
content avoids the status-bar and home-indicator regions. A fixed footer therefore
stopped above the home-indicator area even when its visual background should have
continued behind that system inset. The engine already accepted CSS expressions of
the form `env(safe-area-inset-bottom, 0px)`, but always selected the fallback.

## Decision

Keep safe-area values as shell-owned metadata and pass them into the portable
`Tina4HTMLDom` calculation context through a small setter. The iOS Objective-C
view supplies `UIView.safeAreaInsets` before each frame. The portable defaults stay
zero, so other shells retain their existing behavior. The four standard names are
resolved: `top`, `right`, `bottom`, and `left`.

Applications may use these values in fixed dimensions and padding. OMGhee uses the
bottom value to extend the footer background and increase document bottom clearance
while leaving the tab controls in the usable content portion of the footer.

## Consequences

- iOS layouts can be edge-to-edge without hard-coding device-specific inset sizes.
- No OS dependency enters the core; only the iOS shell supplies non-zero values.
- Android can adopt the same bridge later when its window-inset handling is wired.
- Existing desktop, test, and non-notch layouts remain unchanged because their
  default insets are zero.
