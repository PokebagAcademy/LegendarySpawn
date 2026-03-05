package fr.combblemon.legendaryspawner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModConfig {

    // ---- Légendaires connus ----

    public static final List<String> ALL_LEGENDARIES = List.of(
            // Gen 1
            "mewtwo", "mew",
            // Gen 2
            "lugia", "hooh", "celebi", "raikou", "entei", "suicune",
            // Gen 3
            "regirock", "regice", "registeel", "latias", "latios",
            "kyogre", "groudon", "rayquaza", "jirachi", "deoxys",
            // Gen 4
            "uxie", "mesprit", "azelf", "dialga", "palkia",
            "heatran", "regigigas", "giratina", "cresselia", "darkrai",
            "shaymin", "arceus",
            // Gen 5
            "victini", "cobalion", "terrakion", "virizion",
            "reshiram", "zekrom", "kyurem", "keldeo", "meoletta", "genesect",
            // Gen 6
            "xerneas", "yveltal", "zygarde", "diancie", "hoopa", "volcanion",
            // Gen 7
            "solgaleo", "lunala", "necrozma",
            "tapulele", "tapukoko", "tapubulu", "tapufini",
            "magearna", "marshadow", "zeraora",
            // Gen 8
            "zacian", "zamazenta", "eternatus",
            "kubfu", "regieleki", "regidrago",
            "glastrier", "spectrier", "calyrex",
            "enamorus", "zarude", "meltan",
            // Gen 9
            "koraidon", "miraidon",
            "okidogi", "munkidori", "fezandipiti", "ogerpon",
            "terapagos", "pecharunt",
            "wochien", "chienpao", "tinglu", "chiyu",
            // Mythiques divers
            "manaphy", "phione"
    );

    // ---- Paramètres globaux ----

    /** Intervalle en minutes entre chaque spawn. */
    public int intervalMinutes = 30;

    /** Niveau des légendaires qui spawnent. */
    public int legendaryLevel = 70;

    /**
     * Configuration par légendaire.
     * Clé = nom du Pokémon (ex: "mewtwo"), valeur = ses paramètres de spawn.
     */
    public Map<String, LegendaryEntry> legendaries = new LinkedHashMap<>();

    // ---- Chargement / Sauvegarde ----

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("legendaryspawner.json");

    public static ModConfig load() {
        ModConfig cfg = new ModConfig();

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

        if (cfg.legendaries == null) {
            cfg.legendaries = new LinkedHashMap<>();
        }

        // Ajoute les légendaires manquants avec des valeurs par défaut
        for (String name : ALL_LEGENDARIES) {
            cfg.legendaries.putIfAbsent(name, new LegendaryEntry());
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