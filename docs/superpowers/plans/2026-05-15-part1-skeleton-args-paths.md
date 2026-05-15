# Part 1: Skeleton, Arg Parsing, Path Resolution — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire up the Gradle `application` plugin, implement `Args` (CLI arg parsing) and `Paths` (Windows path resolution), and produce a runnable binary that prints the manifest URL and both resolved paths, then exits.

**Architecture:** `Args.parse()` and `Paths` throw unchecked exceptions on invalid input; `Main` catches them, prints to stderr, and calls `System.exit(1)`. This keeps `Args` and `Paths` fully unit-testable without any exit-mocking.

**Tech Stack:** Java 21, Gradle 9 (Groovy DSL), JUnit Jupiter 6, Gson 2.11.0 (wired in build only — not used in Part 1 code).

---

## File Map

| Action   | Path |
|----------|------|
| Modify   | `build.gradle` |
| Modify   | `src/main/java/org/modsync_client/Main.java` |
| Create   | `src/main/java/org/modsync_client/Args.java` |
| Create   | `src/main/java/org/modsync_client/Paths.java` |
| Create   | `src/test/java/org/modsync_client/ArgsTest.java` |
| Create   | `src/test/java/org/modsync_client/PathsTest.java` |

---

## Task 1: Update `build.gradle`

**Files:**
- Modify: `build.gradle`

- [ ] **Step 1: Replace `build.gradle` content**

```groovy
plugins {
    id 'java'
    id 'application'
}

group = 'org.example'
version = '1.0-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = 'org.modsync_client.Main'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'com.google.code.gson:gson:2.11.0'
    testImplementation platform('org.junit:junit-bom:6.0.0')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Verify Gradle syncs cleanly**

```bash
./gradlew dependencies --configuration compileClasspath
```

Expected: output includes `com.google.code.gson:gson:2.11.0` with no errors.

- [ ] **Step 3: Commit**

```bash
git add build.gradle
git commit -m "build: add application plugin, Java 21 toolchain, Gson dependency"
```

---

## Task 2: Implement `Args` with tests (TDD)

**Files:**
- Create: `src/test/java/org/modsync_client/ArgsTest.java`
- Create: `src/main/java/org/modsync_client/Args.java`

- [ ] **Step 1: Create the test file**

```java
package org.modsync_client;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ArgsTest {

    @Test
    void parsesUrlOnly() {
        Args args = Args.parse(new String[]{"https://example.com/manifest.json"});
        assertEquals("https://example.com/manifest.json", args.manifestUrl());
        assertFalse(args.yes());
        assertFalse(args.dryRun());
        assertNull(args.minecraftDir());
        assertFalse(args.noPause());
    }

    @Test
    void parsesAllFlags() {
        Args args = Args.parse(new String[]{
            "https://example.com/manifest.json",
            "--yes", "--dry-run", "--no-pause",
            "--minecraft-dir", "/tmp/mods"
        });
        assertTrue(args.yes());
        assertTrue(args.dryRun());
        assertTrue(args.noPause());
        assertEquals(Path.of("/tmp/mods"), args.minecraftDir());
    }

    @Test
    void minecraftDirCanAppearBeforeUrl() {
        Args args = Args.parse(new String[]{"--minecraft-dir", "/tmp/mods", "https://x.com/m.json"});
        assertEquals("https://x.com/m.json", args.manifestUrl());
        assertEquals(Path.of("/tmp/mods"), args.minecraftDir());
    }

    @Test
    void throwsOnNoArgs() {
        assertThrows(IllegalArgumentException.class, () -> Args.parse(new String[]{}));
    }

    @Test
    void throwsOnUnknownFlag() {
        assertThrows(IllegalArgumentException.class,
            () -> Args.parse(new String[]{"https://x.com/m.json", "--unknown"}));
    }

    @Test
    void throwsOnMissingMinecraftDirValue() {
        assertThrows(IllegalArgumentException.class,
            () -> Args.parse(new String[]{"https://x.com/m.json", "--minecraft-dir"}));
    }
}
```

- [ ] **Step 2: Run tests — verify they fail (class not found)**

```bash
./gradlew test --tests "org.modsync_client.ArgsTest" 2>&1 | tail -20
```

Expected: compilation error or `ClassNotFoundException` — `Args` does not exist yet.

- [ ] **Step 3: Create `Args.java`**

```java
package org.modsync_client;

import java.nio.file.Path;

public record Args(
    String manifestUrl,
    boolean yes,
    boolean dryRun,
    Path minecraftDir,
    boolean noPause
) {
    static final String USAGE =
        "Usage: modsync <manifest-url> [--yes] [--dry-run] [--minecraft-dir <path>] [--no-pause]";

    public static Args parse(String[] args) {
        String manifestUrl = null;
        boolean yes = false;
        boolean dryRun = false;
        Path minecraftDir = null;
        boolean noPause = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--yes"      -> yes = true;
                case "--dry-run"  -> dryRun = true;
                case "--no-pause" -> noPause = true;
                case "--minecraft-dir" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException(USAGE);
                    minecraftDir = Path.of(args[++i]);
                }
                default -> {
                    if (args[i].startsWith("--")) throw new IllegalArgumentException(USAGE);
                    if (manifestUrl != null)       throw new IllegalArgumentException(USAGE);
                    manifestUrl = args[i];
                }
            }
        }

        if (manifestUrl == null) throw new IllegalArgumentException(USAGE);
        return new Args(manifestUrl, yes, dryRun, minecraftDir, noPause);
    }
}
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
./gradlew test --tests "org.modsync_client.ArgsTest"
```

Expected:
```
ArgsTest > parsesUrlOnly() PASSED
ArgsTest > parsesAllFlags() PASSED
ArgsTest > minecraftDirCanAppearBeforeUrl() PASSED
ArgsTest > throwsOnNoArgs() PASSED
ArgsTest > throwsOnUnknownFlag() PASSED
ArgsTest > throwsOnMissingMinecraftDirValue() PASSED
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/modsync_client/Args.java \
        src/test/java/org/modsync_client/ArgsTest.java
