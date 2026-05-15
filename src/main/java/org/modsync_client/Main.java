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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

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

        StateStore store = new StateStore(stateFile);
        State state;
        try {
            state = store.load();
        } catch (IOException e) {
            System.err.println("Error loading state: " + e.getMessage());
            System.exit(1);
            return;
        }

        Path modsDir = minecraftDir.resolve("mods");
        State.PackState packState = state.packs.get(manifest.packName);

        try {
            if (SyncPlanner.isUpToDate(manifest, modsDir, packState)) {
                System.out.println();
                System.out.println("Already up to date.");
                return;
            }
        } catch (IOException e) {
            System.err.println("Error checking state: " + e.getMessage());
            System.exit(1);
            return;
        }

        SyncPlan plan;
        try {
            plan = SyncPlanner.plan(manifest, modsDir, packState);
        } catch (IOException e) {
            System.err.println("Error planning sync: " + e.getMessage());
            System.exit(1);
            return;
        }

        if (!plan.collisions().isEmpty()) {
            System.out.println();
            System.out.println("The following files exist in mods/ but were not placed by this tool:");
            for (SyncPlan.Collision c : plan.collisions()) {
                System.out.println("  " + c.manifestEntry().filename + " (local hash: " + c.existingHash().substring(0, 8) + "...)");
            }
            if (!args.yes()) {
                System.out.print("Overwrite them? [y/N] ");
                String answer = new Scanner(System.in).nextLine().trim();
                if (!answer.equalsIgnoreCase("y")) {
                    System.out.println("Aborted.");
                    return;
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
            return;
        }

        if (!args.yes()) {
            boolean hasRemovals = !plan.toRemove().isEmpty();
            if (hasRemovals) {
                System.out.print("\nType 'yes' to proceed (files will be deleted): ");
                String answer = new Scanner(System.in).nextLine().trim();
                if (!answer.equals("yes")) {
                    System.out.println("Aborted.");
                    return;
                }
            } else {
                System.out.print("\nProceed? [Y/n] ");
                String answer = new Scanner(System.in).nextLine().trim();
                if (answer.equalsIgnoreCase("n")) {
                    System.out.println("Aborted.");
                    return;
                }
            }
        }

        Optional<State.PackState> newPackState;
        try {
            newPackState = new SyncExecutor(modsDir, args.yes(), args.dryRun())
                    .execute(plan, manifest, args.manifestUrl());
        } catch (IOException e) {
            System.err.println("Sync failed: " + e.getMessage());
            System.exit(1);
            return;
        }

        newPackState.ifPresent(ps -> {
            state.packs.put(manifest.packName, ps);
            try {
                store.save(state);
            } catch (IOException e) {
                System.err.println("Warning: failed to save state: " + e.getMessage());
            }
        });

        System.out.println();
        System.out.println("Sync complete.");
    }
}
