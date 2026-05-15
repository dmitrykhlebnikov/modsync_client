package org.modsync_client.manifest;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Manifest {

    @SerializedName("pack_name")
    public String packName;

    @SerializedName("pack_version")
    public String packVersion;

    @SerializedName("minecraft_version")
    public String minecraftVersion;

    public List<ModEntry> mods;
    public Loader loader;

    public static class ModEntry {
        public String filename;
        public String url;
        public String sha256;
    }

    public static class Loader {
        public String type;
        public String version;
    }
}
