package org.modsync_client.manifest;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ManifestFetcher {

    private static final Gson GSON = new Gson();

    public static Manifest fetch(String url) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        String body;
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Manifest request failed: HTTP " + response.statusCode() + " from " + url);
            }
            body = response.body();
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Failed to fetch manifest from " + url + ": " + e.getMessage(), e);
        }
        return parse(body);
    }

    static Manifest parse(String json) {
        Manifest manifest;
        try {
            manifest = GSON.fromJson(json, Manifest.class);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Malformed manifest JSON: " + e.getMessage(), e);
        }
        if (manifest == null) {
            throw new IllegalArgumentException("Malformed manifest JSON: empty document");
        }
        validate(manifest);
        return manifest;
    }

    private static void validate(Manifest m) {
        if (m.packVersion == null || m.packVersion.isBlank()) {
            throw new IllegalArgumentException("Manifest is missing required field: pack_version");
        }
        if (m.mods == null || m.mods.isEmpty()) {
            throw new IllegalArgumentException("Manifest mods list is empty or missing");
        }
        if (m.loader == null) {
            throw new IllegalArgumentException("Manifest is missing required field: loader");
        }
    }
}
