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
     * Poids relatif de spawn (plus élevé = plus fréquent).
     * Minimum : 1.
     */
    public int weight = 1;
}