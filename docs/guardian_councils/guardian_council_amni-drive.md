# Guardian Council — Amni-Drive (scaffold)

**Convened:** 2026-07-03
**Question before the council:** What architecture should Amni-Drive take to deliver a customizable Android Auto alternative with "full app play," on the car head unit, with a distraction gate that is present-by-default but permissive for the backseat?

Five guardians each propose an approach. Majority rules; ties explore both paths.

---

## Guardian 1 — The Architect (structure & long-term shape)

**Proposal:** Native head-unit **launcher** (home-screen replacement) as the product, not a projection receiver. Structure the code so the **gate is a single chokepoint** (`GateManager.decide(...)`) that every launch flows through, and make **displays first-class** (a `DisplayRole` classification layer) so per-display policy is not bolted on later but is the spine of the design.

**Reasoning:** Full app play is only possible when apps run natively on the head unit. If the gate is a chokepoint, safety can never be accidentally bypassed by a new code path; if displays are first-class, Anthony's rear-vs-driver split falls out naturally instead of being special-cased.

**Vote:** Native launcher + gate-as-chokepoint + display-role spine.

---

## Guardian 2 — The Sentinel (safety & risk)

**Proposal:** Ship the gate **on by default for the driver display, fail-closed**, but with a **one-tap disable** and a **fully ungated rear display**. Motion signal must **fail to the safe state** (no-fix ⇒ MOVING ⇒ gated), never to "parked." Mirror the platform's own proven three-state model (Parked/Idling/Moving).

**Reasoning:** The real hazard is driver-facing video-at-speed; the backseat is not a hazard. A defeatable single park-wire is a known industry loophole, so we never unlock on one signal alone — we unlock on positive stationary evidence *or* explicit passenger confirmation. Respecting Anthony's "no hard locks," the driver override is one tap — but the *default* must be the defensible posture, and the backseat must not be nannied.

**Vote:** Per-display, fail-closed, one-tap driver override, ungated rear. **Strong concurrence with the Architect.**

---

## Guardian 3 — The Scholar (research & precedent)

**Proposal:** Ground every choice in what already exists. Reuse the **AOSP launcher pattern** (`HOME`+`LAUNCHER` intents, `queryIntentActivities`, `RoleManager.ROLE_HOME`) and **mirror AAOS's `CarUxRestrictions`** semantics rather than inventing a gate model. Reject the projection path on evidence: OpenAuto/aasdk/Crankshaft are receivers that *cannot* unlock arbitrary apps because Google's phone-side host owns that policy.

**Reasoning:** Three sourced research briefs (saved under `docs/research/`) converge: (a) launcher-on-tablet is the closest testable analog to a real aftermarket unit; (b) projection cannot do full app play; (c) GPS-speed + manual toggle is the right default motion signal, with CarPropertyManager/OBD as upgrades. Don't fight precedent.

**Vote:** Launcher path; borrow AAOS's gate model; projection rejected on evidence.

---

## Guardian 4 — The Engineer (buildability & testing)

**Proposal:** **One APK that installs everywhere.** Access the Car API by **reflection** so the app compiles with zero `android.car` dependency and runs on a plain tablet, lighting up AAOS signals only when present. Primary test bench = **a cheap Android tablet as a stand-in head unit**; secondary = the Android Studio Automotive emulator (with VHAL speed/gear injection) for the AAOS-only paths. Keep the distraction-decision logic **pure and unit-testable** off-device.

**Reasoning:** Anthony's real hardware is unknown today; a reflection-based single APK avoids blocking on it. Pure gate/motion/classifier logic can be JVM-unit-tested now; device-only concerns (default-home, multi-display, Fused speed) are isolated behind interfaces.

**Vote:** Single reflection-based APK; tablet-first testing; pure testable core. **Concurrence.**

---

## Guardian 5 — The Pathfinder (user goal & future doors)

**Proposal:** Optimize for **the kids-in-the-backseat use case working out of the box**, and leave clean doors open: OBD-II as a motion upgrade, AAOS occupant-zones for precise display tagging, and a possible future companion for wireless multi-display. Put **every toggle somewhere obvious** — a settings section *and* a persistent driver-home quick-tile — so the override never requires a treasure hunt.

**Reasoning:** Anthony's stated real use case is backseat video for kids; the default experience must nail that with zero configuration, while the driver keeps an easy, visible override. Future-proofing shouldn't complicate v0.1 — just don't wall it off.

**Vote:** Backseat-first defaults; visible dual-location toggle; upgrade doors left open. **Concurrence.**

---

## Decision (5–0, unanimous)

**Adopted architecture:**
1. **Native head-unit launcher** (home replacement) — projection path rejected on evidence.
2. **Gate as a single chokepoint** (`GateManager.decide`), with **displays first-class** (`DisplayRole`).
3. **Per-display policy:** rear/passenger **ungated by default**, primary driver **light-gated by default with one-tap disable**, cluster/unknown treated as driver.
4. **Motion:** GPS speed + manual toggle default, **fail-closed**, mirroring AAOS's three-state model; OBD-II and `CarPropertyManager`/`CarUxRestrictions` as reflection-guarded upgrades.
5. **One reflection-based APK** installs everywhere; **tablet-first** testing; **pure unit-testable** decision core.
6. **Toggle lives in two obvious places:** Settings "Distraction gate" section + persistent driver-home quick-tile.

No ties; no split paths required. Sentinel holds a standing note: any future code path that launches an app **must** route through `GateManager.decide` — do not add a bypass.
