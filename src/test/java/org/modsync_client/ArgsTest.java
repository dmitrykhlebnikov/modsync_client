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
