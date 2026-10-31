#!/usr/bin/env bash
# Reproducible GateBridge benchmark: open loop, constant rate, baseline vs gateway.
#
# Usage (from the repository root):
#   scripts/bench_docker.sh
#   RATES="5000 10000" REPS=3 DURATION=30 scripts/bench_docker.sh
#
# Defaults take roughly 55 minutes (2 targets x 5 rates x 5 reps x ~65 s).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

RATES="${RATES:-2000 5000 10000 20000 30000}"   # requests per second, total across connections
REPS="${REPS:-5}"                               # independent repetitions per point
DURATION="${DURATION:-60}"                      # seconds measured per run
WARMUP="${WARMUP:-90}"                          # seconds of JIT warm-up through the gateway
CONNS="${CONNS:-256}"
THREADS="${THREADS:-2}"
PAUSE="${PAUSE:-5}"                             # seconds between runs

COMPOSE="docker compose -f docker-compose.bench.yml"
OUT="results/$(date +%Y%m%d-%H%M%S)"
RAW="$OUT/raw"
mkdir -p "$RAW"

URL_DIRECT="http://upstream:3001/hello"
URL_GATEWAY="http://gateway:4001/proxy/hello"

wrk() { $COMPOSE exec -T loadgen wrk "$@"; }

echo "== Starting containers"
if [ "${BENCH_BUILD:-0}" = "1" ]; then
  $COMPOSE up -d --build            # build from the local checkout
else
  $COMPOSE pull                     # use BENCH_IMAGE / LOADGEN_IMAGE from the registry
  $COMPOSE up -d --no-build
fi

echo "== Waiting for readiness"
for url in "$URL_DIRECT" "$URL_GATEWAY"; do
  ok=0
  for _ in $(seq 1 60); do
    code=$($COMPOSE exec -T loadgen curl -s -o /dev/null -w '%{http_code}' "$url" || true)
    if [ "$code" = "200" ]; then ok=1; break; fi
    sleep 1
  done
  [ "$ok" = 1 ] || { echo "FAILED: $url did not return 200"; $COMPOSE logs gateway upstream | tail -40; exit 1; }
done

# Both paths must return the same body size, otherwise the comparison is unfair.
size_direct=$($COMPOSE exec -T loadgen curl -s "$URL_DIRECT" | wc -c)
size_gateway=$($COMPOSE exec -T loadgen curl -s "$URL_GATEWAY" | wc -c)
echo "   body bytes: direct=$size_direct gateway=$size_gateway"
[ "$size_direct" = "$size_gateway" ] || echo "   WARNING: body sizes differ"

echo "== Recording environment"
{
  echo "date: $(date -Is)"
  echo "kernel: $(uname -srvm)"
  echo "cpu: $(grep -m1 'model name' /proc/cpuinfo 2>/dev/null || true)"
  echo "cores: $(nproc)"
  echo "governor: $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null || echo n/a)"
  echo "docker: $(docker --version)"
  echo "git: $(git rev-parse HEAD 2>/dev/null || echo n/a) dirty=$(git status --porcelain 2>/dev/null | wc -l)"
  echo "jvm: $($COMPOSE exec -T gateway java -version 2>&1 | head -1)"
  echo "images: $(docker image inspect --format '{{.RepoTags}} {{index .RepoDigests 0}}' $($COMPOSE images -q 2>/dev/null | sort -u) 2>/dev/null | tr '\n' ' ')"
  echo "params: RATES='$RATES' REPS=$REPS DURATION=$DURATION WARMUP=$WARMUP CONNS=$CONNS THREADS=$THREADS"
  echo "--- compose config"
  $COMPOSE config
} > "$OUT/env.txt"

echo "== Warm-up (not recorded): ${WARMUP}s through the gateway, 20s direct"
mid_rate=$(echo "$RATES" | awk '{print $(int((NF+1)/2))}')
wrk -t"$THREADS" -c"$CONNS" -d"${WARMUP}s" -R"$mid_rate" "$URL_GATEWAY" > /dev/null
wrk -t"$THREADS" -c"$CONNS" -d20s -R"$mid_rate" "$URL_DIRECT" > /dev/null
sleep "$PAUSE"

