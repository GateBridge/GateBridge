#!/usr/bin/env bash
# ==============================================================================
# GateBridge - Local Multi-Profile Test Runner
# Executes Maven test suite for default target (java21) and bytecode compilation 
# checks for compatibility profiles (java17, java8).
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$REPO_ROOT"

echo "=========================================================="
echo " Starting GateBridge Multi-Profile Verification"
echo "=========================================================="
echo ""

PASSED=()
FAILED=()

# 1. Primary Target JDK (Java 21) - Full Test Suite
echo "----------------------------------------------------------"
echo " Testing Primary Profile: java21 (Full Test Suite)"
echo "----------------------------------------------------------"
if mvn clean test -Pjava21 -q; then
  echo " STATUS: SUCCESS [Profile java21]"
  PASSED+=("java21")
else
  echo " STATUS: FAILURE [Profile java21]"
  FAILED+=("java21")
fi
echo ""

# 2. Bytecode Compatibility Overlay Profiles (Java 17 & Java 8)
COMPAT_PROFILES=("java17" "java8")
for profile in "${COMPAT_PROFILES[@]}"; do
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
