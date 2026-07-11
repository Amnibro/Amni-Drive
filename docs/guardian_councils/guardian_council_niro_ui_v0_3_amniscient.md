# Guardian Council — v0.3 Amni-Scient restyle + weather backgrounds + splitscreen (2026-07-10)

Anthony's feedback on v0.2: "style is pretty bad... match my amni-scient theming, including customizable theming itself. Tiles look old and huge, splitscreen maybe. Weather by location as a background (rain/hail/thunder-lightning/sun/clouds/snow). Bring car displays forward. Borrow from CarPlay."

## Architect
Adopt the Amni-Scient tokens verbatim (bg #0a0b0e, panel #13161c, accent #00ff9d + glow, JetBrains Mono chrome, glass gradient, radius 14). Move the dock from bottom (100px of scarce height) to a CarPlay-style **left rail** (~104px of abundant width) — on a 1920×720 ultrawide, width is cheap and height is gold. Home becomes a dashboard: nav pane + media pane + widget stack, all glass cards floating over a full-bleed weather layer. Proposes: rail | body(topbar/main) skeleton shared by every screen.

## Sentinel
Weather backgrounds must never cost legibility or gate visibility. Rules: (1) cards are frosted glass (backdrop blur + ≥66% panel opacity) so text contrast survives any weather; (2) weather animation auto-calms while MOVING (slow particles, no lightning flashes) — default ON, toggleable; (3) the drive-state chip and gate toggle keep fixed positions in every skin/theme; (4) GateManager.decide() remains the only chokepoint — theming is presentation-only, zero gate logic in theme code. Lock badges stay coral (#ff5a5a = site --error) regardless of accent choice so "gated" never disappears into a custom theme.

## Scholar
CarPlay borrowings that are actually good: left icon rail with recents, dashboard multi-card home, compact rounded-square icons with labels OUTSIDE the tile (Apple HIG), page dots, split-view divider handle. Car-UX numbers still bind: touch targets ≥76dp (icon cells 106px + label = ~150px hit area ✓), glance text ≥24sp for primary data, menu depth ≤2. Animated backgrounds: NHTSA glance guidance says avoid sudden luminance spikes in driver view — thunder flash capped/disabled while MOVING (agrees with Sentinel). Weather source: Open-Meteo free API by lat/lon (no key) — GPS is already a dependency.

## Engineer
All implementable in Compose: weather layer = Canvas particle system (or AGSL shader on 33+, Canvas fallback for minSdk 26); theme = CompositionLocal AmniTheme(accent, mode, weatherOn, tileSize) persisted in DataStore; per-display theme (driver vs rear) trivial since RearPresentation composes its own tree. Splitscreen honesty: our OWN dashboard panes (map/media/widgets) split freely; forcing two third-party APKs side-by-side needs multi-window (launchAdjacent works on many units; freeform on rooted) — ship dashboard-split first, real app-split as a flagged experiment. Compact grid = LazyVerticalGrid with 8 columns, icon 106dp.

## Pathfinder
This is the differentiator kit: weather-reactive ambient displays, per-display theming, accent presets echoing the Amni family (Volt green default = Amni-Scient signature; Ice/Ember/Plasma/Crimson/Gold as the site's per-product accents). "Bring displays forward" roadmap: smart suggestion chip (predicted destination), parked-mode ambient screen (weather + clock as art), trip/OBD widget, voice chip. Ship the skin now, suggestions later.

## Verdict — 5–0 unanimous
1. Full Amni-Scient reskin: exact tokens, mono chrome, glass cards, glow accents, subtle grid backdrop.
2. Left-rail dock (CarPlay), dashboard home (nav | media | widgets), compact 106px icon tiles with external labels.
3. Full-bleed weather backgrounds (sun/clouds/rain/thunder/snow/hail) by GPS location via Open-Meteo; frosted cards; calm-while-moving default.
4. Appearance settings page: 6 accent swatches, Dark/Light/Auto-sunset, weather toggle+intensity+previews, tile size, terminal-chrome extras; per-display themes (rear can differ).
5. Splitscreen: dashboard panes split with divider handle now; third-party app split via launchAdjacent flagged experimental.
6. Mockups first at 1920×720; Compose only after Anthony approves (rule 8).
