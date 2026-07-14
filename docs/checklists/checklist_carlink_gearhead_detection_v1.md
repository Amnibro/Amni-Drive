# Checklist — CarLink gearhead detection fix (v1.8.1)

Bug: `gearhead=false` during live Kia sessions. `ActivityManager.runningAppProcesses` only returns our own processes since Android 5.1, and `ps -A` is hidepid-blinded for app UIDs. Safety impact: AUTO-mode AOA hold-off relied on a signal that could false-negative.

- [x] Backups → backups/*.v1.8.1.bak
- [x] NEW wire/CarLink.kt — CarConnection LiveData → StateFlow (official projection signal)
- [x] AaSessionWatchService — gearheadAlive/gearheadCarSession OR CarLink; tick logs carConn
- [x] AoaProbeService — gearheadProjectionAlive/gearheadCarSessionAlive OR CarLink
- [x] MeshDevBridgeService — gearheadAlive/gearheadCarSession OR CarLink
- [x] AmniDriveApp — CarLink.start
- [x] AndroidManifest — no change needed (QUERY_ALL_PACKAGES already grants provider visibility)
- [x] version 1.8.1 (76)
- [x] test + lint + assembleRelease
- [x] install + live verify carConn=2 during Kia session
- [x] changelog + architecture_map
