package fr.combblemon.legendaryspawner;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * /nextleg :
 * - Joueur (legendaryspawner.nextleg) : 2 lignes max — timer/chance + éligibles (liste sur 1 ligne)
 * - Admin (legendaryspawner.nextleg.details) : idem + 1 ligne par joueur en ligne
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

        // --- Timer ---
        long secs    = ctrl.getTicksRemaining() / 20;
        String timer = secs / 60 + "m" + String.format("%02d", secs % 60) + "s";

        // --- Chance ---
        double currentChance = tracker.getCurrentChance(config);
        double bonus         = tracker.getGlobalBonus();
        String bonusStr      = bonus > 0
                ? String.format(" §8(+%.0f%% acc.)", bonus)
                : "";

        // --- Mod en pause ? ---
        if (config.minPlayersToTick > 0) {
            int online = src.getServer().getPlayerManager().getCurrentPlayerCount();
            if (online < config.minPlayersToTick) {
                send(src, lang.get("nextleg.mod_paused", "min", String.valueOf(config.minPlayersToTick)));
                return 1;
            }
        }

        boolean isAdmin = PermissionManager.check(src, PermissionManager.NEXTLEG_DETAILS, 2);
        Entity caller   = src.getEntity();

        // Ligne 1 : timer + chance (commune à tous)
        send(src, lang.get("nextleg.header",
                "timer", timer,
                "chance", String.format("%.1f", currentChance),
                "bonus", bonusStr));

        if (isAdmin) {
            // ── Vue admin : une ligne par joueur ──
            for (ServerPlayerEntity player : src.getServer().getPlayerManager().getPlayerList()) {
                List<String> eligible = ctrl.buildEligibleNames(player);
                if (eligible.isEmpty()) {
                    send(src, lang.get("nextleg.player_none",
                            "player", player.getName().getString(),
                            "context", buildContextString(player)));
                } else {
                    send(src, lang.get("nextleg.player_line",
                            "player", player.getName().getString(),
                            "context", buildContextString(player),
                            "list", formatList(eligible)));
                }
            }

        } else if (caller instanceof ServerPlayerEntity player) {
            // ── Vue joueur : ses propres éligibles sur 1 ligne ──
            List<String> eligible = ctrl.buildEligibleNames(player);
            if (eligible.isEmpty()) {
                send(src, lang.get("nextleg.own_none"));
            } else {
                send(src, lang.get("nextleg.eligible_list", "list", formatList(eligible)));
            }
        }

        return 1;
    }

    private static String formatList(List<String> names) {
        return names.stream()
                .map(SpawnController::formatName)
                .collect(Collectors.joining("§7, §a"));
    }

    private static String buildContextString(ServerPlayerEntity player) {
        String dim = player.getServerWorld().getRegistryKey().getValue().toString();
        String dimStr = switch (dim) {
            case "minecraft:overworld"  -> "overworld";
            case "minecraft:the_nether" -> "nether";
            case "minecraft:the_end"    -> "end";
            default -> dim;
        };
        String timeStr    = player.getServerWorld().isDay() ? "jour" : "nuit";
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
