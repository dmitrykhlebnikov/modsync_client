package org.modsync_client.manifest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ManifestFetcherTest {

    private static final String VALID_JSON = """
            {
              "pack_name": "My Pack",
              "pack_version": "1.0",
              "minecraft_version": "1.21.1",
              "loader": {"type": "fabric", "version": "0.15.0"},
              "mods": [
                {"filename": "sodium-0.6.jar", "url": "http://example.com/sodium-0.6.jar", "sha256": "abc123"}
              ]
            }
            """;

    @Test
    void parsesValidManifest() {
        Manifest m = ManifestFetcher.parse(VALID_JSON);

        assertEquals("My Pack", m.packName);
        assertEquals("1.0", m.packVersion);
        assertEquals("1.21.1", m.minecraftVersion);
        assertEquals(1, m.mods.size());
        assertEquals("sodium-0.6.jar", m.mods.get(0).filename);
        assertEquals("http://example.com/sodium-0.6.jar", m.mods.get(0).url);
        assertEquals("abc123", m.mods.get(0).sha256);
        assertEquals("fabric", m.loader.type);
        assertEquals("0.15.0", m.loader.version);
    }

    @Test
    void throwsOnMissingPackVersion() {
        String json = """
                {
                  "pack_name": "My Pack",
                  "minecraft_version": "1.21.1",
                  "mods": [{"filename": "foo.jar", "url": "http://x.com/foo.jar", "sha256": "aaa"}],
                  "loader": {"type": "fabric", "version": "0.15.0"}
                }
                """;
        var ex = assertThrows(IllegalArgumentException.class, () -> ManifestFetcher.parse(json));
        assertTrue(ex.getMessage().contains("pack_version"));
    }

    @Test
    void throwsOnEmptyMods() {
        String json = """
                {
                  "pack_name": "My Pack",
                  "pack_version": "1.0",
                  "minecraft_version": "1.21.1",
                  "mods": [],
                  "loader": {"type": "fabric", "version": "0.15.0"}
                }
                """;
        var ex = assertThrows(IllegalArgumentException.class, () -> ManifestFetcher.parse(json));
        assertTrue(ex.getMessage().contains("mods"));
    }

    @Test
    void throwsOnMissingLoader() {
        String json = """
                {
                  "pack_name": "My Pack",
                  "pack_version": "1.0",
                  "minecraft_version": "1.21.1",
                  "mods": [{"filename": "foo.jar", "url": "http://x.com/foo.jar", "sha256": "aaa"}]
                }
                """;
        var ex = assertThrows(IllegalArgumentException.class, () -> ManifestFetcher.parse(json));
        assertTrue(ex.getMessage().contains("loader"));
    }

    @Test
    void throwsOnMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> ManifestFetcher.parse("not json"));
    }
}
