package org.modsync_client.loader;

import org.junit.jupiter.api.Test;
import org.modsync_client.manifest.Manifest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoaderCheckTest {

    private static Manifest manifest(String loaderType, String loaderVersion, String mcVersion) {
        Manifest m = new Manifest();
        m.loader = new Manifest.Loader();
        m.loader.type = loaderType;
        m.loader.version = loaderVersion;
        m.minecraftVersion = mcVersion;
        return m;
    }

    // --- Fabric ---

    @Test
    void noWarningWhenExactFabricProfileFound() {
        Manifest m = manifest("fabric", "0.16.0", "1.21.1");
        List<LoaderCheck.Warning> warnings = LoaderCheck.check(
                m, List.of("fabric-loader-0.16.0-1.21.1", "1.21.1"));

        assertTrue(warnings.isEmpty());
    }

    @Test
    void softWarnWhenFabricVersionMismatches() {
        Manifest m = manifest("fabric", "0.16.0", "1.21.1");
        List<LoaderCheck.Warning> warnings = LoaderCheck.check(
                m, List.of("fabric-loader-0.15.0-1.21.1"));

        assertEquals(1, warnings.size());
        assertEquals(LoaderCheck.Severity.SOFT, warnings.get(0).severity());
        assertTrue(warnings.get(0).message().contains("0.16.0"));
    }

    @Test
    void loudWarnWhenNoFabricProfileFound() {
        Manifest m = manifest("fabric", "0.16.0", "1.21.1");
        List<LoaderCheck.Warning> warnings = LoaderCheck.check(m, List.of("1.21.1"));

        assertEquals(1, warnings.size());
        assertEquals(LoaderCheck.Severity.LOUD, warnings.get(0).severity());
        assertTrue(warnings.get(0).message().toLowerCase().contains("fabric"));
    }

    @Test
    void loudWarnWhenProfileListIsEmpty() {
        Manifest m = manifest("fabric", "0.16.0", "1.21.1");
        List<LoaderCheck.Warning> warnings = LoaderCheck.check(m, List.of());

        assertEquals(1, warnings.size());
        assertEquals(LoaderCheck.Severity.LOUD, warnings.get(0).severity());
    }

    // --- NeoForge ---

    @Test
    void noWarningWhenExactNeoForgeProfileFound() {
        Manifest m = manifest("neoforge", "21.1.0", "1.21.1");
        List<LoaderCheck.Warning> warnings = LoaderCheck.check(
                m, List.of("neoforge-21.1.0", "1.21.1"));

        assertTrue(warnings.isEmpty());
    }

    @Test
    void loudWarnWhenNeoForgeVersionMismatches() {
        Manifest m = manifest("neoforge", "21.1.0", "1.21.1");
        List<LoaderCheck.Warning> warnings = LoaderCheck.check(
                m, List.of("neoforge-21.0.0"));

        assertEquals(1, warnings.size());
        assertEquals(LoaderCheck.Severity.LOUD, warnings.get(0).severity());
        assertTrue(warnings.get(0).message().contains("21.1.0"));
    }

    @Test
    void loudWarnWhenNoNeoForgeProfileFound() {
        Manifest m = manifest("neoforge", "21.1.0", "1.21.1");
        List<LoaderCheck.Warning> warnings = LoaderCheck.check(m, List.of("1.21.1"));

        assertEquals(1, warnings.size());
        assertEquals(LoaderCheck.Severity.LOUD, warnings.get(0).severity());
        assertTrue(warnings.get(0).message().toLowerCase().contains("neoforge"));
    }
}
