## v1.6.19 — 2026-07-13 — Everything fits the 400px glass: minimal car drawer + compact layout picker
**Ship:** carSurface drawer = Media/Theme/Tile-layout + gate/weather switches only (Home/Apps/Nav live on the rail — redundant rows dropped); TileLayoutPicker `compact` mode (38dp rows, no blurbs) — all 6 layouts incl. MAP ONLY reachable on 800x400. **DHU-verified:** full drawer visible no-scroll; picker applies (drawer subtitle tracks pick); regression sweep v1.6.18 all-pass (theme+gate persist across restart). versionCode **69**.
## v1.6.18 — 2026-07-13 — Gate one-tap toggle actually works (driver+master, effective-state based)
**Bug:** rail shield/menu toggle flipped only `driverGateEnabled`; UI + GateResolver require `driver && master` (master defaults false since gate-off-by-default) → toggle could never show ON.
**Ship:** `setDriverGate` sets BOTH flags; RideDashboard toggles `!(driver && master)`. onClick debug log (coords + surface dims). **DHU-verified:** tap → green shield + "62 gated" chip; tap again → OFF. Facts: DHU surface = **800x400** (host bar eats 80px); host drops ~1-in-3 rapid scripted clicks. versionCode **68**.
## v1.6.17 — 2026-07-13 — On-glass tap keyboard: full search→route chain works in-car
**Gap:** AA surface has no IME — SearchPanel's BasicTextField was untypeable on the car.
**Ship:** compact QWERTY+digits+SPACE/⌫ tap keyboard inside SearchPanel when dense (we own every pixel + touch). **DHU-verified end-to-end:** magnifier → type "aec" → live Photon results → GO → OSRM route "24 min · 14.0 mi" traffic-colored on the road, TRIP tile synced, END clears. Note: rapid host clicks can drop (~1 in 3 at 1.2s spacing) — keys are tolerant, users retap. versionCode **67**.
## v1.6.16 — 2026-07-13 — In-dash theme cycle + car drawer fits without scroll
**Ship:** MenuDrawer THEME row (tap cycles 6 presets live — DHU-verified Amni Light→Crypt→…→Amni Dark, whole dash recolors instantly); carSurface drawer drops phone-only rows (Phone/Rear/Amni Link/Settings/footer) so everything fits the 480px glass — no scrolling (AA `onScroll` has NO position data, injected drags anchor at last click = inherently flaky; avoid scroll-dependent UI on carSurface). Drawer Column verticalScroll for phone. `dhu_click_cap.ps1` gains -Drag. versionCode **66**.
## v1.6.15 — 2026-07-13 — FIX RIDE flicker + dead touch (main-thread latch deadlock) · host-chrome insets
**User:** UI flickering; can't access apps or change media; host APP_ICON square covers clock/weather.
**Root cause:** `startFullUi` ran on main looper, posted Presentation creation to the SAME looper, then blocked on `latch.await(4s)` → always timed out → `fullUi=false` forever → canvas fallback loop fought the VirtualDisplay for the surface (flicker) and every touch took the pan branch instead of injection (dead UI). Presentation still appeared late, so it LOOKED right.
**Ship:** inline main-thread VD+Presentation construction (no latch); canvas loop only on Presentation failure (never two surface producers); carSurface reserves host chrome — CarStatusStrip end pad 84dp + rightmost tile column top pad 34dp. MediaSourceSheet hoisted to Dashboard top level (was cramped inside media tile). Media pick on carSurface = `playViaBrowser` (MediaBrowserCompat connect → transportControls.play()) — NEVER launches the activity (activity launch fell back to phone screen + AA dropped to clock). Guardian council 5-0. versionCode **65**.
**DHU VERIFIED live:** taps open APPS drawer + media picker + close ✕; Spotify pick → PlaybackState PLAYING(3), dash stays Amni; zero flicker (3× rapid caps identical brightness). `scripts/dhu_click_cap.ps1` = reusable click/capture harness (PS5.1: pass clicks as "x,y;x,y" string — array params char-splat).
## v1.6.14 — 2026-07-13 — Automate RIDE verify + OWN gearhead-replacement tracks
**Ship:** `scripts/dhu_ride_verify.ps1` (install·HU server·DHU·open·screenshots·crash check). Mesh `GET /own`, `POST /action/open-link`, status fields for wall/next. Research **14** (RIDE / OWN / Link tracks). `scripts/own_status.ps1`. versionCode **64**.
## v1.6.13 — 2026-07-13 — Touch inject · hide square chrome · gate off · music picker
**User:** white square overlay; can’t hit menu/apps; music tile not changeable; gate on by default.
**Ship:** Action strip = `APP_ICON` only (no custom square). Surface touch scaled + main-thread down/up/move. Gate defaults **off** + one-shot migrate. Media tile CHANGE / art opens source picker (Spotify etc.). versionCode **63**.
## v1.6.12 — 2026-07-13 — Fix DHU crash: NavigationTemplate needs ActionStrip
**Live test:** AA error “Amni-Drive unexpected error”; stack `Action strip for this template must be set` (1.6.10 stripped strip entirely).
**Ship:** icon-only ActionStrip (no Search/Media/Route titles) + map PAN. versionCode **62**.
## v1.6.11 — 2026-07-13 — Tile layout picker + display-aware tiles
**User:** layout-aware tiles that adapt size/position; pick layout of the dash.
**Ship:** `TileLayout` (Auto / Map focus / Media focus / Balanced / Stacked / Map only) persisted in ThemeStore. HOME tiles resize to window (rail, pad, widget width, app cell scale). Menu **Tile layout** + Settings LOOKS rows. Auto re-picks from glass size. versionCode **61**.
## v1.6.10 — 2026-07-13 — Kill AA Search/Media/Route chrome · traffic · surface launch
**User:** stock Search/Media/Route pills over Amni UI; traffic colors dead; Spotify/apps leave Amni.
**Ship:** `NavigationTemplate` **no host ActionStrip** (only map PAN for surface). OSRM speeds resampled onto geometry (red/orange/yellow/green). App launches target AA **VirtualDisplay** id; media apps stay on HOME media panel + media keys. versionCode **60**.
## v1.6.9 — 2026-07-13 — In-map search + media split (no full-screen takeovers)
**User:** nav Search + Spotify/media launched full-screen instead of split with map in Amni Drive.
**Ship:** AA Search/Media/Route actions pulse Compose surface (`AaNavState`) — no `SearchTemplate` push. Map SearchPanel overlays map. MediaCard always beside map on car; **media keys** control Spotify/active session (no `CATEGORY_APP_MUSIC` activity). Menu Media stays in-dash. versionCode **59**.
## v1.6.8 — 2026-07-13 — Layout fit window + map-first NavigationTemplate
**User:** UI live under black Search/Drive AA chrome; need layout fit window shape/size.
**Ship:** `AaHomeScreen` → `NavigationTemplate` (map surface first, Search/Route actions — less list overlay). `Dashboard` carSurface tiers: **micro** (map + chip strip), **dense** (slim rail, CarStatusStrip, no media col, compact weather/trip/gate), **full** phone. `MapCard` dense chrome (smaller buttons). versionCode **58**. Re-open Amni on DHU after install.
## v1.6.7 — 2026-07-13 — Fix Search crash (Compose lifecycle + search rows)
**Live:** Search crashed app; root `ViewTreeLifecycleOwner not found` from AA surface Presentation ComposeView (process death).
**Ship:** RearPresentation sets Lifecycle + SavedStateRegistry owners; canvas first then Presentation; Search results use DistanceSpan+Place. versionCode 57.
## v1.6.6 — 2026-07-13 — Fix nav crash: DistanceSpan on PlaceList rows
**Live DHU:** two Amni tiles; nav crashed with `All non-browsable rows must have a distance span`.
**Ship:** AaHomeScreen rows use DistanceSpan + Place metadata; media label `Amni (media stub)`. versionCode 56.
## v1.6.5 — 2026-07-13 — AA drawer: media session always-on (no phone launch)
**User:** no red screen but Amni missing from AA drawer.
**Ship:** media session active at start; auto-start MediaService from app; no MainActivity from media; keep no USB accessory (1.6.4). versionCode 55.
## v1.6.4 — 2026-07-12 — Red-screen fix: stop competing for USB accessory
**User:** dash red screen (AA fail) after recent RIDE/media churn.
**Cause:** Amni still registered `USB_ACCESSORY_ATTACHED` → steals AOA from gearhead → HU red error.
**Ship:** remove accessory intent-filter; AoaAttach never auto-claims outside explicit OWN; mode ride. versionCode 54.
## v1.6.3 — 2026-07-12 — Restore AA drawer + Play installer spoof + no phone media
**User:** after killing media, Amni vanished from AA drawer (template-only sideloads often hidden on factory HU).
**Ship:** media back for drawer visibility but **never** starts phone UI; HostValidator always ALLOW_ALL; NAV+POI; install as `-i com.android.vending`; versionCode 53.
## v1.6.2 — 2026-07-12 — Kill media door; nav-only RIDE on head unit
**User:** single Amni button opened media Now Playing; full UI launched on **phone** not HU.
**Ship:** remove `media` from automotive_app_desc; disable `AmniMediaService` (no MediaBrowser export, no phone startActivity); NAV-only CarAppService; home template cues surface; RideDashboard no phone settings. versionCode 52. **Open Amni on car → nav template + map surface.**
## v1.6.1 — 2026-07-12 — RIDE polish + gearhead process detect
**Ship:** mesh gearhead detect via `ps`; AaHomeScreen title cues full-map UI. versionCode 51. Install + mode=ride. Open **Amni Drive** on car AA for Surface + Presentation Dashboard.

