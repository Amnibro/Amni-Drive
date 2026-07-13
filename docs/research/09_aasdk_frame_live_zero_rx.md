# Live test — aasdk VersionResponse TX, still 0 RX (2026-07-11 ~23:13)

## Setup
- Amni **1.3.14** (v27) on SM-F966U
- USB `ACCESSORY` / Android Auto / CONFIGURED
- gearhead killed for claim; open with `gearhead=false`

## Probe (material)
```
OPENED fd=146
phase A total=0
AAP TX unsolicited VersionResponse aasdk-frame 12B
  hex=00 03 00 08 00 02 00 01 00 06 00 00
  (ch=0, flags=BULK=3, size=8, msg=VERSION_RESPONSE=2, major=1, minor=6, status=0)
AAP negotiate result=false (gotAny=false)
TLS framed begin NEED_UNWRAP → timeout (no SSL_HANDSHAKE bodies)
TLS raw fallback → timeout
totalRX=0
proj VirtualDisplay + FULL UI on default display (launchDisplayId denied)
```

## Decode of TX frame (aasdk layout confirmed)
| Bytes | Meaning |
|-------|---------|
| `00` | channel CONTROL |
| `03` | FRAME_BULK \| PLAIN \| SPECIFIC |
| `00 08` | payload size 8 BE |
| `00 02` | MessageId VERSION_RESPONSE |
| `00 01` | major 1 |
| `00 06` | minor 6 |
| `00 00` | status OK |

## Conclusion
- Framing matches openDsh aasdk messenger layout.
- Factory HU still sends **nothing** after open (no VersionRequest, no SSL_HANDSHAKE).
- Possible: HU only talks if phone is Google-signed host; or HU expects VersionRequest first from phone (invert roles); or session already dead after gearhead kill.
- Next: try phone-initiated VersionRequest first; capture passive sniff while **gearhead** owns session (mirror RX); cert identity research.

## Not M5
