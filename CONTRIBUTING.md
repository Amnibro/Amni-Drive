# Contributing to Amni-Drive

Thanks for helping build an open Android Auto replacement.

## Project goal

**Replace Android Auto** with Amni-Drive on the phone↔car wire (AOA/USB and wireless), while keeping:

- Amni Link (full UI to Android head units / tablets)
- Native launcher mode
- Official Car App Library path as a soft fallback

## How to contribute

1. Fork / branch from `main`.
2. Keep Kotlin style consistent with existing modules (`wire/`, `link/`, `aa/`, `ui/`).
3. Add or update unit tests for pure logic (frames, session, gate, routing helpers).
4. Update `changelog.md` and `architecture_map.md` for user-visible changes.
5. Open a PR with: what changed, how to test, car/phone models if relevant.

## Development setup

```bash
# Requires Android SDK 34+, JDK 17+
export JAVA_HOME=...   # Android Studio JBR works
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

On Windows without the wrapper jar:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
gradle -p . :app:testDebugUnitTest :app:assembleDebug
```

## Live car debugging

With wireless ADB to a phone plugged into a car:

```powershell
adb devices
adb shell dumpsys usb | findstr ACCESSORY
adb logcat -s CAR.GH:* GH.ConnLoggerV2:* GH.WifiBluetoothRcvr:*
```

See `docs/research/05_live_aa_session_capture.md` for a real Niro capture.

## Scope rules

- Prefer public reverse-engineering references (aasdk lineage) over closed dumps of Google binaries.
- Do not commit secrets, keystores, personal logs with phone numbers, or proprietary OEM firmware.
- Personal safety: distraction gate defaults stay on for driver-facing full apps.
- Experimental AA wire work must stay clearly labeled until it works end-to-end.

## Code of conduct

Be kind. No harassment. Technical disagreement is fine; personal attacks are not.
