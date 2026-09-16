# GateBridge Multi-JDK Build Workflow

## Overview

GateBridge supports building and releasing for multiple Java versions (Java 8 and Java 21) with automatic source code overlaying and bytecode validation.

The build system consists of:
- **GitHub Actions Workflow** (`build.yml`) - Triggers compilation, creates version tags, and pushes to JitPack
- **JitPack Configuration** (`jitpack.yml`) - Detects JDK version from git tag and builds accordingly
- **Maven Source Overlay Plugin** - Dynamically overlays Java 8 compatible code for Java 8 builds
- **Maven Profiles** - Configuration for each Java version

---

## Build Workflow Flow

### Step 1: Developer Push to `build` Branch
```bash
git push origin main:build
```

This triggers the GitHub Actions workflow defined in `.github/workflows/build.yml`.

---

### Step 2: GitHub Actions - Matrix Build

The workflow runs **TWO parallel jobs** (one for Java 8, one for Java 21):

#### Job: Java 8 Build
```
1. Checkout repository with full git history
2. Set up Java 8 (Temurin distribution)
3. Compile with -Pjava8 profile
   └─> Triggers maven.compiler.release=8
   └─> Triggers SourceOverlayMojo (GENERATE_SOURCES phase)
       ├─> Copies java/src/ → target/generated-sources/overlay
       └─> Applies java/src-java17/ and java/src-java8/ sequentially (cascading overlays)
   └─> Compiles from target/generated-sources/overlay
   └─> Produces bytecode compatible with Java 8 (major version 52)
4. Version computation: 1.0.0.5-beta-jdk8
5. Create release branch: release/jdk8/1.0.0.5-beta
6. Bump version in pom.xml to 1.0.0.5-beta-jdk8
7. Commit and push release branch
8. Create git tag: 1.0.0.5-beta-jdk8
9. Push tag to GitHub
10. Trigger JitPack build (with 2-minute polling)
```

#### Job: Java 21 Build
```
1. Checkout repository with full git history
2. Set up Java 21 (Temurin distribution)
3. Compile with -Pjava21 profile (default profile)
   └─> Triggers maven.compiler.release=21
   └─> Triggers SourceOverlayMojo (no overlay applied)
   └─> Compiles from java/src/
   └─> Produces bytecode compatible with Java 21 (major version 65)
4. Version computation: 1.0.0.5-beta-jdk21
5. Create release branch: release/jdk21/1.0.0.5-beta
6. Bump version in pom.xml to 1.0.0.5-beta-jdk21
7. Commit and push release branch
8. Create git tag: 1.0.0.5-beta-jdk21
9. Push tag to GitHub
10. Trigger JitPack build (with 2-minute polling)
```

---

### Step 3: JitPack Detection and Build

When JitPack detects the new tags (`1.0.0.5-beta-jdk8` and `1.0.0.5-beta-jdk21`):

#### For `1.0.0.5-beta-jdk8` tag:
1. Detects `jdk8` in tag name via `jitpack.yml`
2. Executes: `jdk_switcher use openjdk8`
3. Compiles source with OpenJDK 8
4. SourceOverlayMojo applies java/src-java8 overlay
5. Produces JAR with Java 8 bytecode compatibility
6. Published to: `com.github.GateBridge:GateBridge:1.0.0.5-beta-jdk8`

#### For `1.0.0.5-beta-jdk21` tag:
1. Detects `jdk21` in tag name via `jitpack.yml`
2. Executes: `jdk_switcher use openjdk21`
3. Compiles source with OpenJDK 21
4. SourceOverlayMojo skips overlay (java/src-java8 not applied)
5. Produces JAR with Java 21 bytecode features
6. Published to: `com.github.GateBridge:GateBridge:1.0.0.5-beta-jdk21`

---

## Component Details

### 1. GitHub Actions Workflow (`.github/workflows/build.yml`)

**Trigger Events:**
- Manual dispatch: `workflow_dispatch` (can override JDKs to build)
- Push to `build` branch

**Matrix Configuration:**
```yaml
matrix:
  jdk: [8, 21]
```

**Key Steps:**

```yaml
- name: Compile for Java ${{ matrix.jdk }}
  run: |
    if [ "${{ matrix.jdk }}" = "8" ]; then
      mvn -B clean package -DskipTests -Dskip.docker -Pjava8
    else
      mvn -B clean package -DskipTests -Dskip.docker -Pjava21
    fi
```

⚠️ **CRITICAL:** Use `-Pjava8` profile flag, NOT `-Dmaven.compiler.release=8`
- Profiles activate `<properties>` in the POM that plugins can read
- `-D` flags override properties but don't activate profiles

---

### 2. JitPack Configuration (`jitpack.yml`)

```yaml
before_install:
  - if [[ $RELEASE_TAG == *"jdk8" ]]; then jdk_switcher use openjdk8; fi
  - if [[ $RELEASE_TAG == *"jdk21" ]]; then jdk_switcher use openjdk21; fi

jdk:
  - openjdk21
  - openjdk8
```

**How it works:**
- `$RELEASE_TAG` is the git tag being built
- JitPack conditionally switches JDK before compilation
- Ensures correct bytecode version is produced for each release

**Note:** JitPack caches builds. To force a rebuild, visit:
```
https://jitpack.io/#com.github.GateBridge/GateBridge
```
and click "Look up" or "Force rebuild" for specific tags.

