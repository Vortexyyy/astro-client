# Astro benchmark

Benchmark mode is built into the mod but off unless `ASTRO_BENCH` is set. Run it with:

```bash
bash scripts/bench.sh <label>            # none, default and all HUD setups
bash scripts/bench.sh <label> all        # just one setup
```

Each run starts the 1.21.11 dev client and loads `run/1.21.11/saves/astro-bench` (created on the first run).
Fixed settings: vsync off, unlimited FPS, render distance 12, 854x480 window.
It warms up for 20 s, then records 30 s.
Results are written to `run/1.21.11/bench/<label>-<hud>.json`, and GPU use goes in `.gpu.csv` alongside them.
Close any other dev clients first: a second client locks the test world and skews the numbers.

HUD setups:
- `none`: no HUD modules.
- `default`: FPS, CPS and Keystrokes.
- `all`: every HUD module.

## Results (2026-11-13, RTX 5070 Ti / Ryzen 7 9850X3D, 1.21.11)

Each row is one back-to-back pass on the settled test world.
Compare each HUD setup with the `none` run of the same pass: whole-game FPS drifts between passes.

| Build | HUD | Avg FPS | vs none | 1% low | HUD ms/frame | CPU % | Heap peak / steady MB |
|---|---|---|---|---|---|---|---|
| 0.3.3 | none | 1508 | — | 67 | 0.000 | 8.6 | 713 / 345 |
| 0.3.3 | default | 1386 | −8% | 65 | 0.180 | 10.4 | 707 / 345 |
| 0.3.3 | all | 1188 | −21% | 57 | 0.319 | 10.5 | 708 / 343 |
| 0.4.0 | none | 1932 | — | 95 | 0.000 | 10.4 | 719 / 341 |
| 0.4.0 | default | 1945 | +1% (noise) | 92 | 0.012 | 9.0 | 719 / 341 |
| 0.4.0 | all | 1936 | 0% (noise) | 93 | 0.018 | 9.6 | 738 / 341 |

At these frame rates whole-game FPS can swing by 15–25% between runs (camera direction, chunk state,
GPU clocks, a minimised window drops to 10 FPS). Trust `HUD ms/frame` for Astro's own cost, and repeat
runs before trusting a small FPS difference.