## v1.6.0 — 2026-07-12 — RIDE full Compose Dashboard on AA Surface
**Why:** OWN host identity wall (AuthComplete -3). Full factory UI = Gearhead TLS; Amni paints Dashboard.
**Ship:** VirtualDisplay + RearPresentation + RideDashboard; touch inject; canvas fallback. versionCode 50.

## v1.5.8 — 2026-07-12 — CLAIM forces FD/session reset
**Live:** CLAIM stuck on ghost ACCESSORY (lready holding FD/session) after Auth wall; no clean re-open without physical unplug.
**Ship:** ACTION_CLAIM clears free-poll + sessionRunning + closeAccessory before poll. versionCode 49. APK built; install when ADB returns (AOA may drop wireless adbd). **Not M5.**
## v1.5.7 — 2026-07-12 — Auth status=-3 cert wall + plain-first control
**Live 1.5.6:** TLS FINISHED; HU plain AuthComplete body status **int32 -3**; then ENC 0xff; SD Request sent but no SD Response/ChannelOpen.
**Ship:** plain-first control TX (match HU flags 0x03); log AUTH FAIL / cert wall; research 13. versionCode 48. **Not M5.** Root block = host identity, not SD shape alone.

## v1.5.6 — 2026-07-12 — Phone-role ServiceDiscoveryRequest + ChannelOpen from HU SD
**Root cause (live 1.5.4/1.5.5):** After Auth we sent ServiceDiscoveryResponse (HU-shaped). Phone must send ServiceDiscoveryRequest (device_name=4, device_brand=5); HU replies channels; phone ChannelOpenRequest.
**Ship:** Request + name variants; parse SD Response; open channels; ENC full-cipher decrypt; mesh public GET /health+/token. versionCode 47. **Not M5.**
## v1.5.5 â€” 2026-07-12 â€” Post-TLS SD variants + ssl-msg wrap + 0xff retry
**Live 1.5.4:** discovery=true (we sent SD after auth); HU answered with ENC short `00 ff` / no ChannelOpen.
**Ship:** TX control via ssl-msg (SSL_HANDSHAKE-wrapped app data) + enc-frame + tunnel; multi SD variants (720/480, ch1/ch2); short-enc 0xff treated as reject â†’ next SD variant; tunnel TLS record RX (0x15/16/17). versionCode 46. **Not M5.**

## v1.5.4 â€” 2026-07-12 â€” Post-TLS dual path (AAP + TLS tunnel) + proactive SD
**Live:** TLS FINISHED; AuthComplete TX; HU RX auth body opaque; no ServiceDiscoveryRequest.
**Ship:** Dual post-TLS RX (plain AAP frames + TLS app-data tunnel starting 0x17); Auth/SD/ChannelOpen TX in plain+enc-frame+tunnel; proactive ServiceDiscovery after auth or 2.5s; exclusive session kept. versionCode 45. **Not M5.**

## v1.5.3 â€” 2026-07-12 â€” Post-TLS Auth/Discovery + exclusive session
**After live TLS FINISHED (1.5.2):** RX looked like AuthComplete; need ServiceDiscovery + ChannelOpen before video.
**Ship:** `AaPostTls` (AuthComplete OK, ServiceDiscoveryResponse with video/input/sensor, ChannelOpen/Ping/Focus handlers, plain+enc TX); `AaPb` minimal protobuf; SSL `encryptApp`/`decryptApp`; exclusive `sessionRunning` (no double OPEN); encrypted AAP video frames after TLS. versionCode 44. **Not M5.**

## v1.5.2 â€” 2026-07-12 â€” Software PKCS12 TLS host (beat AndroidKeyStore RSA wall)
**Live wall:** Version 1.4 + ClientHello seed OK; Conscrypt `RSA routines:OPENSSL_internal:internal error` on unwrap with AndroidKeyStore SIGN-only key as AA TLS server.
**Ship:** `assets/amni_aa_host.p12` (RSA-2048 SHA256 self-signed CN=Android Auto research); `AoaSslSession` loads software PKCS12 KeyManagers; TLSv1.2 preferred; richer wrap/unwrap logs. versionCode 43. HU may still reject non-Google cert after handshake advances â€” that is the next signal. **Not M5.**

## v1.4.9 â€” 2026-07-12 â€” Seed ClientHello into TLS after VersionResponse 1.4
**Live dig:** Gearhead `disabled-user`; HU sent VersionRequest **1.4**; Amni replied same tick; HU sent SSL_HANDSHAKE ClientHello (288B). 1.4.8 dropped that body before TLS (`seedBodies=0`). **Ship:** keep SSL bodies â†’ `handshakeFramedBulk(..., seedBodies)`. Immediate VersionResponse in phase A. PROBE_MINORS includes 4. versionCode 40 installed. **Not M5.**

