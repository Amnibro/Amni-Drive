# Research 11 — Dual path: RIDE (A) + OWN (B)

_Date: 2026-07-12_

## Product goal
Full Amni-Drive UI on factory HU (not maps-only, not Google templates as the end state).

## Path A — RIDE
- Gearhead completes AA handshake (USB/AOA + TLS + channels).
- Amni never opens the accessory FD.
- Amni Car App (NAV) receives a drawing Surface → paint full custom UI.
- “Interrupt stream after connect” without root ≈ paint *this* surface, not steal bulk USB.

## Path B — OWN
- Amni claims accessory when gearhead free (or mode=OWN).
- AAP version negotiate → TLS research → H.264 of VirtualDisplay (full MainActivity).
- Blocked on host identity / HU silence until RE breakthrough.

## AUTO
- gearhead alive → behave as RIDE  
- gearhead free + accessory → claim OWN  

## Success metrics
| Metric | Path A | Path B |
|--------|--------|--------|
| Gearhead holds session | required | optional |
| Surface draw on car | primary | n/a |
| AOA OPEN + AAP RX | forbidden if GH holds | required |
| Full phone Dashboard H.264 | later inject research | primary |

## Non-goals
Shipping stolen Google host certs. Killing gearhead mid-session by default.
