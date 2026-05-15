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
