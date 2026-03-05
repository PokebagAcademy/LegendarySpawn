package fr.combblemon.legendaryspawner;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class LegendaryCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            dispatcher.register(
                literal("legendaryspawner")
                .requires(src -> src.hasPermissionLevel(2)) // Op niveau 2
                .then(literal("forcespawn")
                    .executes(ctx -> {
                        SpawnController ctrl = LegendarySpawnerMod.getInstance().getSpawnController();
                        if (ctrl == null) {
                            send(ctx.getSource(), "§cLe mod n'est pas encore initialisé.", false);
                            return 0;
                        }
                        send(ctx.getSource(), "§6[LS] §eForçage du spawn d'un légendaire...", false);
                        ctrl.forceSpawn();
                        return 1;
                    })
                )
                .then(literal("reload")
                    .executes(ctx -> {
                        LegendarySpawnerMod.getInstance().reloadConfig();
                        send(ctx.getSource(), "§6[LS] §aConfig rechargée et timer reset !", false);
                        return 1;
                    })
                )
                .then(literal("setinterval")
                    .then(argument("minutes", IntegerArgumentType.integer(1, 1440))
                        .executes(ctx -> {
                            int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                            ModConfig cfg = LegendarySpawnerMod.getInstance().getConfig();
                            cfg.intervalMinutes = minutes;
                            cfg.save();
                            LegendarySpawnerMod.getInstance().reloadConfig();
                            send(ctx.getSource(), "§6[LS] §aIntervalle mis à §e" + minutes + " minutes §a!", false);
                            return 1;
                        })
                    )
                )
                .then(literal("timer")
                    .executes(ctx -> {
                        SpawnController ctrl = LegendarySpawnerMod.getInstance().getSpawnController();
                        if (ctrl == null) {
                            send(ctx.getSource(), "§cNon initialisé.", false);
                            return 0;
                        }
                        long ticks = ctrl.getTicksRemaining();
                        long seconds = ticks / 20;
                        long minutes = seconds / 60;
                        long secs = seconds % 60;
                        send(ctx.getSource(), String.format("§6[LS] §eProchain spawn dans §b%d min %d sec§e.", minutes, secs), false);
                        return 1;
                    })
                )
                .then(literal("help")
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        send(src, "§6§l=== LegendarySpawner ===", false);
                        send(src, "§e/ls forcespawn §7- Force un spawn immédiat", false);
                        send(src, "§e/ls reload §7- Recharge la config JSON", false);
                        send(src, "§e/ls setinterval <min> §7- Change l'intervalle", false);
                        send(src, "§e/ls timer §7- Temps restant avant prochain spawn", false);
                        return 1;
                    })
                )
            );

            // Alias /ls
            dispatcher.register(
                literal("ls")
                .requires(src -> src.hasPermissionLevel(2))
                .redirect(dispatcher.getRoot().getChild("legendaryspawner"))
            );
        });
    }

    private static void send(ServerCommandSource source, String message, boolean actionBar) {
        source.sendMessage(Text.literal(message));
    }
}