---

### 3. Maven Source Overlay Plugin

**Configuration in `pom.xml`:**
```xml
<sourceDirectory>target/generated-sources/overlay</sourceDirectory>

<plugin>
    <groupId>com.github.GateBridge</groupId>
    <artifactId>source-overlay-plugin</artifactId>
    <version>1.0.5</version>
    <executions>
        <execution>
            <phase>generate-sources</phase>
            <goals>
                <goal>overlay</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**How it works:**
1. Runs during GENERATE_SOURCES phase (earliest phase)
2. Detects target Java version from `maven.compiler.release` property
3. If Java 8 detected:
   - Copies `java/src/` → `target/generated-sources/overlay`
   - Overlays `java/src-java8/` on top (replacing conflicts)
   - Result: Java 8 compatible sources
4. If Java 21+ detected:
   - Uses `java/src/` as-is (no overlay)
   - Result: Full modern Java 21 sources

**Plugin source:** See `source-overlay-plugin/src/main/java/io/hexacloud/maven/SourceOverlayMojo.java`

---

### 4. Maven Profiles

**`pom.xml` profiles:**

```xml
<profile>
    <id>java8</id>
    <properties>
        <maven.compiler.release>8</maven.compiler.release>
    </properties>
</profile>

<profile>
    <id>java21</id>
    <activation>
        <activeByDefault>true</activeByDefault>
    </activation>
    <properties>
        <maven.compiler.release>21</maven.compiler.release>
    </properties>
</profile>
```

**Activation:**
```bash
mvn clean package -Pjava8    # Build for Java 8
mvn clean package -Pjava21   # Build for Java 21
mvn clean package            # Default: Java 21 (activeByDefault)
```

---

## Complete Flow Diagram

```
Developer Push to 'build' branch
    │
    ├─→ GitHub Actions Workflow starts
    │
    ├─→ Matrix Job 1: Java 8
    │   ├─ Setup Java 8
    │   ├─ mvn clean package -Pjava8
    │   │  └─ SourceOverlayMojo applies java/src-java8 overlay
    │   ├─ Version: 1.0.0.5-beta-jdk8
    │   ├─ Create tag & push
    │   └─ Trigger JitPack
    │
    └─→ Matrix Job 2: Java 21
        ├─ Setup Java 21
        ├─ mvn clean package -Pjava21 (default)
        │  └─ SourceOverlayMojo: no overlay
        ├─ Version: 1.0.0.5-beta-jdk21
        ├─ Create tag & push
        └─ Trigger JitPack

JitPack Detection & Build:
    │
    ├─→ Tag: 1.0.0.5-beta-jdk8
    │   ├─ jdk_switcher use openjdk8
    │   ├─ mvn package
    │   └─ Publish JAR (Java 8 bytecode)
    │
    └─→ Tag: 1.0.0.5-beta-jdk21
        ├─ jdk_switcher use openjdk21
        ├─ mvn package
        └─ Publish JAR (Java 21 bytecode)

Downloads available at:
    https://jitpack.io/com/github/watashi-00/GateBridge/{VERSION}/
```

---

## Verification

### Verify Java 8 Compatibility

Create a test project with the Java 8 profile:

```bash
cd /path/to/test-jdk8
mvn clean compile -Pjdk8
```

If you see:
```
[ERROR] Bad class file ... Unsupported class file major version 65
```

Then the Java 8 version was not properly compiled. Check:
1. JitPack built with correct JDK (check `jitpack.yml`)
2. GitHub Actions used correct profile (check `build.yml`)
3. SourceOverlayMojo applied overlay (check logs)

### Verify Bytecode Version

```bash
javap -v target/classes/hexacloud/core/utils/ThreadManager.class | grep "major version"
```

Expected output:
```
major version: 52     # Java 8
major version: 65     # Java 21
```

---

## Troubleshooting

### Issue: Java 8 version has Java 21 bytecode

**Cause:** Overlay not applied or JDK not detected correctly

**Solution:**
1. Check `maven.compiler.release` is set in profile
2. Verify SourceOverlayMojo logs show detection
3. Force JitPack rebuild at: https://jitpack.io

### Issue: JitPack build times out

**Cause:** Complex compilation or resource limits

**Solution:**
1. Check GitHub Actions logs for compilation errors
2. JitPack auto-retries on-demand requests (slower)
3. Consider splitting large modules

### Issue: Git tag push fails

**Cause:** Tag already exists or permissions

**Solution:**
```bash
# Delete local and remote tag
git tag -d 1.0.0.5-beta-jdk8
git push origin :1.0.0.5-beta-jdk8

# Try workflow again
```

---

## Manual Local Build

To manually build locally for testing:

```bash
# Build for Java 8
mvn clean package -DskipTests -Pjava8

# Build for Java 21  
mvn clean package -DskipTests -Pjava21

# Verify bytecode
javap -v target/classes/hexacloud/core/utils/ThreadManager.class | grep "major version"
```

---

## References

- [Maven Compiler Plugin - Release Parameter](https://maven.apache.org/plugins/maven-compiler-plugin/compile-mojo.html#release)
- [GitHub Actions Setup Java](https://github.com/actions/setup-java)
- [JitPack Documentation](https://jitpack.io/docs/)
- [Project Loom (Virtual Threads)](https://openjdk.org/projects/loom/)

