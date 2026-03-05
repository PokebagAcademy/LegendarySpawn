package fr.combblemon.legendaryspawner;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Commande /nextleg :
 * - Tous les joueurs (legendaryspawner.nextleg) : timer + leurs légendaires éligibles
 * - Admins (legendaryspawner.nextleg.details) : timer + tous les joueurs et leurs éligibles
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

        ModConfig config   = LegendarySpawnerMod.getInstance().getConfig();
        ChanceTracker tracker = LegendarySpawnerMod.getInstance().getChanceTracker();
        LangConfig lang    = LegendarySpawnerMod.getInstance().getLang();

        long ticks   = ctrl.getTicksRemaining();
        long seconds = ticks / 20;
        String minStr = String.valueOf(seconds / 60);
        String secStr = String.valueOf(seconds % 60);

        boolean isAdmin = PermissionManager.check(src, PermissionManager.NEXTLEG_DETAILS, 2);
        Entity caller   = src.getEntity();

        send(src, lang.get("nextleg.header", "minutes", minStr, "seconds", secStr));
        send(src, lang.get("nextleg.separator"));

        if (isAdmin) {
            // Vue admin : tous les joueurs
            List<ServerPlayerEntity> players = src.getServer().getPlayerManager().getPlayerList();
            int totalEligible = 0;

            for (ServerPlayerEntity player : players) {
                List<String> eligible = ctrl.buildEligibleNames(player);
                String ctx  = buildContextString(player);
                send(src, lang.get("nextleg.player_header",
                        "player", player.getName().getString(),
                        "context", ctx));

                if (eligible.isEmpty()) {
                    send(src, lang.get("nextleg.no_eligible_player"));
                } else {
                    // Trier par chance décroissante
                    eligible.sort((a, b) -> Double.compare(
                            tracker.getCurrentChance(b, config.legendaries.get(b), config),
                            tracker.getCurrentChance(a, config.legendaries.get(a), config)));

                    for (String name : eligible) {
                        double current = tracker.getCurrentChance(name, config.legendaries.get(name), config);
                        double bonus   = tracker.getBonus(name);
                        String bonusStr = bonus > 0
                                ? String.format(" §8(+%.1f%% accumulé)", bonus)
                                : "";
                        send(src, lang.get("nextleg.eligible_entry",
                                "pokemon", SpawnController.formatName(name),
                                "chance", String.format("%.1f", current),
                                "bonus", bonusStr));
                        totalEligible++;
                    }
                }
            }

            send(src, lang.get("nextleg.separator"));
            send(src, lang.get("nextleg.total", "count", String.valueOf(totalEligible)));

        } else if (caller instanceof ServerPlayerEntity player) {
            // Vue joueur : ses propres éligibles
            List<String> eligible = ctrl.buildEligibleNames(player);

            if (eligible.isEmpty()) {
                send(src, lang.get("nextleg.own_none"));
            } else {
                send(src, lang.get("nextleg.own_header"));
                eligible.sort((a, b) -> Double.compare(
                        tracker.getCurrentChance(b, config.legendaries.get(b), config),
                        tracker.getCurrentChance(a, config.legendaries.get(a), config)));

                for (String name : eligible) {
                    double current = tracker.getCurrentChance(name, config.legendaries.get(name), config);
                    double bonus   = tracker.getBonus(name);
                    String bonusStr = bonus > 0
                            ? String.format(" §8(+%.1f%% accumulé)", bonus)
                            : "";
                    send(src, lang.get("nextleg.eligible_entry",
                            "pokemon", SpawnController.formatName(name),
                            "chance", String.format("%.1f", current),
                            "bonus", bonusStr));
                }
            }
        }

        return 1;
    }

    private static String buildContextString(ServerPlayerEntity player) {
        // Dimension
        String dim = player.getServerWorld().getRegistryKey().getValue().toString();
        String dimStr = switch (dim) {
            case "minecraft:overworld"  -> "overworld";
            case "minecraft:the_nether" -> "nether";
            case "minecraft:the_end"    -> "end";
            default -> dim;
        };

        // Heure
        String timeStr = player.getServerWorld().isDay() ? "jour" : "nuit";

        // Météo
        String weatherStr;
        if (player.getServerWorld().isThundering()) weatherStr = "orage";
        else if (player.getServerWorld().isRaining()) weatherStr = "pluie";
        else weatherStr = "clair";

        return dimStr + " • " + timeStr + " • " + weatherStr;
    }

    private static void send(ServerCommandSource src, String msg) {
        src.sendMessage(Text.literal(msg));
    }
}
