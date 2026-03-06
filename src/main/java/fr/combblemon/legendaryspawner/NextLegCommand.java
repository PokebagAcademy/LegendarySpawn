package fr.combblemon.legendaryspawner;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * /nextleg : timer + chance de spawn + légendaires éligibles pour le joueur (2 lignes max)
 * Vue multi-joueurs → /nextlegadmin user
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

        Entity caller = src.getEntity();

        // Ligne 1 : timer + chance
        send(src, lang.get("nextleg.header",
                "timer", timer,
                "chance", String.format("%.1f", currentChance),
                "bonus", bonusStr));

        // Ligne 2 : top légendaires éligibles avec % pour le joueur
        if (caller instanceof ServerPlayerEntity player) {
            List<String> eligible = ctrl.buildEligibleNames(player);
            if (eligible.isEmpty()) {
                send(src, lang.get("nextleg.own_none"));
            } else {
                send(src, formatEligibleLine(eligible));
            }
        }

        return 1;
    }

    private static final int MAX_SHOWN = 5;

    private static String formatEligibleLine(List<String> eligible) {
        LegendaryConfig cfg = LegendarySpawnerMod.getInstance().getLegendaryConfig();

        // Tri par weight décroissant
        eligible.sort(Comparator.comparingInt((String n) -> {
            LegendaryEntry e = cfg.get(n);
            return e != null ? e.weight : 1;
        }).reversed());

        int show = Math.min(MAX_SHOWN, eligible.size());
        String list = eligible.subList(0, show).stream()
                .map(n -> "§a" + SpawnController.formatName(n))
                .collect(Collectors.joining("§7, "));

        String suffix = eligible.size() > MAX_SHOWN
                ? String.format("§7 ... §8(+%d autres)", eligible.size() - MAX_SHOWN)
                : "";

        return String.format("§7Éligibles §8(§f%d§8)§7: %s%s", eligible.size(), list, suffix);
    }

    private static void send(ServerCommandSource src, String msg) {
        src.sendMessage(Text.literal(msg));
    }
}
