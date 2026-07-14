# Checklist — Voice destination search (v1.10.0)

- [x] Guardian council 5-0 → docs/guardian_councils/guardian_council_voice_search.md
- [x] Backups → backups/*.v1.10.0.bak
- [x] NEW voice/VoiceSearch.kt — SpeechRecognizer wrapper (availability gate, partials, teardown on error/result)
- [x] Manifest — RECORD_AUDIO
- [x] Dashboard — startVoice/onVoice (lazy runtime permission via rememberLauncherForActivityResult), 🎤 status chip bottom-center, geocode top hit → NAV + onGo + spoken "Routing to X"
- [x] MapCard — 🎤 VOICE MapBtn under FIND (phone + glass), HomeTiles threading
- [x] version 1.10.0 (81)
- [x] tests + lint + assembleRelease
- [x] installed on Fold (v1.10.0 verified)
- [ ] device verify: tap 🎤, speak a place, confirm route + spoken confirm (needs Anthony's voice)
- [x] changelog + architecture_map
