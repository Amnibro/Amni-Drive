# Loop capture — AAP TX + TLS silence + VD permission (2026-07-11 ~22:58)

## State
- USB: **ACCESSORY** / Android Auto / CONFIGURED / USB powered
- Amni **1.3.12** then upgrade path to **1.3.13**
- gearhead processes alive; open sometimes with gearhead=false

## Probe (material)
```
OPENED fd=123 gearhead=false
phase A total=0
AAP TX unsolicited VersionResponse 16B (gotReq=false gotAny=false) major=1 minor=6
AAP negotiate result=false
TLS begin NEED_UNWRAP → timeout
TLS result ok=false aap=false
proj start failed: Requires ADD_MIRROR_DISPLAY / CAPTURE_VIDEO_OUTPUT ... use VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
session end totalRX=0 tls=false
```

## Conclusions
1. **M3 open OK** repeatedly.
2. **M4:** phone sends AAP VersionResponse; HU sends **0 bytes** (no VersionRequest, no ClientHello). Framing/identity still wrong or HU ignores non-Google host after open.
3. **Projection:** `VIRTUAL_DISPLAY_FLAG_PRESENTATION` alone treated as screen-share → permission denial. Fix: restore **OWN_CONTENT_ONLY**.
4. After session, re-arm sticky free-poll.

## Not M5
No Amni video on factory HU.
