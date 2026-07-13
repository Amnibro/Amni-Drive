# Checklist — DHU auto-test + Kia comm review + AA-replacement loop (v1)
Date: 2026-07-13 · Session: /loop dynamic

- [x] Review Grok findings (research 13/14) vs current code — Kia error-16 RESOLVED (see below)
- [x] Verify on-device state: release 1.6.19 (versionCode 69, not-debuggable), ride mode default, AOA opt-in (MainActivity claim only on EXTRA_START_AOA/ACTION_OWN_STACK; AoaProbeService.tryOpen refuses in RIDE)
- [x] Run scripts/dhu_ride_verify.ps1 — exit 4 dark screen EXPECTED: phone was in a LIVE Kia AA session (USB current_functions=ACCESSORY, gearhead:projection TOP); DHU + real car compete — killed DHU
- [x] LIVE KIA EVIDENCE 07-13 15:5x: gearhead TemplateNavigationService bound to com.amni.drive/.aa.AmniCarAppService (client=gearhead:projection); Amni.AaWatch "mode=ride · passive · no AOA claim" every 8s; ZERO communication-error/CriticalError lines in full logcat buffer; Spotify streaming to car normally. Comm issues resolved in RIDE mode on the real car.
- [x] Armed persistent logcat monitor (Amni.AaSurface grants/taps + error signatures) — catches the moment the dash renders Amni
- [ ] Deeper DHU interaction pass (dhu_click_cap.ps1 flows: drawer, search, route, gate, theme)
- [ ] Identify next full-UI gaps toward AA replacement; implement smallest increment
- [ ] Tests + lint green, release build, flash Fold
- [ ] Update architecture_map.md + changelog.md
