# Guardian Council — Voice destination search (v1.10.0)

Context: last big AA staple missing. AA host owns the car mic on the projection path, but our process runs ON THE PHONE — SpeechRecognizer uses the phone mic regardless of which display paints, which is exactly right for mounted-phone HU (owned-glass track) and workable in-cabin for RIDE.

- **Architect:** thin `voice/VoiceSearch` object wrapping SpeechRecognizer (create → listen → destroy per session); UI logic stays in Dashboard where VoiceGuide + onGo already live. Vote: object wrapper + Dashboard orchestration.
- **Sentinel:** RECORD_AUDIO is runtime-dangerous — request lazily on first mic tap via rememberLauncherForActivityResult, never at startup; recognizer destroyed on error/result (no hot mic). Vote: lazy permission + hard teardown.
- **Scholar:** SpeechRecognizer.isRecognitionAvailable gate; FREE_FORM model + partial results for live feedback; errors map to a transient chip, never a dialog on glass. Vote: availability gate + partials.
- **Engineer:** one new file + mic MapBtn + a status chip; top geocoder hit routes immediately with spoken confirm — no result-picker v1 (retap to retry). Vote: minimal loop.
- **Pathfinder:** next stones: wake-word later, result disambiguation later; ship the tap-mic→speak→route loop now. Vote: ship.

**Verdict 5–0:** tap 🎤 → listen with live partials chip → geocode top hit → route + spoken "Routing to X"; lazy RECORD_AUDIO; recognizer destroyed after each session.
