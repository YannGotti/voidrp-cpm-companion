package ru.voidrp.cpm;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

public class CosmeticsConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("VoidRpCpm");
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private final File configFile;
    // itemName -> slot (e.g. "hat_tophat" -> "head")
    private Map<String, String> itemSlots = new HashMap<>();

    public CosmeticsConfig() {
        File dir = new File("config/voidrp-cpm");
        dir.mkdirs();
        this.configFile = new File(dir, "cosmetics.json");
        if (!configFile.exists()) writeExample();
        load();
    }

    private void writeExample() {
        Map<String, String> example = new LinkedHashMap<>();
        example.put("hat_example", "head");
        example.put("body_example", "body");
        example.put("pants_example", "legs");
        example.put("feet_example", "feet");
        example.put("wings_example", "accessory");
        try (Writer w = new FileWriter(configFile)) {
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(example, w);
        } catch (IOException e) {
            LOGGER.error("[VoidRpCpm] Could not write cosmetics.json: {}", e.getMessage());
        }
    }

    public void load() {
        if (!configFile.exists()) return;
        try (Reader r = new FileReader(configFile)) {
            Map<String, String> loaded = GSON.fromJson(r, MAP_TYPE);
            if (loaded != null) itemSlots = loaded;
            LOGGER.info("[VoidRpCpm] cosmetics.json loaded: {} items", itemSlots.size());
        } catch (Exception e) {
            LOGGER.error("[VoidRpCpm] Failed to load cosmetics.json: {}", e.getMessage());
        }
    }

    public String getSlot(String itemName) {
        return itemSlots.get(itemName);
    }

    public boolean isKnown(String itemName) {
        return itemSlots.containsKey(itemName);
    }

    public Set<String> getItemNames() {
        return Collections.unmodifiableSet(itemSlots.keySet());
    }

    public Set<String> getSlots() {
        return new HashSet<>(itemSlots.values());
    }
}
