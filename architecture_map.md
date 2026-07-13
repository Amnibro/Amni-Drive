# Amni-Drive â€” Architecture Map

**Project:** Amni-Drive â€” **replace Android Auto** (wire-level host + multi-channel Link + Car App fallback + launcher).
**Owner:** Anthony (mechanical engineer) â€” personal testing project.
**Status:** **v1.8.0** — v1.7.4 FLASHED to Fold (flash_on_unplug watcher caught the unplug; versionName verified); live-Kia interactive proof captured pre-unplug (`Amni.AaSurface onClick fullUi=true surface=1785x690` ×3 = real panel size). v1.8.0 built+committed: message banner + spoken readout (`AmniNotifListener` CATEGORY_MESSAGE filter → `MessageFeed` → Dashboard `MsgBanner` bottom-center w/ 🔊 READ, auto-dismiss 12 s; Settings NAV: banner ON / auto-read OFF) — flash queued via scripts/flash_on_online.ps1 (adb offline-handshake wedge on cellular; port rotates: 46521→49111; throttle port scans ≤1500 sockets/batch). v1.7.4 chain: then-↰ chip + NAV settings pane. Earlier: Grok's post-Kia-confirm triage fully implemented (07-13): v1.7.1 auto-reroute (60m×3, spoken "Rerouting") + TRIP ETA + car lite-mode (no weather particles / 90ms fades on glass); v1.7.2 hybrid typing (SearchPanel "⌨ CAR KEYBOARD" chip → host SearchTemplate via `hostSearchPulse` → result routes in full Amni UI via `pendingGo`); v1.7.3 `media/MediaSessionBridge` (NotificationListener unlocks active sessions → real title/artist/album-art in MediaCard, transportControls with media-key fallback; post-flash grant: `cmd notification allow_listener com.amni.drive/com.amni.drive.media.AmniNotifListener`). v1.7.0–3 built+tested, flash queued for Kia session drop. Earlier v1.7.0 — OSRM `steps=true` → `RouteStep`; `map/TurnGuidance` live-maneuver engine (skip depart, 40 m advance, passed-step next-closer fallback); `TurnBanner` overlay on phone + carSurface (same Compose tree); `VoiceGuide` staged TTS (far / quarter-mile / at-turn) with nav-guidance audio attrs + transient duck; `voiceNav` config (banner 🔊 toggle). Built+tested, NOT yet flashed (live Kia session in progress). **LIVE-KIA CONFIRMED same day:** gearhead bound `AmniCarAppService` on the real car, `mode=ride · passive · no AOA claim`, zero communication errors — error-16 class resolved in RIDE. Prior v1.6.19 state — RIDE surface production-grade on DHU: flicker+touch deadlock fixed (v1.6.15), in-dash theme cycle + fit-to-glass drawer (v1.6.16), on-glass tap keyboard → full search→OSRM traffic route chain (v1.6.17), gate one-tap fixed driver+master (v1.6.18), minimal car drawer + compact layout picker — every control reachable on the real 800x400 surface (v1.6.19). Facts: AA surface = 800x400 on DHU (host bar 80px); onScroll has NO position (no scroll-dependent UI on glass); no IME (own keyboard); other-app activities can't launch on our untrusted VD (media via MediaBrowserCompat play). `scripts/dhu_click_cap.ps1` click/drag/capture harness; `Amni.AaSurface` onClick logging. **Not M5** (OWN still AuthComplete -3; RIDE is the production path; physical Kia confirm pending).
**Created:** 2026-07-03. **Wire pivot v1.3.0:** 2026-07-11. **Mesh bridge v1.4.6:** 2026-07-12.

