# Branching & Commit Guidelines

Welcome to the **GateBridge Core Framework** branching and commit standards guide. Adhering to these rules ensures clean history, predictable releases, and smooth collaboration.

---

## 1. Branching Model

GateBridge uses an active **Development & Release Branching** model:

- **`master`**: Production-ready protected branch. Contains tagged stable releases. Direct pushes to `master` are strictly prohibited.
- **`dev`**: Active integration branch for incoming feature and fix Pull Requests. Prevents constant unneeded release triggers on `master`.
- **Feature & Fix Branches**: All work is performed on dedicated topic branches created off `dev`. Pull Requests target `dev` first.

### Branch Naming Conventions

Use lowercase, kebab-case branch names prefixed by their purpose:

| Prefix | Use Case | Example |
| :--- | :--- | :--- |
| `feat/` | New feature or functionality | `feat/http2-transport` |
| `fix/` | Bug fix or patch | `fix/loom-thread-leak` |
| `refactor/` | Code refactoring without changing behavior | `refactor/path-resolver` |
| `docs/` | Documentation additions or updates | `docs/update-readme` |
| `chore/` | Build scripts, dependencies, or tooling | `chore/bump-pom-version` |
| `test/` | Unit or integration test additions | `test/mock-server-fixture` |

---

## 2. Commit Message Standard

Commits must follow the **Conventional Commits** format:

```
<type>(<scope>): <short summary in imperative present tense>

[optional body explaining motivation and changes]
```

### Commit Types

- `feat`: A new feature
- `fix`: A bug fix
- `docs`: Documentation only changes
- `style`: Code style changes (formatting, missing semi-colons, no production code change)
- `refactor`: A code change that neither fixes a bug nor adds a feature
- `perf`: A code change that improves performance
- `test`: Adding missing tests or correcting existing tests
- `chore`: Changes to build process, auxiliary tools, or libraries

### Examples

```bash
git commit -m "feat(transport): implement Undertow HTTP/2 multiplexing handler"
git commit -m "fix(ssl): resolve certificate reloading race condition in MutableKeyManager"
git commit -m "docs(api): update reverse proxy routing protocol spec"
```

---

## 3. Pull Request & Merge Policy

1. **Pull Requests Required**: All changes must enter `master` via a Pull Request.
2. **Review & Status Checks**: Every PR must receive at least one maintainer review approval and pass all CI automated build/test checks.
3. **Squash and Merge**: Merges to `master` are executed using **Squash and Merge** to maintain a clean, single-commit-per-feature history on `master`.
4. **Branch Cleanup**: Delete topic branches immediately after a successful merge.
