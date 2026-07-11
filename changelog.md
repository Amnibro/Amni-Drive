# Amni-Drive — Changelog

## v1.3.3 — 2026-07-11 — AOA OPENED (Always allow works)
**Live milestone:** User granted Always allow.

```
PERMISSION granted=true ... Android Auto
OPENED fd=113
sniff end total=0
```

**Meaning:** We own the accessory FD. Zero RX is expected until we **speak** (phone is TLS server in AA). DETACH followed shortly after (session reset / gearhead reclaim).

**Code:** Auto-start probe on app launch; hold open 30s; clearer notif; research note `docs/research/06_aoa_open_granted.md`. Next: SSLEngine on accessory duplex.

---

## v1.3.2 — 2026-07-11 — AOA permission request + claim script
**M3 progress:** Accessory visible but `perm=false` (gearhead exclusive).

**Added:**
- `UsbManager.requestPermission` path in `AoaProbeService` (PendingIntent + broadcast)
- `ACTION_CLAIM` + Settings button **START AOA PROBE + REQUEST PERMISSION**
- Dump first RX bytes to `files/aoa_probe/first_chunk.bin` when open succeeds
- `AoaBulkTransport` (AaTransport over accessory FD)
- `scripts/claim_aoa_research.ps1` — force-stop gearhead, start probe, poll for OPEN/PERMISSION
- versionCode 15 / 1.3.2

**Live result:** after force-stop gearhead, probe logs `requesting USB accessory permission…` — user must accept dialog on phone; then re-poll for OPEN + sniffer.

---

## v1.3.1 — 2026-07-11 — Open source kit + AOA probe + /loop
**Mandate:** Loop until Amni replaces Android Auto; make it open source.

**Added:**
- Apache-2.0 `LICENSE`, `NOTICE`, `CONTRIBUTING.md`, `SECURITY.md`, `ROADMAP.md`
- Public README rewrite (OSS + modes + build)
- `wire/AoaProbeService` + `AoaAttachActivity` + `usb_accessory_filter.xml` (log/claim AOA attach)
- Settings › AMNI WIRE → **START AOA PROBE**
- Live captures remain in `docs/research/05_live_aa_session_capture.md`
- versionCode 14 / 1.3.1

**Loop:** Agent scheduler continues M3/M4 while phone ADB is available.

---

## v1.3.0 — 2026-07-11 — Amni Wire: replace Android Auto (scaffold)
**Mandate:** Replace AA even if reverse-engineering the communication link is required.

**Added:**
- Research `docs/research/04_aa_wire_protocol_open_stack.md` (aasdk/OpenAuto/OAL-grounded)
- Guardian council for wire-level replace
- `wire/` package: `AaChannels`, `AaFrameCodec`, `AaSession`, `AaWireHost`, `AaTransport`
- `link/H264Encoder` MediaCodec surface encoder scaffold
- Amni Link **v2 multi-channel**: control `:17321` + video `:17322`
- Settings › **AMNI WIRE** experimental pane
- Unit tests: `AaFrameTest`
- versionCode 13 / versionName 1.3.0 (debug assemble; ship release when testing factory AA soft path)

**Not yet (needs car/DHU):** full AOA handshake, TLS, aasdk protobuf payloads, first factory HU accepting Amni video.

---

