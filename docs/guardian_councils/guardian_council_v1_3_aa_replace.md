# Guardian Council — v1.3 Amni replaces Android Auto (wire-level)

**Date:** 2026-07-11 · **Majority rules** · **Anthony mandate: reverse-engineer the link if needed**

## Question
How do we make Amni-Drive *replace* Android Auto on factory head units, including reverse-engineering the phone↔HU wire protocol?

## Proposals

### Architect
**Three layers, one product goal.** (1) Official Car App Library stays as soft fallback. (2) Amni Link becomes multi-channel (control/video/audio) for Android receivers. (3) **Amni Wire** — phone-side AA-compatible host built on *published* reverse-engineered protocol knowledge (aasdk/OpenAuto/HUIG community), encoding Amni UI as H.264 into the video channel factory units expect.

### Sentinel
Personal testing only. Use open reverse-engineering (aasdk lineage) for interop. No cert theft tooling as a product. Fail closed on drive gate. Label experimental builds clearly.

### Scholar
Source of truth for wire shape: open aasdk / openauto / public protocol notes (protobuf channels, TLS after AOA, phone as SSL server, HU as client). Do not invent from thin air — map every frame type to a public reference. OAL multi-channel design is the right *transport hygiene* even when payload is Amni video not Google templates.

### Engineer
Ship this sprint: (A) protocol research doc + channel map, (B) `wire/` session FSM + frame codec tests, (C) Amni Link split control/video ports (OAL lesson), (D) MediaCodec H.264 encode of host surface scaffold, (E) USB AOA discovery hooks. Full factory HU handshake is multi-sprint; scaffold must compile and unit-test now.

### Pathfinder
Test ladder: loopback wire codec → Amni Link multi-channel on two Androids → Desktop Head Unit / open HU emulator → real car USB. Anthony’s Fold + ADB remains the phone host.

## Vote
| Guardian | Vote |
|---|---|
| Architect | Full stack: Wire + multi-channel Link + keep Car App fallback |
| Sentinel | Same + experimental labeling |
| Scholar | Same + public aasdk-grounded channel map |
| Engineer | Same + ship scaffold this version |
| Pathfinder | Same + test ladder |

**Majority 5/5:** Pivot product goal to **Amni Wire host replaces Google AA host**. Implement research + scaffold + multi-channel Link + H.264 path now. Iterate on real cars until handshake works.
