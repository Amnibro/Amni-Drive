# Checklist — v0.5 map + rain rework (v1, 2026-07-10)

Anthony on v0.4: "decent start — maps still need work and so do the rain backgrounds."

- [x] Council addendum (guardian_council_niro_ui_v0_5_map_rain.md)
- [x] Fetch real map tiles: CARTO dark_all + light_all @2x, z15 around Zilker/Lady Bird Lake (Austin), 6×4 grid each (48/48 ok), stored in niro_mockups_v0.5/tiles_{dark,light}/
- [x] Probe render with coordinate grid → read real road pixel positions → route path that follows actual streets
- [x] Generate rain SVG layers (seeded LCG): varied streaks + windshield droplets, normal + heavy variants
- [x] 01 Home rain — real dark map + route overlay + new rain
- [x] 02 Home sun light — real light map (positron), parked puck at Zilker (no route while parked)
- [x] 04 Split — real dark map crop
- [x] 03 Apps thunder — new heavy rain layer
- [x] 05 Gate — backdrop inherits new rain
- [x] 06/07/08 — carried over, 06 rain preview thumb matched, About bumped to v0.5.0
- [x] Update animated demo rain (droplet layer base64-embedded, stays self-contained)
- [x] © OSM · CARTO attribution on map corners
- [x] Render 1920×720, verify dims + visual pass (downtown Congress lean corrected once)
- [x] Send PNGs + demo
- [x] Backup changelog.md → backups/changelog.md.v0.5.0-design.bak, update changelog
- [ ] NOT COMPLETE until Anthony approves (rule 8)
