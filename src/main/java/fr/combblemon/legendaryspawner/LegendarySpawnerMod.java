package fr.combblemon.legendaryspawner;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegendarySpawnerMod implements ModInitializer {

    public static final String MOD_ID = "legendaryspawner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static LegendarySpawnerMod instance;
    private SpawnController spawnController;
    private ModConfig config;
    private LangConfig lang;

    @Override
    public void onInitialize() {
        instance = this;

        // Charger la config et le lang
        config = ModConfig.load();
        lang = LangConfig.load();
        LOGGER.info("[LegendarySpawner] Config chargée - intervalle : {} minutes", config.intervalMinutes);

        // Enregistrer les commandes
        LegendaryCommand.register();

        // Démarrer le controller de spawn au lancement du serveur
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            spawnController = new SpawnController(server, config);
            spawnController.start();
            LOGGER.info("[LegendarySpawner] Mod activé ! Un légendaire spawne toutes les {} minutes.", config.intervalMinutes);
        });

        // Tick du serveur pour gérer le timer
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (spawnController != null) {
                spawnController.tick();
            }
        });

        // Arrêt propre
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (spawnController != null) spawnController.stop();
        });
    }

    public static LegendarySpawnerMod getInstance() {
        return instance;
    }

    public ModConfig getConfig() {
        return config;
    }

    public LangConfig getLang() {
        return lang;
    }

    public void reloadConfig() {
        config = ModConfig.load();
        lang = LangConfig.load();
        if (spawnController != null) {
            spawnController.updateConfig(config);
        }
    }

    public SpawnController getSpawnController() {
        return spawnController;
    }
}