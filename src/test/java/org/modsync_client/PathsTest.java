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
