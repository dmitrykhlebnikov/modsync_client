package org.modsync_client.sync;

import org.modsync_client.manifest.Manifest;
import org.modsync_client.state.State;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class SyncExecutor {

    private final Path modsDir;
    private final boolean yes;
    private final boolean dryRun;

    public SyncExecutor(Path modsDir, boolean yes, boolean dryRun) {
        this.modsDir = modsDir;
        this.yes = yes;
        this.dryRun = dryRun;
    }

    public Optional<State.PackState> execute(SyncPlan plan, Manifest manifest, String manifestUrl)
            throws IOException {
        if (dryRun) {
            return Optional.empty();
        }

        Path tempDir = Files.createTempDirectory("modsync-" + UUID.randomUUID());
        try {
            Map<String, Path> downloaded = Downloader.downloadAll(plan.toAdd(), tempDir);

            for (State.PackState.ManagedJar stale : plan.toRemove()) {
                Files.deleteIfExists(modsDir.resolve(stale.filename));
            }

            for (Map.Entry<String, Path> e : downloaded.entrySet()) {
                Path dest = modsDir.resolve(e.getKey());
                moveAtomic(e.getValue(), dest);
            }
        } finally {
            deleteTempDir(tempDir);
        }

        State.PackState ps = new State.PackState();
        ps.manifestUrl = manifestUrl;
        ps.packVersion = manifest.packVersion;
        ps.lastSynced = Instant.now().toString();
        ps.managedJars = buildManagedJarList(plan);

        return Optional.of(ps);
    }

    private static void moveAtomic(Path src, Path dest) throws IOException {
        try {
            Files.move(src, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static List<State.PackState.ManagedJar> buildManagedJarList(SyncPlan plan) {
        List<State.PackState.ManagedJar> jars = new ArrayList<>();
        for (Manifest.ModEntry entry : plan.toAdd()) {
            State.PackState.ManagedJar jar = new State.PackState.ManagedJar();
            jar.filename = entry.filename;
            jar.sha256 = entry.sha256;
            jars.add(jar);
        }
        for (Manifest.ModEntry entry : plan.toKeep()) {
            State.PackState.ManagedJar jar = new State.PackState.ManagedJar();
            jar.filename = entry.filename;
            jar.sha256 = entry.sha256;
            jars.add(jar);
        }
        return jars;
    }

    private static void deleteTempDir(Path dir) {
        try {
            try (var stream = Files.walk(dir)) {
                stream.sorted((a, b) -> -a.compareTo(b))
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); }
                            catch (IOException ignored) {}
                        });
            }
        } catch (IOException ignored) {}
    }
}
