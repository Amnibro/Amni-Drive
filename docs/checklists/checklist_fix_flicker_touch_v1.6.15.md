# Checklist — v1.6.15 fix RIDE flicker + dead touch + host-chrome overlap

- [x] Backup AaSurfaceRenderer.kt + Dashboard.kt to backups/
- [x] Guardian council doc (5-0)
- [x] AaSurfaceRenderer: remove main-thread latch deadlock in startFullUi (run inline on main, no CountDownLatch)
- [x] AaSurfaceRenderer: canvas fallback loop ONLY when Presentation fails (never two surface producers)
- [x] Dashboard: reserve top-right space on carSurface for AA host APP_ICON button (CarStatusStrip end pad 84dp + widgets top pad 34dp)
- [x] BONUS: MediaSourceSheet hoisted to Dashboard level (was clipped inside media tile)
- [x] BONUS: playViaBrowser — media pick via MediaBrowserCompat, no activity launch on carSurface
- [x] Bump versionCode 65 / versionName 1.6.15
- [x] Unit tests green
- [x] lintDebug launched (background)
- [x] assembleRelease APK 12.6MB
- [x] Install to Fold (wireless adb) — Success
- [x] DHU verify: no flicker (3× caps identical), APPS drawer opens, media picker opens/picks/closes, Spotify PLAYING(3)
- [x] Update architecture_map.md + changelog.md
