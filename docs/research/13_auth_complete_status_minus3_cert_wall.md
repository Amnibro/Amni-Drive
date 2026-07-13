# Research 13 — AuthComplete status=-3 (host identity wall)

_Live capture 2026-07-12 23:36 with Amni-Drive 1.5.6 on Kia Niro AOA._

## Wire facts

1. Version + TLS framed bulk **FINISHED** with software PKCS12 (`assets/amni_aa_host.p12`, self-signed CN research).
2. Immediately after FINISHED, HU TX plain AAP:
   - frame `00 03 00 0d 00 04 08 fd ff ff ff ff ff ff ff ff 01`
   - flags=`0x03` (BULK, **not** ENC)
   - msg=`0x0004` AuthComplete
   - body protobuf field1 varint = **int32 -3**
3. Amni TX AuthComplete OK + ServiceDiscovery**Request** (phone role).
4. HU TX short ENC `00 0b 00 02 00 ff` → msg `0xff`, no ServiceDiscoveryResponse, no ChannelOpen.

## Interpretation

- aasdk `AuthCompleteIndication.status` is `Status.Enum` (OK=0, FAIL=1). Live HU uses a richer int32; **-3 = application-layer host auth reject** after TLS crypto succeeded.
- TLS accept ≠ identity accept. Factory HU checks phone-side host certificate / Google projection identity at AuthComplete.
- Wrong SD role (1.5.3–1.5.5 Response-as-phone) was a real bug; fixed in 1.5.6. Live still dies at AuthComplete status before SD can complete.

## What will not fix this alone

- More SD name variants
- Multi-path encrypt (ssl-msg / enc-frame / tunnel)
- Soft USB bounce

## Paths forward (personal research)

| Path | Notes |
|------|--------|
| A. RIDE | Gearhead owns AOA/TLS/identity; Amni paints full UI on AA Surface |
| B. OWN + trusted host material | Requires legitimate AA host credentials (not inventable; do not ship stolen keys) |
| C. Lab HU / openauto | Open head-unit stacks that skip Google identity for development |
| D. Continue wire polish | Plain-first control (HU uses 0x03), SD Request, ChannelOpen — ready if identity ever OK |

## Not M5

Dash video on factory glass with OWN stack still blocked by AuthComplete -3 until identity is solved or RIDE surface path lands video.
