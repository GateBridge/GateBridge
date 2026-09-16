# Rapid-Fail Release Validation Design

## Overview
Currently, the release workflow (`.github/workflows/release.yml`) in `GateBridge/GateBridge` executes the full JDK 21 setup, Maven dependency caching, compilation, and the complete test suite before attempting to create the GitHub Release or trigger JitPack. If a release tag or version already exists, the workflow wastes several minutes running tests before failing at the deployment step.

This document specifies the design for a **Rapid-Fail Release Validation** step placed immediately after repository checkout to reject duplicate release attempts in under 3 seconds.

---

## 1. Specification

### 1.1 Trigger & Placement
- **Location**: `.github/workflows/release.yml`
- **Position**: Step 2 (immediately after `actions/checkout@v4` and BEFORE `actions/setup-java@v4` or `mvn clean verify`).

### 1.2 Validation Rules
1. **GitHub Release Duplicate Check**:
   - Query GitHub Releases API using `gh release view "${TAG_NAME}"`.
   - If the release tag already exists on GitHub Releases:
     - Emit error annotation: `::error::Release ${TAG_NAME} already exists on GitHub Releases!`
     - Terminate step immediately with exit code `1`.
2. **JitPack Duplicate Check**:
   - Parse project version from `pom.xml`.
   - Query JitPack API (`https://jitpack.io/api/builds/com.github.${REPO}/${VERSION}`).
   - If JitPack reports status `OK` / already built and published, log error annotation and exit `1`.

---

## 2. Target Workflow Structure (`.github/workflows/release.yml`)

```yaml
name: Release & Deployment

on:
  push:
    tags:
      - 'v*'
  workflow_dispatch:
    inputs:
      tag-name:
        description: 'Release tag name (e.g. v1.5.0)'
        required: false

permissions:
  contents: write

jobs:
  release:
    name: Validate and Publish Release
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/master' || startsWith(github.ref, 'refs/tags/v')

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Rapid-Fail Release Validation
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          TAG_NAME: ${{ github.ref_name }}
          REPO: ${{ github.repository }}
        run: |
          echo "Validating release tag '${TAG_NAME}'..."
          
          # Check if GitHub Release already exists
          if gh release view "${TAG_NAME}" --repo "${REPO}" >/dev/null 2>&1; then
            echo "::error::Release '${TAG_NAME}' ALREADY exists on GitHub Releases!"
            echo "Aborting build early to prevent duplicate release execution."
            exit 1
          fi
          
          # Check POM version
          POM_VERSION=$(grep -oPM1 "(?<=<version>)[^<]+" pom.xml || echo "")
          echo "Target POM version: ${POM_VERSION}"
          
          echo "Rapid-fail validation passed: '${TAG_NAME}' is a new release."

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      - name: Validate build and test suite
        run: mvn -B clean verify
      ...
```

---

## 3. Verification Plan
1. Validate workflow YAML syntax.
2. Test rapid-fail step against existing releases and non-existing release tags.
3. Push changes to `dev` and promote to `master`.
