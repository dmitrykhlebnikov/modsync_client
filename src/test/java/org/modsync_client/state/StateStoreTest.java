package org.modsync_client.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StateStoreTest {

    @TempDir Path dir;

    @Test
    void loadReturnsEmptyStateWhenFileMissing() throws Exception {
        State state = new StateStore(dir.resolve("state.json")).load();
        assertNotNull(state.packs);
        assertTrue(state.packs.isEmpty());
    }

    @Test
    void saveAndLoadRoundTrip() throws Exception {
        Path stateFile = dir.resolve("state.json");
        StateStore store = new StateStore(stateFile);

        State toSave = new State();
        State.PackState ps = new State.PackState();
        ps.manifestUrl = "http://example.com/manifest.json";
        ps.packVersion = "1.0";
        State.PackState.ManagedJar jar = new State.PackState.ManagedJar();
        jar.filename = "sodium.jar";
        jar.sha256 = "abc123";
        ps.managedJars.add(jar);
        toSave.packs.put("Test Pack", ps);

        store.save(toSave);
        State loaded = store.load();

        assertTrue(loaded.packs.containsKey("Test Pack"));
        State.PackState loaded_ps = loaded.packs.get("Test Pack");
        assertEquals("http://example.com/manifest.json", loaded_ps.manifestUrl);
        assertEquals("1.0", loaded_ps.packVersion);
        assertEquals(1, loaded_ps.managedJars.size());
        assertEquals("sodium.jar", loaded_ps.managedJars.get(0).filename);
        assertEquals("abc123", loaded_ps.managedJars.get(0).sha256);
    }

    @Test
    void saveCreatesParentDirectoriesIfNeeded() throws Exception {
        Path stateFile = dir.resolve("subdir/state.json");
        new StateStore(stateFile).save(new State());
        assertTrue(Files.exists(stateFile));
    }

    @Test
    void loadReturnsEmptyStateWhenFileMalformed() throws Exception {
        Path stateFile = dir.resolve("state.json");
        Files.writeString(stateFile, "not valid json {{{");
        State state = new StateStore(stateFile).load();
        assertNotNull(state.packs);
        assertTrue(state.packs.isEmpty());
    }

    @Test
    void loadBacksUpMalformedFile() throws Exception {
        Path stateFile = dir.resolve("state.json");
        Files.writeString(stateFile, "not valid json");
        new StateStore(stateFile).load();
        assertFalse(Files.exists(stateFile), "malformed file should be moved away");
        assertTrue(Files.list(dir).anyMatch(p -> p.getFileName().toString().startsWith("state.json.bad")),
                "backup file should exist");
    }

    @Test
    void saveDoesNotLeaveTemporaryFile() throws Exception {
        Path stateFile = dir.resolve("state.json");
        new StateStore(stateFile).save(new State());
        assertFalse(Files.exists(dir.resolve("state.json.tmp")));
        assertTrue(Files.exists(stateFile));
    }
}
