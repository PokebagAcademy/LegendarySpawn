package fr.combblemon.legendaryspawner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {

    // ---- Paramètres globaux ----

    /** Intervalle en minutes entre chaque tentative de spawn. */
    public int intervalMinutes = 30;

    /** Niveau par défaut des légendaires (si minLevel/maxLevel non définis). */
    public int legendaryLevel = 70;

    /** Minutes avant le spawn pour envoyer une annonce (0 = désactivé). */
    public int warnMinutesBefore = 5;

    /** Empêche le même légendaire de spawner plusieurs fois de suite. */
    public boolean preventRepeat = true;

    /** Nombre de spawns récents mémorisés pour l'anti-répétition. */
    public int recentSpawnMemory = 3;

    /** Enregistre chaque spawn dans logs/legendaryspawner-spawns.log. */
    public boolean logSpawns = true;

    /** Chance de base (en %) qu'un légendaire spawne à chaque tick. */
    public double spawnChance = 25.0;

    /** Bonus de % ajouté à spawnChance à chaque tick raté. S'accumule jusqu'à maxChance. */
    public double chanceIncrement = 10.0;

    /** Chance maximale (en %) que le système peut atteindre via accumulation. */
    public double maxChance = 100.0;

    /** Nombre minimum de joueurs en ligne pour que le timer tourne (0 = toujours actif). */
    public int minPlayersToTick = 0;

    /** Nombre d'IVs à 31 attribués aléatoirement au légendaire spawné (parmi les 6 stats). 0 = désactivé. */
    public int perfectIvCount = 3;

    /** Chance (en %) que le légendaire spawné soit shiny. 0 = désactivé. */
    public double shinyChance = 0.0;

    /** Secondes d'inactivité avant qu'un joueur soit considéré AFK et ignoré pour le spawn. 0 = désactivé. */
    public int ignoreAfkSeconds = 0;

    /** Distance minimale (en blocs) à laquelle le légendaire spawne autour du joueur. */
    public int spawnRadiusMin = 5;

    /** Distance maximale (en blocs) à laquelle le légendaire spawne autour du joueur. */
    public int spawnRadiusMax = 15;

    // ---- Chargement / Sauvegarde ----

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("legendaryspawner")
            .resolve("config.json");

    public static ModConfig load() {
        ModConfig cfg = new ModConfig();
        try { Files.createDirectories(CONFIG_PATH.getParent()); } catch (IOException ignored) {}

        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    cfg = loaded;
                    LegendarySpawnerMod.LOGGER.info("[LegendarySpawner] Config chargée depuis {}", CONFIG_PATH);
                }
            } catch (IOException e) {
                LegendarySpawnerMod.LOGGER.error("[LegendarySpawner] Erreur lecture config : {}", e.getMessage());
            }
        }

        cfg.save();
        return cfg;
    }

    public void save() {
        try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            LegendarySpawnerMod.LOGGER.error("[LegendarySpawner] Erreur sauvegarde config : {}", e.getMessage());
        }
    }
}
