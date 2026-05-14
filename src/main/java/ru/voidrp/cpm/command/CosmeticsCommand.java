package ru.voidrp.cpm.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import ru.voidrp.cpm.CosmeticsConfig;
import ru.voidrp.cpm.CosmeticsManager;
import ru.voidrp.cpm.PlayerDataStore;

import java.util.Map;
import java.util.Set;

public class CosmeticsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CosmeticsManager manager,
                                PlayerDataStore store,
                                CosmeticsConfig config) {

        SuggestionProvider<CommandSourceStack> allItems = (ctx, b) -> {
            config.getItemNames().forEach(b::suggest);
            return b.buildFuture();
        };

        SuggestionProvider<CommandSourceStack> ownedItems = (ctx, b) -> {
            if (ctx.getSource().getEntity() instanceof ServerPlayer p)
                store.getOwned(p.getStringUUID()).forEach(b::suggest);
            return b.buildFuture();
        };

        SuggestionProvider<CommandSourceStack> equippedSlots = (ctx, b) -> {
            if (ctx.getSource().getEntity() instanceof ServerPlayer p)
                store.getSlots(p.getStringUUID()).keySet().forEach(b::suggest);
            return b.buildFuture();
        };

        dispatcher.register(Commands.literal("vc")

            // /vc grant <player> <item>  — OP only
            .then(Commands.literal("grant")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("item", StringArgumentType.word())
                        .suggests(allItems)
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            String item = StringArgumentType.getString(ctx, "item");
                            if (!config.isKnown(item)) {
                                ctx.getSource().sendFailure(Component.literal("Предмет '" + item + "' не найден в cosmetics.json"));
                                return 0;
                            }
                            store.grant(target.getStringUUID(), item);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "✔ Косметика '" + item + "' ([" + config.getSlot(item) + "]) выдана " + target.getName().getString()), true);
                            return 1;
                        })
                    )
                )
            )

            // /vc revoke <player> <item>  — OP only
            .then(Commands.literal("revoke")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("item", StringArgumentType.word())
                        .suggests(allItems)
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            String item = StringArgumentType.getString(ctx, "item");
                            // Unequip from slot if currently worn
                            String slot = config.getSlot(item);
                            if (slot != null) {
                                String slotItem = store.getSlotItem(target.getStringUUID(), slot);
                                if (item.equals(slotItem)) manager.unequip(target, slot, store);
                            }
                            store.revoke(target.getStringUUID(), item);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "✔ Косметика '" + item + "' отозвана у " + target.getName().getString()), true);
                            return 1;
                        })
                    )
                )
            )

            // /vc equip <item>  — надеть вещь в её слот
            .then(Commands.literal("equip")
                .requires(s -> true)
                .then(Commands.argument("item", StringArgumentType.word())
                    .suggests(ownedItems)
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                            ctx.getSource().sendFailure(Component.literal("Только для игроков"));
                            return 0;
                        }
                        String item = StringArgumentType.getString(ctx, "item");
                        String uuid = player.getStringUUID();

                        if (!store.ownsCosmetic(uuid, item)) {
                            player.sendSystemMessage(Component.literal("§cУ вас нет косметики '" + item + "'"));
                            return 0;
                        }
                        String slot = config.getSlot(item);
                        if (slot == null) {
                            player.sendSystemMessage(Component.literal("§cПредмет '" + item + "' не настроен в cosmetics.json"));
                            return 0;
                        }
                        if (!manager.isReady()) {
                            player.sendSystemMessage(Component.literal("§cwardrobe.cpmmodel не загружена на сервере"));
                            return 0;
                        }
                        manager.equip(player, item, slot, store);
                        player.sendSystemMessage(Component.literal("§a[" + slot + "] Надели: " + item));
                        return 1;
                    })
                )
            )

            // /vc unequip <slot>  — снять вещь из слота
            .then(Commands.literal("unequip")
                .requires(s -> true)
                .then(Commands.argument("slot", StringArgumentType.word())
                    .suggests(equippedSlots)
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                            ctx.getSource().sendFailure(Component.literal("Только для игроков"));
                            return 0;
                        }
                        String slot = StringArgumentType.getString(ctx, "slot");
                        if (!manager.unequip(player, slot, store)) {
                            player.sendSystemMessage(Component.literal("§7В слоте '" + slot + "' ничего нет"));
                            return 0;
                        }
                        player.sendSystemMessage(Component.literal("§7[" + slot + "] Снято."));
                        return 1;
                    })
                )
            )

            // /vc list  — что есть и что надето
            .then(Commands.literal("list")
                .requires(s -> true)
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                        ctx.getSource().sendFailure(Component.literal("Только для игроков"));
                        return 0;
                    }
                    String uuid = player.getStringUUID();
                    Set<String> owned = store.getOwned(uuid);
                    Map<String, String> slots = store.getSlots(uuid);

                    if (owned.isEmpty()) {
                        player.sendSystemMessage(Component.literal("§7У вас нет косметик."));
                    } else {
                        player.sendSystemMessage(Component.literal("§6Ваши косметики:"));
                        for (String item : owned) {
                            String slot = config.getSlot(item);
                            String slotLabel = slot != null ? " §8[" + slot + "]" : "";
                            boolean equipped = item.equals(slots.get(slot));
                            String status = equipped ? " §a(надето)" : "";
                            player.sendSystemMessage(Component.literal("  §e" + item + slotLabel + status));
                        }
                        player.sendSystemMessage(Component.literal("§7/vc equip <название>  |  /vc unequip <слот>"));
                    }
                    return 1;
                })
            )

            // /vc reload  — OP only
            .then(Commands.literal("reload")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> {
                    manager.loadWardrobe();
                    config.load();
                    String status = manager.isReady() ? "§awardrobe.cpmmodel загружена" : "§cwardrobe.cpmmodel не найдена!";
                    ctx.getSource().sendSuccess(() -> Component.literal("[VoidRpCpm] " + status), false);
                    return 1;
                })
            )
        );
    }
}
