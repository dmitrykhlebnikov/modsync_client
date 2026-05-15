package org.modsync_client.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public class Console {

    public static void pause(boolean noPause) {
        pause(noPause, System.in);
    }

    static void pause(boolean noPause, InputStream in) {
        if (noPause) return;
        System.out.print("Press Enter to exit...");
        System.out.flush();
        try { in.read(); } catch (IOException ignored) {}
    }

    public static void appendLog(Path logFile, String message) throws IOException {
        appendLog(logFile, message, null);
    }

    public static void appendLog(Path logFile, String message, Throwable t) throws IOException {
        Path parent = logFile.getParent();
        if (parent != null) Files.createDirectories(parent);

        StringBuilder sb = new StringBuilder();
        sb.append('[').append(Instant.now()).append("] ").append(message).append('\n');
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            sb.append(sw).append('\n');
        }

        Files.writeString(logFile, sb.toString(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
