# Roadmap — Amni-Drive replaces Android Auto

Open-source milestone track. Status is honest, not marketing.

## Vision

Phone runs **Amni-Drive** as the projection host. Factory head units that speak Android Auto show **Amni UI** (maps, media, apps policy) without Google’s host owning the session.

## Milestones

### M0 — Product skeleton ✅
- Launcher, maps, gate, themes
- Amni Link (phone host → Android receiver)
- Car App Library soft path

### M1 — Open protocol foundation ✅ (v1.3)
- `wire/` frame codec + session FSM + channel map
- Multi-channel Amni Link (control + video ports)
- H.264 encoder scaffold
- Live AOA capture docs from real car (Niro + Fold)

### M2 — Open source project kit ✅ (this push)
- Apache-2.0, NOTICE, CONTRIBUTING, SECURITY, ROADMAP
- Public-ready README
- AOA probe service logging accessory attach on-device

### M3 — AOA attachment + raw bulk I/O (in progress)
- Claim/log `UsbAccessory` when car starts AOA
- Read/write accessory FDs when gearhead is not holding them
- Capture first post-handshake bytes (research mode)

### M4 — aasdk-aligned session
- TLS + protobuf channels mapped to aasdk open schemas
- Video sink: H.264 from Amni VirtualDisplay
- Input source: touch → Amni UI

### M5 — Factory HU acceptance
- First IDR displayed on a real car
- Stable reconnect, audio path, steering keys
- Documented install for contributors

### M6 — Community 1.0
- Stable APIs, CI, signed releases, contributor docs
- Optional Play track for Car App soft path only

## Non-goals (for now)

- Passing Google certification as a drop-in commercial AA host
- Supporting every OEM wireless AA quirk on day one
- Shipping closed reverse-engineering dumps of Google apps

## How the /loop works

A recurring agent task:

1. If phone on ADB: refresh AOA/`dumpsys usb` + gearhead session notes.
2. Advance M3/M4 code (tests must stay green).
3. Update `docs/research/` captures and `changelog.md`.
4. Stop only when M5 is done **or** blocked on hardware the agent cannot reach.

## Join

See CONTRIBUTING.md. Issues/PRs welcome for wire, Link, UI, and docs.
