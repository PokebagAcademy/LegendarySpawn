package fr.combblemon.legendaryspawner;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Random;

public class SpawnController {

    private final MinecraftServer server;
    private ModConfig config;
    private final Random random = new Random();
    private long tickCounter = 0;
    private long intervalTicks;
    private boolean running = false;

    private static final List<String> ALL_LEGENDARIES = List.of(
            "mewtwo", "mew", "lugia", "ho_oh", "celebi",
            "regirock", "regice", "registeel", "latias", "latios",
            "kyogre", "groudon", "rayquaza", "jirachi", "deoxys",
            "uxie", "mesprit", "azelf", "dialga", "palkia",
            "heatran", "regigigas", "giratina", "cresselia", "darkrai",
            "shaymin", "arceus", "victini", "cobalion", "terrakion",
            "virizion", "reshiram", "zekrom", "kyurem", "xerneas",
            "yveltal", "zygarde", "solgaleo", "lunala", "necrozma",
            "zacian", "zamazenta", "eternatus", "koraidon", "miraidon"
    );

    public SpawnController(MinecraftServer server, ModConfig config) {
        this.server = server;
        this.config = config;
        this.intervalTicks = (long) config.intervalMinutes * 60 * 20;
    }

    public void start() {
        running = true;
        tickCounter = 0;
    }

    public void stop() {
        running = false;
    }

    public void updateConfig(ModConfig newConfig) {
        this.config = newConfig;
        this.intervalTicks = (long) newConfig.intervalMinutes * 60 * 20;
        this.tickCounter = 0;
    }

    public void tick() {
        if (!running) return;
        tickCounter++;
        if (tickCounter >= intervalTicks) {
            tickCounter = 0;
            spawnLegendary();
        }
    }

    public void forceSpawn() {
        tickCounter = 0;
        spawnLegendary();
    }

    public long getTicksRemaining() {
        return intervalTicks - tickCounter;
    }

    private void spawnLegendary() {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        ServerPlayerEntity target = players.get(random.nextInt(players.size()));
        String pokemonName = pickLegendary();

        // Spawn via commande Cobblemon
        String cmd = String.format("pokespawn %s level=%d", pokemonName, config.legendaryLevel);
        server.getCommandManager().executeWithPrefix(
                server.getCommandSource()
                        .withPosition(target.getPos())
                        .withWorld(target.getServerWorld())
                        .withSilent(),
                cmd
        );

        broadcastSpawn(target, pokemonName);
    }

    private String pickLegendary() {
        List<String> pool = (config.legendaries != null && !config.legendaries.isEmpty())
                ? config.legendaries : ALL_LEGENDARIES;
        return pool.get(random.nextInt(pool.size()));
    }

    private void broadcastSpawn(ServerPlayerEntity target, String pokemonName) {
        String displayName = formatName(pokemonName);

        Text msg = Text.literal("")
                .append(Text.literal("[✦ LÉGENDAIRE] ").formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal("Un ").formatted(Formatting.YELLOW))
                .append(Text.literal(displayName).formatted(Formatting.RED, Formatting.BOLD))
                .append(Text.literal(" est apparu près de ").formatted(Formatting.YELLOW))
                .append(Text.literal(target.getName().getString()).formatted(Formatting.AQUA, Formatting.BOLD))
                .append(Text.literal(" !").formatted(Formatting.YELLOW));

        server.getPlayerManager().broadcast(msg, false);

        target.sendMessage(Text.literal("✦ Un légendaire ")
                .formatted(Formatting.GOLD)
                .append(Text.literal(displayName).formatted(Formatting.RED, Formatting.BOLD))
                .append(Text.literal(" est apparu à côté de toi !").formatted(Formatting.YELLOW)));

        target.getServerWorld().playSound(null, target.getBlockPos(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 0.7f);
    }

    private String formatName(String name) {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
            if (i < parts.length - 1) sb.append(" ");
        }
        return sb.toString();
    }
}
