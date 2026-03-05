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
    private LegendaryConfig legendaryConfig;
    private LangConfig lang;
    private ChanceTracker chanceTracker;
    private SpawnStats stats;

    @Override
    public void onInitialize() {
        instance = this;

        config         = ModConfig.load();
        legendaryConfig = LegendaryConfig.load();
        lang           = LangConfig.load();
        chanceTracker  = ChanceTracker.load();
        stats          = SpawnStats.load();

        LOGGER.info("[LegendarySpawner] Config chargée - intervalle : {} minutes", config.intervalMinutes);

        LegendaryCommand.register();
        NextLegCommand.register();
        NextLegAdminCommand.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            spawnController = new SpawnController(server, config, legendaryConfig);
            spawnController.start();
            LOGGER.info("[LegendarySpawner] Mod activé ! Spawn toutes les {} minutes.", config.intervalMinutes);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (spawnController != null) spawnController.tick();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (spawnController != null) spawnController.stop();
            chanceTracker.save();
        });
    }

    public static LegendarySpawnerMod getInstance() { return instance; }

    public ModConfig getConfig()                { return config; }
    public LegendaryConfig getLegendaryConfig() { return legendaryConfig; }
    public LangConfig getLang()                 { return lang; }
    public ChanceTracker getChanceTracker()     { return chanceTracker; }
    public SpawnController getSpawnController() { return spawnController; }
    public SpawnStats getStats()                { return stats; }

    public void reloadConfig() {
        config         = ModConfig.load();
        legendaryConfig = LegendaryConfig.load();
        lang           = LangConfig.load();
        if (spawnController != null) spawnController.updateConfig(config, legendaryConfig);
    }
}
