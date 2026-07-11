# Guardian Council addendum — v0.4 refine (2026-07-10)

Scope: respond to Anthony's v0.3 feedback (subtle backgrounds, better map, heavy frost, animated/custom backgrounds, amni-scient presets).

- **Architect:** background goes FLAT (#0a0b0e, faint accent radial only); weather becomes ambience at ~45% of v0.3 opacity; all information lives on heavily frosted cards (blur 26px, saturate 1.15, panel alpha .55). Map quality is a card-content problem, not a layout problem — layout holds.
- **Sentinel:** subtler weather further reduces MOVING distraction — flash energy halved, calm-mode rule unchanged. Custom-image backgrounds pass through the same dimming scrim so contrast floors hold. Presets/skins remain presentation-only; GateManager.decide() untouched.
- **Scholar:** map mimics Google dark-style semantics: road hierarchy with casings, water/park fills, muted POI dots, route with chevrons + amber traffic segment, shield, scale bar — matches driver expectations. Light preset uses the site's [data-theme="light"] tokens (#f5f6f7/#00875a) for verified contrast.
- **Engineer:** implementation stays Compose-friendly — map = Google Maps SDK (or MapLibre) with a custom JSON dark/light style matching these mockups; animated weather = Canvas particles (demo proves the motion design in CSS); own-image = photo picker + blur/scrim shader; presets = named AmniTheme instances (Dark, Light, Crypt #1565c0, Haven #5e35b1, Core #c62828, Explore #0277bd).
- **Pathfinder:** preset names tie the car to the Amni family identity; animated demo file gives Anthony the feel before any APK exists.

Verdict 5–0: adopt all five refinements; mockups v0.4 then await approval (rule 8).
