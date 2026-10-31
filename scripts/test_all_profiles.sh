#!/usr/bin/env bash
# ==============================================================================
# GateBridge - Local Multi-Profile Test Runner
# Verifies compilation and test suite for the primary target (Java 21)
# and bytecode overlays for compatibility profiles (Java 17, Java 8).
#
# Usage:
#   ./scripts/test_all_profiles.sh          # Full test suite (21) + compatibility builds (17, 8)
#   ./scripts/test_all_profiles.sh --fast   # Fast compilation-only check across all profiles
#   ./scripts/test_all_profiles.sh java8    # Verify specific profile only
# ==============================================================================

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$REPO_ROOT"

FAST_MODE=0
TARGET_PROFILE="all"

for arg in "$@"; do
  case "$arg" in
    --fast|-f)
      FAST_MODE=1
      ;;
    java21|java17|java8)
      TARGET_PROFILE="$arg"
      ;;
    --help|-h)
      echo "Usage: $0 [--fast|-f] [java21|java17|java8]"
      echo "  --fast, -f   Skip unit tests and verify compilation/packaging only"
      echo "  [profile]    Check only the specified profile (default: all)"
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg"
      echo "Run '$0 --help' for usage."
      exit 1
      ;;
  esac
done

echo "=========================================================="
echo " Starting GateBridge Multi-Profile Verification"
[ "$FAST_MODE" -eq 1 ] && echo " Mode: FAST (Compilation & Packaging Only)"
echo "=========================================================="
echo ""

PASSED=()
FAILED=()

# 1. Primary Profile (java21)
if [ "$TARGET_PROFILE" = "all" ] || [ "$TARGET_PROFILE" = "java21" ]; then
  echo "----------------------------------------------------------"
  if [ "$FAST_MODE" -eq 1 ]; then
    echo " Verifying Profile: java21 (Compilation & Packaging)"
    CMD="mvn clean package -Pjava21 -DskipTests -q"
  else
    echo " Testing Profile: java21 (Full Test Suite)"
    CMD="mvn clean test -Pjava21 -q"
  fi
  echo "----------------------------------------------------------"
  if eval "$CMD"; then
    echo " STATUS: SUCCESS [Profile java21]"
    PASSED+=("java21")
  else
    echo " STATUS: FAILURE [Profile java21]"
    FAILED+=("java21")
  fi
  echo ""
fi

# 2. Compatibility Profiles (java17, java8)
for profile in "java17" "java8"; do
  if [ "$TARGET_PROFILE" = "all" ] || [ "$TARGET_PROFILE" = "$profile" ]; then
    echo "----------------------------------------------------------"
    echo " Verifying Overlay Profile: $profile (Compilation & Packaging)"
    echo "----------------------------------------------------------"
    if mvn clean package -P"$profile" -DskipTests -q; then
      echo " STATUS: SUCCESS [Profile $profile]"
      PASSED+=("$profile")
    else
      echo " STATUS: FAILURE [Profile $profile]"
      FAILED+=("$profile")
    fi
    echo ""
  fi
done

echo "=========================================================="
echo " Multi-Profile Verification Summary"
echo "=========================================================="
echo " Passed: ${PASSED[*]:-None}"
echo " Failed: ${FAILED[*]:-None}"
echo "=========================================================="

if [ ${#FAILED[@]} -ne 0 ]; then
  exit 1
fi