git commit -m "feat: add Args record with CLI flag parsing"
```

---

## Task 3: Implement `Paths` with tests (TDD)

**Files:**
- Create: `src/test/java/org/modsync_client/PathsTest.java`
- Create: `src/main/java/org/modsync_client/Paths.java`

- [ ] **Step 1: Create the test file**

Only the `--minecraft-dir` override path is unit-testable without a real `APPDATA` env var. The env-reading paths are covered by the manual runnable check in Task 5.

```java
package org.modsync_client;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PathsTest {

    @Test
    void resolveMinecraftDirUsesOverrideWhenProvided() {
        Args args = Args.parse(new String[]{"https://x.com/m.json", "--minecraft-dir", "/override/mods"});
        Path result = Paths.resolveMinecraftDir(args);
        assertEquals(Path.of("/override/mods"), result);
    }

    @Test
    void resolveMinecraftDirDoesNotCallEnvWhenOverridePresent() {
        // APPDATA may or may not be set; override must win either way.
        Args args = Args.parse(new String[]{"https://x.com/m.json", "--minecraft-dir", "/some/dir"});
        assertDoesNotThrow(() -> Paths.resolveMinecraftDir(args));
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
./gradlew test --tests "org.modsync_client.PathsTest" 2>&1 | tail -20
```

Expected: compilation error — `Paths` does not exist yet.

- [ ] **Step 3: Create `Paths.java`**

```java
package org.modsync_client;

import java.nio.file.Path;

public class Paths {

    public static Path resolveMinecraftDir(Args args) {
        if (args.minecraftDir() != null) {
            return args.minecraftDir();
        }
        return resolveAppData().resolve(".minecraft");
    }

    public static Path resolveStateFile() {
        return resolveAppData().resolve("modsync").resolve("state.json");
    }

    private static Path resolveAppData() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            throw new IllegalStateException("APPDATA is not set — this tool requires Windows");
        }
        return Path.of(appData);
    }
}
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
./gradlew test --tests "org.modsync_client.PathsTest"
```

Expected:
```
PathsTest > resolveMinecraftDirUsesOverrideWhenProvided() PASSED
PathsTest > resolveMinecraftDirDoesNotCallEnvWhenOverridePresent() PASSED
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/modsync_client/Paths.java \
        src/test/java/org/modsync_client/PathsTest.java
git commit -m "feat: add Paths utility for APPDATA-based path resolution"
```

---

## Task 4: Wire up `Main`

**Files:**
- Modify: `src/main/java/org/modsync_client/Main.java`

- [ ] **Step 1: Replace Main.java content**

```java
package org.modsync_client;

public class Main {

    public static void main(String[] argv) {
        Args args;
        try {
            args = Args.parse(argv);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        java.nio.file.Path minecraftDir;
        java.nio.file.Path stateFile;
        try {
            minecraftDir = Paths.resolveMinecraftDir(args);
            stateFile    = Paths.resolveStateFile();
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        System.out.println("Manifest URL : " + args.manifestUrl());
        System.out.println("Minecraft dir: " + minecraftDir);
        System.out.println("State file   : " + stateFile);
    }
}
```

- [ ] **Step 2: Run the full test suite**

```bash
./gradlew test
```

Expected: all tests pass, `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/modsync_client/Main.java
git commit -m "feat: wire Main to print manifest URL and resolved paths"
```

---

## Task 5: Runnable check

**Files:** none (verification only)

- [ ] **Step 1: Run with a manifest URL**

```bash
APPDATA=/tmp/fake-appdata ./gradlew run --args="https://example.com/manifest.json"
```

Expected output:
```
Manifest URL : https://example.com/manifest.json
Minecraft dir: /tmp/fake-appdata/.minecraft
State file   : /tmp/fake-appdata/modsync/state.json
```

- [ ] **Step 2: Verify --minecraft-dir override**

```bash
APPDATA=/tmp/fake-appdata ./gradlew run --args="https://example.com/manifest.json --minecraft-dir /custom/mods"
```

Expected:
```
Manifest URL : https://example.com/manifest.json
Minecraft dir: /custom/mods
State file   : /tmp/fake-appdata/modsync/state.json
```

- [ ] **Step 3: Verify usage on bad args**

```bash
./gradlew run 2>&1 | head -5
```

Expected (on stderr):
```
Usage: modsync <manifest-url> [--yes] [--dry-run] [--minecraft-dir <path>] [--no-pause]
```

- [ ] **Step 4: Verify APPDATA-missing error**

```bash
./gradlew run --args="https://example.com/manifest.json" 2>&1 | head -5
```
(Run this without APPDATA set — on Linux with no APPDATA in env)

Expected:
```
APPDATA is not set — this tool requires Windows
```
