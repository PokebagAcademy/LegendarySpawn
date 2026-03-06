package fr.combblemon.legendaryspawner;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class SpawnController {

    private final MinecraftServer server;
    private ModConfig config;
    private LegendaryConfig legendaryConfig;

    private long tickCounter = 0;
    private long absoluteTick = 0;
    private long intervalTicks;
    private boolean running = false;

    private final LinkedList<String> recentSpawns = new LinkedList<>();
    private final Map<String, Long> cooldownTracker = new HashMap<>();

    // ---- AFK tracking ----
    private final Map<UUID, Vec3d> afkLastPos = new HashMap<>();
    private final Map<UUID, Long>  afkSince   = new HashMap<>();

    private record Candidate(String name, List<ServerPlayerEntity> players, int weight) {}

    private static final String[] IV_PROPERTIES = {
        "hp_iv", "attack_iv", "defence_iv", "special_attack_iv", "special_defence_iv", "speed_iv"
    };

    public SpawnController(MinecraftServer server, ModConfig config, LegendaryConfig legendaryConfig) {
        this.server = server;
        this.config = config;
        this.legendaryConfig = legendaryConfig;
        this.intervalTicks = (long) config.intervalMinutes * 60 * 20;
    }

    public void start() { running = true; tickCounter = 0; }
    public void stop()  { running = false; }

    public void updateConfig(ModConfig newConfig, LegendaryConfig newLegendaryConfig) {
        this.config = newConfig;
        this.legendaryConfig = newLegendaryConfig;
        this.intervalTicks = (long) newConfig.intervalMinutes * 60 * 20;
        if (tickCounter > intervalTicks) tickCounter = intervalTicks - 1;
    }

    public void tick() {
        if (!running) return;

        if (config.minPlayersToTick > 0
                && server.getPlayerManager().getCurrentPlayerCount() < config.minPlayersToTick) {
            return;
        }

        absoluteTick++;
        tickCounter++;

        // Mise à jour AFK toutes les secondes
        if (absoluteTick % 20 == 0) updateAfkTracking();

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

    public void forceSpawn(@Nullable ServerPlayerEntity fixedTarget) {
        tickCounter = 0;
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();

        List<String> enabled = legendaryConfig.getAll().entrySet().stream()
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

        LegendarySpawnerMod.getInstance().getChanceTracker().onSpawn();
        LegendarySpawnerMod.getInstance().getChanceTracker().save();

        doSpawn(chosen, target, legendaryConfig.get(chosen), lang);
    }

    public void forceSpawnSpecific(String pokemonName, @Nullable ServerPlayerEntity fixedTarget) {
        tickCounter = 0;
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
        LegendaryEntry entry = legendaryConfig.get(pokemonName);
        if (entry == null) return;

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        ServerPlayerEntity target = fixedTarget != null
                ? fixedTarget
                : players.get(ThreadLocalRandom.current().nextInt(players.size()));

        LegendarySpawnerMod.getInstance().getChanceTracker().onSpawn();
        LegendarySpawnerMod.getInstance().getChanceTracker().save();

        doSpawn(pokemonName, target, entry, lang);
    }

    public long getTicksRemaining() { return intervalTicks - tickCounter; }

    public boolean isNameOnCooldown(String name) {
        LegendaryEntry entry = legendaryConfig.get(name);
        return entry != null && isOnCooldown(name, entry);
    }

    public record LiveCandidate(String name, int eligiblePlayers, int weight) {}

    /** Retourne les légendaires spawnable RIGHT NOW (au moins 1 joueur éligible, pas en cooldown). */
    public List<LiveCandidate> getLiveCandidates() {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        List<LiveCandidate> result = new ArrayList<>();
        for (Map.Entry<String, LegendaryEntry> entry : legendaryConfig.getAll().entrySet()) {
            LegendaryEntry def = entry.getValue();
            if (!def.enabled) continue;
            if (isOnCooldown(entry.getKey(), def)) continue;
            long matching = players.stream()
                    .filter(p -> !isAfk(p))
                    .filter(p -> matchesBiome(def, p))
                    .filter(p -> matchesDimension(def, p))
                    .filter(p -> matchesTimeOfDay(def, p))
                    .filter(p -> matchesWeather(def, p))
                    .count();
            if (matching > 0)
                result.add(new LiveCandidate(entry.getKey(), (int) matching, Math.max(1, def.weight)));
        }
        return result;
    }

    // ---- Spawn automatique ----

    private void spawnLegendary(@Nullable ServerPlayerEntity fixedTarget) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
        ChanceTracker tracker = LegendarySpawnerMod.getInstance().getChanceTracker();

        double currentChance = tracker.getCurrentChance(config);
        if (ThreadLocalRandom.current().nextDouble() * 100.0 >= currentChance) {
            tracker.onFailedTick(config);
            tracker.save();
            return;
        }

        List<Candidate> candidates = buildEligibleCandidates(players);
        if (candidates.isEmpty()) {
            tracker.onFailedTick(config);
            tracker.save();
            LegendarySpawnerMod.LOGGER.warn(lang.get("spawn.no_eligible"));
            return;
        }

        List<Candidate> pool = applyAntiRepeat(candidates);
        Candidate chosen = weightedPick(pool);
        ServerPlayerEntity target = fixedTarget != null
                ? fixedTarget
                : chosen.players().get(ThreadLocalRandom.current().nextInt(chosen.players().size()));

        tracker.onSpawn();
        tracker.save();

        doSpawn(chosen.name(), target, legendaryConfig.get(chosen.name()), lang);
    }

    private void doSpawn(String pokemonName, ServerPlayerEntity target, LegendaryEntry entry, LangConfig lang) {
        int level = getLevel(entry);
        Vec3d pos = getSpawnPosition(target);

        StringBuilder cmd = new StringBuilder(String.format("pokespawn %s level=%d", pokemonName, level));

        // IVs parfaits aléatoires
        int ivCount = Math.min(Math.max(0, config.perfectIvCount), IV_PROPERTIES.length);
        if (ivCount > 0) {
            List<String> pool = new ArrayList<>(Arrays.asList(IV_PROPERTIES));
            Collections.shuffle(pool, ThreadLocalRandom.current());
            for (int i = 0; i < ivCount; i++) {
                cmd.append(" ").append(pool.get(i)).append("=31");
            }
        }

        // Shiny
        boolean shiny = config.shinyChance > 0
                && ThreadLocalRandom.current().nextDouble() * 100.0 < config.shinyChance;
        if (shiny) cmd.append(" shiny=true");

        server.getCommandManager().executeWithPrefix(
                server.getCommandSource()
                        .withPosition(pos)
                        .withWorld(target.getServerWorld())
                        .withSilent(),
                cmd.toString()
        );

        recordRecentSpawn(pokemonName);
        cooldownTracker.put(pokemonName, absoluteTick);
        SpawnLogger.log(pokemonName, target, level);
        broadcastSpawn(target, pokemonName, entry, shiny, lang);

        // Stats
        LegendarySpawnerMod.getInstance().getStats().record(pokemonName, target.getName().getString());
    }

    // ---- Position de spawn au sol ----

    private Vec3d getSpawnPosition(ServerPlayerEntity player) {
        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        int min = config.spawnRadiusMin;
        int max = Math.max(min + 1, config.spawnRadiusMax);
        double radius = min + ThreadLocalRandom.current().nextDouble() * (max - min);
        double x = player.getX() + Math.cos(angle) * radius;
        double z = player.getZ() + Math.sin(angle) * radius;

        ServerWorld world = player.getServerWorld();
        int groundY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);

        return new Vec3d(x, groundY, z);
    }

    // ---- AFK ----

    private void updateAfkTracking() {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            Vec3d pos  = p.getPos();
            Vec3d last = afkLastPos.get(p.getUuid());
            if (last == null || last.squaredDistanceTo(pos) > 0.01) {
                afkLastPos.put(p.getUuid(), pos);
                afkSince.put(p.getUuid(), absoluteTick);
            }
        }
    }

    private boolean isAfk(ServerPlayerEntity player) {
        if (config.ignoreAfkSeconds <= 0) return false;
        Long since = afkSince.get(player.getUuid());
        if (since == null) return false;
        return (absoluteTick - since) >= (long) config.ignoreAfkSeconds * 20;
    }

    // ---- Éligibilité ----

    public List<String> buildEligibleNames(ServerPlayerEntity player) {
        List<String> eligible = new ArrayList<>();
        for (Map.Entry<String, LegendaryEntry> entry : legendaryConfig.getAll().entrySet()) {
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

    private List<Candidate> buildEligibleCandidates(List<ServerPlayerEntity> players) {
        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<String, LegendaryEntry> entry : legendaryConfig.getAll().entrySet()) {
            LegendaryEntry def = entry.getValue();
            if (!def.enabled) continue;
            if (isOnCooldown(entry.getKey(), def)) continue;

            List<ServerPlayerEntity> matching = players.stream()
                    .filter(p -> !isAfk(p))
                    .filter(p -> matchesBiome(def, p))
                    .filter(p -> matchesDimension(def, p))
                    .filter(p -> matchesTimeOfDay(def, p))
                    .filter(p -> matchesWeather(def, p))
                    .collect(Collectors.toList());

            if (!matching.isEmpty()) {
                candidates.add(new Candidate(entry.getKey(), matching, Math.max(1, def.weight)));
            }
        }
        return candidates;
    }

    // ---- Sélection pondérée ----

    private Candidate weightedPick(List<Candidate> candidates) {
        int total = candidates.stream().mapToInt(Candidate::weight).sum();
        int roll  = ThreadLocalRandom.current().nextInt(total);
        int cum   = 0;
        for (Candidate c : candidates) {
            cum += c.weight();
            if (roll < cum) return c;
        }
        return candidates.get(candidates.size() - 1);
    }

    private String weightedPickName(List<String> names) {
        int total = names.stream().mapToInt(n -> Math.max(1, legendaryConfig.get(n).weight)).sum();
        int roll  = ThreadLocalRandom.current().nextInt(total);
        int cum   = 0;
        for (String name : names) {
            cum += Math.max(1, legendaryConfig.get(name).weight);
            if (roll < cum) return name;
        }
        return names.get(names.size() - 1);
    }

    // ---- Anti-répétition ----

    private List<Candidate> applyAntiRepeat(List<Candidate> candidates) {
        if (!config.preventRepeat || config.recentSpawnMemory <= 0 || recentSpawns.isEmpty())
            return candidates;
        List<Candidate> filtered = candidates.stream()
                .filter(c -> !recentSpawns.contains(c.name()))
                .collect(Collectors.toList());
        return filtered.isEmpty() ? candidates : filtered;
    }

    private void recordRecentSpawn(String name) {
        recentSpawns.addFirst(name);
        while (recentSpawns.size() > Math.max(1, config.recentSpawnMemory)) recentSpawns.removeLast();
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
        int min = entry.minLevel, max = entry.maxLevel;
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

    private void broadcastSpawn(ServerPlayerEntity target, String pokemonName,
                                LegendaryEntry entry, boolean shiny, LangConfig lang) {
        String display = getDisplayName(pokemonName, entry);
        String msgKey  = shiny ? "spawn.broadcast_shiny" : "spawn.broadcast";
        server.getPlayerManager().broadcast(
                Text.literal(lang.get(msgKey,
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

    // ---- Utilitaires ----

    public static String getDisplayName(String name, LegendaryEntry entry) {
        if (entry != null && entry.displayName != null && !entry.displayName.isEmpty())
            return entry.displayName;
        return formatName(name);
    }

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
