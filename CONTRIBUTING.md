# Contributing to GateBridge Core

Thank you for your interest in contributing to **GateBridge Core**! As an open-source high-performance Java cluster gateway framework, community contributions are essential to making GateBridge faster, safer, and more extensible.

---

## Code of Conduct

By participating in this project, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md). Please report unacceptable behavior to the project maintainers.

---

## Development Prerequisites

Before building or contributing code, ensure your local development environment satisfies the following requirements:

- **Java JDK 21 LTS** or later (Project Loom / Virtual Threads enabled).
- **Apache Maven 3.9+**.
- **Git**.

---

## How to Contribute

### 1. Find or Create an Issue
Before writing code, check the GitHub Issue tracker. If no issue exists for your bug report or feature request, please open a new issue using the appropriate template.

### 2. Set Up Local Environment & Branch
Clone the repository and create your topic branch off `dev`:

```bash
git clone https://github.com/GateBridge/GateBridge.git
cd GateBridge
git checkout dev
git pull origin dev
git checkout -b feat/your-feature-name
```

Refer to [BRANCHING.md](BRANCHING.md) for branch naming standards.

### 3. Build & Test Locally
Ensure the project compiles cleanly and all unit tests pass before making edits:

```bash
mvn clean test
```

### 4. Code & Testing Standards
- Write clean, self-documenting Java code following standard Java naming conventions.
- Use **Virtual Threads (`Thread.ofVirtual()`)** for non-blocking I/O concurrency where applicable.
- Add JUnit 5 unit tests for all new code paths or bug fixes.
- Avoid introducing unnecessary heavy external dependencies into `gatebridge-core`.

### 5. Commit & Push
Format your commits according to [Conventional Commits](BRANCHING.md):

```bash
git add .
git commit -m "feat(scope): concise description of changes"
git push origin feat/your-feature-name
```

### 6. Create a Pull Request
Open a Pull Request targeting the **`dev`** branch. Fill out the [PR Template](.github/PULL_REQUEST_TEMPLATE.md) completely, referencing relevant issue numbers. Maintains and automated CI will test your PR before merging into `dev`.

---

## Questions & Support
Feel free to open an issue or start a GitHub Discussion if you have architectural questions or need guidance on implementation details!
