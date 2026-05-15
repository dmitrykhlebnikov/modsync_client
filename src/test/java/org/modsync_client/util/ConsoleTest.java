package org.modsync_client.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleTest {

    @TempDir Path dir;

    @Test
    void pauseDoesNotReadFromStreamWhenNoPauseIsTrue() {
        InputStream neverRead = new InputStream() {
            @Override public int read() {
                throw new AssertionError("pause must not read stdin when noPause=true");
            }
        };
        assertDoesNotThrow(() -> Console.pause(true, neverRead));
    }

    @Test
    void pauseReadsFromStreamWhenNoPauseIsFalse() {
        InputStream enter = new ByteArrayInputStream("\n".getBytes());
        assertDoesNotThrow(() -> Console.pause(false, enter));
    }

    @Test
    void appendLogCreatesFileAndWritesMessage() throws IOException {
        Path logFile = dir.resolve("log.txt");
        Console.appendLog(logFile, "hello");
        assertTrue(Files.exists(logFile));
        assertTrue(Files.readString(logFile).contains("hello"));
    }

    @Test
    void appendLogAppendsToExistingContent() throws IOException {
        Path logFile = dir.resolve("log.txt");
        Console.appendLog(logFile, "first");
        Console.appendLog(logFile, "second");
        String content = Files.readString(logFile);
        assertTrue(content.contains("first"));
        assertTrue(content.contains("second"));
    }

    @Test
    void appendLogCreatesParentDirectoriesIfNeeded() throws IOException {
        Path logFile = dir.resolve("subdir/log.txt");
        Console.appendLog(logFile, "msg");
        assertTrue(Files.exists(logFile));
    }

    @Test
    void appendLogWithThrowableIncludesStackTrace() throws IOException {
        Path logFile = dir.resolve("log.txt");
        Exception ex = new RuntimeException("boom");
        Console.appendLog(logFile, "error occurred", ex);
        String content = Files.readString(logFile);
        assertTrue(content.contains("boom"));
        assertTrue(content.contains("RuntimeException"));
    }
}
