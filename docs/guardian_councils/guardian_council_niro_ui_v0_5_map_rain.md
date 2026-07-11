# Guardian Council addendum — v0.5 map + rain (2026-07-10)

Scope: Anthony wants the map and the rain background pushed further.

- **Architect:** stop drawing fake streets — composite REAL basemap tiles (CARTO dark_all / light_all, OSM data) under our SVG route/puck/pin overlays. Mockup honesty improves too: this is exactly what the app will render (MapLibre + CARTO/OSM style JSON), so the mock equals the product.
- **Sentinel:** tiles are static local files in the mock (no network at render); in-app, MapLibre caches offline. Attribution "© OSM · CARTO" rendered on-map (license). Route overlay keeps accent color with strong casing so it never vanishes into the basemap.
- **Scholar:** rain realism for a static frame = distribution variance: 80+ streaks with varied length/opacity/width at ~20° from vertical, plus on-glass droplets (windshield metaphor drivers instantly read). Uniform repeating gradients read as hatching — replace them.
- **Engineer:** deterministic seeded LCG generates the streak/droplet SVGs (reproducible, tweakable); tiles fetched once with proper UA; pixel math z15 @2x, 6×4 grid, offsets computed from tile fractions; route coordinates read off a probe render with a coordinate grid overlay.
- **Pathfinder:** real Austin under the glass is a demo-day kill shot — "that's my actual drive home."

Verdict 5–0: real tiles + generated rain; keep everything else from v0.4.
