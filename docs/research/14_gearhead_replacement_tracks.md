# Research 14 — Gearhead replacement tracks

_Date: 2026-07-13_

## Goal
Own the car glass without Google templates forever. “Replace gearhead” ≠ one magic commit.

## Three tracks (all real)

| Track | What it replaces | Status | Blocker |
|-------|------------------|--------|---------|
| **RIDE** | Google *chrome* on glass | Live DHU/car Surface + full Amni Dashboard | Host still gearhead for TLS |
| **OWN Wire** | Google *host* on phone | AOA+TLS reaches AuthComplete | **status=-3 cert/identity wall** (research 13) |
| **Amni Link** | Google AA *entirely* on Android HUs | JPEG+touch protocol HOST↔RECV | Needs second Android device as HU |

## Non-negotiables
- Do **not** ship stolen Google host certificates.
- Default **RIDE** so wireless ADB survives (OWN claim can kill adbd).
- Factory glass: RIDE until lab HU or legitimate host material.

## Autonomous pursue plan
1. **Keep RIDE production-usable** — `scripts/dhu_ride_verify.ps1` automates install + HU server + DHU + screenshots.
2. **OWN wire stays ready** — plain Auth + SD Request + ChannelOpen path; mesh `GET /own` reports wall + next action; `POST /action/claim-own` only when intentional.
3. **Amni Link = open gearhead replacement** for aftermarket / tablet HU — mesh `POST /action/open-link`; same Dashboard UI as phone.

## Success definition (M5)
Full Amni UI on **target glass** without Google map-list templates as the primary experience.
- Factory: RIDE surface path (gearhead underneath).
- Owned Android glass: Link host→receiver or native launcher.
- OWN video: only after AuthComplete status=0 (or lab HU).

## Mesh API (automation)
- `GET /own` — mode, gearhead, accessory, wall string, recommended next
- `POST /action/mode?v=ride|own|auto`
- `POST /action/claim-own` — explicit OWN (may drop wifi adb)
- `POST /action/open-link` — launch Amni Link UI
