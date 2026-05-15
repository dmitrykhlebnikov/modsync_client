package org.modsync_client.sync;

import org.modsync_client.manifest.Manifest;
import org.modsync_client.util.Hashing;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

public class Downloader {

    private static final int MAX_CONCURRENT = 8;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static Map<String, Path> downloadAll(List<Manifest.ModEntry> entries, Path tempDir)
            throws IOException {
        Semaphore sem = new Semaphore(MAX_CONCURRENT);
        Map<String, Future<Path>> futures = new HashMap<>();

        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Manifest.ModEntry entry : entries) {
                futures.put(entry.filename, exec.submit(() -> {
                    sem.acquire();
                    try {
                        return download(entry, tempDir);
                    } finally {
                        sem.release();
                    }
                }));
            }
        }

        Map<String, Path> result = new HashMap<>();
        for (Map.Entry<String, Future<Path>> fe : futures.entrySet()) {
            try {
                result.put(fe.getKey(), fe.getValue().get());
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException ioe) throw ioe;
                throw new IOException("Download failed for " + fe.getKey() + ": " + cause.getMessage(), cause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Download interrupted", e);
            }
        }
        return result;
    }

    private static Path download(Manifest.ModEntry entry, Path tempDir) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(entry.url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<InputStream> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted: " + entry.filename, e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close();
            throw new IOException("HTTP " + response.statusCode() + " for " + entry.url);
        }

        Path dest = tempDir.resolve(entry.filename);
        try (InputStream body = response.body()) {
            Files.copy(body, dest);
        }

        String actual = Hashing.sha256(dest);
        if (!actual.equals(entry.sha256)) {
            Files.deleteIfExists(dest);
            throw new IOException("SHA-256 mismatch for " + entry.filename
                    + ": expected " + entry.sha256 + ", got " + actual);
        }

        return dest;
    }
}
