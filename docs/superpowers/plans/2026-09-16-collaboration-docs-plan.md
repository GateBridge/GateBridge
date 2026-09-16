# Collaboration Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create comprehensive, standardized collaboration documentation files (`BRANCHING.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `.github/PULL_REQUEST_TEMPLATE.md`, `.github/ISSUE_TEMPLATE/`) in English for both `GateBridge` Core and `GateBridge Enterprise` repositories.

**Architecture:** Standardized GitHub Flavored Markdown (GFM) documentation and YAML/Markdown templates tailored to open-source (`GateBridge` Core) and private (`GateBridge Enterprise`) contexts.

**Tech Stack:** Markdown, GitHub Issue/PR YAML schema, Git.

## Global Constraints
- Language: English.
- Formatting: GitHub Flavored Markdown (GFM).
- GitHub Flow with `master` as primary branch in `GateBridge` Core and `main` as primary branch in `GateBridge Enterprise`.
- All PRs require Squash & Merge and code review.

---

### Task 1: Create Core Collaboration Files (`BRANCHING.md` & `CONTRIBUTING.md` in `gatebridge`)

**Files:**
- Create: `gatebridge/BRANCHING.md`
- Create: `gatebridge/CONTRIBUTING.md`

- [ ] **Step 1: Write `gatebridge/BRANCHING.md`**
Create `BRANCHING.md` in `gatebridge` detailing the GitHub Flow, branch prefixes (`feat/`, `fix/`, `refactor/`, `docs/`, `chore/`), Conventional Commits format, and Squash Merge policies.

- [ ] **Step 2: Write `gatebridge/CONTRIBUTING.md`**
Create `CONTRIBUTING.md` in `gatebridge` explaining setup prerequisites (Java 21, Maven 3.9+), step-by-step contribution flow, testing requirements (`mvn clean test`), and open-source contribution guidelines.

- [ ] **Step 3: Commit Task 1**
```bash
cd gatebridge && git add BRANCHING.md CONTRIBUTING.md && git commit -m "docs: add BRANCHING.md and CONTRIBUTING.md for core repo"
```

---

### Task 2: Create Core Governance Files & Templates (`CODE_OF_CONDUCT.md` & `.github/` in `gatebridge`)

**Files:**
- Create: `gatebridge/CODE_OF_CONDUCT.md`
- Create: `gatebridge/.github/PULL_REQUEST_TEMPLATE.md`
- Create: `gatebridge/.github/ISSUE_TEMPLATE/bug_report.yml`
- Create: `gatebridge/.github/ISSUE_TEMPLATE/feature_request.yml`

- [ ] **Step 1: Write `gatebridge/CODE_OF_CONDUCT.md`**
Create `CODE_OF_CONDUCT.md` based on Contributor Covenant v2.1.

- [ ] **Step 2: Write `gatebridge/.github/PULL_REQUEST_TEMPLATE.md`**
Create PR template with description, type of change, and verification checklist.

- [ ] **Step 3: Write `gatebridge/.github/ISSUE_TEMPLATE/bug_report.yml` and `feature_request.yml`**
Create structured YAML issue forms for bugs and feature requests.

- [ ] **Step 4: Commit Task 2**
```bash
cd gatebridge && git add CODE_OF_CONDUCT.md .github/ && git commit -m "docs: add CODE_OF_CONDUCT.md and GitHub issue/PR templates"
```

---

### Task 3: Create Enterprise Collaboration Files (`BRANCHING.md` & `CONTRIBUTING.md` in `gatebridge-enterprise`)

**Files:**
- Create: `gatebridge-enterprise/BRANCHING.md`
- Create: `gatebridge-enterprise/CONTRIBUTING.md`

- [ ] **Step 1: Write `gatebridge-enterprise/BRANCHING.md`**
Create `BRANCHING.md` tailored for enterprise repository with `main` branch rules, feature branches, Conventional Commits, and code review standards.

- [ ] **Step 2: Write `gatebridge-enterprise/CONTRIBUTING.md`**
Create `CONTRIBUTING.md` tailored for internal/private enterprise contributions, security guidelines, and test execution.

- [ ] **Step 3: Commit Task 3**
```bash
cd gatebridge-enterprise && git add BRANCHING.md CONTRIBUTING.md && git commit -m "docs: add BRANCHING.md and CONTRIBUTING.md for enterprise repo"
```

---

### Task 4: Create Enterprise Governance Files & Templates (`CODE_OF_CONDUCT.md` & `.github/` in `gatebridge-enterprise`)

**Files:**
- Create: `gatebridge-enterprise/CODE_OF_CONDUCT.md`
- Create: `gatebridge-enterprise/.github/PULL_REQUEST_TEMPLATE.md`
- Create: `gatebridge-enterprise/.github/ISSUE_TEMPLATE/bug_report.yml`
- Create: `gatebridge-enterprise/.github/ISSUE_TEMPLATE/feature_request.yml`

- [ ] **Step 1: Write `gatebridge-enterprise/CODE_OF_CONDUCT.md`**
Create `CODE_OF_CONDUCT.md` for enterprise team collaboration.

- [ ] **Step 2: Write `gatebridge-enterprise/.github/PULL_REQUEST_TEMPLATE.md`**
Create enterprise PR template.

- [ ] **Step 3: Write `gatebridge-enterprise/.github/ISSUE_TEMPLATE/bug_report.yml` and `feature_request.yml`**
Create enterprise issue forms.

- [ ] **Step 4: Commit Task 4**
```bash
cd gatebridge-enterprise && git add CODE_OF_CONDUCT.md .github/ && git commit -m "docs: add CODE_OF_CONDUCT.md and GitHub templates"
```

---

### Task 5: Push to Remote & Verify

- [ ] **Step 1: Push `gatebridge` Core**
```bash
cd gatebridge && git push origin master
```

- [ ] **Step 2: Push `gatebridge-enterprise`**
```bash
cd gatebridge-enterprise && git push origin main
```

- [ ] **Step 3: Verification**
Verify clean git status in both repositories.
