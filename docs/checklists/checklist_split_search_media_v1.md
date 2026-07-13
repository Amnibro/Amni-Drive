# Checklist — in-app search + media split (v1.6.9)

Goal: Search and Spotify/media stay split with map inside Amni Drive surface; no full-screen AA SearchTemplate or music Activity.

- [x] Backup AaNavState, AaHomeScreen, Dashboard, MapCard
- [x] Aa Search action → open in-map SearchPanel (no Screen push)
- [x] MapCard observes search pulse; wider panel when dense
- [x] MediaCard: session/key controls, no LAUNCH MUSIC startActivity on car
- [x] showMedia on car dense split layout
- [x] Menu Media no full-screen launch on carSurface
- [x] Build install 1.6.9
- [x] changelog + architecture_map
- [ ] User/DHU confirm split behavior
