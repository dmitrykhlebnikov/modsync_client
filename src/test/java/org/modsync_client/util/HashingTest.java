package org.modsync_client.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HashingTest {

    @TempDir
    Path tempDir;

    @Test
    void hashesKnownContent() throws Exception {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello");

        String hash = Hashing.sha256(file);

        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", hash);
    }

    @Test
    void hashesEmptyFile() throws Exception {
        Path file = tempDir.resolve("empty.txt");
        Files.writeString(file, "");

        String hash = Hashing.sha256(file);

        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }

    @Test
    void producesLowercaseHex64Chars() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "some content");

        String hash = Hashing.sha256(file);

        assertTrue(hash.matches("[0-9a-f]{64}"), "expected 64-char lowercase hex, got: " + hash);
    }

    @Test
    void differentContentProducesDifferentHash() throws Exception {
        Path a = tempDir.resolve("a.txt");
        Path b = tempDir.resolve("b.txt");
        Files.writeString(a, "content-a");
        Files.writeString(b, "content-b");

        assertNotEquals(Hashing.sha256(a), Hashing.sha256(b));
    }
}
