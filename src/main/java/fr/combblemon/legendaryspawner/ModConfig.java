package fr.combblemon.legendaryspawner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ModConfig {

    // ---- Paramètres ----

    /** Intervalle en minutes entre chaque spawn */
    public int intervalMinutes = 30;

    /** Niveau des légendaires qui spawnent */
    public int legendaryLevel = 70;

    /**
     * Liste des légendaires possibles.
     * Laisser vide pour utiliser TOUS les légendaires.
     */
    public List<String> legendaries = new ArrayList<>();

    // ---- Chargement / Sauvegarde ----

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("legendaryspawner.json");

    public static ModConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
                ModConfig cfg = GSON.fromJson(reader, ModConfig.class);
                if (cfg != null) {
                    LegendarySpawnerMod.LOGGER.info("[LegendarySpawner] Config chargée depuis {}", CONFIG_PATH);
                    return cfg;
                }
            } catch (IOException e) {
                LegendarySpawnerMod.LOGGER.error("[LegendarySpawner] Erreur lecture config : {}", e.getMessage());
            }
        }

        // Créer la config par défaut
        ModConfig defaultConfig = new ModConfig();
        defaultConfig.save();
        LegendarySpawnerMod.LOGGER.info("[LegendarySpawner] Config par défaut créée dans {}", CONFIG_PATH);
        return defaultConfig;
    }

    public void save() {
        try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            LegendarySpawnerMod.LOGGER.error("[LegendarySpawner] Erreur sauvegarde config : {}", e.getMessage());
        }
    }
}
