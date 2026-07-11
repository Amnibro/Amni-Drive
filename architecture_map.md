# Amni-Drive — Architecture Map

**Project:** Amni-Drive — **replace Android Auto** (wire-level host + multi-channel Link + Car App fallback + launcher).
**Owner:** Anthony (mechanical engineer) — personal testing project.
**Status:** **v1.3.0 — Amni Wire scaffold** (open reverse-engineered AA channel map, frame/session/host, multi-channel Link, H.264 encoder stub). Car App soft path retained. Real HU handshake next.
**Created:** 2026-07-03. **Wire pivot v1.3.0:** 2026-07-11.

> **v0.6.0 additions (2026-07-10):** design pass v0.2→v0.5 (mockups in `docs/design/`, guardian councils per pass) landed as code:
> - `ui/AmniTheme.kt` — Amni-Scient palette + presets (Amni Dark/Light, Crypt, Haven, Core, Explore), custom accents, `ThemeStore` (DataStore "amni_theme"), `LocalAmni` CompositionLocal.
> - `map/` — `GeoMath` (Web-Mercator), `TileStore` (CARTO dark/light + Esri satellite tiles, mem LRU + disk cache, attributed), `MapView` (Compose slippy map: pan/long-press gestures, follow/N-up-vs-heading, 3D tilt via graphicsLayer, zoom 11–17, route with OSRM speed-colored traffic segments, destination pin, report pins, GPS puck w/ heading cone), `OsrmRouting` (public OSRM, geojson + speed annotations), `LocationTracker` (Fused 1 Hz shared by map/weather/speed), `ReportStore` (crash/police/hazard/slowdown/camera/closure → filesDir JSON, 2 h TTL).
> - `weather/WeatherService.kt` — Open-Meteo current by GPS; `ui/WeatherLayer.kt` — Canvas rain/snow/hail particles + thunder flash, auto-calm while MOVING.
> - `ui/Dashboard.kt` + `MapCard.kt` + `AppDrawer.kt` — left-rail dock, mono chrome, glass cards, HOME (map | media | weather/trip/gate widgets), NAV full-map, APPS drawer (real icons, category chips, lock badges), gate sheet with **SEND TO REAR** primary; `SettingsActivity` two-pane (Gate + Appearance).
> - `HomeScreen.kt` removed (superseded; backup in `backups/`). Manifest +INTERNET. GateManager.decide() remains the only gate chokepoint — theming/UI are presentation-only.

> Mandated-workflow note: this file did not exist before v0.1.0 (new project), so this *is* the initial architecture map required by the "scan `architecture_map.md` first" rule. Every future task on Amni-Drive must scan this file first and update it on completion.

---

## 1. What Amni-Drive is

