# Rapid-Fail Release Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement rapid-fail release validation in `GateBridge/GateBridge` `.github/workflows/release.yml` to reject duplicate release attempts in < 3 seconds before running Maven builds and test suites.

**Architecture:** Add a Rapid-Fail Release Validation step directly after `actions/checkout@v4` in `.github/workflows/release.yml` that checks `gh release view` for existing releases and aborts early if the release already exists.

**Tech Stack:** GitHub Actions, GitHub CLI (`gh`), Bash.

## Global Constraints
- Target Repository: `GateBridge/GateBridge` (Core)
- Workflow File: `.github/workflows/release.yml`
- Execution Time Limit for Validation: < 5 seconds.

---

### Task 1: Update `.github/workflows/release.yml` with Rapid-Fail Step

**Files:**
- Modify: `.github/workflows/release.yml:22-38`

**Interfaces:**
- Consumes: `github.ref_name`, `github.repository`, `secrets.GITHUB_TOKEN`
- Produces: Fast exit 1 if release tag already exists, or pass-through to JDK setup & test execution

- [ ] **Step 1: Check existing `.github/workflows/release.yml`**

Run: `view_file` on `/home/watashi/Projects/Java-framework/gatebridge/gatebridge/.github/workflows/release.yml`

- [ ] **Step 2: Add Rapid-Fail Release Validation step**

Insert the validation step directly after `actions/checkout@v4`:
```yaml
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
```

- [ ] **Step 3: Verify YAML syntax**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/release.yml'))"`
Expected: Clean exit (code 0)

- [ ] **Step 4: Commit changes**

```bash
git add .github/workflows/release.yml
git commit -m "ci(release): add rapid-fail duplicate release validation step"
```

---

### Task 2: Local Verification & Promotion

**Files:**
- Verify: `.github/workflows/release.yml`

- [ ] **Step 1: Verify git status**

Run: `git status`

- [ ] **Step 2: Merge `dev` into `master` and push to remote**

```bash
git checkout dev
git push origin dev
git checkout master
git merge --no-ff dev -m "merge: dev into master"
git push origin master
git checkout dev
```

- [ ] **Step 3: Verify CI workflow status on GitHub**

Run: `gh run list --repo GateBridge/GateBridge`
Expected: All active runs green.
