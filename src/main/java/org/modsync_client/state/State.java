package org.modsync_client.state;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class State {

    public Map<String, PackState> packs = new HashMap<>();

    public static class PackState {
        @SerializedName("manifest_url")
        public String manifestUrl;

        @SerializedName("pack_version")
        public String packVersion;

        @SerializedName("managed_jars")
        public List<ManagedJar> managedJars = new ArrayList<>();

        @SerializedName("last_synced")
        public String lastSynced;

        public static class ManagedJar {
            public String filename;
            public String sha256;
        }
    }
}
