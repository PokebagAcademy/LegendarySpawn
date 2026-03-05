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
        messages.put("spawn.no_eligible",
                "§c[LegendarySpawner] Aucun légendaire éligible pour les joueurs connectés (biomes/conditions non remplies).");

        // --- Commandes générales ---
        messages.put("command.not_initialized",
                "§cLe mod n'est pas encore initialisé.");
        messages.put("command.force_spawn",
                "§6[LS] §eForçage du spawn d'un légendaire...");
        messages.put("command.reload_success",
                "§6[LS] §aConfig et lang rechargées, timer reset !");
        messages.put("command.set_interval",
                "§6[LS] §aIntervalle mis à §e{minutes} §aminutes !");
        messages.put("command.timer",
                "§6[LS] §eProchain spawn dans §b{minutes} min {seconds} sec§e.");

        // --- Aide ---
        messages.put("command.help_header",      "§6§l=== LegendarySpawner ===");
        messages.put("command.help_forcespawn",  "§e/ls forcespawn §7- Force un spawn immédiat");
        messages.put("command.help_reload",      "§e/ls reload §7- Recharge la config et le lang");
        messages.put("command.help_setinterval", "§e/ls setinterval <min> §7- Change l'intervalle");
        messages.put("command.help_timer",       "§e/ls timer §7- Temps restant avant prochain spawn");
        messages.put("command.help_legendary",   "§e/ls legendary <list|enable|disable> §7- Gère les légendaires");

        // --- Gestion des légendaires ---
        messages.put("command.legendary_enabled",
                "§6[LS] §a{pokemon} §aactivé.");
        messages.put("command.legendary_disabled",
                "§6[LS] §c{pokemon} §cdésactivé.");
        messages.put("command.legendary_not_found",
                "§c[LS] Légendaire §e{pokemon} §cinconnu dans la config.");
        messages.put("command.legendary_list_header",
                "§6§l=== Légendaires configurés ===");
        messages.put("command.legendary_list_entry_enabled",
                "§a✔ §e{pokemon} §7(poids: {weight})");
        messages.put("command.legendary_list_entry_disabled",
                "§c✘ §7{pokemon} §8(désactivé)");
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