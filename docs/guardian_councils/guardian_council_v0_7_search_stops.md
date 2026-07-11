# Guardian Council addendum — v0.7.0 search/stops/auto-locate + head-unit reality (2026-07-11)

- **Architect:** search + stops = state, not new screens: a stops list (dest = last) recalculated through one OSRM call; search panel lives on the map card. Camera auto-centers on first fix because follow defaults on — the bug was upstream (no fix ever arrived).
- **Sentinel:** head units are Play-Services-less and often offline — never fail silent. GMS-detect → LocationManager fallback; NO NETWORK chip when ConnectivityManager reports nothing so a grey map explains itself. Search/typing is a parked activity by nature (IME); gate logic untouched.
- **Scholar:** geocoding = Photon (komoot, typo-tolerant, location-biased, no key) with Nominatim fallback (proper UA per usage policy). OSRM handles via-waypoints natively with semicolon-separated coords.
- **Engineer:** LocationListener on API 26–29 must implement all four methods (AbstractMethodError trap); seed from getLastKnownLocation on both paths; tiles already disk-cache so once the unit sees WiFi/hotspot the map survives brief offline.
- **Pathfinder:** hotspot-from-phone is the realistic head-unit network story — cache + honest status make that workflow feel native.

Verdict 5–0: ship v0.7.0 as scoped; arm the flash watcher again.