## v1.2.4 — 2026-07-11 — AA fix: NAVIGATION category (was POI) + ship RELEASE build
**Diagnosis (combines prior attempts):** Anthony's '23 Niro still not launching Amni-Drive over factory AA. Two independent misconfigs were both live: (1) manifest CarAppService category had drifted to **POI-only** (v1.2.2) — wrong for a routing app; factory AA lists third-party **NAVIGATION** apps (Waze/TomTom class), POI is a restricted class most factory units hide for sideloads. (2) v1.2.3 found AA filters **debug** sideloads — needs the non-debuggable **release** APK (HostValidator → Google host allowlist). Prior versions changed one axis at a time so the working combo (NAVIGATION + release) was never actually tried together.
**Fix — TWO coupled bugs (the catch-22 prior versions kept half-fixing):**
1. Category POI → `androidx.car.app.category.NAVIGATION` + `androidx.car.app.action.NAVIGATE` (geo) intent-filter.
2. `AaHomeScreen` used `PlaceListMapTemplate`, which Google restricts to **POI apps only** (confirmed via developer.android.com nav/POI template docs). With NAVIGATION category that template is illegal → app would list but **crash on tap**. Swapped to `PlaceListNavigationTemplate` (navigation-legal, map+place-list, API level 1; verified present in car.app 1.7.0 aar via javap; dropped `setCurrentLocationEnabled` which isn't on it). AaSearchScreen (SearchTemplate) + AaRouteScreen (Pane/Message) are category-agnostic — unchanged.
   → The POI↔NAVIGATION tension is exactly why v1.2.1 (both), v1.2.2 (POI), v1.2.3 (release) each fixed only one axis. NAVIGATION + nav-legal template + release build is the combination that was never tried together.
Built **release** (isDebuggable=false, debug-key signed, 11.9 MB); merged manifest = NAVIGATION + non-debuggable; grep confirms no PlaceListMapTemplate remains; assembleRelease clean (only deprecation warnings). versionCode 12 / 1.2.4.
**Still open / honest caveat:** even correctly declared, some factory AA builds refuse unpublished sideloaded nav apps entirely — the Google-sanctioned test path is the **Desktop Head Unit (DHU)**, not the car. Cannot verify on-car or via DHU without Anthony's phone on this PC's adb (phone is away). If it still won't list after a clean AA reconnect, next step is DHU verification or pulling AA logs off the phone. NOT complete until Anthony confirms it launches in the car (rule 8).

## v1.2.3 — 2026-07-11 — AA: non-debuggable release for real cars
**Description:** Still missing from AA app list with Unknown sources ON. AA 17.x + mid-2026 reports filter pure sideloads; debug builds especially.
**Fix:** Ship **release** APK (`isDebuggable=false`, signed with debug key for sideload), HostValidator uses Google host allowlist when not debuggable, reinstall with `installer=com.android.vending`, re-grant location. versionCode 11.
**Verified:** release install Success on Fold.

---

## v1.2.2 — 2026-07-11 — AA: Play-installer reinstall + POI-only
**Description:** Amni still missing from AA app list. Google has been filtering pure sideloads (2026 reports even with Unknown sources).
**Fix:** Single **POI** CarAppService category (nav dual-filter removed). Car App Library 1.7. Reinstall with `adb install -i com.android.vending` so installerPackageName = Play Store. Script: `scripts/install_for_android_auto.ps1`.
**Verified:** phone install Success; `com.amni.drive installer=com.android.vending`; versionCode 10 / 1.2.2.

---

## v1.2.1 — 2026-07-11 — AA: show on dash (POI + setup)
**Description:** Anthony: AA works on car but Amni Drive missing from app list (#2).
**Fix:** Register car service as **POI** (matches PlaceListMap UI; nav-only apps often stay hidden when incomplete) **and** NAVIGATION. Settings › CAR / USB: stronger Unknown-sources steps + **OPEN ANDROID AUTO SETTINGS** button.
**versionCode 9 / 1.2.1.** Build green; install when ADB up.

---

## v1.2.0 — 2026-07-11 — UI modernize (soft glass shell)
**Description:** Anthony liking the app — modernize interface while away from car. Autonomous polish pass.

**UI:**
- Richer palette (deeper darks, softer lights), gradient glass cards with accent hairline, SoftSurface + shadows
- Rail: brand mark, scale-on-select, gradient selected pills
- Top bar: time-of-day greeting + “Anthony”, clock in soft surface
- Animated page transitions (HOME / APPS / NAV)
- Cards: accent bars, cleaner type hierarchy, gradient primary buttons
- App drawer: elevated tiles, gradient filter chips
- Map chrome: squircle controls, friendlier route banner copy
- versionCode 8 / versionName 1.2.0

**Verified:** compile + unit tests + assembleDebug. Install attempted if ADB online.

---

## v1.1.0 — 2026-07-11 — Factory car: Android Auto when you plug USB
**Description:** Anthony has no aftermarket unit — only stock car + phone USB. Shipped official **Android Auto Car App Library** navigation app so Amni-Drive appears on the factory dash when plugged in (or wireless AA).

**Added:**
- `androidx.car.app` + `app-projected` 1.4.0
- `aa/AmniCarAppService` · `AmniCarSession` · `AaHomeScreen` (PlaceListMap) · `AaSearchScreen` · `AaRouteScreen` (OSRM)
- `res/xml/automotive_app_desc.xml` (template apps)
- Manifest: CarAppService NAVIGATION + geo NAVIGATE, minCarApiLevel, GMS car meta-data
- Settings › **CAR / USB** — step-by-step: AA developer mode, Unknown sources, plug in, open Amni Drive on dash
- versionCode 7 / versionName 1.1.0

**How Anthony uses it:** Install APK → enable Android Auto Developer settings → Unknown sources ON → grant location → USB into car → Android Auto app list → **Amni Drive** → Search / Route.

**Verified:** compile + unit tests + assembleDebug green. Needs Anthony car confirm (rule 8).

---

## v1.0.0 — 2026-07-11 — Amni Link: Android Auto replacement (phone → head unit)
**Description:** Anthony: ignore old "reject projection" notes and build a real Android Auto replacement. Shipped **Amni Link** — open phone-host / head-unit-receiver projection stack. Phone is the brain; dash is the screen.

**Added:**
- `link/LinkProto` — framed binary protocol (magic `AMNI`, v1): HELLO, HELLO_ACK, VIDEO_CFG, FRAME (JPEG), TOUCH, PING/PONG, BYE.
- `link/LinkHost` — TCP server on port **17321**, ~12 fps JPEG capture of the host UI, touch inject into the content view.
- `link/LinkReceiver` — TCP client, frame decode, normalized touch out, heartbeat.
- `link/LinkDiscovery` — NSD advertise/browse `_amni-link._tcp` so units find the phone without typing IPs (manual IP still available).
- `LinkActivity` — dual-role UI: PHONE HOST | HEAD-UNIT RECEIVER | picker. Second launcher icon **Amni Link**.
- Settings › **AMNI LINK** setup pane; Dashboard menu → Amni Link.
- Manifest: Wi‑Fi multicast, cleartext for LAN sockets, exported LinkActivity.
- Unit tests: `LinkProtoTest` (4 cases).

**Two modes, one APK:**
1. **Amni Link (new)** — phone runs full car UI → streams to any Android head unit on same Wi‑Fi/hotspot.
2. **Native launcher (unchanged)** — install on the unit, set as HOME for full local apps.

**Verified (2026-07-11):** `testDebugUnitTest` pass (25 tests) · `assembleDebug` → `app-debug.apk` ~15.7 MB · compile clean (2 NSD deprecation warnings only). NOT complete until Anthony confirms phone host → second device receiver on LAN (rule 8).

**Roadmap (not in this drop):** H.264 hardware encode, audio path, USB accessory transport, optional factory-AA bridge research.

---

## v0.8.0 — 2026-07-11 — Themed map, offline areas, launcher UX
**Description:** Anthony: map theming, internet via phone without hotspot, and "how do I open this vs Android Auto?"

**Added/Fixed:**
- Map follows the theme: MAP STYLE gains **AUTO — MATCH THEME** (new default) — light presets get light tiles, dark presets dark; a subtle accent wash tints the basemap (skipped on satellite) so Crypt/Haven/Core actually color the map; map chrome (turn banner, control buttons, hint chips) is palette-driven for light mode. Manual layer chips still override per-session.
- **⬇ SAVE THIS AREA OFFLINE** in the Layers panel: prefetches zooms 11–16 (~10 mi around the camera, ~400 tiles) into the disk cache with live progress — map/nav then work with zero connectivity (routing/search still need a connection).
- Launcher UX: removed the auto HOME-role nag on every launch (annoying on the Fold); Settings › About now has **SET AS DEFAULT LAUNCHER (HEAD UNIT)** (RoleManager on Q+, ACTION_HOME_SETTINGS fallback) plus plain-words instructions.
- versionCode 4 / versionName 0.8.0.

**Phone-internet guidance (documented for Anthony):** USB tethering (phone Settings → Hotspot & tethering → USB tethering with phone plugged into the unit) or Bluetooth tethering (pair, then enable BT tethering on the phone; unit uses BT PAN "internet access") both feed the unit without a WiFi hotspot; offline areas cover the gaps.

**Verified (2026-07-11):** 21/21 tests · lint pass · 15.6 MB APK. Backups in backups/*.v0.8.0.bak. Watcher re-armed; APK sent. NOT complete until Anthony confirms on device (rule 8).

**ON-DEVICE INVESTIGATION (07-11, wireless adb to Fold SM-F966U over Tailscale 100.116.191.230:38073, Kia connected over USB live):** Verified on real hardware — 1.2.4 installed (installer=com.android.vending), system package DB shows `Category: androidx.car.app.category.NAVIGATION` + NAVIGATE action registered. AA gearhead 17.1.662414, Android 16. AA Developer settings: Unknown sources = ON, Application Mode = Release, Test harness = ON. DESPITE all correct: Amni-Drive does NOT appear in AA "Customize launcher" app list (alphabetically absent between Adobe Acrobat and Calendar) after unknown-sources toggle rescan AND gearhead force-stop+restart. DHU (desktop-head-unit.exe present in SDK extras) tunnels adb:5277 but projection never completes without in-AA "Start head unit server" tap. Conclusion: AA 17.x is not surfacing the correctly-declared sideloaded nav app via soft rescans; untried remaining trigger = physical USB unplug/replug (can't do remotely). DEEPER CEILING: even if listed, AA only renders Google TEMPLATES (PlaceListNavigationTemplate etc.) — NOT the custom Compose dashboard. Full custom UI needs aftermarket Android head unit (runs APK directly) or phone/tablet mounted. OpenAuto/openautolink = Pi-as-AA-receiver (still Google UI, replaces screen, no custom app); Bluefruit Feather = MCU, cannot run AA projection. UI-driving recipe that worked: screencap to /sdcard then `adb pull` (PowerShell `>` corrupts binary via UTF-16); `input tap`/`swipe`/`keyevent`; map displayed→physical coords.
