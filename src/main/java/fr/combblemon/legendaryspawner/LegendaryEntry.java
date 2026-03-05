package fr.combblemon.legendaryspawner;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration de spawn pour un légendaire spécifique.
 */
public class LegendaryEntry {

    /** Si ce légendaire peut spawner. */
    public boolean enabled = true;

    /**
     * IDs de biomes où ce légendaire peut spawner (ex: "minecraft:plains").
     * Vide = n'importe quel biome.
     */
    public List<String> biomes = new ArrayList<>();

    /**
     * Moment de la journée requis.
     * Valeurs : "any", "day", "night"
     */
    public String timeOfDay = "any";

    /**
     * Météo requise.
     * Valeurs : "any", "clear", "rain", "thunder"
     */
    public String weather = "any";

    /**
     * Dimension requise.
     * Valeurs : "any", "overworld", "nether", "end" ou un ID complet ("minecraft:overworld")
     */
    public String dimension = "any";

    /**
     * Poids relatif de spawn (plus élevé = plus fréquent). Minimum : 1.
     */
    public int weight = 1;

    /**
     * Niveau minimum du légendaire. -1 = utilise le legendaryLevel global.
     * Si seul minLevel est défini (maxLevel = -1), ce niveau est utilisé exactement.
     */
    public int minLevel = -1;

    /**
     * Niveau maximum du légendaire. -1 = utilise le legendaryLevel global.
     * Si seul maxLevel est défini (minLevel = -1), ce niveau est utilisé exactement.
     * Si les deux sont définis, un niveau aléatoire entre minLevel et maxLevel est choisi.
     */
    public int maxLevel = -1;

    /**
     * Cooldown en minutes avant que ce légendaire puisse respawner.
     * 0 = aucun cooldown.
     */
    public int cooldownMinutes = 0;
}
