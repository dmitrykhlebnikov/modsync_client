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
