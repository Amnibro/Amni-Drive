# Research Brief 03 — AA Projection Stack (REJECTED ALTERNATIVE)

_Compiled 2026-07-03. Sourced. Documents why the projection path was rejected._

## The projection model
Android Auto is projection, not an in-car OS: phone does all compute, head unit is a remote H.264 display + input surface.
- **Transport:** USB via AOAP (head unit = USB host/AOA accessory) or wireless (BT bootstrap → 5 GHz Wi-Fi Direct). TLS-secured session, Protobuf messages, H.264 video.
- **App model:** "Android for Cars App Library" (`androidx.car.app`) — third-party apps are **templated** (~10 fixed Template classes), rendered by a Google-controlled **host** app. Categories: navigation, POI, media, messaging, IoT, weather. No arbitrary framebuffer for third parties.

## Open-source receiver implementations
| Project | What | Status 2025–26 | HW |
|---|---|---|---|
| aasdk (f1xpl) | C++ AA head-unit protocol lib (USB/TCP, SSL, Protobuf, A/V) | original dormant; forks carry on | Linux/Pi/Windows |
| OpenAuto (f1xpl) | Qt head-unit emulator on aasdk | original abandoned (~Apr 2018) | Pi 3/Linux/Windows |
| Crankshaft (opencardev) | Turnkey Raspbian image w/ OpenAuto | active (Jan 2025 release) | Pi 3/4 |
| openDsh / Dash | Most active modern fork family (openauto+aasdk) | semi-active (2024) | Pi + SBCs |
| headunit / OpenHU | Early Android/desktop impl | legacy | Android/desktop |

- **Can:** connect a real phone, display projected AA UI (480p–1080p, 30/60 fps on Pi), pass through touch/buttons/voice/audio/BT.
- **Cannot:** run arbitrary apps; are uncertified ("R&D purposes"); break on Google protocol/cert changes; cannot unlock categories the phone-side host refuses.

## Why projection cannot do "full app play"
1. **Templated model is the gate** — a general app can't draw arbitrary pixels; must use fixed templates via the Google host. Video/game rendering isn't a representable template.
2. **UX restrictions** — driving state (Parked/Idling/Moving) + `CarUxRestrictions` block video/complex interaction when not parked. Safety policy, not accident.
3. **AA vs AAOS split** — "video while parked" (YouTube/Netflix) is largely an AAOS feature (apps run natively on the car), not projected AA. On projected AA, YouTube = audio-only media controls.

## Wireless adapters (AAWireless, Motorola MA1)
Wireless bridges for cars that already support wired AA — impersonate a wired phone to the head unit, pair phone over BT then 5 GHz Wi-Fi. Do NOT add AA or bypass app restrictions. AAWireless remains independent (not Google-acquired).

## Legal
- DMCA §1201(f) interoperability exception is the legal footing for aasdk/OpenAuto (clean-room reimplementation for interop).
- Risk concentrates in anti-circumvention (certs/auth/code-signing) + EULA "no reverse engineering" clauses (a separate contractual layer). Commercialization raises exposure. Projects stay non-commercial, R&D, uncertified, GPL.

## Verdict for Amni-Drive
**Rejected.** Fails the "full app play" requirement outright (structural, not fixable), is protocol-fragile, uncertified, and legally delicate. Native launcher on the head unit (Brief 01) is the correct path.

## Sources
- AA Head Unit Integration spec: https://milek7.pl/.stuff/galdocs/huig13_cache.html
- Android for Cars App Library: https://developer.android.com/training/cars/apps/library
- Desktop Head Unit (DHU): https://developer.android.com/training/cars/testing/dhu
- Car UX Restrictions: https://source.android.com/docs/automotive/driver_distraction/car_uxr
- Build video apps (parked, AAOS): https://developer.android.com/training/cars/parked/video
- f1xpl OpenAuto: https://github.com/f1xpl/openauto
- aasdk: https://github.com/openDsh/aasdk
- openDsh org: https://github.com/opendsh
- Crankshaft: https://github.com/opencardev/crankshaft
- YouTube on AA = audio-only: https://android.gadgethacks.com/news/youtube-on-android-auto-arrives-as-audio-only-media-controls/
- MA1 vs AAWireless: https://www.androidpolice.com/motorola-sound-ma1-vs-aa-wireless/
- DMCA §1201(f) analysis: https://leppardlaw.com/federal/computer-crimes/evaluating-the-role-of-reverse-engineering-in-dmca-compliance-under-us-federal-law/
