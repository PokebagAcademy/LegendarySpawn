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
 *     timer + chance globale d'apparition ce cycle + liste des légendaires éligibles
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

        ModConfig config      = LegendarySpawnerMod.getInstance().getConfig();
        ChanceTracker tracker = LegendarySpawnerMod.getInstance().getChanceTracker();
        LangConfig lang       = LegendarySpawnerMod.getInstance().getLang();

        long ticks  = ctrl.getTicksRemaining();
        long secs   = ticks / 20;
        String minStr = String.valueOf(secs / 60);
        String secStr = String.format("%02d", secs % 60);

        boolean isAdmin = PermissionManager.check(src, PermissionManager.NEXTLEG_DETAILS, 2);
        Entity caller   = src.getEntity();

        if (isAdmin) {
            // ── Vue admin : tous les joueurs ──
            List<ServerPlayerEntity> players = src.getServer().getPlayerManager().getPlayerList();

            send(src, lang.get("nextleg.header", "minutes", minStr, "seconds", secStr));
            send(src, lang.get("nextleg.separator"));

            int totalEligible = 0;
            for (ServerPlayerEntity player : players) {
                List<String> eligible = ctrl.buildEligibleNames(player);
                double combined = combinedChance(eligible, tracker, config);
                String ctx = buildContextString(player);

                send(src, lang.get("nextleg.player_header",
                        "player", player.getName().getString(),
                        "context", ctx,
                        "combined", String.format("%.1f", combined)));

                if (eligible.isEmpty()) {
                    send(src, lang.get("nextleg.no_eligible_player"));
                } else {
                    eligible.sort((a, b) -> Double.compare(
                            tracker.getCurrentChance(b, config.legendaries.get(b), config),
                            tracker.getCurrentChance(a, config.legendaries.get(a), config)));

                    for (String name : eligible) {
                        double chance = tracker.getCurrentChance(name, config.legendaries.get(name), config);
                        double bonus  = tracker.getBonus(name);
                        String bonusStr = bonus > 0 ? String.format(" §8(+%.1f%% acc.)", bonus) : "";
                        send(src, lang.get("nextleg.eligible_entry",
                                "pokemon", SpawnController.formatName(name),
                                "chance", String.format("%.1f", chance),
                                "bonus", bonusStr));
                        totalEligible++;
                    }
                }
            }

            send(src, lang.get("nextleg.separator"));
            send(src, lang.get("nextleg.total", "count", String.valueOf(totalEligible)));

        } else if (caller instanceof ServerPlayerEntity player) {
            // ── Vue joueur : ses propres données ──
            List<String> eligible = ctrl.buildEligibleNames(player);
            double combined = combinedChance(eligible, tracker, config);

            send(src, lang.get("nextleg.header", "minutes", minStr, "seconds", secStr));
            send(src, lang.get("nextleg.combined_chance", "chance", String.format("%.1f", combined)));

            if (eligible.isEmpty()) {
                send(src, lang.get("nextleg.own_none"));
            } else {
                send(src, lang.get("nextleg.separator"));
                eligible.sort((a, b) -> Double.compare(
                        tracker.getCurrentChance(b, config.legendaries.get(b), config),
                        tracker.getCurrentChance(a, config.legendaries.get(a), config)));

                for (String name : eligible) {
                    double chance = tracker.getCurrentChance(name, config.legendaries.get(name), config);
                    double bonus  = tracker.getBonus(name);
                    String bonusStr = bonus > 0 ? String.format(" §8(+%.1f%% acc.)", bonus) : "";
                    send(src, lang.get("nextleg.eligible_entry",
                            "pokemon", SpawnController.formatName(name),
                            "chance", String.format("%.1f", chance),
                            "bonus", bonusStr));
                }
            }
        } else {
            // Console sans détails : juste le timer
            send(src, lang.get("nextleg.header", "minutes", minStr, "seconds", secStr));
        }

        return 1;
    }

    /**
     * Calcule la probabilité qu'au moins un légendaire spawne parmi la liste d'éligibles.
     * P(au moins un) = 1 - ∏(1 - pᵢ)
     */
    private static double combinedChance(List<String> eligible, ChanceTracker tracker, ModConfig config) {
        double noSpawn = 1.0;
        for (String name : eligible) {
            double p = tracker.getCurrentChance(name, config.legendaries.get(name), config) / 100.0;
            noSpawn *= (1.0 - p);
        }
        return (1.0 - noSpawn) * 100.0;
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
        if (player.getServerWorld().isThundering())   weatherStr = "orage";
        else if (player.getServerWorld().isRaining()) weatherStr = "pluie";
        else                                           weatherStr = "clair";
        return dimStr + " • " + timeStr + " • " + weatherStr;
    }

    private static void send(ServerCommandSource src, String msg) {
        src.sendMessage(Text.literal(msg));
    }
}
