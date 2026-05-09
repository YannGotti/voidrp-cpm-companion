package ru.voidrp.cpm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

public class PlayerDataStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("VoidRpCpm");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Entry>>() {}.getType();

    public static class Entry {
        public Set<String> owned = new HashSet<>();
        public String active = null;
    }

    private final File dataFile;
    private Map<String, Entry> data = new HashMap<>();

    public PlayerDataStore() {
        File dir = new File("config/voidrp-cpm");
        dir.mkdirs();
        this.dataFile = new File(dir, "players.json");
        load();
    }

    private synchronized void load() {
        if (!dataFile.exists()) return;
        try (Reader r = new FileReader(dataFile)) {
            Map<String, Entry> loaded = GSON.fromJson(r, MAP_TYPE);
            if (loaded != null) data = loaded;
        } catch (Exception e) {
            LOGGER.error("[VoidRpCpm] Failed to load players.json: {}", e.getMessage());
        }
    }

    private synchronized void save() {
        try (Writer w = new FileWriter(dataFile)) {
            GSON.toJson(data, w);
        } catch (Exception e) {
            LOGGER.error("[VoidRpCpm] Failed to save players.json: {}", e.getMessage());
        }
    }

    private Entry getOrCreate(String uuid) {
        return data.computeIfAbsent(uuid, k -> new Entry());
    }

    public synchronized boolean ownsCosmetic(String uuid, String cosmetic) {
        Entry e = data.get(uuid);
        return e != null && e.owned.contains(cosmetic);
    }

    public synchronized Set<String> getOwned(String uuid) {
        Entry e = data.get(uuid);
        return e == null ? Collections.emptySet() : Collections.unmodifiableSet(e.owned);
    }

    public synchronized String getActive(String uuid) {
        Entry e = data.get(uuid);
        return e == null ? null : e.active;
    }

    public synchronized void grant(String uuid, String cosmetic) {
        getOrCreate(uuid).owned.add(cosmetic);
        save();
    }

    public synchronized void revoke(String uuid, String cosmetic) {
        Entry e = data.get(uuid);
        if (e == null) return;
        e.owned.remove(cosmetic);
        if (cosmetic.equals(e.active)) e.active = null;
        save();
    }

    public synchronized void setActive(String uuid, String cosmetic) {
        getOrCreate(uuid).active = cosmetic;
        save();
    }

    public synchronized void clearActive(String uuid) {
        Entry e = data.get(uuid);
        if (e != null) { e.active = null; save(); }
    }
}
