package fr.combblemon.legendaryspawner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Suit le bonus de chance global accumulé entre les ticks.
 *
 * Fonctionnement :
 * - Chaque tick qui rate (pas de spawn) ajoute chanceIncrement au bonus global.
 * - Quand un légendaire spawne, le bonus est remis à 0.
 * - La chance effective = spawnChance (config) + bonus accumulé, plafonné à maxChance.
 * - Persiste dans config/legendaryspawner-chances.json pour survivre aux redémarrages.
 */
public class ChanceTracker {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SAVE_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("legendaryspawner-chances.json");

    private double globalBonus = 0.0;

    // ---- Lecture ----

    /** Chance effective actuelle = spawnChance + bonus accumulé, plafonné à maxChance. */
    public double getCurrentChance(ModConfig config) {
        return Math.min(config.spawnChance + globalBonus, config.maxChance);
    }

    /** Retourne uniquement le bonus accumulé (pour affichage). */
    public double getGlobalBonus() {
        return globalBonus;
    }

    // ---- Écriture ----

    /** Appelé quand le tick rate (pas de spawn) : incrémente le bonus. */
    public void onFailedTick(ModConfig config) {
        if (config.chanceIncrement <= 0) return;
        globalBonus = Math.min(globalBonus + config.chanceIncrement,
                config.maxChance - config.spawnChance);
    }

    /** Appelé quand un légendaire spawne : remet le bonus à 0. */
    public void onSpawn() {
        globalBonus = 0.0;
    }

    // ---- Persistance ----

    public void save() {
        try (Writer writer = new FileWriter(SAVE_PATH.toFile())) {
            GSON.toJson(new SaveData(globalBonus), writer);
        } catch (IOException e) {
            LegendarySpawnerMod.LOGGER.error("[LegendarySpawner] Erreur sauvegarde chances : {}", e.getMessage());
        }
    }

    public static ChanceTracker load() {
        ChanceTracker tracker = new ChanceTracker();
        if (Files.exists(SAVE_PATH)) {
            try (Reader reader = new FileReader(SAVE_PATH.toFile())) {
                SaveData data = GSON.fromJson(reader, SaveData.class);
                if (data != null) tracker.globalBonus = data.globalBonus;
                LegendarySpawnerMod.LOGGER.info("[LegendarySpawner] Bonus accumulé chargé : +{}%", tracker.globalBonus);
            } catch (IOException e) {
                LegendarySpawnerMod.LOGGER.error("[LegendarySpawner] Erreur lecture chances : {}", e.getMessage());
            }
        }
        return tracker;
    }

    private static class SaveData {
        double globalBonus;
        SaveData(double globalBonus) { this.globalBonus = globalBonus; }
    }
}
