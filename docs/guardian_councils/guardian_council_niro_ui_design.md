# Guardian Council — Niro 10.25" UI design (2026-07-10)

**Question:** What layout language should Amni-Drive v0.2 adopt for a 1920×720 ultrawide head-unit panel ('23 Niro form factor), and how should the gate surface in the UI?

**Architect:** Three-zone driver home (nav | media | favorites) with a persistent bottom rail; settings as a two-pane master-detail since 720px of height can't afford page-per-screen navigation. Vote: three-zone + two-pane.

**Sentinel:** Whatever the layout, the gate must be *visible state*, not buried policy: drive-state chip always in the top bar, lock badges on gated tiles the moment state goes MOVING, and the blocked dialog must offer the safe path first (send to rear) before the override (passenger confirm). One-tap gate toggle stays on the dock. Vote: three-zone, gate-forward.

**Scholar:** AAOS Design guidelines: touch targets ≥76dp on head units, glance-readable type ≥24sp for primary content, dark theme default for night driving, max 2-deep menu depth. Two-pane settings satisfies depth; three-zone home mirrors proven AAOS CarLauncher zoning. Vote: three-zone + two-pane.

**Engineer:** Three-zone maps 1:1 onto a Compose Row of three weighted Cards — cheap to implement over the existing HomeScreen scaffold; two-pane settings is a Row(NavRail, Content). No new dependencies. Vote: three-zone + two-pane.

**Pathfinder:** Add personality — greeting line, colorful app-tile gradients, a distinct "Rear Lounge" identity for the back screen so the ungated space *feels* different from the driver space. Fun is the product. Vote: three-zone + playful skin.

**Verdict: 5–0 unanimous** — three-zone driver home, two-pane settings, gate-forward visible state, playful-but-night-safe skin. Mockups first at exact panel resolution; Compose implementation only after Anthony approves.
