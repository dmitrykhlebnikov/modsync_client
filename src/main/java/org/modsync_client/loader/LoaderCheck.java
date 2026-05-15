package org.modsync_client.loader;

import org.modsync_client.manifest.Manifest;

import java.util.List;

public class LoaderCheck {

    public enum Severity { SOFT, LOUD }

    public record Warning(Severity severity, String message) {}

    public static List<Warning> check(Manifest manifest, List<String> lastVersionIds) {
        String type = manifest.loader.type.toLowerCase();
        String loaderVersion = manifest.loader.version;
        String mcVersion = manifest.minecraftVersion;

        return switch (type) {
            case "fabric" -> checkFabric(loaderVersion, mcVersion, lastVersionIds);
            case "neoforge" -> checkNeoForge(loaderVersion, lastVersionIds);
            default -> List.of(new Warning(Severity.LOUD,
                    "Unknown loader type '" + type + "' — cannot verify profile."));
        };
    }

    private static List<Warning> checkFabric(String loaderVersion, String mcVersion,
                                              List<String> ids) {
        String expected = "fabric-loader-" + loaderVersion + "-" + mcVersion;
        if (ids.contains(expected)) return List.of();

        // Check if any fabric profile exists with the right MC but wrong loader version
        String mcSuffix = "-" + mcVersion;
        boolean hasFabricForMc = ids.stream()
                .anyMatch(id -> id.startsWith("fabric-loader-") && id.endsWith(mcSuffix));
        if (hasFabricForMc) {
            return List.of(new Warning(Severity.SOFT,
                    "Fabric loader version mismatch: manifest requires " + loaderVersion
                    + " for MC " + mcVersion
                    + ". Update via the Fabric installer."));
        }

        return List.of(new Warning(Severity.LOUD,
                "No Fabric profile found for MC " + mcVersion
                + ". Install Fabric " + loaderVersion
                + " from https://fabricmc.net/use/installer/"));
    }

    private static List<Warning> checkNeoForge(String loaderVersion, List<String> ids) {
        String expected = "neoforge-" + loaderVersion;
        if (ids.contains(expected)) return List.of();

        boolean hasAnyNeoForge = ids.stream().anyMatch(id -> id.startsWith("neoforge-"));
        if (hasAnyNeoForge) {
            return List.of(new Warning(Severity.LOUD,
                    "NeoForge version mismatch: manifest requires " + loaderVersion
                    + ". Install NeoForge " + loaderVersion
                    + " from https://neoforged.net/"));
        }

        return List.of(new Warning(Severity.LOUD,
                "No NeoForge profile found. Install NeoForge " + loaderVersion
                + " from https://neoforged.net/"));
    }
}
