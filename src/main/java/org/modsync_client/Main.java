package org.modsync_client;

import org.modsync_client.loader.LauncherProfilesReader;
import org.modsync_client.loader.LoaderCheck;
import org.modsync_client.manifest.Manifest;
import org.modsync_client.manifest.ManifestFetcher;

import java.nio.file.Path;
import java.util.List;

public class Main {

    public static void main(String[] argv) {
        Args args;
        try {
            args = Args.parse(argv);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        Path minecraftDir;
        Path stateFile;
        try {
            minecraftDir = Paths.resolveMinecraftDir(args);
            stateFile    = Paths.resolveStateFile();
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        System.out.println("Manifest URL : " + args.manifestUrl());
        System.out.println("Minecraft dir: " + minecraftDir);
        System.out.println("State file   : " + stateFile);

        Manifest manifest;
        try {
            manifest = ManifestFetcher.fetch(args.manifestUrl());
        } catch (IllegalStateException | IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
            return;
        }

        System.out.println();
        System.out.println("Pack         : " + manifest.packName);
        System.out.println("Version      : " + manifest.packVersion);
        System.out.println("Mods         : " + manifest.mods.size());
        System.out.println("Loader       : " + manifest.loader.type
                + " " + manifest.loader.version
                + " / MC " + manifest.minecraftVersion);

        List<String> lastVersionIds = LauncherProfilesReader.read(minecraftDir);
        if (lastVersionIds.isEmpty()) {
            System.out.println();
            System.out.println("WARNING: Minecraft launcher not found — open it once first.");
        }
        List<LoaderCheck.Warning> warnings = LoaderCheck.check(manifest, lastVersionIds);
        for (LoaderCheck.Warning w : warnings) {
            System.out.println();
            String prefix = w.severity() == LoaderCheck.Severity.LOUD ? "WARNING: " : "Note: ";
            System.out.println(prefix + w.message());
        }
    }
}