package fr.combblemon.legendaryspawner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class SpawnStats {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SAVE_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("legendaryspawner")
            .resolve("stats.json");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    public int totalSpawns = 0;
    public Map<String, Integer> byPokemon = new LinkedHashMap<>();
    public Map<String, Integer> byPlayer  = new LinkedHashMap<>();
    public String lastPokemon;
    public String lastPlayer;
    public String lastTime;

    public void record(String pokemon, String player) {
        totalSpawns++;
        byPokemon.merge(pokemon, 1, Integer::sum);
        byPlayer.merge(player,  1, Integer::sum);
        lastPokemon = pokemon;
        lastPlayer  = player;
        lastTime    = LocalDateTime.now().format(FMT);
        save();
    }

    public String topPokemon() {
        return byPokemon.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> SpawnController.formatName(e.getKey()) + " (" + e.getValue() + "x)")
                .orElse("aucun");
    }

    public String topPlayer() {
        return byPlayer.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey() + " (" + e.getValue() + "x)")
                .orElse("aucun");
    }

    public void save() {
        try {
            Files.createDirectories(SAVE_PATH.getParent());
            try (Writer w = new FileWriter(SAVE_PATH.toFile())) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            LegendarySpawnerMod.LOGGER.error("[LegendarySpawner] Erreur sauvegarde stats : {}", e.getMessage());
        }
    }

    public static SpawnStats load() {
        if (!Files.exists(SAVE_PATH)) return new SpawnStats();
        try (Reader r = new FileReader(SAVE_PATH.toFile())) {
            SpawnStats stats = GSON.fromJson(r, SpawnStats.class);
            return stats != null ? stats : new SpawnStats();
        } catch (IOException e) {
            LegendarySpawnerMod.LOGGER.error("[LegendarySpawner] Erreur lecture stats : {}", e.getMessage());
            return new SpawnStats();
        }
    }
}
