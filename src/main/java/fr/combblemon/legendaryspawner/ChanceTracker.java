package fr.combblemon.legendaryspawner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Suit l'accumulation des bonus de chance par légendaire.
 * Persiste dans config/legendaryspawner-chances.json pour survivre aux redémarrages.
 *
 * Fonctionnement :
 * - Chaque légendaire a un bonus accumulé (en %) qui s'ajoute à son spawnChance de base.
 * - Le bonus augmente chaque cycle où le légendaire était éligible mais n'a pas spawné.
 * - Le bonus est remis à 0 quand le légendaire spawne.
 * - Le bonus ne peut pas dépasser maxChance - spawnChance.
 */
public class ChanceTracker {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Double>>() {}.getType();
    private static final Path SAVE_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("legendaryspawner-chances.json");

    // Bonus accumulés (en %) par nom de légendaire
    private final Map<String, Double> bonuses = new LinkedHashMap<>();

    // ---- Lecture ----

    /**
     * Retourne la chance effective actuelle d'un légendaire (base + bonus accumulé, plafonné à maxChance).
     */
    public double getCurrentChance(String name, LegendaryEntry entry, ModConfig config) {
        double base = entry.spawnChance >= 0 ? entry.spawnChance : config.defaultSpawnChance;
        double max  = entry.maxChance  >= 0 ? entry.maxChance  : config.defaultMaxChance;
        double bonus = bonuses.getOrDefault(name, 0.0);
        return Math.min(base + bonus, max);
    }

    /**
     * Retourne uniquement le bonus accumulé (pour affichage).
     */
    public double getBonus(String name) {
        return bonuses.getOrDefault(name, 0.0);
    }

    // ---- Écriture ----

    /**
     * Incrémente le bonus d'un légendaire (appelé quand il était éligible mais n'a pas spawné).
     */
    public void incrementBonus(String name, LegendaryEntry entry, ModConfig config) {
        double increment = entry.chanceIncrement >= 0 ? entry.chanceIncrement : config.defaultChanceIncrement;
        if (increment <= 0) return;
        bonuses.merge(name, increment, Double::sum);
    }

    /**
     * Remet le bonus d'un légendaire à 0 (appelé quand il spawne).
     */
    public void resetBonus(String name) {
        bonuses.remove(name);
    }

    // ---- Persistance ----

    public void save() {
        try (Writer writer = new FileWriter(SAVE_PATH.toFile())) {
            GSON.toJson(bonuses, writer);
        } catch (IOException e) {
            LegendarySpawnerMod.LOGGER.error("[LegendarySpawner] Erreur sauvegarde chances : {}", e.getMessage());
        }
    }

    public static ChanceTracker load() {
        ChanceTracker tracker = new ChanceTracker();
        if (Files.exists(SAVE_PATH)) {
            try (Reader reader = new FileReader(SAVE_PATH.toFile())) {
                Map<String, Double> loaded = GSON.fromJson(reader, MAP_TYPE);
                if (loaded != null) tracker.bonuses.putAll(loaded);
                LegendarySpawnerMod.LOGGER.info("[LegendarySpawner] Chances accumulées chargées depuis {}", SAVE_PATH);
            } catch (IOException e) {
                LegendarySpawnerMod.LOGGER.error("[LegendarySpawner] Erreur lecture chances : {}", e.getMessage());
            }
        }
        return tracker;
    }
}
