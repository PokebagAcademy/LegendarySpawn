package fr.combblemon.legendaryspawner;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class SpawnController {

    private final MinecraftServer server;
    private ModConfig config;

    private long tickCounter = 0;
    private long absoluteTick = 0;   // ne remet jamais à 0 (pour les cooldowns)
    private long intervalTicks;
    private boolean running = false;

    private final LinkedList<String> recentSpawns = new LinkedList<>();
    private final Map<String, Long> cooldownTracker = new HashMap<>();

    public SpawnController(MinecraftServer server, ModConfig config) {
        this.server = server;
        this.config = config;
        this.intervalTicks = (long) config.intervalMinutes * 60 * 20;
    }

    public void start() { running = true; tickCounter = 0; }
    public void stop()  { running = false; }

    /** Met à jour la config sans remettre le timer à zéro. */
    public void updateConfig(ModConfig newConfig) {
        this.config = newConfig;
        this.intervalTicks = (long) newConfig.intervalMinutes * 60 * 20;
        if (tickCounter > intervalTicks) tickCounter = intervalTicks - 1;
    }

    public void tick() {
        if (!running) return;
        absoluteTick++;
        tickCounter++;

        if (config.warnMinutesBefore > 0) {
            long warnTicks = (long) config.warnMinutesBefore * 60 * 20;
            if (warnTicks < intervalTicks && tickCounter == intervalTicks - warnTicks) {
                sendWarnMessage();
            }
        }

        if (tickCounter >= intervalTicks) {
            tickCounter = 0;
            spawnLegendary(null);
        }
    }

    /**
     * Force un spawn immédiat. Bypass les conditions biome/météo/heure et les rolls de chance.
     * @param fixedTarget joueur cible, ou null pour un joueur aléatoire.
     */
    public void forceSpawn(@Nullable ServerPlayerEntity fixedTarget) {
        tickCounter = 0;
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();

        // Tous les légendaires activés, avec poids — sans conditions ni chance
        List<String> enabled = config.legendaries.entrySet().stream()
                .filter(e -> e.getValue().enabled)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (enabled.isEmpty()) {
            LegendarySpawnerMod.LOGGER.warn(lang.get("spawn.no_enabled"));
            return;
        }

        String chosen = weightedPickName(enabled);
        ServerPlayerEntity target = fixedTarget != null
                ? fixedTarget
                : players.get(ThreadLocalRandom.current().nextInt(players.size()));

        doSpawn(chosen, target, config.legendaries.get(chosen), lang);
    }

    public long getTicksRemaining() { return intervalTicks - tickCounter; }

    // ---- Logique de spawn automatique ----

    private void spawnLegendary(@Nullable ServerPlayerEntity fixedTarget) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
        ChanceTracker tracker = LegendarySpawnerMod.getInstance().getChanceTracker();

        // 1. Choisir un joueur aléatoire
        ServerPlayerEntity candidate = fixedTarget != null
                ? fixedTarget
                : players.get(ThreadLocalRandom.current().nextInt(players.size()));

        // 2. Trouver les légendaires éligibles pour ce joueur (conditions + cooldown)
        List<String> eligible = buildEligibleNames(candidate);
        if (eligible.isEmpty()) return;

        // 3. Anti-répétition
        List<String> pool = applyAntiRepeat(eligible);

        // 4. Roll de chance pour chaque légendaire du pool
        List<String> successes = pool.stream()
                .filter(name -> {
                    double chance = tracker.getCurrentChance(name, config.legendaries.get(name), config);
                    return ThreadLocalRandom.current().nextDouble() * 100.0 < chance;
                })
                .collect(Collectors.toList());

        if (successes.isEmpty()) {
            // Aucun légendaire n'a passé le roll → on incrémente les bonus des éligibles
            for (String name : eligible) {
                tracker.incrementBonus(name, config.legendaries.get(name), config);
            }
            tracker.save();
            return;
        }

        // 5. Tirage pondéré parmi les succès
        String chosen = weightedPickName(successes);

        // 6. Reset du bonus du gagnant, incrément des autres éligibles
        tracker.resetBonus(chosen);
        for (String name : eligible) {
            if (!name.equals(chosen)) {
                tracker.incrementBonus(name, config.legendaries.get(name), config);
            }
        }
        tracker.save();

        doSpawn(chosen, candidate, config.legendaries.get(chosen), lang);
    }

    private void doSpawn(String pokemonName, ServerPlayerEntity target, LegendaryEntry entry, LangConfig lang) {
        int level = getLevel(entry);
        Vec3d spawnPos = getSpawnPosition(target);

        String cmd = String.format("pokespawn %s level=%d", pokemonName, level);
        server.getCommandManager().executeWithPrefix(
                server.getCommandSource()
                        .withPosition(spawnPos)
                        .withWorld(target.getServerWorld())
                        .withSilent(),
                cmd
        );

        recordRecentSpawn(pokemonName);
        cooldownTracker.put(pokemonName, absoluteTick);
        SpawnLogger.log(pokemonName, target, level);
        broadcastSpawn(target, pokemonName, lang);
    }

    // ---- Position de spawn autour du joueur ----

    private Vec3d getSpawnPosition(ServerPlayerEntity player) {
        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        int min = config.spawnRadiusMin;
        int max = Math.max(min + 1, config.spawnRadiusMax);
        double radius = min + ThreadLocalRandom.current().nextDouble() * (max - min);
        return new Vec3d(
                player.getX() + Math.cos(angle) * radius,
                player.getY(),
                player.getZ() + Math.sin(angle) * radius
        );
    }

    // ---- Eligibilité (public pour /nextleg) ----

    /**
     * Retourne la liste des noms de légendaires éligibles pour un joueur donné.
     * Tient compte des conditions (biome, météo, heure, dimension) et des cooldowns.
     */
    public List<String> buildEligibleNames(ServerPlayerEntity player) {
        List<String> eligible = new ArrayList<>();
        for (Map.Entry<String, LegendaryEntry> entry : config.legendaries.entrySet()) {
            LegendaryEntry def = entry.getValue();
            if (!def.enabled) continue;
            if (isOnCooldown(entry.getKey(), def)) continue;
            if (!matchesBiome(def, player)) continue;
            if (!matchesDimension(def, player)) continue;
            if (!matchesTimeOfDay(def, player)) continue;
            if (!matchesWeather(def, player)) continue;
            eligible.add(entry.getKey());
        }
        return eligible;
    }

    // ---- Sélection pondérée ----

    private String weightedPickName(List<String> names) {
        int total = names.stream().mapToInt(n -> Math.max(1, config.legendaries.get(n).weight)).sum();
        int roll = ThreadLocalRandom.current().nextInt(total);
        int cumulative = 0;
        for (String name : names) {
            cumulative += Math.max(1, config.legendaries.get(name).weight);
            if (roll < cumulative) return name;
        }
        return names.get(names.size() - 1);
    }

    // ---- Anti-répétition ----

    private List<String> applyAntiRepeat(List<String> eligible) {
        if (!config.preventRepeat || config.recentSpawnMemory <= 0 || recentSpawns.isEmpty()) {
            return eligible;
        }
        List<String> filtered = eligible.stream()
                .filter(n -> !recentSpawns.contains(n))
                .collect(Collectors.toList());
        return filtered.isEmpty() ? eligible : filtered;
    }

    private void recordRecentSpawn(String name) {
        recentSpawns.addFirst(name);
        while (recentSpawns.size() > Math.max(1, config.recentSpawnMemory)) {
            recentSpawns.removeLast();
        }
    }

    // ---- Cooldown ----

    private boolean isOnCooldown(String name, LegendaryEntry entry) {
        if (entry.cooldownMinutes <= 0) return false;
        Long last = cooldownTracker.get(name);
        if (last == null) return false;
        return (absoluteTick - last) < (long) entry.cooldownMinutes * 60 * 20;
    }

    // ---- Niveau ----

    private int getLevel(LegendaryEntry entry) {
        int min = entry.minLevel;
        int max = entry.maxLevel;
        if (min < 0 && max < 0) return config.legendaryLevel;
        if (min < 0) return max;
        if (max < 0) return min;
        if (min > max) { int t = min; min = max; max = t; }
        return min == max ? min : min + ThreadLocalRandom.current().nextInt(max - min + 1);
    }

    // ---- Filtres de conditions ----

    private boolean matchesBiome(LegendaryEntry entry, ServerPlayerEntity player) {
        if (entry.biomes == null || entry.biomes.isEmpty()) return true;
        RegistryEntry<Biome> be = player.getServerWorld().getBiome(player.getBlockPos());
        String id = be.getKey().map(k -> k.getValue().toString()).orElse("");
        return entry.biomes.contains(id);
    }

    private boolean matchesDimension(LegendaryEntry entry, ServerPlayerEntity player) {
        if (entry.dimension == null || entry.dimension.equalsIgnoreCase("any")) return true;
        String dim = player.getServerWorld().getRegistryKey().getValue().toString();
        return switch (entry.dimension.toLowerCase()) {
            case "overworld" -> dim.equals("minecraft:overworld");
            case "nether"    -> dim.equals("minecraft:the_nether");
            case "end"       -> dim.equals("minecraft:the_end");
            default          -> dim.equals(entry.dimension);
        };
    }

    private boolean matchesTimeOfDay(LegendaryEntry entry, ServerPlayerEntity player) {
        if (entry.timeOfDay == null || entry.timeOfDay.equalsIgnoreCase("any")) return true;
        ServerWorld w = player.getServerWorld();
        return switch (entry.timeOfDay.toLowerCase()) {
            case "day"   -> w.isDay();
            case "night" -> w.isNight();
            default      -> true;
        };
    }

    private boolean matchesWeather(LegendaryEntry entry, ServerPlayerEntity player) {
        if (entry.weather == null || entry.weather.equalsIgnoreCase("any")) return true;
        ServerWorld w = player.getServerWorld();
        return switch (entry.weather.toLowerCase()) {
            case "clear"   -> !w.isRaining();
            case "rain"    -> w.isRaining() && !w.isThundering();
            case "thunder" -> w.isThundering();
            default        -> true;
        };
    }

    // ---- Broadcast ----

    private void broadcastSpawn(ServerPlayerEntity target, String pokemonName, LangConfig lang) {
        String display = formatName(pokemonName);
        server.getPlayerManager().broadcast(
                Text.literal(lang.get("spawn.broadcast",
                        "pokemon", display,
                        "player", target.getName().getString())),
                false
        );
        target.sendMessage(Text.literal(lang.get("spawn.notify_player", "pokemon", display)));
        target.getServerWorld().playSound(null, target.getBlockPos(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 0.7f);
    }

    private void sendWarnMessage() {
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
        server.getPlayerManager().broadcast(
                Text.literal(lang.get("spawn.warn", "minutes", String.valueOf(config.warnMinutesBefore))),
                false
        );
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.getServerWorld().playSound(null, p.getBlockPos(),
                    SoundEvents.UI_TOAST_IN, SoundCategory.MASTER, 0.8f, 1.0f);
        }
    }

    // ---- Utilitaire (public pour /nextleg et SpawnLogger) ----

    public static String formatName(String name) {
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
