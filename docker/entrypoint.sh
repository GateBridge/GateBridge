#!/bin/sh
set -e
cmd="${1:-gateway}"
case "$cmd" in
  gateway|upstream)
    exec java $JAVA_OPTS -cp "/app/classes:/app/lib/*" hexacloud.application.BenchMain "$cmd" ;;
  bench)
    shift
    exec /app/bin/bench.sh "$@" ;;
  version)
    cat /app/VERSION ;;
  *)
    exec "$@" ;;
esac
