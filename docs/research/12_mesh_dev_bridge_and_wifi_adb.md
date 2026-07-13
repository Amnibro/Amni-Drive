# Research 12 — Mesh Dev Bridge + Wi‑Fi ADB survival

_Date: 2026-07-12_

## Problem
Launching Amni-Drive (or auto AOA claim) reconfigured USB accessory and restarted `adbd`, killing **wireless ADB** while the phone was in the car.

## Fix (v1.4.6)
- Default `aaPathMode = ride`
- No `openAccessory` unless mode is **OWN** (explicit Settings button / claim)
- `AoaAttachActivity` and `AoaProbeService` ignore attach/poll in RIDE/AUTO
- Passive `AaSessionWatchService` logs only

## Mesh Dev Bridge
`MeshDevBridgeService` — HTTP `0.0.0.0:7931`, token auth.
- `GET /health` `/status` `/logs/watch` `/logs/probe` `/logcat`
- `POST /action/open-nav` `/action/mode?v=ride|own` `/action/claim-own` `/action/watch-tick`
- Token in Settings › AMNI WIRE; prefs `amni_mesh_bridge`
- PC script: `scripts/mesh_bridge.ps1 -Ip 100.x.x.x -Token …`

## Install once
Need one ADB or sideload install of 1.4.6+; afterward car debug works over Meshnet without ADB.
