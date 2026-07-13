# Checklist — Dual-path full Amni UI on factory glass (v1.4.3)

**Date:** 2026-07-12  
**Mandate:** Keep Path A (ride Gearhead session + paint full UI) and Path B (Amni phone-host wire). Pivot only on unresolvable walls. Automate car tests via wireless ADB.

- [x] Guardian council dual-path
- [x] `aaPathMode` config: auto | ride | own
- [x] AoaProbe refuse open in RIDE / AUTO+gearhead
- [x] `AaSessionWatchService` passive dual-path watch + AUTO claim policy
- [x] Expand `AaSurfaceRenderer` to fuller Amni chrome (cards, dock, HUD)
- [x] Settings › AMNI WIRE path mode selector
- [x] `scripts/car_dual_path_loop.ps1`
- [x] Install 1.4.3 on Fold (wireless) before car plug
- [x] 1.4.4 gearhead:car session detect + reconnect installer script
- [ ] Reconnect ADB after car USB; flash 1.4.4 if needed
- [ ] Live car: Path A — open Amni nav on Kia, confirm surface paint
- [ ] Live car: Path B only if gearhead free / explicit OWN
- [x] Update architecture_map + changelog (pending live result notes)
