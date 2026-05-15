package org.modsync_client;

import java.nio.file.Path;

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
    }
}