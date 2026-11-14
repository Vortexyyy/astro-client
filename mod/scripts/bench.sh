#!/usr/bin/env bash
# Astro benchmark: runs the 1.21.11 dev client in benchmark mode once per HUD setup and prints a table.
# usage: scripts/bench.sh <label> [none default all]
# Each run: 20 s warm-up in run/1.21.11/saves/astro-bench, 30 s recorded. Results: run/1.21.11/bench/<label>-<hud>.json
set -u
cd "$(dirname "$0")/.."
LABEL="${1:-run}"; shift || true
HUDS=("${@:-none default all}"); [ $# -eq 0 ] && HUDS=(none default all)
OUT=run/1.21.11/bench; mkdir -p "$OUT"
for HUD in "${HUDS[@]}"; do
  NAME="$LABEL-$HUD"
  ( nvidia-smi --query-gpu=utilization.gpu,memory.used --format=csv,noheader,nounits -lms 1000 > "$OUT/$NAME.gpu.csv" 2>/dev/null ) &
  GPU=$!
  ASTRO_BENCH="$NAME" ASTRO_BENCH_HUD="$HUD" ./gradlew :mc1_21_11:runClient -q > "$OUT/$NAME.log" 2>&1
  kill $GPU 2>/dev/null
  [ -f "$OUT/$NAME.json" ] || echo "$NAME: no result, see $OUT/$NAME.log"
done
printf '%-8s %8s %8s %9s %9s %7s %8s %8s %7s\n' HUD avgFPS 1%low frameMs hudMs cpu% heapPk heapSt gpu%
for HUD in "${HUDS[@]}"; do
  f="$OUT/$LABEL-$HUD.json"; [ -f "$f" ] || continue
  gpu=$(awk -F, '{u+=$1;n++} END{if(n)printf "%.0f",u/n}' "$OUT/$LABEL-$HUD.gpu.csv" 2>/dev/null)
  sed -E 's/.*"avgFps":([0-9.]+),"low1Fps":([0-9.]+),"avgFrameMs":([0-9.]+),"p99FrameMs":[0-9.]+,"hudMsPerFrame":([0-9.]+),"cpuProcessPct":([0-9.-]+),"heapPeakMb":([0-9.]+),"heapSteadyMb":([0-9.]+).*/\1 \2 \3 \4 \5 \6 \7/' "$f" |
    awk -v h="$HUD" -v g="${gpu:--}" '{printf "%-8s %8s %8s %9s %9s %7s %8s %8s %7s\n",h,$1,$2,$3,$4,$5,$6,$7,g}'
done
