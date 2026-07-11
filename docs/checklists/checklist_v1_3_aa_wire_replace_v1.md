# Checklist — v1.3.0 Amni Wire: replace Android Auto

**Mandate:** Amni-Drive replaces AA even if reverse-engineering the wire is required.
**Date:** 2026-07-11

- [x] Guardian council + research brief 04 (open stack)
- [x] Architecture map pivot: Amni Wire primary goal
- [x] `wire/` package: channels, frame codec, session FSM, transport iface
- [x] Unit tests for wire frames + session transitions
- [x] Amni Link multi-channel (control :17321 + video :17322) OAL-inspired
- [x] H.264 encode scaffold (`link/H264Encoder.kt`) for host surface path
- [x] Settings › AMNI WIRE experimental pane + docs
- [x] version 1.3.0 · changelog
- [ ] Real-car AOA capture / DHU handshake (needs Anthony + car)
- [ ] Full aasdk-aligned protobuf channel payloads (next sprint)
