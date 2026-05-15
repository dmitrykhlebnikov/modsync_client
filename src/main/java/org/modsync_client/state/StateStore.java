package org.modsync_client.state;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

public class StateStore {

    private static final Gson GSON = new Gson();

    private final Path stateFile;

    public StateStore(Path stateFile) {
        this.stateFile = stateFile;
    }

    public State load() throws IOException {
        if (!Files.exists(stateFile)) {
            return new State();
        }
        String json;
        try {
            json = Files.readString(stateFile);
        } catch (IOException e) {
            return new State();
        }
        try {
            State state = GSON.fromJson(json, State.class);
            if (state == null) return new State();
            if (state.packs == null) state.packs = new java.util.HashMap<>();
            return state;
        } catch (JsonParseException e) {
            backupCorrupt();
            System.err.println("Warning: state.json was corrupt and has been backed up. Starting fresh.");
            return new State();
        }
    }

    public void save(State state) throws IOException {
        Path parent = stateFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(state));
        try {
            Files.move(tmp, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, stateFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void backupCorrupt() {
        Path backup = stateFile.resolveSibling(
                stateFile.getFileName() + ".bad." + Instant.now().toEpochMilli());
        try {
            Files.move(stateFile, backup);
        } catch (IOException ignored) {}
    }
}
