# Guardian Council — Owned-glass HU fullscreen mode (v1.9.0)

Context: on the factory Kia, RIDE's ceiling is fixed AA chrome (Gearhead composites status+facet bars before encoding — no API strips them). Anthony wants TRUE full Amni UI. The only path is owned glass (mounted phone/tablet/aftermarket Android HU), where today drive mode still shows Android's status + gesture bars.

- **Architect:** immersive belongs at the Activity window layer, not per-composable — one `WindowInsetsControllerCompat` application in `applyDriveIntent`, re-asserted on window-focus regain (immersive is lost on dialogs/IME). Vote: window-layer immersive + focus hook.
- **Sentinel:** hiding bars must never trap the user — keep `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` so a swipe reveals bars transiently; config-gated with default ON only in drive mode, normal launcher use untouched. Vote: swipe-transient + config gate.
- **Scholar:** `setDecorFitsSystemWindows(false)` is already set in drive mode; immersive on API 26+ via WindowInsetsControllerCompat covers all our minSdk range; deprecated systemUiVisibility not needed. Vote: WindowInsetsControllerCompat only.
- **Engineer:** smallest diff — one helper + one config flag + one Settings row; no new deps; unit-testable config default. Vote: ship minimal.
- **Pathfinder:** this is the first stone of the owned-glass track; next stones = Link receiver fullscreen + tablet deploy recipe. Vote: ship now, iterate.

**Verdict 5–0:** window-layer immersive, swipe-transient, `huFullscreen` config default true, applied only when drive mode activates; re-assert on focus.
