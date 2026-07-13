# Guardian Council — Dual-path full UI (Amni-Drive)

**Question:** How do we put the **whole** Amni-Drive UI on the factory car screen while keeping both “ride AA session” and “own the host” options?

## Guardians

### Architect
Two pipes, one product goal. **Path A (RIDE):** Gearhead authenticates; Amni paints maximum surface (nav Surface + media). **Path B (OWN):** Amni is the AA phone host; VirtualDisplay H.264 → VIDEO_SINK. Mode flag `aaPathMode` defaults **AUTO** (ride if gearhead alive). Never steal FD while gearhead holds.

### Sentinel
Stealing AOA caused Communication error 16. RIDE must hard-block `openAccessory`. OWN is opt-in or gearhead-free only. Session watch logs everything for forensics; no silent claim in RIDE.

### Scholar
Post-auth “interrupt stream” requires in-process hooks (root). Without root, Path A = official Surface (full custom paint, still Gearhead session). Path B = open RE wire (aasdk framing) until HU accepts host identity.

### Engineer
Ship: ConfigStore path, AoaProbe guards, AaSessionWatchService, richer Surface canvas, car_dual_path_loop.ps1, version 1.4.3. Projection engine already encodes full MainActivity for Path B when OPEN works.

### Pathfinder
Car loop: wireless ADB → watch USB accessory + gearhead → reports every N cycles → Anthony plugs Kia → Path A first (open Amni on car apps) → only force OWN if RIDE surface rejected and gearhead can be released.

## Vote
**Unanimous dual-path.** Product win condition = full Amni UI on glass. Implementation win = either path that delivers it. No premature pivot.
