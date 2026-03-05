package fr.combblemon.legendaryspawner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Gestion des configurations par légendaire.
 * Chaque légendaire a son propre fichier JSON dans config/legendaryspawner/legendaries/.
 */
public class LegendaryConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final Path DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("legendaryspawner")
            .resolve("legendaries");

    // ---- Liste de référence et poids par défaut ----

    public static final List<String> ALL_LEGENDARIES = List.of(
            // Gen 1
            "mewtwo", "mew", "articuno", "zapdos", "moltres",
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
            "tornadus", "thundurus", "landorus",
            "reshiram", "zekrom", "kyurem", "keldeo", "meloetta", "genesect",
            // Gen 6
            "xerneas", "yveltal", "zygarde", "diancie", "hoopa", "volcanion",
            // Gen 7
            "solgaleo", "lunala", "necrozma",
            "tapu_koko", "tapu_lele", "tapu_bulu", "tapu_fini",
            "magearna", "marshadow", "zeraora",
            "cosmog", "cosmoem",
            // Gen 8
            "zacian", "zamazenta", "eternatus",
            "kubfu", "typenull", "silvally", "regieleki", "regidrago",
            "glastrier", "spectrier", "calyrex",
            "enamorus", "zarude", "meltan", "melmetal",
            // Gen 9
            "koraidon", "miraidon",
            "okidogi", "munkidori", "fezandipiti", "ogerpon",
            "terapagos", "pecharunt",
            "wochien", "chienpao", "tinglu", "chiyu",
            // Mythiques divers
            "manaphy", "phione"
    );

    private static final Map<String, Integer> DEFAULT_WEIGHTS = new HashMap<>();
    static {
        for (String n : new String[]{
                "rayquaza", "xerneas", "yveltal", "kyogre", "groudon",
                "tornadus", "thundurus", "landorus",
                "tapu_koko", "tapu_lele", "tapu_bulu", "tapu_fini",
                "necrozma", "kubfu", "koraidon", "miraidon",
                "deoxys", "volcanion", "magearna", "marshadow", "zeraora"
        }) DEFAULT_WEIGHTS.put(n, 1);

        for (String n : new String[]{
                "cresselia", "darkrai", "heatran", "eternatus",
                "wochien", "chienpao", "tinglu", "chiyu",
                "mewtwo", "calyrex", "glastrier", "spectrier",
                "enamorus", "fezandipiti", "ogerpon", "shaymin", "genesect"
        }) DEFAULT_WEIGHTS.put(n, 2);

        for (String n : new String[]{
                "giratina", "arceus", "cobalion", "terrakion", "virizion",
                "keldeo", "reshiram", "zekrom", "kyurem",
                "hoopa", "cosmog", "cosmoem", "lunala", "solgaleo",
                "zacian", "zamazenta", "regieleki", "regidrago",
                "typenull", "silvally", "okidogi", "munkidori", "terapagos",
                "jirachi", "meloetta", "zarude", "pecharunt", "diancie"
        }) DEFAULT_WEIGHTS.put(n, 4);

        for (String n : new String[]{
                "latias", "latios", "articuno", "zapdos", "moltres",
                "regirock", "regice", "registeel"
        }) DEFAULT_WEIGHTS.put(n, 6);

        for (String n : new String[]{
                "raikou", "entei", "suicune",
                "lugia", "hooh", "regigigas", "dialga", "palkia"
        }) DEFAULT_WEIGHTS.put(n, 8);

        for (String n : new String[]{
                "mew", "celebi", "mesprit", "azelf", "uxie",
                "victini", "meltan", "melmetal"
        }) DEFAULT_WEIGHTS.put(n, 15);
    }

    // ---- Données en mémoire ----

    private final Map<String, LegendaryEntry> entries = new LinkedHashMap<>();

    // ---- Accès ----

    public Map<String, LegendaryEntry> getAll()        { return entries; }
    public LegendaryEntry get(String name)             { return entries.get(name); }
    public boolean exists(String name)                 { return entries.containsKey(name); }
    public void put(String name, LegendaryEntry entry) { entries.put(name, entry); }

    // ---- Chargement ----

    public static LegendaryConfig load() {
        LegendaryConfig cfg = new LegendaryConfig();
        try { Files.createDirectories(DIR); } catch (IOException e) {
            LegendarySpawnerMod.LOGGER.error("[LegendarySpawner] Impossible de créer legendaries/ : {}", e.getMessage());
        }

        // Migration depuis l'ancien format si le dossier est vide
        cfg.migrateFromOldConfig();

        // Charger les fichiers JSON existants
        try {
            Files.list(DIR)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        String name = p.getFileName().toString().replace(".json", "");
                        try (Reader r = new FileReader(p.toFile())) {
                            LegendaryEntry entry = GSON.fromJson(r, LegendaryEntry.class);
                            if (entry != null) cfg.entries.put(name, entry);
                        } catch (IOException e) {
                            LegendarySpawnerMod.LOGGER.error("[LegendarySpawner] Erreur lecture {}.json : {}", name, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            LegendarySpawnerMod.LOGGER.error("[LegendarySpawner] Erreur lecture dossier legendaries/ : {}", e.getMessage());
        }

        // Ajouter les légendaires manquants avec poids par défaut
        for (String name : ALL_LEGENDARIES) {
            if (!cfg.entries.containsKey(name)) {
                LegendaryEntry entry = new LegendaryEntry();
                entry.weight = DEFAULT_WEIGHTS.getOrDefault(name, 1);
                cfg.entries.put(name, entry);
                cfg.save(name);
            }
        }

        return cfg;
    }

    /** Migration transparente depuis l'ancien legendaryspawner.json. */
    private void migrateFromOldConfig() {
        if (!entries.isEmpty()) return;
        Path oldPath = FabricLoader.getInstance().getConfigDir().resolve("legendaryspawner.json");
        if (!Files.exists(oldPath)) return;

        try (Reader r = new FileReader(oldPath.toFile())) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null || !root.has("legendaries")) return;
            JsonObject legs = root.getAsJsonObject("legendaries");

            // Migrations de noms
            renameKey(legs, "meoletta",  "meloetta");
            renameKey(legs, "ho_oh",     "hooh");
            renameKey(legs, "tapukoko",  "tapu_koko");
            renameKey(legs, "tapulele",  "tapu_lele");
            renameKey(legs, "tapubulu",  "tapu_bulu");
            renameKey(legs, "tapufini",  "tapu_fini");
            renameKey(legs, "type_null", "typenull");

            for (Map.Entry<String, JsonElement> e : legs.entrySet()) {
                LegendaryEntry entry = GSON.fromJson(e.getValue(), LegendaryEntry.class);
                if (entry != null) {
                    entries.put(e.getKey(), entry);
                    save(e.getKey());
                }
            }
            LegendarySpawnerMod.LOGGER.info("[LegendarySpawner] Migration : {} légendaires importés.", entries.size());
        } catch (IOException e) {
            LegendarySpawnerMod.LOGGER.error("[LegendarySpawner] Erreur migration ancien config : {}", e.getMessage());
        }
    }

    private static void renameKey(JsonObject obj, String oldKey, String newKey) {
        if (obj.has(oldKey) && !obj.has(newKey)) obj.add(newKey, obj.remove(oldKey));
        else if (obj.has(oldKey))               obj.remove(oldKey);
    }

    // ---- Sauvegarde ----

    public void save(String name) {
        LegendaryEntry entry = entries.get(name);
        if (entry == null) return;
        Path file = DIR.resolve(name + ".json");
        try (Writer w = new FileWriter(file.toFile())) {
            GSON.toJson(entry, w);
        } catch (IOException e) {
            LegendarySpawnerMod.LOGGER.error("[LegendarySpawner] Erreur sauvegarde {}.json : {}", name, e.getMessage());
        }
    }

    public void saveAll() {
        entries.keySet().forEach(this::save);
    }
}
