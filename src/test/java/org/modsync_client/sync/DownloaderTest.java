package org.modsync_client.sync;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.modsync_client.manifest.Manifest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DownloaderTest {

    @TempDir Path tempDir;

    private HttpServer server;
    private int port;

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

    private static Manifest.ModEntry entry(String filename, byte[] content, String url) throws Exception {
        Manifest.ModEntry e = new Manifest.ModEntry();
        e.filename = filename;
        e.url = url;
        e.sha256 = sha256(content);
        return e;
    }

    @Test
    void downloadsFileWithCorrectHash() throws Exception {
        byte[] content = "lithium-jar-bytes".getBytes();
        serve("lithium.jar", content);

        Manifest.ModEntry e = entry("lithium.jar", content, url("lithium.jar"));
        Map<String, Path> result = Downloader.downloadAll(List.of(e), tempDir);

        assertTrue(result.containsKey("lithium.jar"));
        assertArrayEquals(content, Files.readAllBytes(result.get("lithium.jar")));
    }

    @Test
    void downloadsMultipleFilesSuccessfully() throws Exception {
        byte[] a = "content-a".getBytes();
        byte[] b = "content-b".getBytes();
        serve("a.jar", a);
        serve("b.jar", b);

        Manifest.ModEntry ea = entry("a.jar", a, url("a.jar"));
        Manifest.ModEntry eb = entry("b.jar", b, url("b.jar"));
        Map<String, Path> result = Downloader.downloadAll(List.of(ea, eb), tempDir);

        assertEquals(2, result.size());
        assertArrayEquals(a, Files.readAllBytes(result.get("a.jar")));
        assertArrayEquals(b, Files.readAllBytes(result.get("b.jar")));
    }

    @Test
    void throwsOnHashMismatch() throws Exception {
        byte[] content = "actual-content".getBytes();
        serve("test.jar", content);

        Manifest.ModEntry e = new Manifest.ModEntry();
        e.filename = "test.jar";
        e.url = url("test.jar");
        e.sha256 = "0000000000000000000000000000000000000000000000000000000000000000";

        assertThrows(IOException.class, () -> Downloader.downloadAll(List.of(e), tempDir));
    }

    @Test
    void throwsOnHttpErrorStatus() throws Exception {
        server.createContext("/jars/missing.jar", ex -> {
            ex.sendResponseHeaders(404, -1);
            ex.getResponseBody().close();
        });

        Manifest.ModEntry e = new Manifest.ModEntry();
        e.filename = "missing.jar";
        e.url = url("missing.jar");
        e.sha256 = "any";

        assertThrows(IOException.class, () -> Downloader.downloadAll(List.of(e), tempDir));
    }
}
