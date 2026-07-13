# Guardian Council — split search + media

## Proposals
| Guardian | Approach |
|----------|----------|
| Architect | Keep NavigationTemplate; search is Compose overlay; media is Dashboard column |
| Sentinel | No USB/OWN; no new dangerous perms; media keys not full Spotify activity |
| Scholar | SearchTemplate always full-host chrome — wrong for RIDE surface. AudioManager media keys control active session |
| Engineer | AaNavState.searchPulse + MediaCard transport; showMedia on carSurface |
| Pathfinder | DHU re-open Amni after install; tap Search in strip should open map panel |

## Vote
Majority: Engineer + Scholar. **Decision:** in-surface search pulse + media key panel, no full-screen launches from car UI.
