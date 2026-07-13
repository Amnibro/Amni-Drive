# Loop capture — AOA OPEN + TLS NEED_UNWRAP timeout (2026-07-11)

## Device
- Phone: SM-F966U (wireless ADB `192.168.0.96`)
- HU: factory AA accessory (Kia path, model=Android Auto)
- APK: com.amni.drive **1.3.10** / versionCode **23** (debug)

## USB
- `current_functions=ACCESSORY`, `kernel_state=CONFIGURED`, model=Android Auto
- gearhead processes still installed (`:projection` `:shared` `:car` `:provider`) but ActivityManager reported **gearhead=false** at open time (projection not owning FD)

## Probe sequence (Amni.AoaProbe + external `files/aoa_probe/probe-YYYYMMDD.log`)
```
poll: ... Android Auto ... perm=true gearhead=false
OPENED fd=108 gearhead=false
phase A passive sniff 2s → total=0
TLS key alias exists / context ready km=1
TLS begin status=NEED_UNWRAP
TLS handshake timeout last=NEED_UNWRAP  (~12s)
TLS result ok=false
proj VirtualDisplay id=341 1920x720@24
proj start failed: Permission Denial: starting Intent OPEN_NAV MainActivity launchDisplayId=341
projection engine failed to start
session end totalRX=0 tls=false
```

## Interpretation
1. **M3 open path works** when accessory is free (Always-allow + openAccessory returns FD). Dual-path skip re-open when already holding FD is correct.
2. **M4 blocked on wire silence:** HU sent **0 bytes**. TLS server waits for ClientHello (`NEED_UNWRAP`) and times out. Either HU expects Google identity/cert before ClientHello, session already failed on HU side, or gearhead must speak first bytes another way.
3. **Full-UI projection on VirtualDisplay** hits Android **launchDisplayId** permission denial from background service — separate from TLS; fix with ActivityOptions / system alert / MediaProjection path later.
4. External probe log path works without `run-as` (release-safe for future).

## Dual-path (non-destructive)
- Detect gearhead via `ActivityManager.runningAppProcesses` name prefix `com.google.android.projection.gearhead`
- On `openAccessory null`, arm free-poll every 3s × 40 (no force-stop)
- Do not claim exclusive kill of gearhead mid-session

## Not M5
Factory HU did **not** show Amni video. totalRX=0, tls=false.

## Follow-up (v1.3.11 / loop 2026-07-12)
- Added plaintext **AAP version** step before TLS (`AaAapVersion`: VersionRequest/Response major=1 minor=6, LE 8-byte header + BE msg id).
- Next plug-in should log `AAP TX VersionResponse` then whether any RX / ClientHello appears.
- Phone ADB offline this cycle; APK built, not installed.

## Watch auto-test capture (2026-07-11 ~22:25)
- ADB online mid-watch; USB **ACCESSORY** / Android Auto / CONFIGURED; gearhead car+projection+shared+provider alive.
- Installed **1.3.11** (v24) Success.
- Probe (process **before** force-stop of install): `OPENED fd=117 gearhead=false` → phase A total=0 → TLS NEED_UNWRAP (log string still pre-AAP — old process held code).
- Post-run ADB dropped (`waiting for device`); later state: USB **DISCONNECTED** / `current_functions=0x40000`.
- **Lesson:** after `adb install -r`, always `am force-stop com.amni.drive` then relaunch probe so AAP path runs on the open session.
- Loop 22:28 reinstall + force-stop + OWN_STACK + AoaAttach; car still unplugged.