# Parse one wrk2 output into a CSV row: target,rate,rep,p50,p99,p999,max,rps,errors   (latencies in ms)
parse() {
  awk -v t="$2" -v r="$3" -v rep="$4" '
    function ms(s,  v, u) {
      v = s; sub(/[a-z]+$/, "", v)
      u = s; sub(/^[0-9.]+/, "", u)
      if (u == "us") return v / 1000
      if (u == "ms") return v + 0
      if (u == "s")  return v * 1000
      if (u == "m")  return v * 60000
      return v + 0
    }
    /^ *[0-9.]+%/ { p = $1; sub(/%/, "", p); L[p + 0] = ms($2) }
    /Requests\/sec:/ { rps = $2 }
    /Non-2xx or 3xx responses:/ { bad += $NF }
    /Socket errors:/ { line = $0; gsub(/[^0-9 ]/, " ", line); n = split(line, a, " "); for (i = 1; i <= n; i++) bad += a[i] }
    END { printf "%s,%s,%s,%.3f,%.3f,%.3f,%.3f,%.2f,%d\n", t, r, rep, L[50], L[99], L[99.9], L[100], rps, bad }
  ' "$1"
}

echo "target,rate,rep,p50_ms,p99_ms,p999_ms,max_ms,rps,errors" > "$OUT/results.csv"

echo "== Measuring (interleaved: target order alternates every repetition)"
for rep in $(seq 1 "$REPS"); do
  if [ $((rep % 2)) -eq 1 ]; then order="direct gateway"; else order="gateway direct"; fi
  for rate in $RATES; do
    for target in $order; do
      url="$URL_DIRECT"; [ "$target" = gateway ] && url="$URL_GATEWAY"
      f="$RAW/${target}_r${rate}_rep${rep}.txt"
      echo "   rep=$rep target=$target rate=$rate"
      wrk -t"$THREADS" -c"$CONNS" -d"${DURATION}s" -R"$rate" --latency "$url" > "$f" || true
      parse "$f" "$target" "$rate" "$rep" >> "$OUT/results.csv"
      sleep "$PAUSE"
    done
  done
done

cp results/gc-*.log "$OUT/" 2>/dev/null || true
$COMPOSE stats --no-stream > "$OUT/container-stats.txt" 2>/dev/null || true

echo "== Summary"
python3 - "$OUT/results.csv" <<'PY' | tee "$OUT/summary.txt"
import csv, statistics as st, sys
from collections import defaultdict

rows = list(csv.DictReader(open(sys.argv[1])))
g = defaultdict(list)
for r in rows:
    g[(r["target"], int(r["rate"]))].append(r)

def med(rs, k): return st.median(float(x[k]) for x in rs)
def spread(rs, k):
    v = [float(x[k]) for x in rs]
    return min(v), max(v)

print(f"{'target':8} {'rate':>6} {'rps':>8} {'p50':>7} {'p99':>8} {'p99.9':>8} {'max':>8} {'p99 min..max':>16} {'err':>5} ok")
for (t, rate) in sorted(g, key=lambda k: (k[1], k[0])):
    rs = g[(t, rate)]
    errs = sum(int(x["errors"]) for x in rs)
    rps = med(rs, "rps")
    lo, hi = spread(rs, "p99_ms")
    ok = "yes" if errs == 0 and rps >= 0.99 * rate else "NO"
    print(f"{t:8} {rate:>6} {rps:>8.0f} {med(rs,'p50_ms'):>7.2f} {med(rs,'p99_ms'):>8.2f} "
          f"{med(rs,'p999_ms'):>8.2f} {med(rs,'max_ms'):>8.1f} {lo:>7.2f}..{hi:<7.2f} {errs:>5} {ok}")

print("\nGateway overhead (median gateway minus median direct, ms)")
print(f"{'rate':>6} {'d p50':>8} {'d p99':>8} {'d p99.9':>8}")
for rate in sorted({k[1] for k in g}):
    if ("gateway", rate) in g and ("direct", rate) in g:
        a, b = g[("gateway", rate)], g[("direct", rate)]
        print(f"{rate:>6} {med(a,'p50_ms')-med(b,'p50_ms'):>8.2f} "
              f"{med(a,'p99_ms')-med(b,'p99_ms'):>8.2f} {med(a,'p999_ms')-med(b,'p999_ms'):>8.2f}")
PY

echo "Results in $OUT"
