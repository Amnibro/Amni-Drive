# Research Brief 04 — Android Auto wire protocol (open reverse-engineered stack)

_Compiled 2026-07-11. Grounded in public open-source reverse engineering (aasdk, OpenAuto, community HUIG notes, OpenAutoLink architecture). Purpose: Amni-Drive phone host that replaces Google’s AA host and projects Amni UI to factory head units._

## Roles

| Role | USB | TLS | Content |
|---|---|---|---|
| **Phone (device / accessory after AOA)** | Accessory mode after AOA switch | **SSL server** after channel setup | Historically Google AA host UI/templates |
| **Head unit (USB host)** | Host / AOA initiator | **SSL client** | Decodes video, sends touch/keys |

Open source **aasdk / OpenAuto** implement the **head-unit side**. Amni-Drive’s replacement path implements the **phone side**: same wire, **our** H.264 frames + input sink.

## Transport phases (USB wired)

1. **Enumeration** — phone as normal MTP/ADB or accessory-capable device.
2. **AOA (Android Open Accessory)** — HU sends accessory strings; phone re-enumerates as accessory; bulk endpoints carry the AA stream.
3. **Version / service discovery** — framed messages negotiate protocol version and services.
4. **TLS** — session encrypted (OpenSSL in aasdk lineage).
5. **Channel multiplex** — multiple logical channels over one encrypted stream (control, video sink, audio, sensor, input, bluetooth, etc.).

Wireless AA adds BT bootstrap → Wi‑Fi Direct / hotspot TCP, then same higher-layer session.

## Framing (public reverse-engineered shape)

High-level (exact field layouts tracked in code against aasdk message headers as we implement):

```
[ channel_id : u8/u16 ]
[ flags : u8 ]
[ payload_size : u16/u32 ]
[ payload : bytes ]   // often protobuf
```

Control messages use protobuf schemas reverse-engineered in the aasdk tree (`protos/`). Video channel carries codec config + NAL units. Input channel carries touch/key events from HU → phone.

## Channel map (conceptual — IDs verified against aasdk as native layer lands)

| Channel purpose | Direction | Amni Wire use |
|---|---|---|
| Control | bi | Session setup, ping, status |
| Video | phone → HU | H.264 of Amni UI (VirtualDisplay / MediaCodec) |
| Audio media | phone → HU | Media session / TTS later |
| Audio speech / mic | bi | Assistant / calls later |
| Input | HU → phone | Touch, rotary, hardkeys → inject into Amni UI |
| Sensor | HU → phone | GPS, speed (optional; we also have phone GPS) |
| Bluetooth | bi | RFCOMM / wireless AA later |

## OpenAutoLink lesson (transport hygiene)

Even when payload is Amni not Google:

- **Separate control vs media** (avoid head-of-line blocking).
- **Version + min_version negotiate**.
- **Keyframe request** on video.
- **JSON or typed control** for debugability on our own multi-channel Amni Link.
- mDNS/NSD discovery for wireless receivers.

OAL’s documented bridge protocol (`protocol.md`) is **not** Google AA; it is app↔bridge. We steal structure, not wire compatibility with OAL bridges, unless we later add an OAL adapter.

## Amni replacement strategy

```
                    ┌─────────────────────────────┐
                    │  Factory HU (stock AA radio) │
                    │  expects AA wire + video     │
                    └──────────────▲──────────────┘
                                   │ AOA + TLS + channels
                    ┌──────────────┴──────────────┐
                    │  Amni Wire (phone host)      │
                    │  replaces Google AA process  │
                    │  video = Amni Drive UI H.264 │
                    │  input → MotionEvent inject  │
                    └─────────────────────────────┘
```

Parallel tracks:

| Track | Status | Delivers |
|---|---|---|
| **A. Car App Library** | Shipped | Soft presence inside Google AA |
| **B. Amni Link multi-channel** | Upgrading | Full UI to Android HU/tablet |
| **C. Amni Wire (AA host)** | Scaffolding | Full UI to factory AA radios |

## Legal / product posture (personal project)

- Build on **already-published** reverse engineering (aasdk lineage) for interoperability.
- Personal testing, not commercial cert spoofing product.
- Expect breakage when Google changes handshake; pin versions and log everything.

## Sources

- opencardev/aasdk (and forks) — HU-side C++ protocol library
- opencardev/openauto — HU emulator
- mossyhub/openautolink — AAOS JNI aasdk, multi-channel design notes
- Community protocol notes (HUIG caches, XDA reverse-engineering threads)
- Android AOA docs: https://source.android.com/docs/core/interaction/accessories/aoa

## Next verification steps

1. Unit-test Amni framing + session FSM offline.
2. Multi-channel Link two-device LAN.
3. Capture/reference aasdk message enums into `wire/AaChannels.kt`.
4. USB accessory mode listener on phone; log AOA probes from car.
5. First TLS handshake attempt against Desktop Head Unit or open HU emulator.
6. First H.264 IDR accepted by a real factory HU (milestone).
