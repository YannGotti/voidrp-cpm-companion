package ru.voidrp.cpm;

import com.mojang.logging.LogUtils;
import com.tom.cpm.api.ICPMPlugin;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import ru.voidrp.cpm.command.CosmeticsCommand;

import java.util.function.Supplier;

@Mod(VoidRpCpm.MOD_ID)
public class VoidRpCpm {
    public static final String MOD_ID = "voidrp_cpm";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static CosmeticsManager cosmeticsManager;
    private static PlayerDataStore playerDataStore;
    private static CosmeticsConfig cosmeticsConfig;

    public VoidRpCpm(IEventBus modBus, ModContainer container) {
        modBus.addListener(this::setup);
        modBus.addListener(this::enqueueIMC);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onCommand);
    }

    private void setup(FMLCommonSetupEvent event) {
        cosmeticsManager = new CosmeticsManager();
        playerDataStore = new PlayerDataStore();
        cosmeticsConfig = new CosmeticsConfig();
        cosmeticsManager.loadWardrobe();
    }

    private void enqueueIMC(InterModEnqueueEvent event) {
        InterModComms.sendTo("cpm", "api", () -> (Supplier<ICPMPlugin>) CpmCompat::new);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CosmeticsCommand.register(event.getDispatcher(), cosmeticsManager, playerDataStore, cosmeticsConfig);
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getServer().execute(() -> cosmeticsManager.applyOnLogin(player, playerDataStore));
        }
    }

    private void onCommand(CommandEvent event) {
        String input = event.getParseResults().getReader().getString().trim();
        if (input.startsWith("cpmclient") || input.startsWith("cpm ") || input.equals("cpm")) {
            if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player) {
                if (!hasPermission(player)) {
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("§cУ вас нет доступа к командам CPM"));
                }
            }
        }
    }

    public static boolean hasPermission(ServerPlayer player) {
        if (player.hasPermissions(2)) return true;
        if (playerDataStore == null) return false;
        return !playerDataStore.getOwned(player.getStringUUID()).isEmpty();
    }

    public static CosmeticsManager getCosmeticsManager() { return cosmeticsManager; }
    public static PlayerDataStore getPlayerDataStore() { return playerDataStore; }
    public static CosmeticsConfig getCosmeticsConfig() { return cosmeticsConfig; }
}
