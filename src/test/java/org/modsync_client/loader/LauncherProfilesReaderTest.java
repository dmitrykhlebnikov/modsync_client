package org.modsync_client.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LauncherProfilesReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsLastVersionIdsFromValidFile() throws IOException {
        Files.writeString(tempDir.resolve("launcher_profiles.json"), """
                {
                  "profiles": {
                    "abc": { "lastVersionId": "fabric-loader-0.16.0-1.21.1", "name": "Fabric" },
                    "def": { "lastVersionId": "1.21.1", "name": "Vanilla" }
                  }
                }
                """);

        List<String> ids = LauncherProfilesReader.read(tempDir);

        assertTrue(ids.contains("fabric-loader-0.16.0-1.21.1"));
        assertTrue(ids.contains("1.21.1"));
        assertEquals(2, ids.size());
    }

    @Test
    void returnsEmptyListWhenFileIsMissing() {
        List<String> ids = LauncherProfilesReader.read(tempDir);

        assertTrue(ids.isEmpty());
    }

    @Test
    void returnsEmptyListOnMalformedJson() throws IOException {
        Files.writeString(tempDir.resolve("launcher_profiles.json"), "not json at all");

        List<String> ids = LauncherProfilesReader.read(tempDir);

        assertTrue(ids.isEmpty());
    }

    @Test
    void toleratesProfilesWithNoLastVersionId() throws IOException {
        Files.writeString(tempDir.resolve("launcher_profiles.json"), """
                {
                  "profiles": {
                    "abc": { "name": "No version id here" },
                    "def": { "lastVersionId": "1.21.1" }
                  }
                }
                """);

        List<String> ids = LauncherProfilesReader.read(tempDir);

        assertEquals(List.of("1.21.1"), ids);
    }

    @Test
    void toleratesExtraUnknownTopLevelKeys() throws IOException {
        Files.writeString(tempDir.resolve("launcher_profiles.json"), """
                {
                  "clientToken": "some-token",
                  "launcherVersion": {"name": "3.0.0"},
                  "profiles": {
                    "abc": { "lastVersionId": "neoforge-21.1.0" }
                  }
                }
                """);

        List<String> ids = LauncherProfilesReader.read(tempDir);

        assertEquals(List.of("neoforge-21.1.0"), ids);
    }
}
