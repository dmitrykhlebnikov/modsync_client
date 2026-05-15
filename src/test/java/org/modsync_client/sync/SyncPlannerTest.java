package org.modsync_client.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.modsync_client.manifest.Manifest;
import org.modsync_client.state.State;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SyncPlannerTest {

    @TempDir
    Path modsDir;

    // Compute SHA-256 independently of Hashing.java so tests don't rely on the impl
    private static String sha256(String content) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(content.getBytes()));
    }

    private static Manifest.ModEntry entry(String filename, String content) throws Exception {
        Manifest.ModEntry e = new Manifest.ModEntry();
        e.filename = filename;
        e.url = "http://example.com/" + filename;
        e.sha256 = sha256(content);
        return e;
    }

    private static Manifest manifest(Manifest.ModEntry... entries) {
        Manifest m = new Manifest();
        m.mods = List.of(entries);
        m.loader = new Manifest.Loader();
        m.loader.type = "fabric";
        m.loader.version = "0.16.0";
        m.minecraftVersion = "1.21.1";
        m.packName = "Test Pack";
        m.packVersion = "1.0";
        return m;
    }

    private static State.PackState packState(State.PackState.ManagedJar... jars) {
        State.PackState ps = new State.PackState();
        ps.managedJars = List.of(jars);
        return ps;
    }

    private static State.PackState.ManagedJar managed(String filename, String content) throws Exception {
        var jar = new State.PackState.ManagedJar();
        jar.filename = filename;
        jar.sha256 = sha256(content);
        return jar;
    }

    // --- toAdd ---

    @Test
    void fileNotOnDiskIsAddedToAdd() throws Exception {
        Manifest m = manifest(entry("sodium.jar", "sodium-content"));

        SyncPlan plan = SyncPlanner.plan(m, modsDir, null);

        assertEquals(1, plan.toAdd().size());
        assertEquals("sodium.jar", plan.toAdd().get(0).filename);
        assertTrue(plan.toKeep().isEmpty());
        assertTrue(plan.toRemove().isEmpty());
        assertTrue(plan.collisions().isEmpty());
    }

    @Test
    void allFilesAbsentProducesAllToAdd() throws Exception {
        Manifest m = manifest(
                entry("sodium.jar", "s-content"),
                entry("lithium.jar", "l-content"));

        SyncPlan plan = SyncPlanner.plan(m, modsDir, null);

        assertEquals(2, plan.toAdd().size());
    }

    // --- toKeep ---

    @Test
    void fileWithMatchingHashIsKept() throws Exception {
        String content = "sodium-content";
        Files.writeString(modsDir.resolve("sodium.jar"), content);
        Manifest m = manifest(entry("sodium.jar", content));

        SyncPlan plan = SyncPlanner.plan(m, modsDir, null);

        assertTrue(plan.toAdd().isEmpty());
        assertEquals(1, plan.toKeep().size());
        assertEquals("sodium.jar", plan.toKeep().get(0).filename);
    }

    // --- managed overwrite (toAdd) ---

    @Test
    void managedFileWithWrongHashIsRequeued() throws Exception {
        Files.writeString(modsDir.resolve("sodium.jar"), "old-content");
        Manifest m = manifest(entry("sodium.jar", "new-content"));
        State.PackState ps = packState(managed("sodium.jar", "old-content"));

        SyncPlan plan = SyncPlanner.plan(m, modsDir, ps);

        assertEquals(1, plan.toAdd().size());
        assertTrue(plan.toKeep().isEmpty());
        assertTrue(plan.collisions().isEmpty());
    }

    // --- collision ---

    @Test
    void unmanagedFileWithWrongHashIsCollision() throws Exception {
        Files.writeString(modsDir.resolve("sodium.jar"), "user-installed-version");
        Manifest m = manifest(entry("sodium.jar", "pack-version"));
        // packState has no entry for sodium.jar → it's unmanaged

        SyncPlan plan = SyncPlanner.plan(m, modsDir, null);

        assertTrue(plan.toAdd().isEmpty());
        assertTrue(plan.toKeep().isEmpty());
        assertEquals(1, plan.collisions().size());
        assertEquals("sodium.jar", plan.collisions().get(0).manifestEntry().filename);
    }

    @Test
    void unmanagedFileWithWrongHashIsCollisionEvenWithOtherManagedJars() throws Exception {
        Files.writeString(modsDir.resolve("sodium.jar"), "user-version");
        Manifest m = manifest(entry("sodium.jar", "pack-version"));
        // lithium is managed but sodium is not
        State.PackState ps = packState(managed("lithium.jar", "lithium-content"));

        SyncPlan plan = SyncPlanner.plan(m, modsDir, ps);

        assertEquals(1, plan.collisions().size());
        assertEquals("sodium.jar", plan.collisions().get(0).manifestEntry().filename);
    }

    // --- toRemove ---

    @Test
    void managedJarAbsentFromManifestIsRemoved() throws Exception {
        Manifest m = manifest(entry("sodium.jar", "s-content"));
        State.PackState ps = packState(
                managed("sodium.jar", "s-content"),
                managed("old-mod.jar", "old-content"));

        SyncPlan plan = SyncPlanner.plan(m, modsDir, ps);

        assertEquals(1, plan.toRemove().size());
        assertEquals("old-mod.jar", plan.toRemove().get(0).filename);
    }

    @Test
    void unmanagedJarNotInManifestIsIgnored() throws Exception {
        // file exists on disk, not in manifest, not in managed_jars → not touched
        Files.writeString(modsDir.resolve("user-mod.jar"), "user-mod");
        Manifest m = manifest(entry("sodium.jar", "s-content"));

        SyncPlan plan = SyncPlanner.plan(m, modsDir, null);

        assertTrue(plan.toRemove().isEmpty());
        assertEquals(1, plan.toAdd().size()); // sodium is missing
    }

    @Test
    void noRemovesWhenPackStateIsNull() throws Exception {
        Manifest m = manifest(entry("sodium.jar", "s-content"));

        SyncPlan plan = SyncPlanner.plan(m, modsDir, null);

        assertTrue(plan.toRemove().isEmpty());
    }

    @Test
    void noRemovesWhenManagedJarsIsEmpty() throws Exception {
        Manifest m = manifest(entry("sodium.jar", "s-content"));
        State.PackState ps = new State.PackState();
        ps.managedJars = List.of();

        SyncPlan plan = SyncPlanner.plan(m, modsDir, ps);

        assertTrue(plan.toRemove().isEmpty());
    }

    // --- isUpToDate ---

    @Test
    void isUpToDateReturnsTrueWhenVersionMatchesAndAllJarsPresent() throws Exception {
        String content = "sodium-content";
        Files.writeString(modsDir.resolve("sodium.jar"), content);
        Manifest m = manifest(entry("sodium.jar", content));

        State.PackState ps = new State.PackState();
        ps.packVersion = "1.0";
        ps.managedJars = List.of(managed("sodium.jar", content));

        assertTrue(SyncPlanner.isUpToDate(m, modsDir, ps));
    }

    @Test
    void isUpToDateReturnsFalseWhenPackStateIsNull() throws Exception {
        assertFalse(SyncPlanner.isUpToDate(manifest(entry("sodium.jar", "c")), modsDir, null));
    }

    @Test
    void isUpToDateReturnsFalseWhenVersionDiffers() throws Exception {
        String content = "sodium-content";
        Files.writeString(modsDir.resolve("sodium.jar"), content);
        Manifest m = manifest(entry("sodium.jar", content));

        State.PackState ps = new State.PackState();
        ps.packVersion = "2.0";
        ps.managedJars = List.of(managed("sodium.jar", content));

        assertFalse(SyncPlanner.isUpToDate(m, modsDir, ps));
    }

    @Test
    void isUpToDateReturnsFalseWhenManagedJarMissing() throws Exception {
        Manifest m = manifest(entry("sodium.jar", "content"));

        State.PackState ps = new State.PackState();
        ps.packVersion = "1.0";
        ps.managedJars = List.of(managed("sodium.jar", "content"));

        assertFalse(SyncPlanner.isUpToDate(m, modsDir, ps));
    }

    @Test
    void isUpToDateReturnsFalseWhenManagedJarHashWrong() throws Exception {
        Files.writeString(modsDir.resolve("sodium.jar"), "tampered");
        Manifest m = manifest(entry("sodium.jar", "original"));

        State.PackState ps = new State.PackState();
        ps.packVersion = "1.0";
        ps.managedJars = List.of(managed("sodium.jar", "original"));

        assertFalse(SyncPlanner.isUpToDate(m, modsDir, ps));
    }
}
