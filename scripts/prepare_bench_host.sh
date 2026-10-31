#!/usr/bin/env bash
# Prepares a dedicated Linux machine to run the GateBridge benchmark.
# Run once per boot, as root. Nothing here is persistent, a reboot restores the defaults.
#
#   sudo scripts/prepare_bench_host.sh
#   sudo BENCH_IMAGE=ghcr.io/gatebridge/gatebridge-bench:<sha> \
#        LOADGEN_IMAGE=ghcr.io/gatebridge/gatebridge-loadgen:<sha> scripts/prepare_bench_host.sh
set -euo pipefail

[ "$(id -u)" -eq 0 ] || { echo "Run as root (sudo)."; exit 1; }
cd "$(dirname "${BASH_SOURCE[0]}")/.."

warn() { echo "WARNING: $*"; }

echo "== Architecture"
[ "$(uname -m)" = "x86_64" ] || warn "$(uname -m) detected, the wrk2 loadgen image is x86_64 only."

echo "== Docker"
command -v docker >/dev/null || { echo "Docker is not installed: https://docs.docker.com/engine/install/"; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "Docker Compose v2 plugin is missing."; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker daemon is not running."; exit 1; }

echo "== CPU frequency governor -> performance"
for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
  [ -w "$g" ] && echo performance > "$g" || true
done
echo "   now: $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null || echo 'n/a (VM or no cpufreq)')"

echo "== Turbo boost (variable clocks add noise; disable for repeatable runs)"
if [ -w /sys/devices/system/cpu/intel_pstate/no_turbo ]; then
  echo 1 > /sys/devices/system/cpu/intel_pstate/no_turbo && echo "   intel turbo disabled"
elif [ -w /sys/devices/system/cpu/cpufreq/boost ]; then
  echo 0 > /sys/devices/system/cpu/cpufreq/boost && echo "   boost disabled"
else
  warn "could not control turbo, leave it as is and note it in your results."
fi

echo "== Swap"
if [ -n "$(swapon --show --noheadings 2>/dev/null)" ]; then
  swapoff -a && echo "   swap disabled until reboot"
fi

echo "== Kernel network settings"
sysctl -qw net.core.somaxconn=65535 \
           net.ipv4.ip_local_port_range="1024 65535" \
           net.ipv4.tcp_max_syn_backlog=65535 \
           net.core.netdev_max_backlog=65535 || warn "sysctl failed"

echo "== Core layout"
cores=$(nproc)
echo "   $cores cores. Defaults need 8: upstream 0-1, gateway 2-5, loadgen 6-7."
[ "$cores" -ge 8 ] || warn "fewer than 8 cores, set CPUSET_UP, CPUSET_GW, CPUSET_LG (disjoint sets)."

echo "== Noisy neighbours"
busy=$(ps -eo pcpu --no-headers | awk '{s+=$1} END {printf "%d", s}')
[ "$busy" -lt 30 ] || warn "host is already using ~${busy}% of one core in total, stop other workloads."

echo "== Images"
if [ -n "${BENCH_IMAGE:-}" ] && [ -n "${LOADGEN_IMAGE:-}" ]; then
  docker pull "$BENCH_IMAGE"
  docker pull "$LOADGEN_IMAGE"
  echo "   export BENCH_IMAGE=$BENCH_IMAGE LOADGEN_IMAGE=$LOADGEN_IMAGE"
else
  echo "   BENCH_IMAGE / LOADGEN_IMAGE not set, run with BENCH_BUILD=1 to build from this checkout."
fi

mkdir -p results
echo "== Ready. Next: scripts/bench_docker.sh"
