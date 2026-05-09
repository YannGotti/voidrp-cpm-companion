package ru.voidrp.cpm;

import com.tom.cpm.api.ICommonAPI;
import com.tom.cpm.shared.io.ModelFile;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

public class CosmeticsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("VoidRpCpm");

    private final File modelsDir;
    private final Map<String, ModelFile> models = new LinkedHashMap<>();
    private ICommonAPI api;

    public CosmeticsManager() {
        this.modelsDir = new File("config/voidrp-cpm/models");
        this.modelsDir.mkdirs();
    }

    public void setApi(ICommonAPI api) {
        this.api = api;
    }

    public void loadModels() {
        models.clear();
        File[] files = modelsDir.listFiles(
            (dir, name) -> name.endsWith(".cpmmodel") || name.endsWith(".cpm")
        );
        if (files == null) return;
        for (File f : files) {
            String name = f.getName().replaceAll("\\.(cpmmodel|cpm)$", "");
            try (InputStream is = new FileInputStream(f)) {
                models.put(name, ModelFile.load(is));
                LOGGER.info("[VoidRpCpm] Loaded model: {}", name);
            } catch (IOException e) {
                LOGGER.error("[VoidRpCpm] Failed to load {}: {}", f.getName(), e.getMessage());
            }
        }
    }

    public int getModelCount() { return models.size(); }
    public Set<String> getModelNames() { return Collections.unmodifiableSet(models.keySet()); }
    public boolean hasModel(String name) { return models.containsKey(name); }

    public boolean applyCosmetic(ServerPlayer player, String name) {
        if (api == null || !models.containsKey(name)) return false;
        api.setPlayerModel(ServerPlayer.class, player, models.get(name), true);
        return true;
    }

    public void resetCosmetic(ServerPlayer player) {
        if (api == null) return;
        api.resetPlayerModel(ServerPlayer.class, player);
    }

    public void applyOnLogin(ServerPlayer player, PlayerDataStore store) {
        if (api == null) return;
        String uuid = player.getStringUUID();
        String active = store.getActive(uuid);
        if (active != null && store.ownsCosmetic(uuid, active) && models.containsKey(active)) {
            applyCosmetic(player, active);
        } else {
            resetCosmetic(player);
        }
    }
}
