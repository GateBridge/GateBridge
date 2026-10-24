#!/usr/bin/env bash
# Runs inside the image:  docker run ... <image> bench
# Open loop (constant rate) load with wrk2 against GATEWAY_URL, and optionally DIRECT_URL as baseline.
#
#   GATEWAY_URL  required, e.g. http://10.0.0.5:4001/proxy/hello
#   DIRECT_URL   optional, e.g. http://10.0.0.5:3001/hello (enables overhead computation)
#   RATES="2000 5000 10000 20000 30000"  REPS=5  DURATION=60  WARMUP=90  CONNS=256  THREADS=2  PAUSE=5
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:?set GATEWAY_URL, e.g. http://10.0.0.5:4001/proxy/hello}"
DIRECT_URL="${DIRECT_URL:-}"
RATES="${RATES:-2000 5000 10000 20000 30000}"
REPS="${REPS:-5}"
DURATION="${DURATION:-60}"
WARMUP="${WARMUP:-90}"
CONNS="${CONNS:-256}"
THREADS="${THREADS:-2}"
PAUSE="${PAUSE:-5}"
RESULTS_DIR="${RESULTS_DIR:-/results}"

OUT="$RESULTS_DIR/$(date +%Y%m%d-%H%M%S)"
RAW="$OUT/raw"
mkdir -p "$RAW" 2>/dev/null || { echo "Cannot write to $RESULTS_DIR. Mount a folder: -v \"\$PWD/results:/results\""; exit 1; }

wait_ready() {
  for _ in $(seq 1 60); do
    [ "$(curl -s -o /dev/null -w '%{http_code}' "$1" || true)" = "200" ] && return 0
    sleep 1
  done
  echo "FAILED: $1 did not return 200. Check that the containers are up and the port is reachable (firewall)."
  exit 1
}

rtt() {
  for _ in $(seq 1 20); do curl -s -o /dev/null -w '%{time_connect}\n' "$1" || true; done | sort -n |
    awk '{a[NR]=$1} END {printf "min=%.3fms median=%.3fms max=%.3fms", a[1]*1000, a[int((NR+1)/2)]*1000, a[NR]*1000}'
}

echo "== Checking targets"
wait_ready "$GATEWAY_URL"
[ -z "$DIRECT_URL" ] || wait_ready "$DIRECT_URL"
if [ -n "$DIRECT_URL" ]; then
  sg=$(curl -s "$GATEWAY_URL" | wc -c); sd=$(curl -s "$DIRECT_URL" | wc -c)
  echo "   body bytes: gateway=$sg direct=$sd"
  [ "$sg" = "$sd" ] || echo "   WARNING: body sizes differ, the comparison is not fair"
fi

{
  echo "date: $(date -Is)"
  echo "image commit: $(cat /app/VERSION 2>/dev/null || echo n/a)"
  echo "loadgen kernel: $(uname -srvm)"
  echo "loadgen cpu: $(grep -m1 'model name' /proc/cpuinfo 2>/dev/null || true)"
  echo "loadgen cpus visible: $(nproc) cpuset: $(cat /sys/fs/cgroup/cpuset.cpus.effective 2>/dev/null || echo n/a)"
  echo "gateway url: $GATEWAY_URL"
  echo "direct url: ${DIRECT_URL:-none}"
  echo "tcp connect gateway: $(rtt "$GATEWAY_URL")"
  [ -z "$DIRECT_URL" ] || echo "tcp connect direct: $(rtt "$DIRECT_URL")"
  echo "params: RATES='$RATES' REPS=$REPS DURATION=$DURATION WARMUP=$WARMUP CONNS=$CONNS THREADS=$THREADS PAUSE=$PAUSE"
  echo "note: also record the gateway image tag, the SUT cpuset and the SUT hardware"
} > "$OUT/env.txt"

mid_rate=$(echo "$RATES" | awk '{print $(int((NF+1)/2))}')
echo "== Warm-up (not recorded): ${WARMUP}s through the gateway"
wrk -t"$THREADS" -c"$CONNS" -d"${WARMUP}s" -R"$mid_rate" "$GATEWAY_URL" > /dev/null || true
if [ -n "$DIRECT_URL" ]; then
  wrk -t"$THREADS" -c"$CONNS" -d20s -R"$mid_rate" "$DIRECT_URL" > /dev/null || true
fi
sleep "$PAUSE"

# wrk2 output -> CSV row: target,rate,rep,p50,p99,p999,max,rps,errors   (latencies in ms)
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

echo "== Measuring (interleaved, target order alternates every repetition)"
for rep in $(seq 1 "$REPS"); do
  if [ -z "$DIRECT_URL" ]; then order="gateway"
  elif [ $((rep % 2)) -eq 1 ]; then order="direct gateway"
  else order="gateway direct"; fi
  for rate in $RATES; do
    for target in $order; do
      url="$GATEWAY_URL"; [ "$target" = direct ] && url="$DIRECT_URL"
      f="$RAW/${target}_r${rate}_rep${rep}.txt"
      echo "   rep=$rep target=$target rate=$rate"
      wrk -t"$THREADS" -c"$CONNS" -d"${DURATION}s" -R"$rate" --latency "$url" > "$f" || true
      parse "$f" "$target" "$rate" "$rep" >> "$OUT/results.csv"
      sleep "$PAUSE"
    done
  done
done

echo "== Summary"
python3 ${SUMMARIZE:-/app/bin/summarize.py} "$OUT/results.csv" | tee "$OUT/summary.txt"
echo "Results saved in the mounted results folder: $(basename "$OUT")/"
