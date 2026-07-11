# Research Brief 02 — Vehicle Motion Detection for the Distraction Gate

_Compiled 2026-07-03. Sourced. Supports the per-display safety gate._

## Signal sources ranked by robustness
| Rank | Source | Authority | Availability | Latency | Standstill reliability | Role |
|---|---|---|---|---|---|---|
| 1 | AAOS `CarPropertyManager` (speed+gear+brake) | Highest (vehicle bus) | AAOS only | ~real-time | Best (gear=Park definitive) | Primary on AAOS |
| 2 | OBD-II ELM327 (PID `010D`) | High (ECU) | Any car + adapter | 50–200 ms | Good (true 0) | Upgrade / tunnel-proof |
| 3 | **GPS speed (Fused / getSpeed)** | Medium (Doppler) | Universal | 1 Hz, cold-start | Fair (needs deadband) | **Default / testing** |
| 4 | Park/reverse wires | Low (defeatable) | Aftermarket | Low | Coarse | Corroborator only |
| 5 | Accel / ActivityRecognition | Low (no abs speed) | Universal | 10 s+ | Ambiguous | Dropout bridge / confirm |

## 1. GPS speed — FusedLocationProviderClient / Location.getSpeed()
- Blends GPS/Wi-Fi/cell/sensors; `getSpeed()` m/s, guard with `hasSpeed()`; `getSpeedAccuracyMetersPerSecond()` (68% confidence) if `hasSpeedAccuracy()`.
- Speed from GNSS Doppler (not position deltas) → ~±0.5–1 m/s open sky, more reliable than differencing positions.
- **Standstill jitter** is the failure mode: apply deadband (<~1.5–2 m/s ≈ 3–4 mph = "stopped"), require N consecutive over-threshold samples before "moving," reject poor-accuracy samples. `Priority.HIGH_ACCURACY`, ~1 s interval when gate active.
- Cold fix tens of seconds; warm ~1 s; ~1 Hz updates; tunnels/garages drop fix → "no signal ≠ parked."
- Needs `ACCESS_FINE_LOCATION` (+ Android 10+ background handling).

## 2. OBD-II ELM327 (Bluetooth)
- Speed PID `010D` (km/h), RPM `010C`. Strict request/response; wait per PID.
- Libs: `fr3ts0n/AndrOBD`, `barnhill/AndroidOBD`, `Tomiwa-Ot/obd`; `Ircama/ELM327-emulator` for carless testing.
- Clone adapters flaky (baud 38400 vs 115200, partial PID support, dropouts). Ground-truth when connected; setup friction too high to be the default.

## 3. Head-unit / AAOS hardware — CarPropertyManager
- Aftermarket wires: park-brake (many units only unlock video when grounded) + reverse-gear (backup cam). Coarse, defeatable → hints only.
- AAOS `CarPropertyManager` + `VehiclePropertyIds`: `PERF_VEHICLE_SPEED` (m/s), `GEAR_SELECTION`, `PARKING_BRAKE_ON`; needs `Car.PERMISSION_SPEED` etc.; `registerCallback`.
- **Only 4 of ~70 properties are mandatory — and they are exactly `GEAR_SELECTION`, `NIGHT_MODE`, `PERF_VEHICLE_SPEED`, `PARKING_BRAKE_ON`.** The 3 we need are guaranteed on AAOS, but values/availability still vary by OEM. Not present on plain tablets.

## 4. Accelerometer / sensor-fusion fallback
- `ActivityRecognitionClient`: `IN_VEHICLE`/`STILL`/`WALKING` w/ confidence — cheap, low-power, but coarse + laggy (10 s+), can't tell "stopped at light" from "parked." Corroborator only.
- Raw accel/gyro/mag + Kalman: can classify stopped/driving but drifts, no absolute speed. Best as GPS-dropout dead-reckoning bridge + standstill confirm.

## 5. Android's own framework (mirror it)
- `CarDrivingStateService` maps speed+gear+brake → Parked (baseline) / Idling (`no_video|no_config`) / Moving (`fully_restricted`).
- `CarUxRestrictionsManager` publishes active set; apps self-gate. Flags: `UX_RESTRICTIONS_NO_VIDEO`, `NO_KEYBOARD`, `NO_SETUP`, `FULLY_RESTRICTED`; `isRequiresDistractionOptimization()`.
- Video (`android:appCategory="video"`) blocked whenever not parked; audio may continue.
- NHTSA guidelines (adopted by Android): single glance ≤ 2 s, total task ≤ 12 s, no video/scrolling/photos while driving, tasks interruptible.

## 6. Why it matters
- NHTSA: 3,208 killed in distraction-affected crashes in 2024 (~2% down from 3,283 in 2023); 289,310 injured (2022). Visual-manual tasks up to ~3× crash risk.
- Liability: shipping distracting content playing while moving is exactly what NHTSA/AAOS restrictions prevent; a defeatable single-wire park hack is a known loophole. Fail-safe (default-locked, unlock on positive stationary evidence + explicit passenger confirm) is defensible.

## Recommended default (testable build)
1. **GPS speed + manual toggle.** Fused HIGH_ACCURACY 1 Hz; "moving" if speed > deadband (~1.5–2 m/s) for ≥2 valid samples; "stopped" only after several sub-threshold acceptable-accuracy samples. **Fail closed:** no-fix/timeout → moving (locked).
2. **Explicit override:** "Parked" button + "Passenger — not the driver" confirm; auto re-lock when speed re-crosses threshold.
3. **Upgrade A — OBD-II:** prefer PID `010D` when ELM327 paired.
4. **Upgrade B — AAOS:** read `PERF_VEHICLE_SPEED`+`GEAR_SELECTION`+`PARKING_BRAKE_ON`, or consume `CarUxRestrictionsManager` directly.
5. **Fuse, don't trust one:** accel/ActivityRecognition bridges dropouts; never unlock on a single defeatable wire.

## Sources
- Fused Location Provider: https://developers.google.com/location-context/fused-location-provider
- Location (getSpeed/hasSpeed/speedAccuracy): https://developer.android.com/reference/android/location/Location
- VehiclePropertyIds: https://developer.android.com/reference/android/car/VehiclePropertyIds
- CarPropertyManager: https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager
- AAOS mandatory 4 properties: https://groups.google.com/a/android.com/g/automotive-developers/c/UuqplzBEWZ0
- AndrOBD: https://github.com/fr3ts0n/AndrOBD
- ELM327-emulator: https://github.com/Ircama/ELM327-emulator
- Car UX Restrictions: https://source.android.com/docs/automotive/driver_distraction/car_uxr
- Consume driving state: https://source.android.com/docs/automotive/driver_distraction/consume
- Driver Distraction Guidelines: https://source.android.com/docs/automotive/driver_distraction/guidelines
- Build video apps (parked): https://developer.android.com/training/cars/parked/video
- NHTSA Visual-Manual Guidelines: https://www.federalregister.gov/documents/2013/04/26/2013-09883/visual-manual-nhtsa-driver-distraction-guidelines-for-in-vehicle-electronic-devices
- NHTSA distracted driving: https://www.nhtsa.gov/risky-driving/distracted-driving
- Distracted Driving 2023 (CrashStats): https://crashstats.nhtsa.dot.gov/Api/Public/Publication/813703
