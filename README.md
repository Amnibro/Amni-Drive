# Amni-Drive

**Open-source Android Auto replacement** — phone as the brain, factory car screen as the display. Zero ADB, zero root, no account, built-in OTA auto-updates, and packaged Amni companion apps.

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Website](https://img.shields.io/badge/Website-amni--scient.com%2Famni--drive-00D48A.svg)](https://amni-scient.com/amni-drive)
[![Latest Release](https://img.shields.io/github/v/release/Amnibro/Amni-Drive?color=00D48A)](https://github.com/Amnibro/Amni-Drive/releases/latest)

## Download

- **Official page & OTA info:** https://amni-scient.com/amni-drive
- **Direct signed APK:** https://amni-scient.com/downloads/amni-drive.apk
- **Car Suite Pack (ZIP — Amni-Drive + Amni-Map Car Edition + Amni-Browse):** https://amni-scient.com/downloads/amni-drive-suite.zip
- **GitHub Releases:** https://github.com/Amnibro/Amni-Drive/releases/latest

| Mode | What it does |
|---|---|
| **Amni Wire (USB)** | Phone-side Android Auto USB host (AOA + AAP + TLS + hardware H.264) projecting the full Amni-Drive cockpit directly onto factory car head units — touch, steering-wheel keys, rotary dial, and 20 FPS frame pacing. |
| **Amni Link** | Full custom UI stream over Wi-Fi / LAN to any Android head unit or tablet. |
| **Native Launcher** | Full-screen car home screen for aftermarket Android head units. |
| **Car App Library** | Soft navigation mode inside stock Android Auto when needed. |

> Not affiliated with Google. Not certified. Experimental. Use safely and obey local driving laws — the motion distraction gate is enabled by default for driver-facing full apps.

## Highlights

- **Resizable & Full-Screen Cockpit Layouts:** Switch live between Tri-Cockpit, 50/50 Split, Center Orb (circular map + side widgets), Map Hero, Theater, Horizon, and Full Map. Drag tile dividers to resize live, tap **⤢** on any tile for full screen, and choose **Left Rail** or **Bottom-Center Dock**.
- **Drag-Edit Widgets:** Long-press any widget card to drag-reorder or swipe-remove (Weather, Trip/Speed, Clock, Compass, Quick Media, Quick Apps, Gate, Vehicle).
- **Live Embedded Amni-Map:** Renders **Amni-Map v2.12.0+** natively inside the map tile over a shared surface (`DashInput`) with 3D vehicle puck, voice guidance, offline areas, and parked GPS drift stabilization.
- **Media Box & Synced Web Sign-Ins:** Run YouTube, Spotify Web, Netflix, Amni-Weather, Amni-Chat Web, Amni-Learn, and more inside the car display with a built-in car touch keyboard and persistent phone-synced login cookies.
- **Built-in SHA-256 OTA Auto-Updater & Amni Apps Hub:** Checks [`/drive/android-latest.json`](https://amni-scient.com/drive/android-latest.json) and [`/drive/apps-catalog.json`](https://amni-scient.com/drive/apps-catalog.json) every 6 hours, verifies SHA-256 checksums before invoking Android's installer, and installs/updates companion Amni-Scient apps (**Amni-Map**, **Amni-Browse**, **Amni-Haven**, **Amni-Chat**, **Amni-Type**, **Amni-Price**, **HedgeDoc**) in one tap from **Settings → OTA & AMNI APPS**.

## Zero-ADB Setup

1. Install **Amni-Drive** (and **Amni-Map** from the Car Suite Pack ZIP or inside **Settings → OTA & AMNI APPS**).
2. Open **Settings → ADVANCED → AMNI WIRE → Open Android Auto App Info** and disable or clear defaults on stock Android Auto so Amni-Drive can claim the car USB accessory.
3. *(Optional)* Open **Car Web Sign-Ins** in **AMNI WIRE** to sign into YouTube, Spotify Web, or Netflix once on your phone — cookies stay on-device and sync automatically to the car Media tile.
4. Plug your phone into your car's Android Auto USB port and tap **Always open Amni-Drive**.

## Build From Source

```bash
# JDK 17+, Android SDK 35+
./gradlew :app:testDebugUnitTest :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

Signed release build (requires `~/.config/amni/signing/android/amni-drive.properties` or your own keystore):

```bash
./gradlew :app:testDebugUnitTest :app:assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

See [`architecture_map.md`](architecture_map.md), [`ROADMAP.md`](ROADMAP.md), [`changelog.md`](changelog.md), and [`docs/research/`](docs/research/) for protocol notes and architecture details.

## Open Source

- License: **Apache License 2.0** ([LICENSE](LICENSE), [NOTICE](NOTICE))
- Privacy Policy: https://amni-scient.com/privacy-drive
- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md)
- Security: [SECURITY.md](SECURITY.md)

Wire-protocol design draws on public reverse-engineering (aasdk / OpenAuto / community documentation). See [NOTICE](NOTICE) for trademarks and third-party services (OSM, CARTO, Esri, OSRM, Open-Meteo).

PRs welcome.