## v1.4.7 â€” 2026-07-12 â€” Stop competing for USB accessory (gearhead owns attach)
**Live car dig:** Mesh status showed `accessory=true` + `mode=ride` + **empty probe log** (no openAccessory). Wireless ADB still died â€” that is the **car USB â†’ AOA mode** killing/restarting `adbd`, not Amni claiming the FD. Mesh bridge stayed up (`100.116.191.230:7931`).
**Extra harden:** remove `USB_ACCESSORY_ATTACHED` intent-filter from `AoaAttachActivity` so Amni is not an accessory handler (was competing with Gearhead for the car plug). OWN path still claims only via explicit button. versionCode 38.

## v1.4.6 â€” 2026-07-12 â€” Mesh Dev Bridge + Wiâ€‘Fi ADB survival (no USB claim on launch)
**Problem:** Launching Amni-Drive auto-touched AOA (`openAccessory` / free-poll), which reconfigures USB and restarts `adbd` â€” **kills wireless ADB** in the car.

**Fix:**
- Default `aaPathMode = ride`
- AOA open/poll/free-poll **only** when mode is OWN (explicit Settings claim)
- `AoaAttachActivity` passive unless OWN
- Watch service passive (log only)

**Mesh Dev Bridge:**
- `wire/MeshDevBridgeService` â€” HTTP `0.0.0.0:7931`, token auth
- `GET /health` `/status` `/logs/watch` `/logs/probe` `/logcat`
- `POST /action/open-nav` `/action/mode` `/action/claim-own` `/action/watch-tick`
- Auto-starts with app; Settings â€º AMNI WIRE shows token + start/stop
- PC: `scripts/mesh_bridge.ps1 -Ip 100.x.x.x -Token â€¦`
- Research 12

versionCode 37 / 1.4.6. Build + unit tests green. Install needs one-shot ADB/sideload.

## v1.4.4 â€” 2026-07-12 â€” AUTO uses gearhead:car session (not mere process) for Path A hold
**Fix:** Gearhead processes stay resident even parked â€” treating any gearhead process as â€œsession aliveâ€ blocked OWN forever and confused AUTO. Now only `gearhead:car` / `:projection` means active car session â†’ RIDE hold. AUTO may claim when those are absent; RIDE still never steals FD.
**Ship:** versionCode 35 / 1.4.4. Reconnect loop: `scripts/wait_reconnect_install_loop.ps1` (wireless ADB drops when car USB switches).

## v1.4.3 â€” 2026-07-12 â€” Dual-path full UI (RIDE + OWN), keep both until hard wall
**Mandate (Anthony):** speak AA-style wire, show **full** Amni UI on the car; keep Path A (ride authenticated Gearhead session / paint) and Path B (own phone-host) â€” pivot only if unresolvable. Automate with wireless ADB while phone is in the car.

**Added:**
- `DriveConfig.aaPathMode` = `auto` | `ride` | `own` (+ ConfigStore + Settings â€º AMNI WIRE mode chips).
- `wire/AaSessionWatchService` â€” FGS watch of USB accessory + gearhead; logs `watch-*.log`; AUTO claims AOA only when gearhead free; RIDE never claims.
- `AoaProbeService` refuses `openAccessory` in RIDE and in AUTO while gearhead alive (kills Communication error 16 from FD steal).
- `AaSurfaceRenderer` expanded: map + route + speed HUD + NAV/TRIP/GATE/SESSION cards + bottom dock (HOME/NAV/APPS/MEDIA/SETTINGS) â€” full custom paint on AA Surface (Path A).
- `scripts/car_dual_path_loop.ps1` â€” long car capture loop (USB/gearhead reports + screenshots).
- Research 11 + guardian council + checklist dual-path.
- versionCode 34 / versionName 1.4.3.

**Verified:** assembleDebug + unit tests green Â· installed on SM-F966U wireless ADB. **Live car Path A open-Amni-on-Kia still needs Anthony plug.**

## v1.4.2 â€” 2026-07-12 â€” Custom UI on the factory screen via AA nav Surface (ride Gearhead, don't replace it)
**Reframe (Anthony):** full custom UI is the whole point; templates are a non-starter; "override the AA stream from the phone." Correct mechanism: the factory HU can't be given a forged host (crypto wall â€” Wire path dead), BUT a **navigation** Car App gets a raw drawing **Surface** from AA (how Maps/Waze paint custom pixels). So render the Amni dashboard onto that Surface while **Gearhead owns the certified session** â€” no cert forging, no mirror hack.

**Root-cause fix â€” "Communication error 16":** it was **us**. `AoaProbeService` auto-claimed the AOA accessory on every app launch, corrupting Gearhead's real AA session. Removed the auto-start from `AmniDriveApp` (Wire probe is now opt-in via Settings â€º AMNI WIRE only). Gearhead can now own a clean session.

**Added:**
- `aa/AaSurfaceRenderer` â€” implements `SurfaceCallback`; on `onSurfaceAvailable` runs a render loop drawing CARTO tiles (`TileStore`) + active route (`AaNavState.route`) + GPS puck + accent HUD onto the AA Surface via `Surface.lockCanvas`. Fractional zoom (`onScale`), pan (`onScroll`), recenter (`onClick`). Reuses `GeoMath` web-mercator projection.
- `AmniCarSession` registers it via `AppManager.setSurfaceCallback()`.
- Manifest: `androidx.car.app.NAVIGATION_TEMPLATES` + `ACCESS_SURFACE`.
- versionCode 33 / versionName 1.4.2.

**Verified (2026-07-12):** `compileDebugKotlin` clean Â· `assembleRelease` â†’ app-release.apk 12.5 MB. **On-device flash pending** (wireless adb wedged while car session was active). Rule 8: unplugâ†’reflashâ†’replugâ†’open Amni-Drive on Kia â†’ confirm Amni map renders on the factory screen. Full Compose-chrome-on-surface is the next increment (this draws map+route+puck+HUD).

## v1.4.1 â€” 2026-07-12 â€” Phone-first AAP after live BULK RX
**Dig (live car ACCESSORY):** Blocking bulk RX fixed the zero-RX lie. HU answered after phone `VersionRequest` with frame `00 0b 00 02 00 ff` (flags=BULK|ENC, msg=0xff). Unsolicited VersionResponse probes alone got silence. TLS still NEED_UNWRAP (no SSL_HANDSHAKE). Phone UI normal Amni; red is car HU AA-fail, not phone.
**Ship:** negotiateBulk reorders to phone-first VersionRequest across minors + SPECIFIC/CONTROL flag probes; respond to HU 0xff/ENC signal with VersionResponse; stop rescan log spam. versionCode 32 / 1.4.1 installed. **Not M5.** Soft USB bounce dropped link â€” needs physical re-plug for next claim.

## v1.4.0 â€” 2026-07-12 â€” Auto-start "plug in & go / wireless & go" (CarPlay/AA-style entry)
**Description:** Anthony: it "needs to run LIKE CarPlay/AA â€” plug in and go, wireless and go." Amni-Drive was a launcher with no connect-trigger; added the auto-entry layer for the mounted phone/tablet dash path.

