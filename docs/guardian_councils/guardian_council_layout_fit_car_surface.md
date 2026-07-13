# Guardian Council — layout fit car surface

**Task:** Fit Amni Dashboard to AA/DHU window shape & size (UI was live under black Search/Drive chrome).

## Proposals

| Guardian | Approach |
|----------|----------|
| **Architect** | NavigationTemplate map-first + Presentation Compose already size-aware via surface WxH; fix Dashboard constraints only. |
| **Sentinel** | Don't steal USB; keep RIDE path; no OWN/cert risks. Layout-only + template chrome reduction. |
| **Scholar** | DHU often 800×480–1280×720; car surfaces short/wide → micro/dense tiers from maxWidth/maxHeight. |
| **Engineer** | BoxWithConstraints tiers; drop media on car; dense cards; pass dense into MapCard. Ship 1.6.8. |
| **Pathfinder** | User needs re-open Amni on DHU after install; autotest optional. |

## Vote
Majority: **Engineer + Scholar** — adaptive Compose layout + keep NavigationTemplate. Sentinel constraints: RIDE only.

**Decision:** Implement micro/dense/full layout in Dashboard; dense MapCard; build/install 1.6.8.
