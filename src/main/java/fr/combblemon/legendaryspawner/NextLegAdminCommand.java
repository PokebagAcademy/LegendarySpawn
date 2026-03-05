package fr.combblemon.legendaryspawner;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /nextlegadmin global [page] — distribution pondérée de tous les légendaires activés
 * /nextlegadmin user [joueur]  — pool éligible + % pour un joueur spécifique
 */
public class NextLegAdminCommand {

    private static final int PAGE_SIZE = 15;

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                literal("nextlegadmin")
                .requires(src -> PermissionManager.check(src, PermissionManager.NEXTLEG_ADMIN, 2))

                // /nextlegadmin global [page]
                .then(literal("global")
                    .executes(ctx -> handleGlobal(ctx.getSource(), 1))
                    .then(argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> handleGlobal(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "page"))))
                )

                // /nextlegadmin user [joueur]
                .then(literal("user")
                    .executes(ctx -> {
                        Entity e = ctx.getSource().getEntity();
                        if (e instanceof ServerPlayerEntity player) return handleUser(ctx.getSource(), player);
                        send(ctx.getSource(), "§cUtilisez /nextlegadmin user <joueur> depuis la console.");
                        return 0;
                    })
                    .then(argument("player", EntityArgumentType.player())
                        .executes(ctx -> handleUser(ctx.getSource(),
                                EntityArgumentType.getPlayer(ctx, "player"))))
                )
            )
        );
    }

    // ---- Global ----

    private static int handleGlobal(ServerCommandSource src, int page) {
        LegendaryConfig legendaryConfig = LegendarySpawnerMod.getInstance().getLegendaryConfig();
        SpawnController ctrl = LegendarySpawnerMod.getInstance().getSpawnController();

        List<Map.Entry<String, LegendaryEntry>> enabled = legendaryConfig.getAll().entrySet().stream()
                .filter(e -> e.getValue().enabled)
                .sorted(Comparator.comparingInt((Map.Entry<String, LegendaryEntry> e) ->
                        e.getValue().weight).reversed())
                .collect(Collectors.toList());

        int totalWeight = enabled.stream().mapToInt(e -> Math.max(1, e.getValue().weight)).sum();
        int totalPages  = Math.max(1, (int) Math.ceil((double) enabled.size() / PAGE_SIZE));
        int p    = Math.max(1, Math.min(page, totalPages));
        int from = (p - 1) * PAGE_SIZE;
        int to   = Math.min(from + PAGE_SIZE, enabled.size());

        send(src, String.format("§6=== Distribution globale §8(p.%d/%d | %d légendaires | weight total: %d) ===",
                p, totalPages, enabled.size(), totalWeight));

        for (Map.Entry<String, LegendaryEntry> e : enabled.subList(from, to)) {
            String name = e.getKey();
            LegendaryEntry entry = e.getValue();
            int w   = Math.max(1, entry.weight);
            double pct = (double) w / totalWeight * 100.0;
            boolean onCooldown = ctrl != null && ctrl.isNameOnCooldown(name);
            String display = SpawnController.getDisplayName(name, entry);
            String line = String.format("§a%s §8[§7w:%d§8] §e%.1f%%", display, w, pct);
            if (onCooldown) line += " §c[cooldown]";
            send(src, line);
        }

        if (totalPages > 1 && p < totalPages)
            send(src, "§8→ /nextlegadmin global " + (p + 1) + " §7pour la suite");

        return 1;
    }

    // ---- User ----

    private static int handleUser(ServerCommandSource src, ServerPlayerEntity player) {
        LegendaryConfig legendaryConfig = LegendarySpawnerMod.getInstance().getLegendaryConfig();
        SpawnController ctrl = LegendarySpawnerMod.getInstance().getSpawnController();
        if (ctrl == null) { send(src, "§cSpawnController non initialisé."); return 0; }

        List<String> eligible = ctrl.buildEligibleNames(player);
        String context = buildContextString(player);

        send(src, String.format("§6=== Spawn pour §b%s §8(%s) ===",
                player.getName().getString(), context));

        if (eligible.isEmpty()) {
            send(src, "§7Aucun légendaire éligible pour ce joueur dans ces conditions.");
            return 1;
        }

        // Tri par weight décroissant
        eligible.sort(Comparator.comparingInt((String n) -> {
            LegendaryEntry e = legendaryConfig.get(n);
            return e != null ? e.weight : 1;
        }).reversed());

        int totalWeight = eligible.stream()
                .mapToInt(n -> { LegendaryEntry e = legendaryConfig.get(n); return e != null ? Math.max(1, e.weight) : 1; })
                .sum();

        send(src, String.format("§7Pool : §f%d §7légendaires | weight total : §f%d",
                eligible.size(), totalWeight));

        for (String name : eligible) {
            LegendaryEntry entry = legendaryConfig.get(name);
            int w   = entry != null ? Math.max(1, entry.weight) : 1;
            double pct = (double) w / totalWeight * 100.0;
            String display = SpawnController.getDisplayName(name, entry);
            send(src, String.format("§a%s §8[§7w:%d§8] §e%.1f%%", display, w, pct));
        }

        return 1;
    }

    // ---- Utils ----

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
