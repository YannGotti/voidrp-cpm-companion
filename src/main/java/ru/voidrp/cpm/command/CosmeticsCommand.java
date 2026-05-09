package ru.voidrp.cpm.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import ru.voidrp.cpm.CosmeticsManager;
import ru.voidrp.cpm.PlayerDataStore;

import java.util.Set;

public class CosmeticsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CosmeticsManager manager,
                                PlayerDataStore store) {
        SuggestionProvider<CommandSourceStack> allModels = (ctx, builder) -> {
            manager.getModelNames().forEach(builder::suggest);
            return builder.buildFuture();
        };

        SuggestionProvider<CommandSourceStack> ownedModels = (ctx, builder) -> {
            CommandSourceStack src = ctx.getSource();
            if (src.getEntity() instanceof ServerPlayer p) {
                store.getOwned(p.getStringUUID()).forEach(builder::suggest);
            }
            return builder.buildFuture();
        };

        dispatcher.register(Commands.literal("vc")
            // /vc grant <player> <cosmetic>  — OP only
            .then(Commands.literal("grant")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("cosmetic", StringArgumentType.word())
                        .suggests(allModels)
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            String cosmetic = StringArgumentType.getString(ctx, "cosmetic");
                            if (!manager.hasModel(cosmetic)) {
                                ctx.getSource().sendFailure(Component.literal("Модель '" + cosmetic + "' не найдена в config/voidrp-cpm/models/"));
                                return 0;
                            }
                            store.grant(target.getStringUUID(), cosmetic);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "✔ Косметика '" + cosmetic + "' выдана игроку " + target.getName().getString()), true);
                            return 1;
                        })
                    )
                )
            )
            // /vc revoke <player> <cosmetic>  — OP only
            .then(Commands.literal("revoke")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("cosmetic", StringArgumentType.word())
                        .suggests(allModels)
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            String cosmetic = StringArgumentType.getString(ctx, "cosmetic");
                            store.revoke(target.getStringUUID(), cosmetic);
                            manager.resetCosmetic(target);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "✔ Косметика '" + cosmetic + "' отозвана у " + target.getName().getString()), true);
                            return 1;
                        })
                    )
                )
            )
            // /vc apply <cosmetic>  — игрок применяет купленную косметику
            .then(Commands.literal("apply")
                .then(Commands.argument("cosmetic", StringArgumentType.word())
                    .suggests(ownedModels)
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                            ctx.getSource().sendFailure(Component.literal("Только для игроков"));
                            return 0;
                        }
                        String cosmetic = StringArgumentType.getString(ctx, "cosmetic");
                        String uuid = player.getStringUUID();
                        if (!store.ownsCosmetic(uuid, cosmetic)) {
                            player.sendSystemMessage(Component.literal("§cУ вас нет косметики '" + cosmetic + "'"));
                            return 0;
                        }
                        if (!manager.hasModel(cosmetic)) {
                            player.sendSystemMessage(Component.literal("§cМодель '" + cosmetic + "' временно недоступна"));
                            return 0;
                        }
                        manager.applyCosmetic(player, cosmetic);
                        store.setActive(uuid, cosmetic);
                        player.sendSystemMessage(Component.literal("§aКосметика '" + cosmetic + "' применена!"));
                        return 1;
                    })
                )
            )
            // /vc remove  — снять активную косметику
            .then(Commands.literal("remove")
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                        ctx.getSource().sendFailure(Component.literal("Только для игроков"));
                        return 0;
                    }
                    manager.resetCosmetic(player);
                    store.clearActive(player.getStringUUID());
                    player.sendSystemMessage(Component.literal("§7Косметика снята."));
                    return 1;
                })
            )
            // /vc list  — список доступных косметик игрока
            .then(Commands.literal("list")
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                        ctx.getSource().sendFailure(Component.literal("Только для игроков"));
                        return 0;
                    }
                    Set<String> owned = store.getOwned(player.getStringUUID());
                    if (owned.isEmpty()) {
                        player.sendSystemMessage(Component.literal("§7У вас нет косметик. Купите на нашем сайте!"));
                    } else {
                        String active = store.getActive(player.getStringUUID());
                        player.sendSystemMessage(Component.literal("§6Ваши косметики:"));
                        for (String c : owned) {
                            String marker = c.equals(active) ? " §a(активна)" : "";
                            player.sendSystemMessage(Component.literal("  §e" + c + marker));
                        }
                        player.sendSystemMessage(Component.literal("§7Применить: /vc apply <название>"));
                    }
                    return 1;
                })
            )
            // /vc reload  — перезагрузить модели с диска, OP only
            .then(Commands.literal("reload")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> {
                    manager.loadModels();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§a[VoidRpCpm] Загружено моделей: " + manager.getModelCount()), false);
                    return 1;
                })
            )
        );
    }
}
