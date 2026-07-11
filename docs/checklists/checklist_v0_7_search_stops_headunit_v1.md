# Checklist — v0.7.0 search/stops/auto-locate + head-unit fixes (v1, 2026-07-11)

Anthony: "need the map to be able to search and find locations, add stops, calculate routes, etc and auto locate to device location. It also doesn't display on my head unit right now."

Diagnosis from the overnight flash log: install+launch SUCCEEDED but device network stats showed "13ms not connected" → no internet = no tiles = grey map. Aftermarket head units also typically lack Google Play Services → FusedLocation never fires → no auto-locate.

- [x] Council addendum (guardian_council_v0_7_search_stops.md)
- [x] Backups → backups/*.v0.7.0.bak
- [x] LocationTracker: GMS-detect → FusedLocation, else android.location.LocationManager (GPS+NETWORK) fallback; seed from getLastKnownLocation both paths
- [x] Geocoder.kt: Photon (komoot) search biased to device location, Nominatim fallback
- [x] OsrmRouting: multi-waypoint route(List<RoutePoint>)
- [x] Search UI in MapCard: search pill → panel (query field + results with GO / +STOP)
- [x] Stops: numbered pins on map, long-press = destination (or add stop when routing), END clears
- [x] Auto-locate: camera centers on first fix (follow default), works without Play Services
- [x] NO NETWORK chip on map card (ConnectivityManager) so a blank map explains itself
- [x] Version 0.7.0 / versionCode 3
- [x] compileDebugKotlin (clean) · 21/21 tests · lint pass · 15.6 MB APK + tests + lint + assembleDebug
- [x] architecture_map + changelog updated (backups first)
- [x] Arm adb watcher (bszbt2fdm) + APK sent → auto-install on next connect; send APK to Anthony
- [ ] NOT COMPLETE until Anthony confirms on the head unit (rule 8)
