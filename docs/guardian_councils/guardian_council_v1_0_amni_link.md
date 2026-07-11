# Guardian Council — v1.0.0 Amni Link (Android Auto replacement)

**Date:** 2026-07-11 · **Majority rules**

## Question
How do we build a real Android Auto replacement for Amni-Drive — phone as brain, car screen as display — without waiting on Google?

## Proposals

### Architect
**Open Amni Link protocol first.** Phone hosts car UI; head unit runs a thin receiver. Framed TCP + JPEG v1, H.264 v1.1, wireless discovery via NSD. Later optional bridges (USB AOAP accessory mode, third-party AA radios) as adapters on top of the same session model.

### Sentinel
Ship **safety with the stream**: gate still runs on the phone host; receiver is display-only. Heartbeat timeout → blank/safe frame. Fail-closed motion stays on host. No silent driver video-at-speed by default.

### Scholar
Do **not** block on reverse-engineering Google's phone-side AA host for v1. Own the stack end-to-end so we can ship full Amni UI (maps, apps drawer, themes). Document AA-protocol interop as Phase 2 research track only.

### Engineer
**One APK, two roles:** PHONE_HOST and HU_RECEIVER. Same binary on Fold + aftermarket unit. `LinkHost` uses root-view capture + JPEG; `LinkReceiver` Image bitmap + touch normalize. Coroutines + blocking sockets on IO dispatcher. Unit-test the codec offline.

### Pathfinder
**LAN Wi‑Fi first** (hotspot from phone or car Wi‑Fi). NSD `_amni-link._tcp` on port 17321. USB tether IP path works free. Wireless AA dongles out of scope until protocol is solid.

## Vote
| Guardian | Vote |
|---|---|
| Architect | Open Amni Link + dual-role APK |
| Sentinel | Same + host-side gate + heartbeat |
| Scholar | Same (defer Google AA reverse-eng) |
| Engineer | Same + JPEG MVP |
| Pathfinder | Same + NSD LAN |

**Majority (5/5):** Build Amni Link open projection as the Android Auto replacement path. v1 = JPEG over TCP + NSD + dual-role UI. H.264/USB/AA-bridge = follow-ons.
