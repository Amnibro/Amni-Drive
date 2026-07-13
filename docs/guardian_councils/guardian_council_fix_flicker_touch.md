# Guardian Council — RIDE flicker + dead touch fix (v1.6.15)

**Problem:** DHU/car shows flickering RIDE UI; taps never land (apps/media unreachable). Screenshot shows Presentation rendering but host APP_ICON square covers clock/weather tile.

**Diagnosis (shared evidence):** `startFullUi` executes on the main looper (posted from `onSurfaceAvailable`), then posts the Presentation-creation runnable to the SAME looper and blocks on `latch.await(4s)`. The runnable cannot run until the block ends → latch always times out → returns false → `fullUi=false` → canvas loop keeps locking the surface the VirtualDisplay renders into (flicker) and all SurfaceCallback input takes the pan branch (no touch injection).

- **Architect:** Restructure — create VD + Presentation inline on main, no latch, no canvas-first warmup. Canvas loop becomes pure fallback. Vote: inline.
- **Sentinel:** Two producers on one Surface is undefined behavior; eliminate the race, keep GateManager path untouched. Vote: inline.
- **Scholar:** CountDownLatch on the thread that must satisfy it is a textbook self-deadlock; Handler.post-from-main never runs before the current message completes. Vote: inline.
- **Engineer:** Keep `releaseFullUi` idempotent; startLoop only in the failure branch; delete latch/TimeUnit imports. Fixed 80dp/30dp reservation for host chrome beats dynamic visible-area plumbing (no over-engineering). Vote: inline + fixed insets.
- **Pathfinder:** Fastest route to a working car dash; DHU verifies taps without the Kia. Vote: inline.

**Verdict: 5-0 — inline main-thread construction, fallback-only canvas loop, fixed carSurface chrome insets.**