**Added:**
- `auto/CarConnectionService` â€” always-on foreground watch service. Runtime-registers `ACTION_POWER_CONNECTED/DISCONNECTED` + `BluetoothDevice.ACTION_ACL_CONNECTED/DISCONNECTED` (runtime reg dodges the API-26+ implicit-broadcast ban). On a qualifying connect: `UiModeManager.enableCarMode()` + `startActivity(MainActivity.openNavIntent())` + a **full-screen-intent** high-priority notification (BAL-safe launch, incoming-call class). Disconnect exits car mode.
- `auto/AutoStartReceiver` â€” `BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED` â†’ starts the watch service so it survives reboots.
- `DriveConfig`/`ConfigStore`: `autoStartOnPower` (default ON), `autoStartOnBluetooth` (default OFF until car learned), `carBtMac`, `autoEnterCarMode` (default ON).
- Settings â€º **AUTO-START** pane: power / bluetooth / car-mode toggles + "My car's Bluetooth" bonded-device picker (tap to set MAC + arm BT trigger) + one-tap "Display over other apps" grant when missing.
- Manifest: `RECEIVE_BOOT_COMPLETED`, `USE_FULL_SCREEN_INTENT`, `WAKE_LOCK`, `SYSTEM_ALERT_WINDOW`; `CAR_DOCK` category on MainActivity; service + boot receiver registered. `AmniDriveApp` starts the service on first launch.
- versionCode 31 / versionName 1.4.0.

**Why it works:** reuses existing `MainActivity.openNavIntent()` (already keep-screen-on + drive mode). Power-connect = plug-in-and-go (enough on a dedicated tablet); BT-MAC match = wireless-and-go, car-specific so headphones don't trigger it. Gate still governs content.

