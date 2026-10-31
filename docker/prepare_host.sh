#!/usr/bin/env bash
# Prepares a Linux machine for the GateBridge benchmark. Standalone, needs only Docker.
# Run as root, once per boot. Nothing is persistent, a reboot restores the defaults.
#
#   sudo BENCH_IMAGE=<dockerhub user>/gatebridge-bench:<tag> ./prepare_host.sh sut       # machine A
#   sudo BENCH_IMAGE=<dockerhub user>/gatebridge-bench:<tag> ./prepare_host.sh loadgen   # machine B
set -euo pipefail

role="${1:-}"
case "$role" in sut|loadgen) ;; *) echo "Usage: sudo [BENCH_IMAGE=...] $0 sut|loadgen"; exit 1 ;; esac
[ "$(id -u)" -eq 0 ] || { echo "Run as root (sudo)."; exit 1; }

warn() { echo "WARNING: $*"; }

echo "== Architecture and Docker"
[ "$(uname -m)" = "x86_64" ] || warn "$(uname -m) detected, the wrk2 inside the image is x86_64 only."
command -v docker >/dev/null || { echo "Docker is not installed: https://docs.docker.com/engine/install/"; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker daemon is not running."; exit 1; }
if [ "$role" = sut ]; then
  docker compose version >/dev/null 2>&1 || { echo "Docker Compose v2 plugin is missing."; exit 1; }
fi

echo "== CPU governor -> performance"
for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
  [ -w "$g" ] && echo performance > "$g" || true
done
echo "   now: $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null || echo 'n/a (VM or no cpufreq)')"

echo "== Turbo boost off (variable clocks add noise)"
if [ -w /sys/devices/system/cpu/intel_pstate/no_turbo ]; then
  echo 1 > /sys/devices/system/cpu/intel_pstate/no_turbo && echo "   intel turbo disabled"
elif [ -w /sys/devices/system/cpu/cpufreq/boost ]; then
  echo 0 > /sys/devices/system/cpu/cpufreq/boost && echo "   boost disabled"
else
  warn "could not control turbo, note it in your results."
fi

echo "== Swap off"
if [ -n "$(swapon --show --noheadings 2>/dev/null)" ]; then swapoff -a && echo "   swap disabled until reboot"; fi

echo "== Kernel network settings"
sysctl -qw net.core.somaxconn=65535 \
           net.ipv4.ip_local_port_range="1024 65535" \
           net.ipv4.tcp_max_syn_backlog=65535 \
           net.core.netdev_max_backlog=65535 || warn "sysctl failed"

echo "== Other load on this machine"
busy=$(ps -eo pcpu --no-headers | awk '{s+=$1} END {printf "%d", s}')
[ "$busy" -lt 30 ] || warn "already using about ${busy}% of one core in total, stop other workloads."

echo "== Image"
if [ -n "${BENCH_IMAGE:-}" ]; then
  docker pull "$BENCH_IMAGE"
  echo "   commit in image: $(docker run --rm "$BENCH_IMAGE" version)"
else
  warn "BENCH_IMAGE not set, skipping pull."
fi

n=$(nproc)
echo
echo "== Next steps ($n cores)"
if [ "$role" = sut ]; then
  [ "$n" -ge 4 ] || warn "fewer than 4 cores, results will be unreliable."
  u=$(( n / 4 )); [ "$u" -ge 1 ] || u=1
  up="0-$((u - 1))"; [ "$u" -eq 1 ] && up="0"
  gw_end=$(( n - 2 )); [ "$gw_end" -ge "$u" ] || gw_end=$u
  gw="$u-$gw_end"; [ "$u" -eq "$gw_end" ] && gw="$u"
  echo "   Core split: upstream $up, gateway $gw, the rest left to the OS and Docker."
  echo "   If the load generator runs on THIS machine too, give it its own cores and shrink the gateway set."
  echo
  echo "   export BENCH_IMAGE=${BENCH_IMAGE:-<image>}"
  echo "   export CPUSET_UP=$up CPUSET_GW=$gw"
  echo "   docker compose -f docker-compose.sut.yml up -d"
  echo "   (open TCP 4001 and 3001 in the firewall for the load generator machine)"
else
  lg="0-1"; [ "$n" -ge 3 ] || lg="0"
  echo "   mkdir -p results"
  echo "   docker run --rm --network host --cpuset-cpus $lg \\"
  echo "     --ulimit nofile=1048576:1048576 -u \$(id -u):\$(id -g) -v \"\$PWD/results:/results\" \\"
  echo "     -e GATEWAY_URL=http://<IP_OF_MACHINE_A>:4001/proxy/hello \\"
  echo "     -e DIRECT_URL=http://<IP_OF_MACHINE_A>:3001/hello \\"
  echo "     ${BENCH_IMAGE:-<image>} bench"
  echo "   Use 127.0.0.1 as the IP if everything runs on the same machine."
fi
