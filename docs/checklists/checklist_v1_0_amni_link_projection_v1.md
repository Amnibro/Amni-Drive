# Checklist — v1.0.0 Amni Link (Android Auto replacement projection)

**Task:** Build Amni Link — open phone→head-unit projection so Amni-Drive replaces Android Auto as the car UI brain.
**Date:** 2026-07-11
**Version:** 1.0.0

## Changes (sequential)

- [x] 1. Guardian council + architecture pivot (Amni Link as primary AA-replacement path)
- [x] 2. `link/LinkProto` framed binary protocol (HELLO / VIDEO_CFG / FRAME / TOUCH / PING)
- [x] 3. `link/LinkHost` TCP server + JPEG frame pump + touch inject
- [x] 4. `link/LinkReceiver` TCP client + frame display + touch out
- [x] 5. `link/LinkDiscovery` NSD advertise + browse
- [x] 6. `LinkActivity` dual role UI (PHONE HOST / HEAD-UNIT RECEIVER)
- [x] 7. Settings › AMNI LINK + Dashboard entry
- [x] 8. Manifest: INTERNET already, + FOREGROUND_SERVICE, NSD, LinkActivity export
- [x] 9. Unit tests for frame codec
- [x] 10. versionCode/versionName → 1.0.0, changelog + architecture_map + README
- [ ] 11. Anthony confirms: phone host → second device receiver on same Wi‑Fi shows UI + touch works

## Verify

- [x] `:app:testDebugUnitTest` pass (25 tests)
- [x] `:app:assembleDebug` APK (~15.7 MB)
- [ ] Two-device LAN smoke (or emulator + phone)
