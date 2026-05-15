# modsync

A Windows CLI tool that syncs `%APPDATA%\.minecraft\mods\` to match a remote modpack manifest. Point it at a `modsync-server` instance and it downloads missing or updated mods, removes stale ones, and leaves your manually-installed mods untouched.

Ships as a self-contained folder (`modsync.exe` + bundled JRE) — no Java installation required on the end-user machine.

---

## Usage

```
modsync.exe <manifest-url> [options]
```

| Option | Description |
|---|---|
| `--yes` | Skip confirmation prompts |
| `--dry-run` | Print the diff without downloading or deleting anything |
| `--minecraft-dir <path>` | Override the default `%APPDATA%\.minecraft` location |
| `--no-pause` | Don't wait for Enter before exiting (useful in scripts) |

### Example

```
modsync.exe http://your-server:8080/manifest.json
modsync.exe http://your-server:8080/manifest.json --yes
modsync.exe http://your-server:8080/manifest.json --dry-run --no-pause
```

On each run, the tool prints the resolved paths, pack summary, and loader warnings before prompting you to proceed.

---

## What it does

1. Fetches `manifest.json` from the server.
2. Checks local state — if `pack_version` matches and all managed jars are on disk with correct hashes, prints "Already up to date." and exits.
3. Diffs the manifest against `mods\`: files to download, files to delete, files to keep.
4. Warns about any mod loader version mismatches against `launcher_profiles.json`.
5. Prompts for confirmation (bypassed by `--yes`; requires explicit `yes` when files will be deleted).
6. Downloads all new/changed jars to a temp directory, verifies SHA-256 for each.
7. Only if all downloads succeed: moves files into `mods\`, deletes stale jars, saves state.

**Unmanaged jars** (files you placed in `mods\` yourself that aren't tracked by modsync) are never deleted. If a manifest entry conflicts with an unmanaged file, you're prompted to confirm the overwrite.

---

## Local state

State is stored at `%APPDATA%\modsync\state.json`. It records which jars were placed by modsync and at what version, per pack, so subsequent syncs know what to remove.

```json
{
  "packs": {
    "MyModpack": {
      "manifest_url": "http://your-server:8080/manifest.json",
      "pack_version": "28b971a...",
      "managed_jars": [
        { "filename": "sodium-0.5.jar", "sha256": "4edf657b..." }
      ],
      "last_synced": "2026-05-15T10:00:00Z"
    }
  }
}
```

A log of every run is appended to `%APPDATA%\modsync\log.txt`.

---

## Building from source

Requires Java 21 JDK.

```bash
# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "org.modsync_client.sync.SyncPlannerTest"

# Build (compile + test)
./gradlew build

# Run locally (dev)
./gradlew run --args="<manifest-url>"
```

---

## Packaging

The packaging tasks must be run on Windows to produce a Windows app-image. They require a full JDK 21 (with `jmods/`) on `PATH`.

```batch
rem 1. Build a minimal JRE (java.base + java.net.http only)
gradlew jlinkRuntime

rem 2. Assemble the app-image folder
gradlew jpackageImage
```

Output is in `build/app-image/modsync/`. Zip that folder and distribute it. Users unzip anywhere and run `modsync.exe` directly — no Java install needed.

### What's in the app-image

```
modsync/
  modsync.exe
  runtime/          ← bundled JRE (~40 MB, stripped)
  app/
    modsync_client-1.0-SNAPSHOT.jar
    gson-2.11.0.jar
```

---

## Architecture

```
src/main/java/org/modsync_client/
  Main.java                   entry point; full pipeline + top-level error handler
  Args.java                   CLI arg parsing
  Paths.java                  resolves %APPDATA% paths (minecraft dir, state file, log)
  manifest/
    Manifest.java             JSON data classes
    ManifestFetcher.java      HTTP GET + Gson parse + validation
  state/
    State.java                data class for state.json
    StateStore.java           atomic load/save (write to .tmp, ATOMIC_MOVE)
  sync/
    SyncPlanner.java          diffs manifest vs mods dir → SyncPlan
    SyncExecutor.java         downloads to temp, verifies, moves atomically
    Downloader.java           parallel downloads via virtual threads (capped at 8)
  loader/
    LauncherProfilesReader.java  reads launcher_profiles.json defensively
    LoaderCheck.java             detects loader version mismatches
  util/
    Hashing.java              streaming SHA-256
    Console.java              pause-before-exit, append-only log writing
```

---

## Server protocol

See [PROTOCOL.md](PROTOCOL.md) for the full `modsync-server` HTTP API — manifest schema, jar download endpoint, and the `pack_version` fingerprint algorithm.


---
## Packaging-executable

The packaging tasks must be run on Windows to produce a Windows `.exe` installer or app-image. They require a full JDK 21 (with `jmods/`) on `PATH`.

---

## Building JAR (required first step)

Before packaging, build the application:

```bash
./gradlew build
```

Output JAR will be in:

```
build/libs/modsync_client-1.0-SNAPSHOT.jar
```

---

## Creating a Windows `.exe` installer with `jpackage`

You can generate a native Windows installer using `jpackage`.

### Basic command

```powershell
jpackage ^
  --type exe ^
  --name modsync ^
  --app-version 21.0.0 ^
  --input build/libs ^
  --main-jar modsync_client-1.0-SNAPSHOT.jar ^
  --main-class org.modsync_client.Main ^
  --icon icon.ico ^
  --dest dist ^
  --win-menu ^
  --win-shortcut
```

---

## What each option means

| Option                                 | Meaning                                                |
| -------------------------------------- | ------------------------------------------------------ |
| `--type exe`                           | Generates a Windows `.exe` installer                   |
| `--name modsync`                       | Application name shown in installer and system         |
| `--app-version 21.0.0`                 | Version displayed in Windows Apps / installer metadata |
| `--input build/libs`                   | Folder containing the compiled JAR file                |
| `--main-jar ...`                       | The JAR file to run                                    |
| `--main-class org.modsync_client.Main` | Entry point (Java main class package)                  |
| `--icon icon.ico`                      | Application icon for installer and shortcut            |
| `--dest dist`                          | Output directory for generated installer               |
| `--win-menu`                           | Adds Start Menu entry                                  |
| `--win-shortcut`                       | Creates desktop shortcut                               |

---

## Output

After running `jpackage`, the installer will be created at:

```
dist/modsync-21.0.0.exe
```

(or similar depending on version/name)

---

## Optional: MSI installer instead of EXE

You can generate an MSI installer:

```powershell
jpackage ^
  --type msi ^
  --name modsync ^
  --app-version 21.0.0 ^
  --input build/libs ^
  --main-jar modsync_client-1.0-SNAPSHOT.jar ^
  --main-class org.modsync_client.Main ^
  --icon icon.ico ^
  --dest dist
```

---

## Notes

* `jpackage` does **not modify your Gradle build** — it only packages output artifacts.
* The JAR must run correctly via:

  ```bash
  java -jar build/libs/modsync_client-1.0-SNAPSHOT.jar
  ```
* Windows packaging must be done on Windows OS.

---

## Packaging (legacy app-image workflow)

The packaging tasks must be run on Windows to produce a Windows app-image. They require a full JDK 21 (with `jmods/`) on `PATH`.

```batch
rem 1. Build a minimal runtime (if using jlink)
gradlew jlinkRuntime

rem 2. Assemble app-image
gradlew jpackageImage
```

Output is in:

```
build/app-image/modsync/
```