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
    private static final String WARDROBE_FILE = "wardrobe.cpmmodel";

    private final File modelsDir;
    private ModelFile wardrobeModel;
    private ICommonAPI api;

    public CosmeticsManager() {
        this.modelsDir = new File("config/voidrp-cpm/models");
        this.modelsDir.mkdirs();
    }

    public void setApi(ICommonAPI api) {
        this.api = api;
    }

    public void loadWardrobe() {
        wardrobeModel = null;
        File f = new File(modelsDir, WARDROBE_FILE);
        if (!f.exists()) {
            LOGGER.warn("[VoidRpCpm] wardrobe.cpmmodel not found in config/voidrp-cpm/models/ — cosmetics will not apply");
            return;
        }
        try (InputStream is = new FileInputStream(f)) {
            wardrobeModel = ModelFile.load(is);
            LOGGER.info("[VoidRpCpm] Loaded wardrobe model");
        } catch (IOException e) {
            LOGGER.error("[VoidRpCpm] Failed to load wardrobe.cpmmodel: {}", e.getMessage());
        }
    }

    public boolean isReady() {
        return api != null && wardrobeModel != null;
    }

    // Apply wardrobe model + re-enable all equipped layers
    public void applyOnLogin(ServerPlayer player, PlayerDataStore store) {
        if (api == null) return;
        String uuid = player.getStringUUID();
        Map<String, String> slots = store.getSlots(uuid);

        if (slots.isEmpty() || wardrobeModel == null) {
            api.resetPlayerModel(ServerPlayer.class, player);
            return;
        }

        api.setPlayerModel(ServerPlayer.class, player, wardrobeModel, true);

        // Re-activate equipped layers
        for (String item : slots.values()) {
            api.playAnimation(ServerPlayer.class, player, item, 1);
        }
    }

    // Equip item into its slot — disables old item in same slot first
    public boolean equip(ServerPlayer player, String itemName, String slot, PlayerDataStore store) {
        if (!isReady()) return false;
        String uuid = player.getStringUUID();

        // Apply wardrobe model if not yet applied (first cosmetic ever)
        api.setPlayerModel(ServerPlayer.class, player, wardrobeModel, true);

        // Disable current item in this slot if any
        String current = store.getSlotItem(uuid, slot);
        if (current != null) {
            api.playAnimation(ServerPlayer.class, player, current, 0);
        }

        // Enable new item
        api.playAnimation(ServerPlayer.class, player, itemName, 1);
        store.equip(uuid, slot, itemName);
        return true;
    }

    // Unequip slot — hide the layer
    public boolean unequip(ServerPlayer player, String slot, PlayerDataStore store) {
        if (api == null) return false;
        String uuid = player.getStringUUID();
        String current = store.getSlotItem(uuid, slot);
        if (current == null) return false;

        api.playAnimation(ServerPlayer.class, player, current, 0);
        store.unequip(uuid, slot);

        // If no more slots equipped, reset model
        if (store.getSlots(uuid).isEmpty()) {
            api.resetPlayerModel(ServerPlayer.class, player);
        }
        return true;
    }

    public void resetPlayer(ServerPlayer player) {
        if (api != null) api.resetPlayerModel(ServerPlayer.class, player);
    }
}
