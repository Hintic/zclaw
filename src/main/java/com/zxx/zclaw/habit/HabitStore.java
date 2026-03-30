package com.zxx.zclaw.habit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores habit shortcuts per soul under .zclaw/souls/<id>/habits.json.
 */
public final class HabitStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_VERSION = 1;

    private final Path workDir;

    public HabitStore(Path workDir) {
        this.workDir = workDir;
    }

    public Path stateFile(String soulId) {
        return workDir.resolve(".zclaw/souls").resolve(safeName(soulId)).resolve("habits.json");
    }

    public State load(String soulId) throws IOException {
        Path file = stateFile(soulId);
        if (!Files.exists(file)) {
            return new State(CURRENT_VERSION, new ArrayList<>());
        }
        String json = Files.readString(file);
        State s = GSON.fromJson(json, State.class);
        if (s == null) {
            return new State(CURRENT_VERSION, new ArrayList<>());
        }
        if (s.version <= 0) {
            s.version = CURRENT_VERSION;
        }
        if (s.items == null) {
            s.items = new ArrayList<>();
        }
        for (HabitItem item : s.items) {
            if (item.aliases == null) {
                item.aliases = new ArrayList<>();
            }
        }
        return s;
    }

    public void save(String soulId, State state) throws IOException {
        Path file = stateFile(soulId);
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(state));
    }

    private static String safeName(String soulId) {
        if (soulId == null || soulId.isBlank()) {
            return "default";
        }
        return soulId.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    public static final class State {
        public int version;
        public List<HabitItem> items;

        public State() {}

        public State(int version, List<HabitItem> items) {
            this.version = version;
            this.items = items;
        }
    }

    public static final class HabitItem {
        public String shortcut;
        public String canonicalIntent;
        public HabitState state;
        public List<String> aliases;
        public int confirmSuccessCount;
        public int autoSuccessCount;
        public int rejectCount;
        public long lastUsedAtEpochMs;
        public long cooldownUntilEpochMs;

        public HabitItem() {}
    }
}