> **v0.6.0 additions (2026-07-10):** design pass v0.2â†’v0.5 (mockups in `docs/design/`, guardian councils per pass) landed as code:
> - `ui/AmniTheme.kt` â€” Amni-Scient palette + presets (Amni Dark/Light, Crypt, Haven, Core, Explore), custom accents, `ThemeStore` (DataStore "amni_theme"), `LocalAmni` CompositionLocal.
> - `map/` â€” `GeoMath` (Web-Mercator), `TileStore` (CARTO dark/light + Esri satellite tiles, mem LRU + disk cache, attributed), `MapView` (Compose slippy map: pan/long-press gestures, follow/N-up-vs-heading, 3D tilt via graphicsLayer, zoom 11â€“17, route with OSRM speed-colored traffic segments, destination pin, report pins, GPS puck w/ heading cone), `OsrmRouting` (public OSRM, geojson + speed annotations), `LocationTracker` (Fused 1 Hz shared by map/weather/speed), `ReportStore` (crash/police/hazard/slowdown/camera/closure â†’ filesDir JSON, 2 h TTL).
> - `weather/WeatherService.kt` â€” Open-Meteo current by GPS; `ui/WeatherLayer.kt` â€” Canvas rain/snow/hail particles + thunder flash, auto-calm while MOVING.
> - `ui/Dashboard.kt` + `MapCard.kt` + `AppDrawer.kt` â€” left-rail dock, mono chrome, glass cards, HOME (map | media | weather/trip/gate widgets), NAV full-map, APPS drawer (real icons, category chips, lock badges), gate sheet with **SEND TO REAR** primary; `SettingsActivity` two-pane (Gate + Appearance).
> - `HomeScreen.kt` removed (superseded; backup in `backups/`). Manifest +INTERNET. GateManager.decide() remains the only gate chokepoint â€” theming/UI are presentation-only.

> **v1.4.0 additions (2026-07-12):** `auto/` â€” CarPlay/AA-style auto-entry for the mounted phone/tablet dash. `CarConnectionService` (FGS) runtime-registers power + Bluetooth-ACL receivers; on qualifying connect it enables car mode and launches `MainActivity.openNavIntent()` via direct startActivity + a full-screen-intent notification (BAL-safe). `AutoStartReceiver` restarts it on boot. Config gains `autoStartOnPower`/`autoStartOnBluetooth`/`carBtMac`/`autoEnterCarMode`; Settings gains the **AUTO-START** pane (power/BT/car-mode toggles + bonded-device "learn my car" picker). This is the concrete answer to "run LIKE CarPlay/AA â€” plug in & go, wireless & go" for the **mounted-dash** deployment (the factory-HU Wire path stays crypto-blocked; see research 10).

> **v1.4.3 dual-path (2026-07-12):** Full Amni UI on factory glass is the win condition. **Path A RIDE** = Gearhead authenticates; Amni never steals AOA FD; `AaSurfaceRenderer` paints full chrome on AA Surface. **Path B OWN** = Amni Wire host (AOA + AAP + TLS research + VirtualDisplay H.264). `aaPathMode` auto|ride|own; `AaSessionWatchService` logs + AUTO claim policy. Research: `docs/research/11_dual_path_ride_and_own.md`. Car loop: `scripts/car_dual_path_loop.ps1`.

> **v1.4.6 mesh + ADB-safe (2026-07-12):** Default **RIDE**; no `openAccessory` unless OWN (fixes wifi adb death on launch). **MeshDevBridgeService** HTTP :7931 token API for status/logs/actions over Nord Meshnet without ADB. Scripts: `mesh_bridge.ps1`. Research 12.

> Mandated-workflow note: this file did not exist before v0.1.0 (new project), so this *is* the initial architecture map required by the "scan `architecture_map.md` first" rule. Every future task on Amni-Drive must scan this file first and update it on completion.

---

## 1. What Amni-Drive is