**Verified (2026-07-12):** `compileDebugKotlin` clean (2 getParcelableExtra deprecation warnings) Â· `assembleDebug` â†’ app-debug.apk 17.1 MB. Backups backups/*.v1.3.17.bak. Council + checklist in docs/. **NOT complete until Anthony confirms on device (rule 8):** plug USB â†’ dash auto-opens; connect car BT â†’ dash auto-opens; check full-screen-intent grant on Android 14+.

## v1.3.17 â€” 2026-07-12 â€” Blocking bulk USB RX (available() false-negative dig)
**Dig:** USB accessory bulk often never shows data in `available()`; ADK docs use blocking `read()` up to 16KB packets. Prior loops may have missed HU VersionRequest.
**Ship:** `AoaBulkIo` reader thread; `negotiateBulk` + `handshakeFramedBulk`; session/projection write via bulk. versionCode 30. Research 10 updated. **Not M5.**

---

## v1.3.16 â€” 2026-07-12 â€” Version multiprobe 1.1â€“1.7 + TLS client experiment
**Explore:** gearhead 17.1.x live; HU still 0 RX after aasdk frames. aasdk default version was **1.1** not 1.6.
**Ship:** negotiate probes VersionResponse for minors 1/5/6/7; default MINOR=1; TLS server then optional **client-mode** experiment if silent. Research: `docs/research/10_host_identity_and_version_probes.md`. versionCode 29. **Not M5.**

---

## v1.3.15 â€” 2026-07-12 â€” Multi-strategy AAP negotiate (phone-first VersionRequest)
**Live 1.3.14:** correct aasdk VersionResponse TX (`00 03 00 08â€¦`) still **gotAny=false** / TLS framed timeout.
**Ship:** negotiate phase1 wait VersionRequest â†’ phase2 **phone-first VersionRequest** â†’ phase3 unsolicited VersionResponse; each phase waits for SSL_HANDSHAKE/version frames. versionCode 28. **Not M5.**

---

## v1.3.14 â€” 2026-07-12 â€” aasdk wire framing + framed TLS
**Fix:** Prior AAP used wrong 8-byte LE header + CONTROL flag; aasdk uses **2-byte** FrameHeader (channel + flags) + **BE** u16 size + MessageId BE + body; version msgs use MessageType **SPECIFIC**. TLS is not raw bulk â€” wrapped in **SSL_HANDSHAKE (0x0003)** frames.
**Ship:** Rewrite `AaAapVersion` to aasdk layout; `AoaSslSession.handshakeFramed`; probe prefers framed TLS then raw fallback. Tests updated. versionCode 27. **Not M5.**

---

## v1.3.13 â€” 2026-07-12 â€” VD OWN_CONTENT_ONLY + session re-poll
**Live:** ACCESSORY OPEN fd=123; AAP TX VersionResponse; TLS NEED_UNWRAP 0 RX; PRESENTATION VD denied capture perms.
**Ship:** VirtualDisplay back to PUBLIC|OWN_CONTENT_ONLY; session end always closeAccessory + scheduleFreePoll. versionCode 26. Research: `docs/research/08_aap_tx_tls_timeout_vd_flags.md`. **Not M5.**

---

## v1.3.12 â€” 2026-07-12 â€” Sticky dual-path free-poll (wait for car plug)
**Loop:** Phone ADB up; car USB DISCONNECTED; free-poll had hit 40-cap and stopped.
**Ship:** Free-poll is **sticky** for service lifetime (2s then 5s interval, no hard 40-cap); re-arm on DETACHED / USB_STATE; claim/start always re-arms. versionCode 25. **Not M5.**

---

## v1.3.11 â€” 2026-07-12 â€” AAP version negotiate before TLS (M4 scaffold)
**Loop:** Hardware offline this tick; prior live: OPENED + TLS NEED_UNWRAP (0 RX).
**Ship:** `AaAapVersion` (aasdk-style LE header + VersionRequest/Response 1.6) Â· probe Phase A2 negotiate before SSLEngine Â· unit tests Â· VirtualDisplay uses PRESENTATION + fallback default-display launch on launchDisplayId denial. versionCode 24. **Not M5.**

---

## v1.3.10 â€” 2026-07-11 â€” Dual-path free-poll + external probe logs
**Loop:** Live car ACCESSORY; **OPENED fd=108** with gearhead=false; TLS server NEED_UNWRAP timeout (0 RX); VirtualDisplay launchDisplayId denial on OPEN_NAV.
**Ship:** Non-destructive dual-path free-poll when openAccessory null; gearhead process detect; dual log filesDir + getExternalFilesDir/aoa_probe; skip re-open if FD held. versionCode 23. Research: `docs/research/07_loop_aoa_open_tls_timeout.md`. **Not M5.**

---

## v1.3.9 â€” 2026-07-11 â€” Automation: OWN_STACK intent + multi-screen capture
**Ask:** automate everything including taps and seeing both screens.
**Ship:** `scripts/automate_own_stack.ps1` (force-stop gearhead, OWN_STACK launch, uiautomator Allow taps, phone + HWC display screenshots, logs). MainActivity `ACTION_OWN_STACK` starts projection + AOA claim (shell cannot start non-exported FGS). Physical Kia panel not ADB-visible. versionCode 22.

---

## v1.3.8 â€” 2026-07-11 â€” Own-stack full UI projection (ignore Google allowlist)
**Mandate:** Full UI regardless of what Google allows â€” phone owns the OS surface.
**Ship:** `AmniProjectionEngine` (VirtualDisplay + H.264 of full Amni NAV UI) Â· `ProjectionHostService` Â· AOA session attaches projection + AAWR video frames after OPEN/TLS Â· Settings â€º AMNI WIRE â†’ **START FULL UI PROJECTION**. Product path is Amni Wire / Link, not Play allowlists. versionCode 21.

---

## v1.3.7 â€” 2026-07-11 â€” Full nav UI from car media
**Ask:** â€œnav (full ui now)â€. Stock AA cannot host Compose on the dash; full UI = phone shell.
**Ship:** Media Play / â€œStart full navigationâ€ opens `MainActivity` on **NAV** page (`OPEN_NAV`, drive mode: keep screen on, show when locked). Dashboard `initialPage`. versionCode 20.

---

## v1.3.6 â€” 2026-07-11 â€” AA media path (the real sideload way)
**Live evidence:** Kia 2024 AA dump: `allow_unknown_sources=true`, `car_app_mode=Developer` â€” template nav/POI still hidden. Google docs: Unknown sources applies to **media** (not Car App Library templates).
**Way:** `AmniMediaService` MediaBrowserService so Amni can show under **media** on the car (Fermata-style). Keep template CarAppService for Play later. versionCode 19.

---

## v1.3.5 â€” 2026-07-11 â€” Real AndroidKeyStore TLS identity for AOA
**Fix:** empty KeyManager meant SSLEngine could not WRAP ServerHello. Now generates RSA key+self-signed cert in AndroidKeyStore (`amni_aa_tls_v1`) for research server mode. OEM will still reject CA; we want ClientHello/alert logs on next plug. versionCode 18.

---

## v1.3.4 â€” 2026-07-11 â€” TLS server attempt after AOA OPEN
**After Always-allow OPENED with 0B RX:** phone must speak as TLS server.

**Added:** `AoaSslSession` (SSLEngine server mode, research self-signed KM) Â· probe runs phase A passive 2s â†’ phase B TLS handshake â†’ phase C residual sniff. Expect OEM cert rejection; goal is log ClientHello / alerts. versionCode 17 / 1.3.4.

---

## v1.3.3 â€” 2026-07-11 â€” AOA OPENED (Always allow works)
**Live milestone:** User granted Always allow.

```
PERMISSION granted=true ... Android Auto
OPENED fd=113
sniff end total=0
```

**Meaning:** We own the accessory FD. Zero RX is expected until we **speak** (phone is TLS server in AA). DETACH followed shortly after (session reset / gearhead reclaim).

**Code:** Auto-start probe on app launch; hold open 30s; clearer notif; research note `docs/research/06_aoa_open_granted.md`. Next: SSLEngine on accessory duplex.

---

## v1.3.2 â€” 2026-07-11 â€” AOA permission request + claim script
**M3 progress:** Accessory visible but `perm=false` (gearhead exclusive).

**Added:**
- `UsbManager.requestPermission` path in `AoaProbeService` (PendingIntent + broadcast)
- `ACTION_CLAIM` + Settings button **START AOA PROBE + REQUEST PERMISSION**
- Dump first RX bytes to `files/aoa_probe/first_chunk.bin` when open succeeds
- `AoaBulkTransport` (AaTransport over accessory FD)
- `scripts/claim_aoa_research.ps1` â€” force-stop gearhead, start probe, poll for OPEN/PERMISSION
- versionCode 15 / 1.3.2

**Live result:** after force-stop gearhead, probe logs `requesting USB accessory permissionâ€¦` â€” user must accept dialog on phone; then re-poll for OPEN + sniffer.

---

## v1.3.1 â€” 2026-07-11 â€” Open source kit + AOA probe + /loop
**Mandate:** Loop until Amni replaces Android Auto; make it open source.

**Added:**
- Apache-2.0 `LICENSE`, `NOTICE`, `CONTRIBUTING.md`, `SECURITY.md`, `ROADMAP.md`
- Public README rewrite (OSS + modes + build)
- `wire/AoaProbeService` + `AoaAttachActivity` + `usb_accessory_filter.xml` (log/claim AOA attach)
- Settings â€º AMNI WIRE â†’ **START AOA PROBE**
- Live captures remain in `docs/research/05_live_aa_session_capture.md`
- versionCode 14 / 1.3.1

**Loop:** Agent scheduler continues M3/M4 while phone ADB is available.

---

## v1.3.0 â€” 2026-07-11 â€” Amni Wire: replace Android Auto (scaffold)
**Mandate:** Replace AA even if reverse-engineering the communication link is required.

**Added:**
- Research `docs/research/04_aa_wire_protocol_open_stack.md` (aasdk/OpenAuto/OAL-grounded)
- Guardian council for wire-level replace
- `wire/` package: `AaChannels`, `AaFrameCodec`, `AaSession`, `AaWireHost`, `AaTransport`
- `link/H264Encoder` MediaCodec surface encoder scaffold
- Amni Link **v2 multi-channel**: control `:17321` + video `:17322`
- Settings â€º **AMNI WIRE** experimental pane
- Unit tests: `AaFrameTest`
- versionCode 13 / versionName 1.3.0 (debug assemble; ship release when testing factory AA soft path)

**Not yet (needs car/DHU):** full AOA handshake, TLS, aasdk protobuf payloads, first factory HU accepting Amni video.

---

## v1.2.4 â€” 2026-07-11 â€” AA fix: NAVIGATION category (was POI) + ship RELEASE build
**Diagnosis (combines prior attempts):** Anthony's '23 Niro still not launching Amni-Drive over factory AA. Two independent misconfigs were both live: (1) manifest CarAppService category had drifted to **POI-only** (v1.2.2) â€” wrong for a routing app; factory AA lists third-party **NAVIGATION** apps (Waze/TomTom class), POI is a restricted class most factory units hide for sideloads. (2) v1.2.3 found AA filters **debug** sideloads â€” needs the non-debuggable **release** APK (HostValidator â†’ Google host allowlist). Prior versions changed one axis at a time so the working combo (NAVIGATION + release) was never actually tried together.
**Fix â€” TWO coupled bugs (the catch-22 prior versions kept half-fixing):**
1. Category POI â†’ `androidx.car.app.category.NAVIGATION` + `androidx.car.app.action.NAVIGATE` (geo) intent-filter.
2. `AaHomeScreen` used `PlaceListMapTemplate`, which Google restricts to **POI apps only** (confirmed via developer.android.com nav/POI template docs). With NAVIGATION category that template is illegal â†’ app would list but **crash on tap**. Swapped to `PlaceListNavigationTemplate` (navigation-legal, map+place-list, API level 1; verified present in car.app 1.7.0 aar via javap; dropped `setCurrentLocationEnabled` which isn't on it). AaSearchScreen (SearchTemplate) + AaRouteScreen (Pane/Message) are category-agnostic â€” unchanged.
   â†’ The POIâ†”NAVIGATION tension is exactly why v1.2.1 (both), v1.2.2 (POI), v1.2.3 (release) each fixed only one axis. NAVIGATION + nav-legal template + release build is the combination that was never tried together.
Built **release** (isDebuggable=false, debug-key signed, 11.9 MB); merged manifest = NAVIGATION + non-debuggable; grep confirms no PlaceListMapTemplate remains; assembleRelease clean (only deprecation warnings). versionCode 12 / 1.2.4.
**Still open / honest caveat:** even correctly declared, some factory AA builds refuse unpublished sideloaded nav apps entirely â€” the Google-sanctioned test path is the **Desktop Head Unit (DHU)**, not the car. Cannot verify on-car or via DHU without Anthony's phone on this PC's adb (phone is away). If it still won't list after a clean AA reconnect, next step is DHU verification or pulling AA logs off the phone. NOT complete until Anthony confirms it launches in the car (rule 8).

## v1.2.3 â€” 2026-07-11 â€” AA: non-debuggable release for real cars
**Description:** Still missing from AA app list with Unknown sources ON. AA 17.x + mid-2026 reports filter pure sideloads; debug builds especially.
**Fix:** Ship **release** APK (`isDebuggable=false`, signed with debug key for sideload), HostValidator uses Google host allowlist when not debuggable, reinstall with `installer=com.android.vending`, re-grant location. versionCode 11.
**Verified:** release install Success on Fold.

---

## v1.2.2 â€” 2026-07-11 â€” AA: Play-installer reinstall + POI-only
**Description:** Amni still missing from AA app list. Google has been filtering pure sideloads (2026 reports even with Unknown sources).
**Fix:** Single **POI** CarAppService category (nav dual-filter removed). Car App Library 1.7. Reinstall with `adb install -i com.android.vending` so installerPackageName = Play Store. Script: `scripts/install_for_android_auto.ps1`.
**Verified:** phone install Success; `com.amni.drive installer=com.android.vending`; versionCode 10 / 1.2.2.

---

## v1.2.1 â€” 2026-07-11 â€” AA: show on dash (POI + setup)
**Description:** Anthony: AA works on car but Amni Drive missing from app list (#2).
**Fix:** Register car service as **POI** (matches PlaceListMap UI; nav-only apps often stay hidden when incomplete) **and** NAVIGATION. Settings â€º CAR / USB: stronger Unknown-sources steps + **OPEN ANDROID AUTO SETTINGS** button.
**versionCode 9 / 1.2.1.** Build green; install when ADB up.

---

## v1.2.0 â€” 2026-07-11 â€” UI modernize (soft glass shell)
**Description:** Anthony liking the app â€” modernize interface while away from car. Autonomous polish pass.

**UI:**
- Richer palette (deeper darks, softer lights), gradient glass cards with accent hairline, SoftSurface + shadows
- Rail: brand mark, scale-on-select, gradient selected pills
- Top bar: time-of-day greeting + â€œAnthonyâ€, clock in soft surface
- Animated page transitions (HOME / APPS / NAV)
- Cards: accent bars, cleaner type hierarchy, gradient primary buttons
- App drawer: elevated tiles, gradient filter chips
- Map chrome: squircle controls, friendlier route banner copy
- versionCode 8 / versionName 1.2.0

**Verified:** compile + unit tests + assembleDebug. Install attempted if ADB online.

---

## v1.1.0 â€” 2026-07-11 â€” Factory car: Android Auto when you plug USB
**Description:** Anthony has no aftermarket unit â€” only stock car + phone USB. Shipped official **Android Auto Car App Library** navigation app so Amni-Drive appears on the factory dash when plugged in (or wireless AA).

**Added:**
- `androidx.car.app` + `app-projected` 1.4.0
- `aa/AmniCarAppService` Â· `AmniCarSession` Â· `AaHomeScreen` (PlaceListMap) Â· `AaSearchScreen` Â· `AaRouteScreen` (OSRM)
- `res/xml/automotive_app_desc.xml` (template apps)
- Manifest: CarAppService NAVIGATION + geo NAVIGATE, minCarApiLevel, GMS car meta-data
- Settings â€º **CAR / USB** â€” step-by-step: AA developer mode, Unknown sources, plug in, open Amni Drive on dash
- versionCode 7 / versionName 1.1.0

**How Anthony uses it:** Install APK â†’ enable Android Auto Developer settings â†’ Unknown sources ON â†’ grant location â†’ USB into car â†’ Android Auto app list â†’ **Amni Drive** â†’ Search / Route.

**Verified:** compile + unit tests + assembleDebug green. Needs Anthony car confirm (rule 8).

---

## v1.0.0 â€” 2026-07-11 â€” Amni Link: Android Auto replacement (phone â†’ head unit)
**Description:** Anthony: ignore old "reject projection" notes and build a real Android Auto replacement. Shipped **Amni Link** â€” open phone-host / head-unit-receiver projection stack. Phone is the brain; dash is the screen.

**Added:**
- `link/LinkProto` â€” framed binary protocol (magic `AMNI`, v1): HELLO, HELLO_ACK, VIDEO_CFG, FRAME (JPEG), TOUCH, PING/PONG, BYE.
- `link/LinkHost` â€” TCP server on port **17321**, ~12 fps JPEG capture of the host UI, touch inject into the content view.
- `link/LinkReceiver` â€” TCP client, frame decode, normalized touch out, heartbeat.
- `link/LinkDiscovery` â€” NSD advertise/browse `_amni-link._tcp` so units find the phone without typing IPs (manual IP still available).
- `LinkActivity` â€” dual-role UI: PHONE HOST | HEAD-UNIT RECEIVER | picker. Second launcher icon **Amni Link**.
- Settings â€º **AMNI LINK** setup pane; Dashboard menu â†’ Amni Link.
- Manifest: Wiâ€‘Fi multicast, cleartext for LAN sockets, exported LinkActivity.
- Unit tests: `LinkProtoTest` (4 cases).

**Two modes, one APK:**
1. **Amni Link (new)** â€” phone runs full car UI â†’ streams to any Android head unit on same Wiâ€‘Fi/hotspot.
2. **Native launcher (unchanged)** â€” install on the unit, set as HOME for full local apps.

**Verified (2026-07-11):** `testDebugUnitTest` pass (25 tests) Â· `assembleDebug` â†’ `app-debug.apk` ~15.7 MB Â· compile clean (2 NSD deprecation warnings only). NOT complete until Anthony confirms phone host â†’ second device receiver on LAN (rule 8).

**Roadmap (not in this drop):** H.264 hardware encode, audio path, USB accessory transport, optional factory-AA bridge research.

---

## v0.8.0 â€” 2026-07-11 â€” Themed map, offline areas, launcher UX
**Description:** Anthony: map theming, internet via phone without hotspot, and "how do I open this vs Android Auto?"

**Added/Fixed:**
- Map follows the theme: MAP STYLE gains **AUTO â€” MATCH THEME** (new default) â€” light presets get light tiles, dark presets dark; a subtle accent wash tints the basemap (skipped on satellite) so Crypt/Haven/Core actually color the map; map chrome (turn banner, control buttons, hint chips) is palette-driven for light mode. Manual layer chips still override per-session.
- **â¬‡ SAVE THIS AREA OFFLINE** in the Layers panel: prefetches zooms 11â€“16 (~10 mi around the camera, ~400 tiles) into the disk cache with live progress â€” map/nav then work with zero connectivity (routing/search still need a connection).
- Launcher UX: removed the auto HOME-role nag on every launch (annoying on the Fold); Settings â€º About now has **SET AS DEFAULT LAUNCHER (HEAD UNIT)** (RoleManager on Q+, ACTION_HOME_SETTINGS fallback) plus plain-words instructions.
- versionCode 4 / versionName 0.8.0.

**Phone-internet guidance (documented for Anthony):** USB tethering (phone Settings â†’ Hotspot & tethering â†’ USB tethering with phone plugged into the unit) or Bluetooth tethering (pair, then enable BT tethering on the phone; unit uses BT PAN "internet access") both feed the unit without a WiFi hotspot; offline areas cover the gaps.

**Verified (2026-07-11):** 21/21 tests Â· lint pass Â· 15.6 MB APK. Backups in backups/*.v0.8.0.bak. Watcher re-armed; APK sent. NOT complete until Anthony confirms on device (rule 8).

**ON-DEVICE INVESTIGATION (07-11, wireless adb to Fold SM-F966U over Tailscale 100.116.191.230:38073, Kia connected over USB live):** Verified on real hardware â€” 1.2.4 installed (installer=com.android.vending), system package DB shows `Category: androidx.car.app.category.NAVIGATION` + NAVIGATE action registered. AA gearhead 17.1.662414, Android 16. AA Developer settings: Unknown sources = ON, Application Mode = Release, Test harness = ON. DESPITE all correct: Amni-Drive does NOT appear in AA "Customize launcher" app list (alphabetically absent between Adobe Acrobat and Calendar) after unknown-sources toggle rescan AND gearhead force-stop+restart. DHU (desktop-head-unit.exe present in SDK extras) tunnels adb:5277 but projection never completes without in-AA "Start head unit server" tap. Conclusion: AA 17.x is not surfacing the correctly-declared sideloaded nav app via soft rescans; untried remaining trigger = physical USB unplug/replug (can't do remotely). DEEPER CEILING: even if listed, AA only renders Google TEMPLATES (PlaceListNavigationTemplate etc.) â€” NOT the custom Compose dashboard. Full custom UI needs aftermarket Android head unit (runs APK directly) or phone/tablet mounted. OpenAuto/openautolink = Pi-as-AA-receiver (still Google UI, replaces screen, no custom app); Bluefruit Feather = MCU, cannot run AA projection. UI-driving recipe that worked: screencap to /sdcard then `adb pull` (PowerShell `>` corrupts binary via UTF-16); `input tap`/`swipe`/`keyevent`; map displayedâ†’physical coords.












## v1.7.0 — 2026-07-13 — Turn-by-turn guidance (banner + voice)
**Description:** First TBT increment toward full AA replacement. OSRM `steps=true` maneuvers parsed into `RouteStep`; `TurnGuidance` engine picks the live next maneuver (skips depart, 40 m advance, passed-step fallback via next-closer check); `TurnBanner` overlays every page except APPS on phone AND carSurface (glass shows it via the same Compose tree); `VoiceGuide` TTS speaks staged prompts (far/quarter-mile/at-turn) with USAGE_ASSISTANCE_NAVIGATION_GUIDANCE audio attrs + transient-duck focus, released on utterance done. 🔊/🔇 tap on the banner toggles `voiceNav` (new DriveConfig field, default ON).
**Verified:** testDebugUnitTest green (incl. 8 new TurnGuidanceTest cases), compileReleaseKotlin green, lintDebug 0 errors, app-release.apk built. Backups in backups/*.v1.7.0.bak. NOT flashed yet — Fold is in a live Kia AA session (flashing would kill Anthony's drive); install queued for when the car session drops. Rule 8 pending.
**Live-Kia note (same session):** gearhead bound AmniCarAppService on the real car; `mode=ride · passive · no AOA claim`; zero communication-error lines — the error-16 class of failures stays resolved in RIDE.

## v1.7.1 — 2026-07-13 — Auto-reroute, ETA, car lite-mode (Grok perf findings pass 1)
**Description:** Anthony confirmed Amni ON THE KIA GLASS (physical Rule-8 for RIDE). Grok's follow-up diagnosis agreed with and being implemented in order. This drop: (1) off-route detection (`distToRouteM` point-to-polyline, 60 m × 3 samples, 15 s cooldown) → automatic OSRM reroute + spoken "Rerouting"; (2) TRIP tile now shows arrival ETA clock; (3) car lite-mode perf: WeatherLayer particles never run on carSurface, page transitions drop to 90 ms fades on glass (was spring+slide through the VirtualDisplay pipeline).
**Verified:** tests green (3 new distToRoute cases), lint 0 errors, release APK. NOT flashed — Kia session still live. Next: SearchTemplate hybrid typing (host keyboard → Amni map), then MediaSession bridge for real Spotify metadata.

## v1.7.2 — 2026-07-13 — Hybrid car typing: host SearchTemplate → Amni map (Grok pass 2)
**Description:** Typing on glass no longer fights the lossy surface click pipeline. SearchPanel on carSurface gains a "⌨ CAR KEYBOARD" chip → `AaNavState.openHostSearch()` pulse → `AaHomeScreen` pushes the existing `AaSearchScreen` (native AA SearchTemplate = the host's real keyboard/rotary input) → picking a result now does `AaNavState.requestGo(place)` + pop instead of the template route screen → Dashboard collects `pendingGo`, flips to NAV, and routes in the full Amni map. Best of both: Google's input, Amni's UI.
**Verified:** tests green, lint 0 errors, release APK. NOT flashed — Kia session still live (v1.7.0→2 all queued). Next: MediaSession bridge (real Spotify title/art).

## v1.7.3 — 2026-07-13 — Real Now Playing: MediaSession bridge (Grok pass 3)
**Description:** Spotify (any media app) title/artist/album-art now first-class in Amni. `media/MediaSessionBridge`: `AmniNotifListener` (NotificationListenerService, manifest-registered) unlocks `MediaSessionManager.getActiveSessions`; bridge follows the best active controller (PLAYING > PAUSED), publishes `NowPlaying(pkg,label,title,artist,art,playing)` StateFlow, re-attaches on session change. MediaCard renders real track + artist + album art (Image crop over the tile), transport buttons drive `transportControls` directly with media-key fallback, "ENABLE MEDIA ACCESS" chip (phone) opens notification-listener settings. Post-flash one-time grant (or via adb: `cmd notification allow_listener com.amni.drive/com.amni.drive.media.AmniNotifListener`).
**Verified:** tests green, lint 0 errors, release APK. NOT flashed — Kia session still live; v1.7.0–3 queued.

## v1.7.4 — 2026-07-13 — "then ↰" preview + NAVIGATION settings pane
**Description:** TurnBanner shows a "then ↰" chip when the next-next maneuver is coming (<400 m to current turn) — classic AA/Maps behavior. New Settings › NAVIGATION pane: Voice guidance toggle (same voiceNav as the banner 🔊) + plain-words notes on auto-reroute and the ⌨ CAR KEYBOARD path.
**Verified:** tests green, lint 0 errors, release APK. v1.7.0–4 queued for flash on Kia session drop.

## v1.8.0 — 2026-07-13 — Message banner + readout (last big AA staple)
**Description:** Incoming texts reach the dash. `AmniNotifListener.onNotificationPosted` filters CATEGORY_MESSAGE + known messaging pkgs (Messages/MMS/WhatsApp/Telegram/Signal/Messenger/Amni-Chat), skips group summaries, dedupes by key+postTime → `MessageFeed` StateFlow. Dashboard bottom-center `MsgBanner` (💬 sender + app + "🔊 READ" + ✕, auto-dismiss 12 s) on phone AND glass; READ speaks "Message from X. <text>" via VoiceGuide. Settings › NAVIGATION: "Message banner" (ON) + "Auto-read messages aloud" (OFF by default — privacy). Uses the same notification access as the media bridge — one grant covers both.
**Also this cycle:** flash_on_unplug watcher CAUGHT the unplug and auto-installed v1.7.4 (Success · versionName=1.7.4 verified) + media-access grant. LIVE-KIA taps observed via monitor before unplug: `Amni.AaSurface onClick fullUi=true surface=1785x690` ×3 — full UI interactive on the factory panel. Wireless adb recovery recipe re-proven: Meshnet port-scan → 46521 (throttle scan jobs — 4000 sockets/job exhausts buffers, but scan still landed).
**Verified:** tests green, lint 0 errors, release APK. v1.8.0 flash queued for next device contact.

## v1.8.1 — 2026-07-13 — FIX gearhead detection: CarConnection API (was blind since day one)
**Bug:** `Amni.AaWatch` logged `gearhead=false` during a LIVE Kia session. Root cause: `ActivityManager.runningAppProcesses` returns only OUR processes since Android 5.1, and `ps -A` is hidepid-blinded for app UIDs — every process-scan-based gearhead check was a permanent false. Safety impact: AUTO-mode AOA hold-off (`gearheadCarSessionAlive`) relied on this signal.
**Ship:** new `wire/CarLink` — observes `androidx.car.app.connection.CarConnection` LiveData (Gearhead's own content provider; 0=none 1=native 2=projection) into a StateFlow, started from `AmniDriveApp`. All six gearhead checks (`AaSessionWatchService`, `AoaProbeService`, `MeshDevBridgeService`) now OR `CarLink.connected`/`CarLink.projecting` with the legacy process scan. Watch tick + notif show `carConn=N`. No manifest change (QUERY_ALL_PACKAGES covers provider visibility).
**Verified LIVE on the Kia:** installed v1.8.1 mid-session → `tick mode=ride gearhead=true carConn=2 accessory=true`. Tests green, lint 0 errors. versionCode **76**.

## v1.8.2 — 2026-07-13 — REMOVE AmniMediaService stub (dead HU icon + Now Playing hijack risk)
**User:** "why is there still an amni-drive media? it doesnt work" — the HU media drawer showed a dead Amni entry.
**Root cause:** `aa/AmniMediaService` was a v1.4.x-era MediaBrowserService stub whose only purpose was drawer visibility before the RIDE surface existed. It registered an always-active fake MediaSession ("Amni Drive / Tap to open Amni media") that could also outrank real sources in `MediaSessionBridge`'s controller pick.
**Ship:** service deleted (file + manifest + `ensureStarted`), `automotive_app_desc.xml` drops `<uses name="media"/>` (lint enforces the pairing). Nav path (`AmniCarAppService`, NAVIGATION category) untouched.
**Also:** nav icon vanished from the HU grid this session — mid-session installs (v1.8.1 at 21:31) force-stop the app and Gearhead drops the CarAppService entry until session restart while the stale media row lingers. Installs queue for session drop from now on (rule already in architecture map). v1.8.2 installed during the 21:54 unplug window; fresh replug rebuilds the grid.
**Verified:** tests green, lint 0 errors, release APK, installed unplugged. versionCode **77**.

## v1.9.0 — 2026-07-13 — OWNED-GLASS TRACK: HU fullscreen (zero Android chrome)
**User:** "all the android auto formatting is still present... is there really no way to have an amni-drive full UI?" — answer: not on the factory Kia (Gearhead composites its status/facet chrome into the encoded stream before the wire; every AA nav app including Google Maps wears it; no API strips it). TRUE full UI lives on owned glass — and there, drive mode still showed Android's own status/gesture bars.
**Ship:** `huFullscreen` config (default ON): drive mode now applies immersive-sticky via `WindowInsetsControllerCompat` (hide systemBars, BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE), re-asserted on window-focus regain; Settings › NAVIGATION "🖥️ HU fullscreen" row. Mounted phone/tablet = 100% Amni pixels, swipe an edge to peek bars. Guardian council 5-0 (docs/guardian_councils/guardian_council_owned_glass_hu_mode.md).
**Verified:** tests green, lint 0 errors, release APK. Flash queued (adb wedged offline-handshake; probe armed). versionCode **78**.

## v1.9.1 — 2026-07-13 — Amni Link chrome-free + never-sleep (host + receiver)
**Gap:** LinkActivity (both roles) showed Android status/gesture bars around the car UI and never set keep-screen-on — a dash receiver would sleep mid-drive.
**Ship:** HOST and RECV roles get FLAG_KEEP_SCREEN_ON + the same `huFullscreen`-gated immersive-sticky as MainActivity (swipe-transient, focus re-assert). PICK screen stays normal.
**Verified:** tests green, lint 0 errors, release APK. Flash queued with v1.9.0. versionCode **79**.

## v1.9.2 — 2026-07-14 — FIX SearchTemplate crash (loading+list race) · full hybrid-search chain DHU-PROVEN
**Found by:** the automated DHU sweep — submitting a search in AaSearchScreen crashed the app: `IllegalArgumentException: Template is in a loading state but a list is set` (AaSearchScreen.kt:81). onGetTemplate always called BOTH `.setLoading(loading)` and `.setItemList(...)`; the `loading=true` invalidate tripped androidx's exclusivity check.
**Ship:** item list only attached when `!loading`.
**DHU-verified end-to-end (the full Grok chain):** 🔍 → ⌨ CAR KEYBOARD chip → host SearchTemplate keyboard → typed query → live Photon results w/ distances → result tap → back in FULL Amni UI with traffic-colored route + **turn banner "200 ft Turn right onto Vliet Boulevard" + "then →" chip** + trip "3 min · 1.0 mi". Also: Amni now AUTO-OPENS on session start (last-used nav app); Now Playing live-updates across track changes. v1.9.0+v1.9.1 flashed earlier tonight (00:02). versionCode **80**.

## v1.10.0 — 2026-07-14 — 🎤 Voice destination search (last big AA staple)
**Ship:** `voice/VoiceSearch` (SpeechRecognizer wrapper: availability gate, FREE_FORM + live partials, hard teardown on error/result — no hot mic). MapCard gains a 🎤 VOICE button under FIND (phone AND car glass — recognition runs on the phone mic, which is the right mic for mounted-phone HU); Dashboard orchestrates: lazy RECORD_AUDIO request on first tap → listen with live "Listening…/«partial»" chip bottom-center → geocode top hit → NAV page + route + spoken "Routing to X" via VoiceGuide. No match/error = transient chip, retap to retry. Guardian council 5-0.
**Verified:** tests green, lint 0 errors, release APK, installed on Fold (v1.10.0). Speech round-trip needs a human voice — Anthony to tap 🎤 and say a place. versionCode **81**.

## v1.10.1 — 2026-07-14 — HOTFIX: v1.10.0 crashlooped the car surface (ActivityResult in Presentation)
**Caught by:** the surface monitor — "bridge started + fullUi=true" repeating every ~1.2 s (7+ pids/10 s) the moment a session granted the surface. `rememberLauncherForActivityResult` throws during composition when there's no ActivityResultRegistryOwner — and the car-surface Dashboard composes inside a **Presentation**, which isn't Activity-backed. Gearhead re-granted the surface after each crash → loop.
**Fix:** launcher only created when `LocalActivityResultRegistryOwner.current != null`; on the glass without permission, 🎤 shows "grant microphone on the phone first" chip instead. **Re-verified on DHU:** same scenario, single surface grant, pid stable, 🎤 button visible under FIND on the glass.
**LESSON:** anything Activity-scoped (ActivityResult, back dispatchers) must be owner-guarded in Dashboard — it composes in MainActivity, LinkActivity, RearPresentation, AND the AA VirtualDisplay Presentation. versionCode **82**.
