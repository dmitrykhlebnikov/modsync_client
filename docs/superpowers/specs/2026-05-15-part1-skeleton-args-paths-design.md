# Part 1 Design: Skeleton, Arg Parsing, Path Resolution

**Date:** 2026-05-15
**Scope:** `modsync-client-plan.md` — Build step 1 only

---

## Goal

Wire up the Gradle project, implement `Args` (CLI arg parsing) and `Paths` (platform path resolution), and produce a runnable binary that prints the manifest URL and both resolved paths, then exits. No networking or file I/O beyond path resolution.

---

## Build changes (`build.gradle`)

Add the `application` plugin and set `mainClassName = 'org.modsync_client.Main'`. Add Gson as the sole `implementation` dependency (`com.google.code.gson:gson:2.11.0`). Keep the existing JUnit 6 test setup unchanged. Keep Groovy DSL (not migrating to `.kts`).

---

## `Args` (record)

```
Args(String manifestUrl, boolean yes, boolean dryRun, Path minecraftDir, boolean noPause)
```

Static factory `Args.parse(String[] args)` walks the array linearly:

- First non-flag token is the manifest URL (required).
- Recognised flags: `--yes`, `--dry-run`, `--minecraft-dir <path>`, `--no-pause`.
- Unknown flag or missing URL → print one-line usage to stderr, `System.exit(1)`.
- `--minecraft-dir` stores a `Path`; validation of whether the directory exists is deferred to `Paths`.

Usage line on error:
```
Usage: modsync <manifest-url> [--yes] [--dry-run] [--minecraft-dir <path>] [--no-pause]
```

---

## `Paths` (static utility)

Two public static methods:

**`resolveMinecraftDir(Args args)`**
1. If `args.minecraftDir()` is non-null, return it directly (no existence check yet).
2. Otherwise read `System.getenv("APPDATA")`. If unset → `System.err.println("APPDATA is not set — this tool requires Windows"); System.exit(1)`.
3. Return `Path.of(appdata, ".minecraft")`.

**`resolveStateFile()`**
1. Read `System.getenv("APPDATA")`; same fail-fast if unset.
2. Return `Path.of(appdata, "modsync", "state.json")`.

Neither method checks whether the returned paths exist on disk — that is left to later steps.

---

## `Main`

```
Args args = Args.parse(argv);
Path minecraftDir = Paths.resolveMinecraftDir(args);
Path stateFile    = Paths.resolveStateFile();

System.out.println("Manifest URL : " + args.manifestUrl());
System.out.println("Minecraft dir: " + minecraftDir);
System.out.println("State file   : " + stateFile);
```

Exits after printing. No further pipeline logic in Part 1.

---

## Error handling

- Bad/missing args: usage line to stderr, `exit(1)`.
- Missing `APPDATA`: descriptive message to stderr, `exit(1)`.
- No other error paths in Part 1.

---

## Testing

Unit tests for `Args.parse`:
- Happy path: URL only, URL + all flags, `--minecraft-dir` override.
- Error path: no args (should exit 1), unknown flag (should exit 1).

Unit tests for `Paths`:
- `resolveMinecraftDir` with a non-null `minecraftDir` arg (no env lookup needed).
- `resolveStateFile` with a mocked `APPDATA` value — this requires either a wrapper around `System.getenv` or a test that sets the env. Keep it simple: test only the override path for now; the env-reading path is covered by manual runnable check.

---

## Runnable check

```
./gradlew run --args="https://example.com/manifest.json"
```

Expected output (Windows):
```
Manifest URL : https://example.com/manifest.json
Minecraft dir: C:\Users\<user>\AppData\Roaming\.minecraft
State file   : C:\Users\<user>\AppData\Roaming\modsync\state.json
```
