package fr.combblemon.legendaryspawner;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class SpawnController {

    private final MinecraftServer server;
    private ModConfig config;

    // Timer : tickCounter se remet à 0 après chaque cycle, absoluteTick ne remet jamais à 0
    private long tickCounter = 0;
    private long absoluteTick = 0;
    private long intervalTicks;
    private boolean running = false;

    // Anti-répétition : mémorise les N derniers légendaires spawnés
    private final LinkedList<String> recentSpawns = new LinkedList<>();

    // Cooldown : absoluteTick auquel chaque légendaire a spawné pour la dernière fois
    private final Map<String, Long> cooldownTracker = new HashMap<>();

    // Candidat interne pour la sélection pondérée
    private record Candidate(String name, List<ServerPlayerEntity> players, int weight) {}

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

    /**
     * Met à jour la config sans remettre le timer à zéro.
     * Si le nouvel intervalle est plus court que la progression actuelle, on cap.
     */
    public void updateConfig(ModConfig newConfig) {
        this.config = newConfig;
        this.intervalTicks = (long) newConfig.intervalMinutes * 60 * 20;
        if (tickCounter > intervalTicks) {
            tickCounter = intervalTicks - 1;
        }
    }

    public void tick() {
        if (!running) return;
        absoluteTick++;
        tickCounter++;

        // Annonce de pré-spawn
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
     * Force un spawn immédiat. Ignore les conditions biome/météo/heure.
     * @param fixedTarget joueur cible, ou null pour un joueur aléatoire.
     */
    public void forceSpawn(@Nullable ServerPlayerEntity fixedTarget) {
        tickCounter = 0;

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();

        // Tous les légendaires activés, avec poids — sans conditions ni cooldown
        List<Candidate> candidates = config.legendaries.entrySet().stream()
                .filter(e -> e.getValue().enabled)
                .map(e -> new Candidate(e.getKey(), players, Math.max(1, e.getValue().weight)))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            LegendarySpawnerMod.LOGGER.warn(lang.get("spawn.no_enabled"));
            return;
        }

        Candidate chosen = weightedPick(candidates);
        ServerPlayerEntity target = fixedTarget != null
                ? fixedTarget
                : players.get(ThreadLocalRandom.current().nextInt(players.size()));

        doSpawn(chosen.name(), target, config.legendaries.get(chosen.name()), lang);
    }

    public long getTicksRemaining() {
        return intervalTicks - tickCounter;
    }

    // ---- Logique de spawn automatique ----

    private void spawnLegendary(@Nullable ServerPlayerEntity fixedTarget) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();

        // Construire les candidats éligibles (conditions + cooldown)
        List<Candidate> allCandidates = buildEligibleCandidates(players);

        if (allCandidates.isEmpty()) {
            LegendarySpawnerMod.LOGGER.warn(lang.get("spawn.no_eligible"));
            return;
        }

        // Appliquer l'anti-répétition
        List<Candidate> pool = allCandidates;
        if (config.preventRepeat && config.recentSpawnMemory > 0 && !recentSpawns.isEmpty()) {
            List<Candidate> filtered = allCandidates.stream()
                    .filter(c -> !recentSpawns.contains(c.name()))
                    .collect(Collectors.toList());
            // Si le filtre vide le pool (tous récents), on utilise tout le pool
            if (!filtered.isEmpty()) {
                pool = filtered;
            }
        }

        Candidate chosen = weightedPick(pool);
        ServerPlayerEntity target = fixedTarget != null
                ? fixedTarget
                : chosen.players().get(ThreadLocalRandom.current().nextInt(chosen.players().size()));

        doSpawn(chosen.name(), target, config.legendaries.get(chosen.name()), lang);
    }

    private List<Candidate> buildEligibleCandidates(List<ServerPlayerEntity> players) {
        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<String, LegendaryEntry> entry : config.legendaries.entrySet()) {
            LegendaryEntry def = entry.getValue();
            if (!def.enabled) continue;
            if (isOnCooldown(entry.getKey(), def)) continue;

            List<ServerPlayerEntity> matching = getMatchingPlayers(def, players);
            if (!matching.isEmpty()) {
                candidates.add(new Candidate(entry.getKey(), matching, Math.max(1, def.weight)));
            }
        }
        return candidates;
    }

    private void doSpawn(String pokemonName, ServerPlayerEntity target, LegendaryEntry entry, LangConfig lang) {
        int level = getLevel(entry);

        String cmd = String.format("pokespawn %s level=%d", pokemonName, level);
        server.getCommandManager().executeWithPrefix(
                server.getCommandSource()
                        .withPosition(target.getPos())
                        .withWorld(target.getServerWorld())
                        .withSilent(),
                cmd
        );

        // Enregistrements
        recordRecentSpawn(pokemonName);
        cooldownTracker.put(pokemonName, absoluteTick);
        SpawnLogger.log(pokemonName, target, level);

        broadcastSpawn(target, pokemonName, lang);
    }

    private void broadcastSpawn(ServerPlayerEntity target, String pokemonName, LangConfig lang) {
        String displayName = formatName(pokemonName);

        server.getPlayerManager().broadcast(
                Text.literal(lang.get("spawn.broadcast",
                        "pokemon", displayName,
                        "player", target.getName().getString())),
                false
        );

        target.sendMessage(Text.literal(lang.get("spawn.notify_player", "pokemon", displayName)));

        target.getServerWorld().playSound(null, target.getBlockPos(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 0.7f);
    }

    private void sendWarnMessage() {
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
        String msg = lang.get("spawn.warn", "minutes", String.valueOf(config.warnMinutesBefore));
        server.getPlayerManager().broadcast(Text.literal(msg), false);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.getServerWorld().playSound(null, player.getBlockPos(),
                    SoundEvents.UI_TOAST_IN, SoundCategory.MASTER, 0.8f, 1.0f);
        }
    }

    // ---- Sélection pondérée ----

    private Candidate weightedPick(List<Candidate> candidates) {
        int total = candidates.stream().mapToInt(Candidate::weight).sum();
        int roll = ThreadLocalRandom.current().nextInt(total);
        int cumulative = 0;
        for (Candidate c : candidates) {
            cumulative += c.weight;
            if (roll < cumulative) return c;
        }
        return candidates.get(candidates.size() - 1);
    }

    // ---- Anti-répétition ----

    private void recordRecentSpawn(String name) {
        recentSpawns.addFirst(name);
        while (recentSpawns.size() > Math.max(1, config.recentSpawnMemory)) {
            recentSpawns.removeLast();
        }
    }

    // ---- Cooldown ----

    private boolean isOnCooldown(String name, LegendaryEntry entry) {
        if (entry.cooldownMinutes <= 0) return false;
        Long lastSpawn = cooldownTracker.get(name);
        if (lastSpawn == null) return false;
        long cooldownTicks = (long) entry.cooldownMinutes * 60 * 20;
        return (absoluteTick - lastSpawn) < cooldownTicks;
    }

    // ---- Calcul de niveau ----

    private int getLevel(LegendaryEntry entry) {
        int min = entry.minLevel;
        int max = entry.maxLevel;

        if (min < 0 && max < 0) return config.legendaryLevel;
        if (min < 0) return max;
        if (max < 0) return min;

        // Les deux définis : on s'assure que min <= max
        if (min > max) { int tmp = min; min = max; max = tmp; }
        return min == max ? min : min + ThreadLocalRandom.current().nextInt(max - min + 1);
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
        RegistryEntry<Biome> biomeEntry = player.getServerWorld().getBiome(player.getBlockPos());
        String biomeId = biomeEntry.getKey().map(k -> k.getValue().toString()).orElse("");
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