**Primary (v1.0+):** **Amni Link** â€” an Android Auto *replacement* we own end-to-end. The **phone hosts** the full Amni-Drive car UI; the **head unit receives** a live video stream + sends touch back. Open protocol (not Google's AA), same APK on both devices.

**Also:** native **home-screen / launcher** mode on aftermarket Android head units for full local app play + per-display distraction gate.

**Four deployment modes**

| Mode | Phone | Car | Best for |
|---|---|---|---|
| **Amni Wire (v1.3 goal)** | Phone-side AA-compatible host, Amni UI as video | Stock factory AA radio (USB/wireless) | **Replace Google AA entirely** |
| **Car App Library** | Soft app inside Google AA | Stock factory AA | Interim until Wire lands |
| **Amni Link multi-channel** | HOST control+video | Android HU / tablet RECEIVER | Aftermarket Android dash |
| **Native launcher** | Optional tether | Amni-Drive as HOME | Aftermarket full-Android units |

### Amni Wire (`wire/`)

```
AaChannels / AaFrameCodec / AaSession / AaWireHost / AaTransport
  â†’ eventually AOA USB + TLS + aasdk-aligned protobufs
  â†’ H264Encoder feeds VIDEO_SINK with Amni UI
  â†’ INPUT_SOURCE â†’ MotionEvent inject
```

Research: `docs/research/04_aa_wire_protocol_open_stack.md`

---

## 1b. Factory Android Auto (USB) â€” stock cars

```
Phone (Amni-Drive APK)
  â””â”€ AmniCarAppService (androidx.car.app, category NAVIGATION)
        â””â”€ AaHomeScreen / AaSearchScreen / AaRouteScreen
              Geocoder + OsrmRouting (same as phone UI)
        â”‚
        â”‚  USB or wireless Android Auto (Google host)
        â–¼
Factory head unit â€” templates only (map list, search, route pane)
```

- Sideload + **Android Auto â†’ Developer settings â†’ Unknown sources** (required until Play-listed).
- Gate/policy still phone-side; dash uses Google driving-safe templates.

## 2. Amni Link architecture (Android head units)

```
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”         TCP :17321          â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚  PHONE (HOST)        â”‚  â”€â”€ JPEG frames + cfg â”€â”€â–º   â”‚  HEAD UNIT (RECV)    â”‚
â”‚  LinkHost            â”‚  â—„â”€â”€ touch + ping â”€â”€â”€â”€â”€â”€    â”‚  LinkReceiver        â”‚
â”‚  Dashboard UI        â”‚         NSD browse          â”‚  Fullscreen frames   â”‚
â”‚  Gate + maps + apps  â”‚  _amni-link._tcp            â”‚  Touch normalize     â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜                             â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
```

- **Protocol (`link/LinkProto`):** magic `AMNI` (0x414D4E49), version 1, type + payload length, types HELLO / VIDEO_CFG / FRAME / TOUCH / PING / PONG / BYE.
- **Host:** captures `android.R.id.content`, scales to 1920Ã—720, JPEG ~q55 @ ~12 fps; injects `MotionEvent` for remote touch.
- **Receiver:** decodes frames to Bitmap; maps pointer to 0..1; manual IP if NSD blocked.
- **Discovery:** `LinkDiscovery` registers/browses `_amni-link._tcp` on port 17321.
- **UI entry:** `LinkActivity` (second launcher icon) + Settings â€º AMNI LINK + menu Cast tile.
- **Roadmap:** H.264 MediaCodec, audio, USB accessory, optional bridges to factory AA radios.

Native launcher modules (`launcher/`, `gate/`, `display/`) remain for mode 2 and for host-side gate policy.

---

## 3. Target-hardware reality (three flavors of "Android head unit")

This matters because the gate has different hooks on each.

1. **Aftermarket "Chinese" Android head units** â€” standard AOSP Android 8â€“13, often rooted, no Google Automotive certification, **no `CarUxRestrictions` / Car API at all**. Most permissive: sideload + set-default-home just like a tablet. Physical **park-brake** and **reverse** trigger wires exist but are coarse and trivially defeatable. *This is the realistic real-world deployment target.*
2. **Genuine Android Automotive OS (AAOS)** â€” full Android maintained by the OEM (Polestar/Volvo/GM/Rivian). Has the **Car API** (`CarPropertyManager`, `CarUxRestrictionsManager`, occupant-zone/multi-display). But the true home (`CarLauncher`) is privileged; replacing it on a **locked production** car needs OEM signing â†’ third-party home only realistic on **your own dev/AAOS build**.
3. **Cheap consumer Android tablet (test bench)** â€” runs the *exact same* AOSP launcher stack (`HOME` intent, `queryIntentActivities`, default-home role) as a Chinese head unit. **This is our primary test target** â€” closest low-friction analog, full app play genuinely works.

**Design consequence:** Amni-Drive must run and gate on a **plain AOSP device with no Car API present**, and *light up richer signals* (speed/gear/UX-restrictions, occupant-zone displays) **only when running on genuine AAOS** â€” detected at runtime via reflection so a single APK installs everywhere.

---

## 4. High-level architecture

```
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚                          Amni-Drive (APK)                              â”‚
â”‚                                                                        â”‚
â”‚  MainActivity (HOME/DEFAULT/LAUNCHER)  â”€â”€ primary driver display       â”‚
â”‚     â”‚  app grid Â· dashboard Â· quick tiles Â· gate quick-toggle          â”‚
â”‚     â–¼                                                                   â”‚
â”‚  launcher/                display/                gate/                 â”‚
â”‚   â”œ AppInventory          â”œ DisplayRouter          â”œ GateManager  â—€â”€â”€ central policy
â”‚   â”‚  (queryIntentâ€¦)       â”‚  (DisplayManager +      â”‚   decide(category, displayRole,
â”‚   â”œ AppClassifier         â”‚   AAOS occupant zones)  â”‚          driveState, config)
â”‚   â”‚  (pkg â†’ AppCategory)  â”œ DisplayRole enum        â”œ AppCategory enum
â”‚   â”” AppLauncher           â”” RearPresentation        â”œ GatePolicy (per-display)
â”‚      launch on target        (full-app UI on        â”” speed/
â”‚      display via              rear/passenger screen)    â”œ MotionSource (iface)
â”‚      setLaunchDisplayId                                  â”œ GpsMotionSource (default)
â”‚                                                          â”œ ManualMotionSource (toggle)
â”‚                                                          â”œ CarPropertyMotionSource (AAOS, reflection)
â”‚                                                          â”” MotionMonitor (fuse â†’ DriveState, hysteresis, fail-closed)
â”‚                                                                        â”‚
â”‚  config/ DriveConfig + ConfigStore (DataStore) â€” persisted, per-display policy, toggles â”‚
â”‚  ui/ SettingsActivity â€” where every toggle lives                       â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
```

### Module responsibilities

- **launcher/** â€” enumerate installed apps (`PackageManager.queryIntentActivities` for `MAIN`+`LAUNCHER`), render the grid, and launch a chosen app. `AppLauncher` routes a launch to a *specific display* via `ActivityOptions.setLaunchDisplayId(...)` â€” this is how a full app gets sent to the rear/passenger screen.
- **display/** â€” `DisplayRouter` enumerates `DisplayManager.getDisplays()` and classifies each as `PRIMARY_DRIVER`, `REAR_PASSENGER`, `CLUSTER`, or `UNKNOWN`. On AAOS it refines classification with `CarOccupantZoneManager` (reflection). `RearPresentation` hosts the ungated full-app home on a secondary display.
- **gate/** â€” the safety core. `GateManager.decide(...)` is the single source of truth for "may this category launch on this display right now?". `AppCategory` classifies apps into `VIDEO / GAME / INTERACTIVE / NAV / MEDIA / COMMS / SAFE`. `GatePolicy` holds the per-display rule.
- **gate/speed/** â€” `MotionMonitor` fuses one or more `MotionSource`s into a `DriveState` (`PARKED / IDLING / MOVING / UNKNOWN`) with deadband + hysteresis and **fail-closed** (unknown/no-fix â†’ treated as MOVING).
- **config/** â€” `DriveConfig` is the persisted settings model (per-display policies, master gate toggle, deadband, passenger-confirm timeout). `ConfigStore` persists via Jetpack DataStore.
- **ui/** â€” `SettingsActivity` is where **every** toggle lives, plus a always-reachable **gate quick-toggle** on the driver home.

---

## 5. Safety design â€” the per-display distraction gate (per Anthony's 2026-07-03 spec)

This is the heart of the project and is **present by default, configurable, no hard locks.**

### 5.1 Philosophy
Permissive where it's safe (backseat), light-touch-but-present where it isn't (driver's sightline), always the driver's override. We explicitly do **not** ship driver-facing video-at-speed as a silent default; we also don't nanny the backseat.

### 5.2 Per-display policy (the key mechanism)

| Display role | Default policy | Rationale |
|---|---|---|
| **REAR_PASSENGER** | **UNGATED** â€” video/games/interactive just work | Not in the driver's sightline. Kids-in-the-backseat scenario works out of the box. |
| **PRIMARY_DRIVER** | **Light gate ON** â€” video/games/interactive gated to *Parked* **or** *passenger-confirmed*; **one-tap disable** | In the driver's eyeline; video-at-speed here is the actual crash-risk case. Not a hard lock â€” one tap turns it off. |
| **CLUSTER / UNKNOWN** | Treated as driver (gate ON) | Conservative default; user can re-tag a display in settings. |

- **Non-distracting categories** (NAV, MEDIA controls, COMMS, phone, SAFE) are **never** gated on any display.
- **Multi-display auto-routing:** when a rear/passenger display is detected, the ungated full-app experience is routed there (`AppLauncher` â†’ `setLaunchDisplayId(rearDisplayId)`; `RearPresentation` for the rear home).
- **Manual override on driver display:** a "Parked" button and an "I'm a passenger" confirmation temporarily unlock distracting content; it **re-locks automatically** once motion re-crosses the moving threshold.

### 5.3 Where the toggle lives (explicit, per Anthony's ask)
1. **`SettingsActivity` â†’ "Distraction gate" section** â€” master on/off, per-display policy table (re-tag displays, set each display gated/ungated), passenger-confirm timeout, deadband/threshold, motion-source selection.
2. **Driver-home quick-toggle** â€” a persistent tile on the primary display for the **one-tap disable** of the driver gate. Always reachable, no menu diving.
3. Persisted in `DriveConfig` via `ConfigStore` (DataStore). Defaults: `driverGate = ON`, `rearGate = OFF`, `masterGate = ON`.

### 5.4 Motion / drive-state signal (default + upgrades)
Mirrors AAOS's proven three-state model (Parked = free, Idling = no video, Moving = fully restricted).

| Rank | Source | Availability | Role |
|---|---|---|---|
| 1 | AAOS `CarPropertyManager` (speed+gear+brake) / `CarUxRestrictionsManager` | AAOS only | Best signal; consume directly when present |
| 2 | OBD-II ELM327 (PID `010D`) | Any car + adapter | Upgrade; tunnel/garage-proof |
| 3 | **GPS speed (FusedLocation)** | Universal | **Default / testing** |
| 4 | Park/reverse wires | Aftermarket | Corroborator only (defeatable) |
| 5 | Accelerometer / ActivityRecognition | Universal | Dropout bridge / standstill confirm |

- **Default build = GPS speed + manual toggle.** `Priority.HIGH_ACCURACY`, ~1 Hz. "Moving" when speed > deadband (~1.5â€“2 m/s â‰ˆ 3â€“4 mph) for â‰¥2 consecutive valid samples; "stopped" only after several sub-threshold samples with acceptable `speedAccuracy`.
- **Fail-closed:** no fix / timeout â†’ treat as **MOVING** (gate active), never as parked.
- **Upgrade A (OBD-II):** when an ELM327 is paired, prefer PID `010D` over GPS.
- **Upgrade B (AAOS):** read `PERF_VEHICLE_SPEED` + `GEAR_SELECTION` + `PARKING_BRAKE_ON` (the 3 mandatory props are exactly these) or consume `CarUxRestrictionsManager.isRequiresDistractionOptimization()` directly.

### 5.5 Why this matters (grounding)
NHTSA: 3,208 killed in distraction-affected crashes in 2024; visual-manual tasks raise crash risk up to ~3Ã—. NHTSA/AAOS guidelines: single glance â‰¤ 2 s, total task â‰¤ 12 s, no displayed video/scrolling while driving. The gate is our stand-in for those glance/interaction limits on the driver display â€” and deliberately absent on a backseat screen where those limits don't apply.

---

## 6. Paths we own vs defer

- **Own now:** Amni Link open projection + native launcher (this codebase).
- **Later research:** factory head units that *only* speak Google Android Auto â€” optional adapter/bridge track, not required for Android dash units.
- **Out of scope for v1:** full AAOS OEM home replacement on locked production cars.

---

## 7. Tech stack

- **Language:** Kotlin. **UI:** Jetpack Compose (primary home + settings) + `Presentation`(hosting `ComposeView`) for the rear display.
- **Min/target SDK:** minSdk 26 (covers old aftermarket units), targetSdk 34.
- **Key deps:** `androidx.activity:activity-compose`, Compose BOM, `androidx.lifecycle` (coroutines/Flow), `androidx.datastore:datastore-preferences`, `com.google.android.gms:play-services-location` (Fused speed).
- **Car API:** accessed via **reflection** (no compile dependency on `android.car`) so one APK runs on plain AOSP tablets *and* lights up on AAOS.
- **Async:** Kotlin coroutines + `Flow` for the motion monitor and app-inventory load (the CLAUDE.md "async where responsiveness matters" rule; the GPU/vectorization rules don't apply to an Android UI launcher â€” noted deliberately).

---

## 8. Source tree

```
Amni-Drive/
â”œâ”€â”€ architecture_map.md          â† this file
â”œâ”€â”€ changelog.md
â”œâ”€â”€ README.md
â”œâ”€â”€ settings.gradle.kts Â· build.gradle.kts Â· gradle.properties Â· .gitignore
â”œâ”€â”€ gradle/wrapper/gradle-wrapper.properties   (jar generated by Android Studio / `gradle wrapper`)
â”œâ”€â”€ app/
â”‚   â”œâ”€â”€ build.gradle.kts Â· proguard-rules.pro
â”‚   â””â”€â”€ src/
â”‚       â”œâ”€â”€ main/AndroidManifest.xml
â”‚       â”œâ”€â”€ main/java/com/amni/drive/
â”‚       â”‚   â”œâ”€â”€ AmniDriveApp.kt Â· MainActivity.kt Â· LinkActivity.kt
â”‚       â”‚   â”œâ”€â”€ link/      (LinkProto, LinkHost, LinkReceiver, LinkDiscovery)
â”‚       â”‚   â”œâ”€â”€ launcher/  (AppEntry, AppInventory, AppLauncher, AppClassifier)
â”‚       â”‚   â”œâ”€â”€ gate/      (AppCategory, DriveState, GatePolicy, GateManager, GateDecision)
â”‚       â”‚   â”œâ”€â”€ gate/speed/(MotionSource, GpsMotionSource, ManualMotionSource, CarPropertyMotionSource, MotionMonitor)
â”‚       â”‚   â”œâ”€â”€ display/   (DisplayRole, DisplayRouter, RearPresentation)
â”‚       â”‚   â”œâ”€â”€ config/    (DriveConfig, ConfigStore)
â”‚       â”‚   â”œâ”€â”€ map/ Â· weather/
â”‚       â”‚   â””â”€â”€ ui/        (Dashboard, SettingsActivity, theme)
â”‚       â”œâ”€â”€ main/res/      (themes day/night, colors, strings, xml)
â”‚       â””â”€â”€ test/java/com/amni/drive/  (GateManagerTest, MotionMonitorTest, AppClassifierTest)
â”œâ”€â”€ docs/
â”‚   â”œâ”€â”€ architecture/          (design notes)
â”‚   â”œâ”€â”€ research/              (the 3 sourced research briefs)
â”‚   â”œâ”€â”€ checklists/
â”‚   â””â”€â”€ guardian_councils/
â””â”€â”€ backups/                   (pre-edit backups per CLAUDE.md workflow)
```

---

## 9. Verification status (what's been checked vs what still needs a device)

**Verified on this machine** (Gradle 8.13, JDK 21 Android Studio JBR), 2026-07-11 (v1.0.0):
- âœ… **Full compile** â€” `:app:compileDebugKotlin` (Amni Link + launcher).
- âœ… **Unit tests 25 pass** â€” prior 21 + `LinkProtoTest` (4).
- âœ… **Debug APK** â€” `app/build/outputs/apk/debug/app-debug.apk` (~15.7 MB).

**Still needs Anthony / two devices:**
- **Amni Link LAN smoke:** phone HOST + second Android device RECEIVER, same hotspot, touch loop.
- Native launcher default-home, multi-display, AAOS reflection as before.
- Task not "complete" until Anthony confirms working (CLAUDE.md rule 8).

---

## 10. Open items needing Anthony

See README Â§"Flags for Anthony". Summary: exact head-unit model/Android version, whether it exposes a **second (rear) display**, how motion signal is sourced on the real unit (GPS vs OBD vs AAOS), and whether the real unit is rooted (affects set-default-home).







