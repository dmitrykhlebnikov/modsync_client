package org.modsync_client.sync;

import org.modsync_client.manifest.Manifest;
import org.modsync_client.state.State;

import java.util.List;

public record SyncPlan(
        List<Manifest.ModEntry> toAdd,
        List<Manifest.ModEntry> toKeep,
        List<State.PackState.ManagedJar> toRemove,
        List<Collision> collisions
) {
    public record Collision(Manifest.ModEntry manifestEntry, String existingHash) {}
}
