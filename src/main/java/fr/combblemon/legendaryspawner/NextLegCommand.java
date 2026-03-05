package fr.combblemon.legendaryspawner;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * /nextleg :
 * - Tous les joueurs (legendaryspawner.nextleg) :
 *     timer + chance globale d'apparition + liste des légendaires éligibles pour ce joueur
 * - Admins (legendaryspawner.nextleg.details) :
 *     idem pour tous les joueurs connectés
 */
public class NextLegCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                literal("nextleg")
                .requires(src -> PermissionManager.check(src, PermissionManager.NEXTLEG, 0))
                .executes(ctx -> execute(ctx.getSource()))
            )
        );
    }

    private static int execute(ServerCommandSource src) {
        SpawnController ctrl = LegendarySpawnerMod.getInstance().getSpawnController();
        if (ctrl == null) {
            send(src, LegendarySpawnerMod.getInstance().getLang().get("command.not_initialized"));
            return 0;
        }

        ModConfig config       = LegendarySpawnerMod.getInstance().getConfig();
        ChanceTracker tracker  = LegendarySpawnerMod.getInstance().getChanceTracker();
        LangConfig lang        = LegendarySpawnerMod.getInstance().getLang();

        long ticks   = ctrl.getTicksRemaining();
        long secs    = ticks / 20;
        String timer = secs / 60 + "m" + String.format("%02d", secs % 60) + "s";

        double currentChance = tracker.getCurrentChance(config);
        double bonus         = tracker.getGlobalBonus();
        String bonusStr      = bonus > 0
                ? String.format(" §8(base §7%.0f%%§8 +%.0f%% accumulé)", config.spawnChance, bonus)
                : "";

        boolean isAdmin = PermissionManager.check(src, PermissionManager.NEXTLEG_DETAILS, 2);
        Entity caller   = src.getEntity();

        if (isAdmin) {
            // ── Vue admin : tous les joueurs ──
            List<ServerPlayerEntity> players = src.getServer().getPlayerManager().getPlayerList();

            send(src, lang.get("nextleg.header", "timer", timer));
            send(src, lang.get("nextleg.combined_chance",
                    "chance", String.format("%.1f", currentChance), "bonus", bonusStr));
            send(src, lang.get("nextleg.separator"));

            for (ServerPlayerEntity player : players) {
                List<String> eligible = ctrl.buildEligibleNames(player);
                send(src, lang.get("nextleg.player_header",
                        "player", player.getName().getString(),
                        "context", buildContextString(player)));

                if (eligible.isEmpty()) {
                    send(src, lang.get("nextleg.no_eligible_player"));
                } else {
                    for (String name : eligible) {
                        send(src, lang.get("nextleg.eligible_entry",
                                "pokemon", SpawnController.formatName(name)));
                    }
                }
            }

        } else if (caller instanceof ServerPlayerEntity player) {
            // ── Vue joueur : ses propres données ──
            List<String> eligible = ctrl.buildEligibleNames(player);

            send(src, lang.get("nextleg.header", "timer", timer));
            send(src, lang.get("nextleg.combined_chance",
                    "chance", String.format("%.1f", currentChance), "bonus", bonusStr));

            if (eligible.isEmpty()) {
                send(src, lang.get("nextleg.own_none"));
            } else {
                send(src, lang.get("nextleg.separator"));
                for (String name : eligible) {
                    send(src, lang.get("nextleg.eligible_entry",
                            "pokemon", SpawnController.formatName(name)));
                }
            }
        } else {
            send(src, lang.get("nextleg.header", "timer", timer));
            send(src, lang.get("nextleg.combined_chance",
                    "chance", String.format("%.1f", currentChance), "bonus", bonusStr));
        }

        return 1;
    }

    private static String buildContextString(ServerPlayerEntity player) {
        String dim = player.getServerWorld().getRegistryKey().getValue().toString();
        String dimStr = switch (dim) {
            case "minecraft:overworld"  -> "overworld";
            case "minecraft:the_nether" -> "nether";
            case "minecraft:the_end"    -> "end";
            default -> dim;
        };
        String timeStr = player.getServerWorld().isDay() ? "jour" : "nuit";
        String weatherStr;
        if (player.getServerWorld().isThundering())      weatherStr = "orage";
        else if (player.getServerWorld().isRaining())    weatherStr = "pluie";
        else                                              weatherStr = "clair";
        return dimStr + " • " + timeStr + " • " + weatherStr;
    }

    private static void send(ServerCommandSource src, String msg) {
        src.sendMessage(Text.literal(msg));
    }
}
