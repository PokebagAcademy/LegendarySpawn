package fr.combblemon.legendaryspawner;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Enregistre les spawns de légendaires dans logs/legendaryspawner-spawns.log.
 */
public class SpawnLogger {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Path LOG_PATH = FabricLoader.getInstance()
            .getGameDir()
            .resolve("logs")
            .resolve("legendaryspawner-spawns.log");

    public static void log(String pokemon, ServerPlayerEntity player, int level) {
        if (!LegendarySpawnerMod.getInstance().getConfig().logSpawns) return;

        String dim = player.getServerWorld().getRegistryKey().getValue().toString();
        String line = String.format("[%s] %s (niv.%d) spawné près de %s à (%d, %d, %d) dans %s%n",
                LocalDateTime.now().format(FORMATTER),
                formatName(pokemon),
                level,
                player.getName().getString(),
                player.getBlockX(), player.getBlockY(), player.getBlockZ(),
                dim);

        try (FileWriter fw = new FileWriter(LOG_PATH.toFile(), true)) {
            fw.write(line);
        } catch (IOException e) {
            LegendarySpawnerMod.LOGGER.error("[LegendarySpawner] Erreur écriture log spawn : {}", e.getMessage());
        }
    }

    /** Retourne les n dernières lignes du log (ordre chronologique). Retourne liste vide si le fichier n'existe pas. */
    public static List<String> getLastLines(int n) {
        if (!Files.exists(LOG_PATH)) return Collections.emptyList();
        try {
            List<String> all = Files.readAllLines(LOG_PATH);
            int from = Math.max(0, all.size() - n);
            return all.subList(from, all.size());
        } catch (IOException e) {
            LegendarySpawnerMod.LOGGER.error("[LegendarySpawner] Erreur lecture log spawn : {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Efface le fichier de log. */
    public static boolean clear() {
        try {
            Files.deleteIfExists(LOG_PATH);
            return true;
        } catch (IOException e) {
            LegendarySpawnerMod.LOGGER.error("[LegendarySpawner] Erreur suppression log spawn : {}", e.getMessage());
            return false;
        }
    }

    private static String formatName(String name) {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
            if (i < parts.length - 1) sb.append(" ");
        }
        return sb.toString();
    }
}
