# Checklist — Owned-glass HU fullscreen mode (v1.9.0)

Goal: TRUE full Amni UI with zero Android chrome on owned glass (mounted phone/tablet/aftermarket HU) — the only path past AA's fixed status/facet chrome on the factory Kia (Gearhead composites those before encoding; no API removes them).

- [x] Guardian council 5-0 → docs/guardian_councils/guardian_council_owned_glass_hu_mode.md
- [x] Backups → backups/*.v1.9.0.bak
- [x] DriveConfig/ConfigStore — huFullscreen (default true) + setter
- [x] MainActivity — applyImmersive() (WindowInsetsControllerCompat, swipe-transient), config-gated in drive branch, re-assert onWindowFocusChanged
- [x] Settings NAV pane — 🖥️ HU fullscreen SetRow
- [x] version 1.9.0 (78)
- [x] tests + lint + assembleRelease
- [ ] flash to Fold (queued — adb wedged; probe armed)
- [ ] device verify: drive mode → no status/nav bars, swipe peeks, toggle OFF restores
- [x] changelog + architecture_map
