package org.modsync_client.loader;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LauncherProfilesReader {

    private static final Gson GSON = new Gson();

    public static List<String> read(Path minecraftDir) {
        Path file = minecraftDir.resolve("launcher_profiles.json");
        if (!Files.exists(file)) {
            return List.of();
        }
        String json;
        try {
            json = Files.readString(file);
        } catch (IOException e) {
            System.err.println("Warning: could not read launcher_profiles.json: " + e.getMessage());
            return List.of();
        }
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null || !root.has("profiles") || !root.get("profiles").isJsonObject()) {
                return List.of();
            }
            JsonObject profiles = root.getAsJsonObject("profiles");
            List<String> ids = new ArrayList<>();
            for (var entry : profiles.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject profile = entry.getValue().getAsJsonObject();
                if (profile.has("lastVersionId") && profile.get("lastVersionId").isJsonPrimitive()) {
                    ids.add(profile.get("lastVersionId").getAsString());
                }
            }
            return ids;
        } catch (JsonParseException e) {
            System.err.println("Warning: could not parse launcher_profiles.json: " + e.getMessage());
            return List.of();
        }
    }
}
