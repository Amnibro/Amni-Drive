# Checklist — Niro 10.25" UI design pass (v1, 2026-07-10)

Goal: design Amni-Drive v0.2 UI at the '23 Niro panel size (10.25", 1920×720), render pixel-exact mockup screenshots of every menu/submenu, deliver to Anthony for approval before any Compose implementation.

- [x] Scan architecture_map.md
- [x] Read current ui/ (HomeScreen.kt, SettingsActivity.kt, Theme.kt)
- [x] Guardian council convened (docs/guardian_councils/guardian_council_niro_ui_design.md)
- [x] Shared design CSS (docs/design/niro_mockups_v0.2/niro.css) — 1920×720, dark night-drive theme extending Theme.kt palette
- [x] Screen 1: Driver Home (nav card + now playing + favorites dock + bottom rail + gate quick-toggle)
- [x] Screen 2: App Drawer while MOVING (category chips, lock badges on gated apps)
- [x] Screen 3: Gate interstitial (send-to-rear / passenger-confirm / cancel)
- [x] Screen 4: Settings — two-pane, Distraction Gate submenu selected
- [x] Screen 5: Settings — Motion & Speed submenu
- [x] Screen 6: Rear Lounge home (ungated rear display)
- [x] Render all via headless Edge at exactly 1920×720 (verified dimensions + visual pass; screen 1 re-rendered on fresh profile after first-run paint race)
- [x] Send PNGs to Anthony
- [x] Update changelog.md
- [ ] NOT COMPLETE until Anthony approves direction (rule 8); Compose implementation is a follow-up task
