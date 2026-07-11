# Live capture — phone↔car AA session
Date: 2026-07-11 (device local ~18:01–18:11)
Device: SM-F966U (Fold) via wireless ADB 100.116.191.230:38073

## Summary
Phone is in **USB Android Open Accessory (AOA)** mode for **Android Auto**, actively projecting via Google Gearhead.

## Evidence

### USB (dumpsys usb)
- current_functions = **ACCESSORY**
- connected = true, configured = true
- current_accessory:
  - manufacturer = Android
  - model = **Android Auto**
  - description = Android
  - version = 1.0
  - uri = http://www.android.com/auto
  - serial = 0000000012345678
- IsDeviceConnected = true (phone is USB device / accessory, car is host)
- IsHostConnected = false
- USB powered = true

### AOA handshake sequence (kernel uevents)
1. USB_STATE CONNECTED / CONFIGURED
2. ACCESSORY=GETPROTOCOL
3. ACCESSORY=SENDSTRING (multiple — AOA identity strings)
4. ACCESSORY=START
5. re-enumerate CONFIGURED
6. Intent USB_ACCESSORY_HANDSHAKE

Observed multiple reconnect cycles: 16:03, 16:07, 16:09, 16:11 (local device time)

### Processes (gearhead = Android Auto)
- gearhead:projection (pid 3120) — PROJECTION process, Channel PROD
- gearhead:shared (pid 3163) — SharedService bound
- gearhead:car (pid 3334) — car process
- CAR.GH onCreate Process: PROJECTION Channel: PROD

### Amni-Drive
- Package replace events seen by projection process (installs)
- Amni is NOT the transport owner — Google gearhead owns USB accessory endpoints

## Implications for Amni Wire
To replace AA on this car we must:
1. Own or co-opt the AOA accessory session (today only gearhead does)
2. Speak post-AOA AA framing (TLS + multiplexed channels) that the Niro HU expects
3. Present video on the AA video sink channel
4. Accept input from HU input source channel

Live capture confirms wired AOA path is what this Niro uses (not wireless AA for this session).

## Update — Amni AOA probe (v1.3.1, same session)

`AoaProbeService` running on phone while car still in ACCESSORY mode:

```
poll: mfr=Android model=Android Auto ver=1.0 ... perm=false
USB_STATE connected=true configured=true accessory=true
```

**Finding:** Accessory is visible to Amni, but **permission is false** — Google gearhead holds exclusive open rights for the active session. Replacing AA requires either:
1. User grants Amni accessory permission (and gearhead not holding FD), or
2. Become the preferred accessory handler before gearhead claims it, or
3. Coordinate force-stop of gearhead for research (breaks stock AA until restart)

Loop continues toward M3 permission + open path without claiming M5 yet.
