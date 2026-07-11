# Amni-Drive

**Open-source Android Auto replacement** — phone as the brain, car screen as the display.

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

Amni-Drive aims to **replace Google Android Auto** on the phone↔car link (USB AOA / wireless), while also shipping:

| Mode | What it does |
|---|---|
| **Amni Wire** *(in progress)* | Phone-side AA-compatible host projecting **Amni UI** to factory head units |
| **Amni Link** | Full custom UI stream to any **Android** head unit / tablet (our open protocol) |
| **Car App Library** | Soft navigation app *inside* stock Android Auto (interim) |
| **Native launcher** | Home screen + full app play on aftermarket Android units |

> Not affiliated with Google. Not certified. Experimental. Use at your own risk while driving — distraction gate is on by default for driver-facing full apps.

## Status

See [`ROADMAP.md`](ROADMAP.md) and [`changelog.md`](changelog.md).

- ✅ Modern car UI (maps, search, routing, themes, gate)
- ✅ Amni Link multi-channel (control + video TCP)
- ✅ Wire frame/session scaffold + H.264 encoder stub
- ✅ Live AOA capture from a real factory car session
- 🔄 AOA probe service + path to claim accessory session
- ⬜ Full aasdk-aligned TLS/protobuf host accepted by factory HU

Architecture: [`architecture_map.md`](architecture_map.md)  
Research: [`docs/research/`](docs/research/)

## Build

```bash
# JDK 17+, Android SDK 34+
./gradlew :app:testDebugUnitTest :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Windows (system Gradle + Android Studio JBR):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
gradle :app:testDebugUnitTest :app:assembleDebug
```

## Quick try

1. Install the debug APK on an Android phone.
2. **Settings → AMNI WIRE → START AOA PROBE** while plugged into a car (logs accessory attach).
3. **Amni Link** for full UI on a second Android device on the same Wi‑Fi.
4. **CAR / USB** for the official Android Auto soft path (developer settings / Unknown sources).

## Open source

- License: **Apache License 2.0** ([LICENSE](LICENSE), [NOTICE](NOTICE))
- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md)
- Security: [SECURITY.md](SECURITY.md)

Wire-protocol design draws on public reverse-engineering (aasdk / OpenAuto / community docs). See NOTICE for trademarks and third-party services (OSM, CARTO, Esri, OSRM, Open-Meteo).

## Community goal

Ship a real, inspectable alternative to closed projection stacks — documented captures, open framing, and a phone host that puts **your** car UI on the dash.

PRs welcome.
