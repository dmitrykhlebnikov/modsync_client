package org.modsync_client.sync;

import org.modsync_client.manifest.Manifest;
import org.modsync_client.state.State;
import org.modsync_client.util.Hashing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SyncPlanner {

    public static SyncPlan plan(Manifest manifest, Path modsDir, State.PackState packState)
            throws IOException {
        Map<String, String> managedHashes = buildManagedHashes(packState);

        List<Manifest.ModEntry> toAdd = new ArrayList<>();
        List<Manifest.ModEntry> toKeep = new ArrayList<>();
        List<SyncPlan.Collision> collisions = new ArrayList<>();

        for (Manifest.ModEntry mod : manifest.mods) {
            Path existing = modsDir.resolve(mod.filename);
            if (!Files.exists(existing)) {
                toAdd.add(mod);
                continue;
            }
            String existingHash = Hashing.sha256(existing);
            if (existingHash.equals(mod.sha256)) {
                toKeep.add(mod);
                continue;
            }
            if (managedHashes.containsKey(mod.filename)) {
                toAdd.add(mod);
            } else {
                collisions.add(new SyncPlan.Collision(mod, existingHash));
            }
        }

        List<State.PackState.ManagedJar> toRemove = buildRemoveList(manifest, packState);

        return new SyncPlan(toAdd, toKeep, toRemove, collisions);
    }

    private static Map<String, String> buildManagedHashes(State.PackState packState) {
        Map<String, String> map = new HashMap<>();
        if (packState != null && packState.managedJars != null) {
            for (State.PackState.ManagedJar jar : packState.managedJars) {
                map.put(jar.filename, jar.sha256);
            }
        }
        return map;
    }

    private static List<State.PackState.ManagedJar> buildRemoveList(
            Manifest manifest, State.PackState packState) {
        if (packState == null || packState.managedJars == null || packState.managedJars.isEmpty()) {
            return List.of();
        }
        Set<String> manifestFilenames = manifest.mods.stream()
                .map(m -> m.filename)
                .collect(Collectors.toSet());
        return packState.managedJars.stream()
                .filter(jar -> !manifestFilenames.contains(jar.filename))
                .collect(Collectors.toList());
    }
}
