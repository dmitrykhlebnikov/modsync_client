package org.modsync_client.sync;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.modsync_client.manifest.Manifest;
import org.modsync_client.state.State;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SyncExecutorTest {

    @TempDir Path modsDir;

    private HttpServer server;
    private int port;
    private static final String MANIFEST_URL = "http://test/manifest.json";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String url(String filename) {
        return "http://localhost:" + port + "/jars/" + filename;
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private void serve(String filename, byte[] content) {
        server.createContext("/jars/" + filename, ex -> {
            ex.sendResponseHeaders(200, content.length);
            ex.getResponseBody().write(content);
            ex.getResponseBody().close();
        });
    }

    private Manifest.ModEntry entry(String filename, byte[] content) throws Exception {
        Manifest.ModEntry e = new Manifest.ModEntry();
        e.filename = filename;
        e.url = url(filename);
        e.sha256 = sha256(content);
        return e;
    }

    private static State.PackState.ManagedJar managedJar(String filename, byte[] content) throws Exception {
        var jar = new State.PackState.ManagedJar();
        jar.filename = filename;
        jar.sha256 = sha256(content);
        return jar;
    }

    private Manifest manifest(Manifest.ModEntry... entries) {
        Manifest m = new Manifest();
        m.packName = "Test Pack";
        m.packVersion = "1.0";
        m.minecraftVersion = "1.21.1";
        m.loader = new Manifest.Loader();
        m.loader.type = "fabric";
        m.loader.version = "0.16.0";
        m.mods = List.of(entries);
        return m;
    }

    @Test
    void successfulSyncMovesFilesToModsDir() throws Exception {
        byte[] content = "sodium-bytes".getBytes();
        serve("sodium.jar", content);

        Manifest.ModEntry e = entry("sodium.jar", content);
        Manifest m = manifest(e);
        SyncPlan plan = new SyncPlan(List.of(e), List.of(), List.of(), List.of());

        Optional<State.PackState> result = new SyncExecutor(modsDir, true, false)
                .execute(plan, m, MANIFEST_URL);

        assertTrue(result.isPresent());
        assertArrayEquals(content, Files.readAllBytes(modsDir.resolve("sodium.jar")));
    }

    @Test
    void successfulSyncDeletesStaleJars() throws Exception {
        Files.writeString(modsDir.resolve("old.jar"), "stale");

        byte[] content = "sodium".getBytes();
        serve("sodium.jar", content);
        Manifest.ModEntry e = entry("sodium.jar", content);
        SyncPlan plan = new SyncPlan(
                List.of(e), List.of(),
                List.of(managedJar("old.jar", "stale".getBytes())),
                List.of());

        new SyncExecutor(modsDir, true, false).execute(plan, manifest(e), MANIFEST_URL);

        assertFalse(Files.exists(modsDir.resolve("old.jar")));
        assertTrue(Files.exists(modsDir.resolve("sodium.jar")));
    }

    @Test
    void successfulSyncReturnsPackStateWithManagedJars() throws Exception {
        byte[] content = "sodium-bytes".getBytes();
        serve("sodium.jar", content);

        Manifest.ModEntry e = entry("sodium.jar", content);
        Manifest m = manifest(e);
        SyncPlan plan = new SyncPlan(List.of(e), List.of(), List.of(), List.of());

        Optional<State.PackState> result = new SyncExecutor(modsDir, true, false)
                .execute(plan, m, MANIFEST_URL);

        assertTrue(result.isPresent());
        State.PackState ps = result.get();
        assertEquals("1.0", ps.packVersion);
        assertEquals(MANIFEST_URL, ps.manifestUrl);
        assertEquals(1, ps.managedJars.size());
        assertEquals("sodium.jar", ps.managedJars.get(0).filename);
        assertEquals(sha256(content), ps.managedJars.get(0).sha256);
    }

    @Test
    void downloadFailureLeavesModsDirUntouched() throws Exception {
        Files.writeString(modsDir.resolve("old.jar"), "stale");

        byte[] sodiumContent = "sodium".getBytes();
        serve("sodium.jar", sodiumContent);
        // lithium.jar → 500
        server.createContext("/jars/lithium.jar", ex -> {
            ex.sendResponseHeaders(500, -1);
            ex.getResponseBody().close();
        });

        Manifest.ModEntry sodium = entry("sodium.jar", sodiumContent);
        Manifest.ModEntry lithium = entry("lithium.jar", "lithium".getBytes());
        SyncPlan plan = new SyncPlan(
                List.of(sodium, lithium), List.of(),
                List.of(managedJar("old.jar", "stale".getBytes())),
                List.of());

        assertThrows(IOException.class,
                () -> new SyncExecutor(modsDir, true, false)
                        .execute(plan, manifest(sodium, lithium), MANIFEST_URL));

        assertTrue(Files.exists(modsDir.resolve("old.jar")), "stale jar must not be deleted");
        assertFalse(Files.exists(modsDir.resolve("sodium.jar")), "sodium must not appear in modsDir");
    }

    @Test
    void dryRunReturnsEmptyAndDoesNotDownload() throws Exception {
        // No server context set up — any real HTTP attempt would fail
        byte[] content = "sodium".getBytes();
        Manifest.ModEntry e = entry("sodium.jar", content);
        SyncPlan plan = new SyncPlan(List.of(e), List.of(), List.of(), List.of());

        Optional<State.PackState> result = new SyncExecutor(modsDir, true, true)
                .execute(plan, manifest(e), MANIFEST_URL);

        assertTrue(result.isEmpty());
        assertFalse(Files.exists(modsDir.resolve("sodium.jar")));
    }
}
