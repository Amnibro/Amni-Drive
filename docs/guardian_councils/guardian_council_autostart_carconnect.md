# Guardian Council — Auto-start on car connect

**Question:** How does Amni-Drive auto-enter the dash "like CarPlay/AA" (plug in & go, wireless & go) on a mounted phone/tablet, given Android's background-activity-launch (BAL) limits?

- **Architect** — Add an always-on foreground `CarConnectionService` owning runtime-registered receivers. Manifest implicit-broadcast ban (API 26+) blocks POWER_CONNECTED/ACL_CONNECTED as *manifest* receivers, but **runtime-registered** ones still fire while the FGS lives. Boot receiver restarts it. ✅
- **Sentinel** — Never bypass the distraction gate; auto-launch only opens the dash (NAV/drive-mode), the per-display gate still governs app content. Power-connect alone is any-charger; require BT-MAC match for true car specificity. ✅
- **Scholar** — BAL on Android 12+ forbids silent background startActivity. The blessed path for connectivity events is a **full-screen-intent notification** (incoming-call class). Use that as primary; direct startActivity works when app is home/recently-foreground/overlay-granted. SYSTEM_ALERT_WINDOW is the surest exemption. ✅
- **Engineer** — Reuse existing `MainActivity.openNavIntent()` (already sets keep-screen-on + drive mode). Cache a config snapshot in the service (collect the DataStore flow) so receivers decide without blocking. `UiModeManager.enableCarMode()` for system car UX. Minimal new surface. ✅
- **Pathfinder** — Two real deployment shapes: (a) dedicated tablet dash — set Amni as HOME, power-connect = enough; (b) daily-driver Fold — BT-MAC match so it only triggers in the car. Ship both, default power=on / bluetooth=off-until-learned. ✅

**Verdict (5/5):** FGS + runtime receivers + full-screen-intent launch + reuse openNavIntent + config toggles with BT-learn. Unanimous.
