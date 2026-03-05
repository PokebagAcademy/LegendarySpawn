package fr.combblemon.legendaryspawner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gestion des messages configurables via legendaryspawner_lang.json.
 * Supporte les placeholders {nom} dans les valeurs.
 */
public class LangConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path LANG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("legendaryspawner_lang.json");

    private Map<String, String> messages = new LinkedHashMap<>();

    // ---- Chargement ----

    public static LangConfig load() {
        LangConfig lang = new LangConfig();
        lang.loadDefaults();

        if (Files.exists(LANG_PATH)) {
            try (Reader reader = new FileReader(LANG_PATH.toFile())) {
                @SuppressWarnings("unchecked")
                Map<String, String> loaded = GSON.fromJson(reader, Map.class);
                if (loaded != null) {
                    lang.messages.putAll(loaded);
                }
            } catch (IOException e) {
                LegendarySpawnerMod.LOGGER.error("[LegendarySpawner] Erreur lecture lang : {}", e.getMessage());
            }
        }

        lang.save();
        return lang;
    }

    private void loadDefaults() {
        // --- Spawn ---
        messages.put("spawn.broadcast",
                "§6§l[✦ LÉGENDAIRE] §eUn §c§l{pokemon} §eest apparu près de §b§l{player} §e!");
        messages.put("spawn.notify_player",
                "§6✦ §eUn légendaire §c§l{pokemon} §eest apparu à côté de toi !");
        messages.put("spawn.warn",
                "§6§l[✦ LÉGENDAIRE] §eUn légendaire apparaîtra dans §b{minutes} minute(s) §e! Soyez prêts !");
        messages.put("spawn.no_eligible",
                "§c[LegendarySpawner] Aucun légendaire éligible (biomes/conditions/cooldowns non remplis). Spawn ignoré.");
        messages.put("spawn.no_enabled",
                "§c[LegendarySpawner] Aucun légendaire activé. Spawn ignoré.");

        // --- Commandes générales ---
        messages.put("command.not_initialized",
                "§cLe mod n'est pas encore initialisé.");
        messages.put("command.force_spawn",
                "§6[LS] §eForçage du spawn d'un légendaire...");
        messages.put("command.force_spawn_player",
                "§6[LS] §eForçage du spawn d'un légendaire sur §b{player}§e...");
        messages.put("command.reload_success",
                "§6[LS] §aConfig et lang rechargées ! (timer préservé)");
        messages.put("command.set_interval",
                "§6[LS] §aIntervalle mis à §e{minutes} §aminutes !");
        messages.put("command.timer",
                "§6[LS] §eProchain spawn dans §b{minutes} min {seconds} sec§e.");

        // --- Aide ---
        messages.put("command.help_header",        "§6§l=== LegendarySpawner ===");
        messages.put("command.help_forcespawn",    "§e/ls forcespawn [joueur] §7- Force un spawn (sur un joueur optionnel)");
        messages.put("command.help_reload",        "§e/ls reload §7- Recharge la config et le lang");
        messages.put("command.help_setinterval",   "§e/ls setinterval <min> §7- Change l'intervalle");
        messages.put("command.help_timer",         "§e/ls timer §7- Temps restant avant prochain spawn");
        messages.put("command.help_legendary",     "§e/ls legendary <list|info|enable|disable|set|biome> §7- Gère les légendaires");

        // --- Liste ---
        messages.put("command.legendary_list_header",
                "§6§l=== Légendaires (page {page}/{total}) ===");
        messages.put("command.legendary_list_entry_enabled",
                "§a✔ §e{pokemon} §7(poids:{weight} niv:{level} cd:{cooldown}min)");
        messages.put("command.legendary_list_entry_disabled",
                "§c✘ §7{pokemon}");
        messages.put("command.legendary_list_page_hint",
                "§7/ls legendary list <page> pour naviguer.");

        // --- Info ---
        messages.put("command.legendary_info_header",
                "§6§l=== {pokemon} ===");
        messages.put("command.legendary_info_enabled",
                "§eActivé: §a{value}");
        messages.put("command.legendary_info_weight",
                "§ePoids: §a{value}");
        messages.put("command.legendary_info_level",
                "§eNiveau: §a{value}");
        messages.put("command.legendary_info_cooldown",
                "§eCooldown: §a{value} min");
        messages.put("command.legendary_info_timeofday",
                "§eHeure: §a{value}");
        messages.put("command.legendary_info_weather",
                "§eMétéo: §a{value}");
        messages.put("command.legendary_info_dimension",
                "§eDimension: §a{value}");
        messages.put("command.legendary_info_biomes",
                "§eBiomes: §a{value}");
        messages.put("command.legendary_info_chance",
                "§eChance: §abase §e{base}% §a→ actuelle §e{current}% §7(+{bonus}% accumulé, max {max}%, +{increment}%/cycle raté)");

        // --- Enable/Disable ---
        messages.put("command.legendary_enabled",
                "§6[LS] §a{pokemon} §aactivé.");
        messages.put("command.legendary_disabled",
                "§6[LS] §c{pokemon} §cdésactivé.");
        messages.put("command.legendary_not_found",
                "§c[LS] Légendaire §e{pokemon} §cinconnu dans la config.");

        // --- Set ---
        messages.put("command.legendary_set_success",
                "§6[LS] §a{pokemon}.{param} §a= §e{value}");
        messages.put("command.legendary_set_invalid",
                "§c[LS] Valeur invalide pour §e{param}§c: §e{value}");
        messages.put("command.legendary_set_unknown_param",
                "§c[LS] Paramètre inconnu: §e{param}§c. Valides: weight, minlevel, maxlevel, cooldown, timeofday, weather, dimension");

        // --- /nextleg ---
        messages.put("nextleg.header",
                "§6§l[✦] §eProchain Tick Légendaire dans : §b{timer}");
        messages.put("nextleg.combined_chance",
                "§eChance d'apparition : §a{chance}%{bonus}");
        messages.put("nextleg.separator",
                "§7─────────────────────────");
        messages.put("nextleg.player_header",
                "§b[{player}] §7({context})");
        messages.put("nextleg.eligible_entry",
                "  §a✔ §e{pokemon}");
        messages.put("nextleg.no_eligible_player",
                "  §7Aucun légendaire éligible.");
        messages.put("nextleg.own_none",
                "§7Aucun légendaire éligible pour ta position actuelle.");
        messages.put("nextleg.total",
                "§eTotal éligibles: §b{count} §elégendaire(s).");

        // --- Biome ---
        messages.put("command.legendary_biome_added",
                "§6[LS] §aBiome §e{biome} §aajouté à §e{pokemon}§a.");
        messages.put("command.legendary_biome_already",
                "§c[LS] §e{biome} §cest déjà configuré pour §e{pokemon}§c.");
        messages.put("command.legendary_biome_removed",
                "§6[LS] §cBiome §e{biome} §cretirée de §e{pokemon}§c.");
        messages.put("command.legendary_biome_not_found",
                "§c[LS] Biome §e{biome} §cnon trouvé dans la config de §e{pokemon}§c.");
        messages.put("command.legendary_biome_cleared",
                "§6[LS] §cTous les biomes de §e{pokemon} §ceffacés (n'importe quel biome accepté).");
    }

    public void save() {
        try (Writer writer = new FileWriter(LANG_PATH.toFile())) {
            GSON.toJson(messages, writer);
        } catch (IOException e) {
            LegendarySpawnerMod.LOGGER.error("[LegendarySpawner] Erreur sauvegarde lang : {}", e.getMessage());
        }
    }

    // ---- Accès aux messages ----

    /**
     * Retourne le message associé à la clé, avec remplacement des placeholders.
     * Les remplacements sont des paires clé/valeur : get("key", "pokemon", "Mewtwo", "player", "Steve")
     */
    public String get(String key, String... replacements) {
        String msg = messages.getOrDefault(key, "§c[MISSING: " + key + "]");
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return msg;
    }
}