**Primary (v1.0+):** **Amni Link** — an Android Auto *replacement* we own end-to-end. The **phone hosts** the full Amni-Drive car UI; the **head unit receives** a live video stream + sends touch back. Open protocol (not Google's AA), same APK on both devices.

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
  → eventually AOA USB + TLS + aasdk-aligned protobufs
  → H264Encoder feeds VIDEO_SINK with Amni UI
  → INPUT_SOURCE → MotionEvent inject
```

Research: `docs/research/04_aa_wire_protocol_open_stack.md`

---

## 1b. Factory Android Auto (USB) — stock cars

```
Phone (Amni-Drive APK)
  └─ AmniCarAppService (androidx.car.app, category NAVIGATION)
        └─ AaHomeScreen / AaSearchScreen / AaRouteScreen
              Geocoder + OsrmRouting (same as phone UI)
        │
        │  USB or wireless Android Auto (Google host)
        ▼
Factory head unit — templates only (map list, search, route pane)
```

- Sideload + **Android Auto → Developer settings → Unknown sources** (required until Play-listed).
- Gate/policy still phone-side; dash uses Google driving-safe templates.

## 2. Amni Link architecture (Android head units)

```
┌──────────────────────┐         TCP :17321          ┌──────────────────────┐
│  PHONE (HOST)        │  ── JPEG frames + cfg ──►   │  HEAD UNIT (RECV)    │
│  LinkHost            │  ◄── touch + ping ──────    │  LinkReceiver        │
│  Dashboard UI        │         NSD browse          │  Fullscreen frames   │
│  Gate + maps + apps  │  _amni-link._tcp            │  Touch normalize     │
└──────────────────────┘                             └──────────────────────┘
```

- **Protocol (`link/LinkProto`):** magic `AMNI` (0x414D4E49), version 1, type + payload length, types HELLO / VIDEO_CFG / FRAME / TOUCH / PING / PONG / BYE.
- **Host:** captures `android.R.id.content`, scales to 1920×720, JPEG ~q55 @ ~12 fps; injects `MotionEvent` for remote touch.
- **Receiver:** decodes frames to Bitmap; maps pointer to 0..1; manual IP if NSD blocked.
- **Discovery:** `LinkDiscovery` registers/browses `_amni-link._tcp` on port 17321.
- **UI entry:** `LinkActivity` (second launcher icon) + Settings › AMNI LINK + menu Cast tile.
- **Roadmap:** H.264 MediaCodec, audio, USB accessory, optional bridges to factory AA radios.

Native launcher modules (`launcher/`, `gate/`, `display/`) remain for mode 2 and for host-side gate policy.

---

## 3. Target-hardware reality (three flavors of "Android head unit")

This matters because the gate has different hooks on each.

1. **Aftermarket "Chinese" Android head units** — standard AOSP Android 8–13, often rooted, no Google Automotive certification, **no `CarUxRestrictions` / Car API at all**. Most permissive: sideload + set-default-home just like a tablet. Physical **park-brake** and **reverse** trigger wires exist but are coarse and trivially defeatable. *This is the realistic real-world deployment target.*
2. **Genuine Android Automotive OS (AAOS)** — full Android maintained by the OEM (Polestar/Volvo/GM/Rivian). Has the **Car API** (`CarPropertyManager`, `CarUxRestrictionsManager`, occupant-zone/multi-display). But the true home (`CarLauncher`) is privileged; replacing it on a **locked production** car needs OEM signing → third-party home only realistic on **your own dev/AAOS build**.
3. **Cheap consumer Android tablet (test bench)** — runs the *exact same* AOSP launcher stack (`HOME` intent, `queryIntentActivities`, default-home role) as a Chinese head unit. **This is our primary test target** — closest low-friction analog, full app play genuinely works.

**Design consequence:** Amni-Drive must run and gate on a **plain AOSP device with no Car API present**, and *light up richer signals* (speed/gear/UX-restrictions, occupant-zone displays) **only when running on genuine AAOS** — detected at runtime via reflection so a single APK installs everywhere.

---

## 4. High-level architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                          Amni-Drive (APK)                              │
│                                                                        │
│  MainActivity (HOME/DEFAULT/LAUNCHER)  ── primary driver display       │
│     │  app grid · dashboard · quick tiles · gate quick-toggle          │
│     ▼                                                                   │
│  launcher/                display/                gate/                 │
│   ├ AppInventory          ├ DisplayRouter          ├ GateManager  ◀── central policy
│   │  (queryIntent…)       │  (DisplayManager +      │   decide(category, displayRole,
│   ├ AppClassifier         │   AAOS occupant zones)  │          driveState, config)
│   │  (pkg → AppCategory)  ├ DisplayRole enum        ├ AppCategory enum
│   └ AppLauncher           └ RearPresentation        ├ GatePolicy (per-display)
│      launch on target        (full-app UI on        └ speed/
│      display via              rear/passenger screen)    ├ MotionSource (iface)
│      setLaunchDisplayId                                  ├ GpsMotionSource (default)
│                                                          ├ ManualMotionSource (toggle)
│                                                          ├ CarPropertyMotionSource (AAOS, reflection)
│                                                          └ MotionMonitor (fuse → DriveState, hysteresis, fail-closed)
│                                                                        │
│  config/ DriveConfig + ConfigStore (DataStore) — persisted, per-display policy, toggles │
│  ui/ SettingsActivity — where every toggle lives                       │
└──────────────────────────────────────────────────────────────────────┘
```

### Module responsibilities

- **launcher/** — enumerate installed apps (`PackageManager.queryIntentActivities` for `MAIN`+`LAUNCHER`), render the grid, and launch a chosen app. `AppLauncher` routes a launch to a *specific display* via `ActivityOptions.setLaunchDisplayId(...)` — this is how a full app gets sent to the rear/passenger screen.
- **display/** — `DisplayRouter` enumerates `DisplayManager.getDisplays()` and classifies each as `PRIMARY_DRIVER`, `REAR_PASSENGER`, `CLUSTER`, or `UNKNOWN`. On AAOS it refines classification with `CarOccupantZoneManager` (reflection). `RearPresentation` hosts the ungated full-app home on a secondary display.
- **gate/** — the safety core. `GateManager.decide(...)` is the single source of truth for "may this category launch on this display right now?". `AppCategory` classifies apps into `VIDEO / GAME / INTERACTIVE / NAV / MEDIA / COMMS / SAFE`. `GatePolicy` holds the per-display rule.
- **gate/speed/** — `MotionMonitor` fuses one or more `MotionSource`s into a `DriveState` (`PARKED / IDLING / MOVING / UNKNOWN`) with deadband + hysteresis and **fail-closed** (unknown/no-fix → treated as MOVING).
- **config/** — `DriveConfig` is the persisted settings model (per-display policies, master gate toggle, deadband, passenger-confirm timeout). `ConfigStore` persists via Jetpack DataStore.
- **ui/** — `SettingsActivity` is where **every** toggle lives, plus a always-reachable **gate quick-toggle** on the driver home.

---

## 5. Safety design — the per-display distraction gate (per Anthony's 2026-07-03 spec)

This is the heart of the project and is **present by default, configurable, no hard locks.**

### 5.1 Philosophy
Permissive where it's safe (backseat), light-touch-but-present where it isn't (driver's sightline), always the driver's override. We explicitly do **not** ship driver-facing video-at-speed as a silent default; we also don't nanny the backseat.

### 5.2 Per-display policy (the key mechanism)

| Display role | Default policy | Rationale |
|---|---|---|
| **REAR_PASSENGER** | **UNGATED** — video/games/interactive just work | Not in the driver's sightline. Kids-in-the-backseat scenario works out of the box. |
| **PRIMARY_DRIVER** | **Light gate ON** — video/games/interactive gated to *Parked* **or** *passenger-confirmed*; **one-tap disable** | In the driver's eyeline; video-at-speed here is the actual crash-risk case. Not a hard lock — one tap turns it off. |
| **CLUSTER / UNKNOWN** | Treated as driver (gate ON) | Conservative default; user can re-tag a display in settings. |

- **Non-distracting categories** (NAV, MEDIA controls, COMMS, phone, SAFE) are **never** gated on any display.
- **Multi-display auto-routing:** when a rear/passenger display is detected, the ungated full-app experience is routed there (`AppLauncher` → `setLaunchDisplayId(rearDisplayId)`; `RearPresentation` for the rear home).
- **Manual override on driver display:** a "Parked" button and an "I'm a passenger" confirmation temporarily unlock distracting content; it **re-locks automatically** once motion re-crosses the moving threshold.

### 5.3 Where the toggle lives (explicit, per Anthony's ask)
1. **`SettingsActivity` → "Distraction gate" section** — master on/off, per-display policy table (re-tag displays, set each display gated/ungated), passenger-confirm timeout, deadband/threshold, motion-source selection.
2. **Driver-home quick-toggle** — a persistent tile on the primary display for the **one-tap disable** of the driver gate. Always reachable, no menu diving.
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

- **Default build = GPS speed + manual toggle.** `Priority.HIGH_ACCURACY`, ~1 Hz. "Moving" when speed > deadband (~1.5–2 m/s ≈ 3–4 mph) for ≥2 consecutive valid samples; "stopped" only after several sub-threshold samples with acceptable `speedAccuracy`.
- **Fail-closed:** no fix / timeout → treat as **MOVING** (gate active), never as parked.
- **Upgrade A (OBD-II):** when an ELM327 is paired, prefer PID `010D` over GPS.
- **Upgrade B (AAOS):** read `PERF_VEHICLE_SPEED` + `GEAR_SELECTION` + `PARKING_BRAKE_ON` (the 3 mandatory props are exactly these) or consume `CarUxRestrictionsManager.isRequiresDistractionOptimization()` directly.

### 5.5 Why this matters (grounding)
NHTSA: 3,208 killed in distraction-affected crashes in 2024; visual-manual tasks raise crash risk up to ~3×. NHTSA/AAOS guidelines: single glance ≤ 2 s, total task ≤ 12 s, no displayed video/scrolling while driving. The gate is our stand-in for those glance/interaction limits on the driver display — and deliberately absent on a backseat screen where those limits don't apply.

---

## 6. Paths we own vs defer

- **Own now:** Amni Link open projection + native launcher (this codebase).
- **Later research:** factory head units that *only* speak Google Android Auto — optional adapter/bridge track, not required for Android dash units.
- **Out of scope for v1:** full AAOS OEM home replacement on locked production cars.

---

## 7. Tech stack

- **Language:** Kotlin. **UI:** Jetpack Compose (primary home + settings) + `Presentation`(hosting `ComposeView`) for the rear display.
- **Min/target SDK:** minSdk 26 (covers old aftermarket units), targetSdk 34.
- **Key deps:** `androidx.activity:activity-compose`, Compose BOM, `androidx.lifecycle` (coroutines/Flow), `androidx.datastore:datastore-preferences`, `com.google.android.gms:play-services-location` (Fused speed).
- **Car API:** accessed via **reflection** (no compile dependency on `android.car`) so one APK runs on plain AOSP tablets *and* lights up on AAOS.
- **Async:** Kotlin coroutines + `Flow` for the motion monitor and app-inventory load (the CLAUDE.md "async where responsiveness matters" rule; the GPU/vectorization rules don't apply to an Android UI launcher — noted deliberately).

---

## 8. Source tree

```
Amni-Drive/
├── architecture_map.md          ← this file
├── changelog.md
├── README.md
├── settings.gradle.kts · build.gradle.kts · gradle.properties · .gitignore
├── gradle/wrapper/gradle-wrapper.properties   (jar generated by Android Studio / `gradle wrapper`)
├── app/
│   ├── build.gradle.kts · proguard-rules.pro
│   └── src/
│       ├── main/AndroidManifest.xml
│       ├── main/java/com/amni/drive/
│       │   ├── AmniDriveApp.kt · MainActivity.kt · LinkActivity.kt
│       │   ├── link/      (LinkProto, LinkHost, LinkReceiver, LinkDiscovery)
│       │   ├── launcher/  (AppEntry, AppInventory, AppLauncher, AppClassifier)
│       │   ├── gate/      (AppCategory, DriveState, GatePolicy, GateManager, GateDecision)
│       │   ├── gate/speed/(MotionSource, GpsMotionSource, ManualMotionSource, CarPropertyMotionSource, MotionMonitor)
│       │   ├── display/   (DisplayRole, DisplayRouter, RearPresentation)
│       │   ├── config/    (DriveConfig, ConfigStore)
│       │   ├── map/ · weather/
│       │   └── ui/        (Dashboard, SettingsActivity, theme)
│       ├── main/res/      (themes day/night, colors, strings, xml)
│       └── test/java/com/amni/drive/  (GateManagerTest, MotionMonitorTest, AppClassifierTest)
├── docs/
│   ├── architecture/          (design notes)
│   ├── research/              (the 3 sourced research briefs)
│   ├── checklists/
│   └── guardian_councils/
└── backups/                   (pre-edit backups per CLAUDE.md workflow)
```

---

## 9. Verification status (what's been checked vs what still needs a device)

**Verified on this machine** (Gradle 8.13, JDK 21 Android Studio JBR), 2026-07-11 (v1.0.0):
- ✅ **Full compile** — `:app:compileDebugKotlin` (Amni Link + launcher).
- ✅ **Unit tests 25 pass** — prior 21 + `LinkProtoTest` (4).
- ✅ **Debug APK** — `app/build/outputs/apk/debug/app-debug.apk` (~15.7 MB).

**Still needs Anthony / two devices:**
- **Amni Link LAN smoke:** phone HOST + second Android device RECEIVER, same hotspot, touch loop.
- Native launcher default-home, multi-display, AAOS reflection as before.
- Task not "complete" until Anthony confirms working (CLAUDE.md rule 8).

---

## 10. Open items needing Anthony

See README §"Flags for Anthony". Summary: exact head-unit model/Android version, whether it exposes a **second (rear) display**, how motion signal is sourced on the real unit (GPS vs OBD vs AAOS), and whether the real unit is rooted (affects set-default-home).
