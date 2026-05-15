package org.modsync_client;

import org.modsync_client.loader.LauncherProfilesReader;
import org.modsync_client.loader.LoaderCheck;
import org.modsync_client.manifest.Manifest;
import org.modsync_client.manifest.ManifestFetcher;
import org.modsync_client.state.State;
import org.modsync_client.state.StateStore;
import org.modsync_client.sync.SyncExecutor;
import org.modsync_client.sync.SyncPlan;
import org.modsync_client.sync.SyncPlanner;
import org.modsync_client.util.Console;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

public class Main {

    public static void main(String[] argv) {
        boolean noPause = Arrays.asList(argv).contains("--no-pause");
        int exitCode = 0;
        try {
            exitCode = run(argv);
        } catch (Throwable t) {
            System.err.println("Fatal error: " + t.getMessage());
            tryWriteLog(t);
            exitCode = 1;
        } finally {
            Console.pause(noPause);
        }
        if (exitCode != 0) System.exit(exitCode);
    }

    private static int run(String[] argv) throws Exception {
        Args args;
        try {
            args = Args.parse(argv);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return 1;
        }

        Path minecraftDir;
        Path stateFile;
        Path logFile;
        try {
            minecraftDir = Paths.resolveMinecraftDir(args);
            stateFile    = Paths.resolveStateFile();
            logFile      = Paths.resolveLogFile();
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            return 1;
        }

        System.out.println("Manifest URL : " + args.manifestUrl());
        System.out.println("Minecraft dir: " + minecraftDir);
        System.out.println("State file   : " + stateFile);

        Manifest manifest;
        try {
            manifest = ManifestFetcher.fetch(args.manifestUrl());
        } catch (IllegalStateException | IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
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

        StateStore store = new StateStore(stateFile);
        State state;
        try {
            state = store.load();
        } catch (IOException e) {
            System.err.println("Error loading state: " + e.getMessage());
            return 1;
        }

        Path modsDir = minecraftDir.resolve("mods");
        State.PackState packState = state.packs.get(manifest.packName);

        try {
            if (SyncPlanner.isUpToDate(manifest, modsDir, packState)) {
                System.out.println();
                System.out.println("Already up to date.");
                Console.appendLog(logFile, "Already up to date: " + manifest.packName + " " + manifest.packVersion);
                return 0;
            }
        } catch (IOException e) {
            System.err.println("Error checking state: " + e.getMessage());
            return 1;
        }

        SyncPlan plan;
        try {
            plan = SyncPlanner.plan(manifest, modsDir, packState);
        } catch (IOException e) {
            System.err.println("Error planning sync: " + e.getMessage());
            return 1;
        }

        if (!plan.collisions().isEmpty()) {
            System.out.println();
            System.out.println("The following files exist in mods/ but were not placed by this tool:");
            for (SyncPlan.Collision c : plan.collisions()) {
                System.out.println("  " + c.manifestEntry().filename
                        + " (local hash: " + c.existingHash().substring(0, 8) + "...)");
            }
            if (!args.yes()) {
                System.out.print("Overwrite them? [y/N] ");
                String answer = new Scanner(System.in).nextLine().trim();
                if (!answer.equalsIgnoreCase("y")) {
                    System.out.println("Aborted.");
                    return 0;
                }
            }
        }

        System.out.println();
        if (!plan.toAdd().isEmpty()) {
            System.out.println("To download (" + plan.toAdd().size() + "):");
            for (var e : plan.toAdd()) System.out.println("  + " + e.filename);
        }
        if (!plan.toRemove().isEmpty()) {
            System.out.println("To remove (" + plan.toRemove().size() + "):");
            for (var e : plan.toRemove()) System.out.println("  - " + e.filename);
        }
        if (!plan.toKeep().isEmpty()) {
            System.out.println("Up to date (" + plan.toKeep().size() + "):");
            for (var e : plan.toKeep()) System.out.println("  = " + e.filename);
        }

        if (args.dryRun()) {
            System.out.println();
            System.out.println("Dry run — no changes made.");
            return 0;
        }

        if (!args.yes()) {
            boolean hasRemovals = !plan.toRemove().isEmpty();
            if (hasRemovals) {
                System.out.print("\nType 'yes' to proceed (files will be deleted): ");
                String answer = new Scanner(System.in).nextLine().trim();
                if (!answer.equals("yes")) {
                    System.out.println("Aborted.");
                    return 0;
                }
            } else {
                System.out.print("\nProceed? [Y/n] ");
                String answer = new Scanner(System.in).nextLine().trim();
                if (answer.equalsIgnoreCase("n")) {
                    System.out.println("Aborted.");
                    return 0;
                }
            }
        }

        Optional<State.PackState> newPackState;
        try {
            newPackState = new SyncExecutor(modsDir, args.yes(), args.dryRun())
                    .execute(plan, manifest, args.manifestUrl());
        } catch (IOException e) {
            System.err.println("Sync failed: " + e.getMessage());
            try { Console.appendLog(logFile, "Sync failed", e); } catch (IOException ignored) {}
            return 1;
        }

        newPackState.ifPresent(ps -> {
            state.packs.put(manifest.packName, ps);
            try {
                store.save(state);
                Console.appendLog(logFile, "Sync complete: " + manifest.packName + " " + manifest.packVersion);
            } catch (IOException e) {
                System.err.println("Warning: failed to save state: " + e.getMessage());
            }
        });

        System.out.println();
        System.out.println("Sync complete.");
        return 0;
    }

    private static void tryWriteLog(Throwable t) {
        try {
            Console.appendLog(Paths.resolveLogFile(), "Fatal error: " + t.getMessage(), t);
        } catch (Throwable ignored) {}
    }
}
