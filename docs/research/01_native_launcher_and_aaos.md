# Research Brief 01 — Custom Android Launcher for a Car Head Unit (CHOSEN PATH)

_Compiled 2026-07-03. Sourced. Supports the native-launcher decision._

## 1. AAOS vs Android Auto vs Aftermarket Chinese head units
- **Android Auto (projection):** phone is the brain; car screen is a secondary display mirroring the phone. Templated apps, approved categories only, no vehicle control. Retrofittable into aftermarket units.
- **Android Automotive OS (AAOS):** full Android running natively on the head unit (Polestar/Volvo/GM/Rivian/Renault). No phone. Apps install on the car; access vehicle HAL via the Car API.
- **Aftermarket Chinese head units:** standard AOSP Android 8–13 (often rooted/userdebug) on cheap SoCs — an Android tablet in the dash. NOT AAOS: no CarUxRestrictions enforcement, no GAS certification, no Car API. **Most permissive target** — sideload any APK, set any launcher. (Often misreport their Android version.)

## 2. Building a custom LAUNCHER ("full app play")
- **Manifest:** declare main activity as home via `MAIN` + `HOME` + `DEFAULT` (+ `LAUNCHER`) intent categories; `SECONDARY_HOME` for multi-display/passenger screens.
- **List apps:** `queryIntentActivities` on `ACTION_MAIN`/`CATEGORY_LAUNCHER` → label, icon, package.
- **Launch any app:** `startActivity(packageManager.getLaunchIntentForPackage(pkg))` — full native launch of Netflix/YouTube/games/browser.
- **Default home:** user via Settings → Default apps → Home; programmatic prompt via `RoleManager.createRequestRoleIntent(ROLE_HOME)` (API 29+); on rooted units force via `adb shell cmd package set-home-activity` or push to `/system`.
- **AAOS caveat:** genuine AAOS home (`CarLauncher`) is privileged; replacing the true home on a locked production car needs OEM signing. Full third-party home realistic on rooted aftermarket units + your own dev/AAOS builds.

## 3. Car-optimized UI (car-ui-lib)
- car-ui-lib: `Toolbar`, `CarUiRecyclerView`, `CarUiListItem`, `CarUiPreference`; rotary + touch. Statically linked per app.
- OEM theming via Runtime Resource Overlays (RROs).
- Design-for-Cars: large touch targets, mandatory day/night themes, short task flows (≤5 steps, ideally 2–3).
- On non-AAOS units car-ui-lib isn't available — hand-roll large-target, high-contrast, day/night UI.

## 4. Native AAOS apps vs projected AA apps
- Projected apps = Car App Library templates only; approved categories.
- Native AAOS apps = real Activities/Compose, more freedom, still bound by driver-distraction guidelines + CarUxRestrictions.
- **Parked apps (Android 15+):** run natively while parked — categories video (AAOS), games (AA+AAOS), browsers (AAOS). Manifest: `CATEGORY` `CAR_LAUNCHER`, `androidx.car.app.category` = video|game|browser, `distractionOptimized=true`. Play filters distribution to fixed devices.

## 5. CarUxRestrictionsManager / driving-state (`android.car.drivingstate`)
- Driving state from VHAL (gear + speed): Parked / Idling / Moving.
- Mapping: Parked = Unrestricted; Idling = no video + no config; Moving = fully restricted.
- Consume UX restrictions, not raw state: `getCurrentCarUxRestrictions()`, `registerListener(...)`; `isRequiresDistractionOptimization()` (false when parked), `getActiveRestrictions()` bitmask (`UX_RESTRICTIONS_NO_VIDEO`).
- Requires Car API (AAOS only). Non-AAOS units have no platform distraction limiting at all — hence unrestricted video/games there (user's responsibility).

## 6. Testing
- AAOS emulator: AVD → Automotive → Automotive system image (Android 12L/13/14, ARM64 w/ GAS for Apple Silicon).
- Simulate driving state: userdebug Quick Settings (speed/gear/park brake) or `adb shell cmd car_service inject-vhal-event <propId> <value>` (speed `0x11600207`, gear `0x11400400`).
- AAOS on Pixel Tablet as real-hardware bench.
- **Cheap Android tablet as stand-in head unit (recommended for the launcher):** same AOSP launcher stack as a Chinese unit; fastest representative test target for full app play.

## Recommended build target
Standard AOSP launcher app (`HOME`+`DEFAULT`+`LAUNCHER`, `queryIntentActivities`, `getLaunchIntentForPackage`) on a cheap Android tablet (Android 11–13) as stand-in head unit. Set default home via `RoleManager.ROLE_HOME` (or ADB on root). Design to car standards regardless (big targets, day/night, short flows). Treat genuine AAOS as secondary target via the Automotive emulator + a parked-app compliant path.

## Sources
- Android Auto vs Automotive: https://www.androidcentral.com/apps-software/android-auto/android-auto-vs-android-automotive
- What is Android Automotive: https://source.android.com/docs/automotive/start/what_automotive
- Custom Android Launcher (Paradox Cat): https://medium.com/paradox-cat-tech-hub/custom-android-launcher-why-and-how-do-i-build-one-6a1b3af89d43
- Intents & intent filters: https://developer.android.com/guide/components/intents-filters
- `<category>` manifest: https://developer.android.com/guide/topics/manifest/category-element
- Car UI library: https://source.android.com/docs/automotive/hmi/car_ui
- Design for Cars: https://developer.android.com/design/ui/cars
- Consume driving state & UX restrictions: https://source.android.com/docs/automotive/driver_distraction/consume
- Car UX Restrictions: https://source.android.com/docs/automotive/driver_distraction/car_uxr
- CarUxRestrictionsManager: https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager
- Build parked apps: https://developer.android.com/training/cars/parked
- AAOS emulator: https://developer.android.com/training/cars/testing/emulator
- AAOS on Pixel Tablet: https://developer.android.com/training/cars/testing/aaos-on-pixel
- YT9216B Chinese HU ROM/root: https://xdaforums.com/t/rom-root-twrp-guide-yt9216b-chinese-android-head-unit.4007063/
