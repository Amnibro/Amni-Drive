# Checklist — v0.8.0 map theming + offline area + launcher UX (v1, 2026-07-11)

Anthony: "get theming working on the map as well? also theres gotta be a way to have internet via my phone, not just hotspot. lastly how do i open this vs android auto?"

- [x] Backups → backups/*.v0.8.0.bak
- [x] Map style AUTO (-1 default): follows preset (light preset → light tiles, dark → dark); Settings seg AUTO/DARK/LIGHT/SAT; manual layer chips still override per-session
- [x] Accent tint wash over basemap (skip on satellite) so Crypt/Haven/Core presets color the map
- [x] Map chrome (banners, buttons, hint chips) palette-driven for light theme
- [x] TileStore.prefetch(): SAVE AREA OFFLINE button in Layers panel — zooms 11–16 around camera, progress %, disk cache
- [x] Remove auto HOME-role nag from MainActivity; add "SET AS DEFAULT LAUNCHER" action in Settings › About
- [x] Version 0.8.0 / versionCode 4
- [x] compile + tests (21/21) + lint (pass) + assemble (15.6 MB) + lint + assemble
- [x] changelog + architecture_map (backup first), memory
- [x] Arm adb watcher (b1sfo6g7t) + APK sent + send APK
- [x] Reply covers: USB/Bluetooth tethering steps + how to open vs Android Auto
- [ ] NOT COMPLETE until Anthony confirms (rule 8)
