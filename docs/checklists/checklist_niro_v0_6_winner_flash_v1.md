# Checklist — v0.6 route fix + maps feature kit + Compose build + flash (v1, 2026-07-10)

Anthony on v0.5: "I love the map now but the line doesn't follow the road — fix that, add the other maps features (alerts/report, N-up vs follow, 3D & traffic views, satellite, 3D buildings, tilted view) and we have ourselves a winner. Once you're done, flash to my phone."

## Phase 1 — design fixes (mockups)
- [x] Council addendum
- [x] OSRM real route geometry (269 pts, 5.6 km) → projected to tile pixels → replaced hand-drawn path in 01/04 (shared defs/use)
- [x] Map control stack on 01/04 (report / follow / layers / 3D) + crash alert marker + amber traffic on real vertices
- [x] Esri World Imagery tiles fetched (tiles_sat/) for satellite thumb
- [x] 09_nav_3d.html — tilted follow-mode nav: report sheet, layers flyout w/ real dark/light/sat thumbs, traffic legend, speed limit + crash chip (switch-size fix, re-rendered)
- [x] Rendered + verified + sent (01, 04, 09)

## Phase 2 — Compose implementation (v0.6.0)
- [x] Read scaffold (ui/, gate/, config/, launcher/, gradle, manifest)
- [x] Backups → backups/*.v0.6.0.bak (7 files + changelog + architecture_map)
- [x] Theme system AmniTheme.kt: 6 presets, custom accents, weather toggle, tile size, map style — DataStore
- [x] Compose slippy map MapView.kt: CARTO+Esri via TileStore (mem LRU + disk), GPS follow, N↑⇄HDG, 3D tilt, zoom/pan/long-press, OSRM route with speed-colored traffic, report pins, puck+cone, pin
- [x] WeatherLayer + WeatherService (Open-Meteo, calm-while-moving)
- [x] Dashboard: left rail + chips + map|media|widgets
- [x] AppDrawer: real icons, category chips, lock badges; GateSheet SEND TO REAR (GateManager.decide() untouched; DriveController.launchRear added)
- [x] SettingsActivity two-pane: Gate + Appearance + About
- [x] ReportPanel → ReportStore (6 types, 2 h TTL)
- [x] compileDebugKotlin clean · testDebugUnitTest 21/21 · lintDebug 0 errors · assembleDebug 15.6 MB
- [x] architecture_map.md + changelog.md updated (backups first)

## Phase 3 — flash
- [x] APK sent to Anthony directly; adb watcher armed (wait-for-device → install -r → launch) — no device attached at build time
- [ ] NOT COMPLETE until Anthony confirms working on his phone (rule 8)
