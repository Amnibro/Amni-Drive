# Research 10 — Host identity + version probes (while HU silent)

_Date: 2026-07-12. Context: M3 OPEN works; HU totalRX=0 after aasdk-correct VersionResponse._

## Live facts
- Factory Kia AOA: model Android Auto, Always-allow open OK.
- Gearhead on phone: **17.1.662414-release** (modern AA).
- Amni TX (confirmed):
  - VersionRequest `00 03 00 06 00 01 00 01 00 06`
  - VersionResponse `00 03 00 08 00 02 00 01 00 06 00 00` (1.6) and multiprobe 1.1/1.5/1.7
- HU never sends VersionRequest / SSL_HANDSHAKE / raw ClientHello when Amni holds FD.

## Role model (aasdk / OAL)
| Side | USB | TLS | Version |
|------|-----|-----|---------|
| Head unit (aasdk) | host / AOA initiator | **SSL client** | sends VERSION_REQUEST first |
| Phone (Google gearhead) | accessory after AOA | **SSL server** | replies VERSION_RESPONSE |
| Amni Wire goal | same as phone | same as phone | same as phone |

OAL implements **HU role**. Amni implements **phone role**. Framing from aasdk is necessary but not sufficient on OEM HU.

## Why silence after OPEN is plausible
1. **Session already owned/poisoned** — gearhead or HU state machine expects Google host only.
2. **TLS identity** — real gearhead presents Google-issued (or Play-protected) server cert. Self-signed AndroidKeyStore (`CN=AmniDrive-AA-Research`) never gets a ClientHello if HU aborts before TLS.
3. **Version negotiation** — aasdk default was 1.1; we probed 1.6 primarily. Multiprobe 1.1–1.7 in 1.3.16.
4. **Wrong layer** — if HU only speaks after USB re-enum with gearhead’s accessory strings, Amni’s open of the *same* accessory may be “dead pipe” (0B both ways until reset).

## What open-source *does* publish
- aasdk embeds a **HU client** cert+key (`Google Automotive Link` / JVC Kenwood-era research material in `Cryptor.cpp`) for **head-unit SSL client**.
- That material is **not** the phone host server identity Google uses on the wire today.
- Using it as Amni’s *server* cert is unlikely to unlock Kia; useful only for OpenAuto/DHU-style HUs that expect that client side.

## Product forks
| Path | Effort | Factory Kia M5? |
|------|--------|-----------------|
| A. Continue phone-host RE (certs, passive gearhead first-bytes) | High | Maybe long-term |
| B. Soft AA (media / Play templates) | Medium | Partial UI only |
| C. Amni Link to Android HU / tablet | Lower wire risk | Full UI, not stock Mobis AA |
| D. OpenAuto/aasdk on aftermarket / Pi | Existing | Not factory Niro |

## Next experiments (phone present)
1. Multiprobe VersionResponse 1.1/1.5/1.6/1.7 (code).
2. TLS client-mode experiment log (rule out role invert) (code).
3. **Do not kill gearhead** — let classic AA establish, then note dumpsys only (no FD steal).
4. Long-term: extract gearhead TLS identity only via legal RE / published research (not shipping stolen keys).

## Not M5
No Amni video on factory dash until HU RX + video path.

## Dig 2026-07-12 — blocking bulk RX (v1.3.17)
**Hypothesis:** all prior `gotAny=false` may be false negatives. Android USB accessory bulk often does **not** report data via `InputStream.available()`; official ADK samples use **blocking** `read()` on a worker thread.

**Ship:** `AoaBulkIo` — dedicated reader thread always blocked on `fis.read(16KB)`; negotiate/TLS framed drain from queue. Phase A passive sniff uses same path.

**Identity note (unchanged):** even after seeing ClientHello, OEM HU still requires trusted phone **server** cert. aasdk public material is **HU client** cert only. Pre-TLS silence vs cert rejection are different failure modes — bulk fix targets pre-TLS first.

**Secondary RE notes:**
- Phone = TLS **server**, HU = TLS **client** with client cert (android-auto crate / aasdk Cryptor).
- XDA historical: AA validates head units cryptographically; reverse of phone host identity is the hard side.
- Soft paths remain parallel if factory host replace stays cert-gated.
