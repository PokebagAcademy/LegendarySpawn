package fr.combblemon.legendaryspawner;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class SpawnController {

    private final MinecraftServer server;
    private ModConfig config;
    private final Random random = new Random();
    private long tickCounter = 0;
    private long intervalTicks;
    private boolean running = false;

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

    // ---- Logique de spawn ----

    private void spawnLegendary() {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();

        // Construire la liste des candidats éligibles : (nom, joueurs compatibles, poids)
        record Candidate(String name, List<ServerPlayerEntity> players, int weight) {}

        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<String, LegendaryEntry> entry : config.legendaries.entrySet()) {
            LegendaryEntry def = entry.getValue();
            if (!def.enabled) continue;

            List<ServerPlayerEntity> matching = getMatchingPlayers(def, players);
            if (!matching.isEmpty()) {
                candidates.add(new Candidate(entry.getKey(), matching, Math.max(1, def.weight)));
            }
        }

        if (candidates.isEmpty()) {
            LegendarySpawnerMod.LOGGER.warn(lang.get("spawn.no_eligible"));
            return;
        }

        // Sélection pondérée d'un légendaire
        int totalWeight = candidates.stream().mapToInt(Candidate::weight).sum();
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        Candidate chosen = candidates.get(candidates.size() - 1);
        for (Candidate c : candidates) {
            cumulative += c.weight;
            if (roll < cumulative) {
                chosen = c;
                break;
            }
        }

        // Sélection aléatoire d'un joueur éligible
        ServerPlayerEntity target = chosen.players().get(random.nextInt(chosen.players().size()));
        String pokemonName = chosen.name();

        // Spawn via commande Cobblemon
        String cmd = String.format("pokespawn %s level=%d", pokemonName, config.legendaryLevel);
        server.getCommandManager().executeWithPrefix(
                server.getCommandSource()
                        .withPosition(target.getPos())
                        .withWorld(target.getServerWorld())
                        .withSilent(),
                cmd
        );

        broadcastSpawn(target, pokemonName, lang);
    }

    private void broadcastSpawn(ServerPlayerEntity target, String pokemonName, LangConfig lang) {
        String displayName = formatName(pokemonName);

        String broadcastMsg = lang.get("spawn.broadcast",
                "pokemon", displayName,
                "player", target.getName().getString());
        server.getPlayerManager().broadcast(Text.literal(broadcastMsg), false);

        String playerMsg = lang.get("spawn.notify_player", "pokemon", displayName);
        target.sendMessage(Text.literal(playerMsg));

        target.getServerWorld().playSound(null, target.getBlockPos(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 0.7f);
    }

    // ---- Filtres de conditions ----

    private List<ServerPlayerEntity> getMatchingPlayers(LegendaryEntry entry, List<ServerPlayerEntity> players) {
        return players.stream()
                .filter(p -> matchesDimension(entry, p))
                .filter(p -> matchesTimeOfDay(entry, p))
                .filter(p -> matchesWeather(entry, p))
                .filter(p -> matchesBiome(entry, p))
                .collect(Collectors.toList());
    }

    private boolean matchesBiome(LegendaryEntry entry, ServerPlayerEntity player) {
        if (entry.biomes == null || entry.biomes.isEmpty()) return true;

        ServerWorld world = player.getServerWorld();
        RegistryEntry<Biome> biomeEntry = world.getBiome(player.getBlockPos());
        String biomeId = biomeEntry.getKey()
                .map(k -> k.getValue().toString())
                .orElse("");

        return entry.biomes.contains(biomeId);
    }

    private boolean matchesDimension(LegendaryEntry entry, ServerPlayerEntity player) {
        if (entry.dimension == null || entry.dimension.equalsIgnoreCase("any")) return true;

        String dimId = player.getServerWorld().getRegistryKey().getValue().toString();
        return switch (entry.dimension.toLowerCase()) {
            case "overworld" -> dimId.equals("minecraft:overworld");
            case "nether"    -> dimId.equals("minecraft:the_nether");
            case "end"       -> dimId.equals("minecraft:the_end");
            default          -> dimId.equals(entry.dimension);
        };
    }

    private boolean matchesTimeOfDay(LegendaryEntry entry, ServerPlayerEntity player) {
        if (entry.timeOfDay == null || entry.timeOfDay.equalsIgnoreCase("any")) return true;

        ServerWorld world = player.getServerWorld();
        return switch (entry.timeOfDay.toLowerCase()) {
            case "day"   -> world.isDay();
            case "night" -> world.isNight();
            default      -> true;
        };
    }

    private boolean matchesWeather(LegendaryEntry entry, ServerPlayerEntity player) {
        if (entry.weather == null || entry.weather.equalsIgnoreCase("any")) return true;

        ServerWorld world = player.getServerWorld();
        return switch (entry.weather.toLowerCase()) {
            case "clear"   -> !world.isRaining();
            case "rain"    -> world.isRaining() && !world.isThundering();
            case "thunder" -> world.isThundering();
            default        -> true;
        };
    }

    // ---- Utilitaire ----

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