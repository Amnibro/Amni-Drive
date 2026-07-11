# AOA open success — 2026-07-11

## Event

Phone: SM-F966U · Amni-Drive probe · user selected **Always allow** for USB accessory.

```
18:24:47.041 PERMISSION granted=true mfr=Android model=Android Auto ... sn=0000000012345678
18:24:47.044 OPENED fd=113 ...
18:24:51.198 sniff end total=0 file=.../first_chunk.bin
18:24:52.792 USB_STATE connected=true configured=false accessory=true
18:24:52.794 OPENED fd=113 ...
18:24:52.993 DETACHED ...
18:24:55.808 USB_STATE connected=false ...
```

## Interpretation

1. **Permission path works** — `UsbManager.requestPermission` + Always allow → `hasPermission=true` → `openAccessory` returns a live FD.
2. **Sniff total=0** — head unit did not push bulk data before detach. For Android Auto, the **phone is normally the TLS server** after AOA; the HU often waits for the phone to start the session. Passive read alone may receive nothing.
3. **Quick DETACHED** — likely gearhead reclaimed, car re-enumerated, or cable/session reset after force-stop of gearhead.

## Next engineering steps

1. Hold the FD open longer; stop gearhead from auto-restarting during research window.
2. After OPEN, start **TLS server** on the accessory duplex stream (SSLEngine) and log ClientHello from HU.
3. Map first post-TLS frames to aasdk control channel protos.
4. Feed H.264 to video sink once control channel is up.

## Milestone

**M3 partial ✅** — open accessory with user grant.  
**M4 next** — speak on the wire (TLS + framing), not only listen.

## Loop note (15m scheduler)

When phone ADB offline: build/tests only; no install. Re-claim on next online cycle with Always-allow + `claim_aoa_research.ps1` if gearhead reclaims FD.
