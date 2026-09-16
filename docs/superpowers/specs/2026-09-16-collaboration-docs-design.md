# Collaboration Documentation Design Spec

**Date:** 2026-09-16  
**Status:** Approved  
**Language:** English  

## Overview
This specification outlines the collaborative standards, branching strategies, contribution guidelines, code of conduct, and GitHub issue/PR templates for the GateBridge ecosystem across both open-source (`GateBridge/GateBridge`) and enterprise (`GateBridge/gatebridge-enterprise`) repositories.

---

## Architecture & File Structure

For both repositories (`gatebridge` and `gatebridge-enterprise`), the following documentation files will be created in standard Markdown (in English):

```
<repo-root>/
├── BRANCHING.md
├── CONTRIBUTING.md
├── CODE_OF_CONDUCT.md
└── .github/
    ├── PULL_REQUEST_TEMPLATE.md
    └── ISSUE_TEMPLATE/
        ├── bug_report.yml
        └── feature_request.yml
```

---

## Document Specifications

### 1. `BRANCHING.md`
- **Workflow**: Simplified GitHub Flow.
- **Protected Branches**:
  - `master` for `GateBridge` (Core)
  - `main` for `gatebridge-enterprise` (Enterprise)
- **Branch Naming Conventions**:
  - `feat/<scope>-<short-description>`: New features (e.g., `feat/http2-transport`)
  - `fix/<scope>-<short-description>`: Bug fixes (e.g., `fix/loom-thread-leak`)
  - `refactor/<scope>-<short-description>`: Internal code refactoring without breaking public contract
  - `docs/<short-description>`: Documentation changes
  - `chore/<short-description>`: Build configuration or dependency updates
- **Commit Message Standard**: Conventional Commits format (`type(scope): concise description in imperative mood`).
- **Merge Strategy**: Pull Requests mandatory. Direct commits to `master`/`main` are blocked. **Squash and Merge** strategy required for linear git history.

### 2. `CONTRIBUTING.md`
- **Development Prerequisites**: Java 21 LTS, Maven 3.9+.
- **Step-by-Step Workflow**:
  1. Create a branch following `BRANCHING.md`.
  2. Implement features or fixes with JUnit 5 / Mockito unit & integration tests.
  3. Verify clean build (`mvn clean compile test`).
  4. Submit Pull Request using the official template.
- **Core vs. Enterprise Scope**:
  - **Core (`GateBridge`)**: Focus on open-source community contributions, zero heavy third-party dependencies, Virtual Thread performance, backward compatibility.
  - **Enterprise (`gatebridge-enterprise`)**: Focus on private corporate extensions, dynamic SSL key management, enterprise logging, strict security controls, and non-disclosure compliance.

### 3. `CODE_OF_CONDUCT.md`
- Standard **Contributor Covenant v2.1** adapted for GateBridge projects to ensure an inclusive, welcoming, and harassment-free environment for all maintainers and contributors.

### 4. GitHub Templates (`.github/`)
- `PULL_REQUEST_TEMPLATE.md`: Mandatory checkboxes for local test passage, build verification, documentation update, and clean commit history.
- `.github/ISSUE_TEMPLATE/bug_report.yml`: Structured form fields for bug description, expected vs actual behavior, environment details, and minimal reproduction steps.
- `.github/ISSUE_TEMPLATE/feature_request.yml`: Structured form fields for proposed feature problem statement, solution proposal, and alternative workarounds.

---

## Verification Criteria
- All created Markdown files must be written in fluent technical English.
- No dead links or placeholders (TBD, TODO).
- Markdown formatting strictly adheres to GitHub Flavored Markdown (GFM).
- Both `gatebridge` and `gatebridge-enterprise` receive their respective tailored files.
