package fr.combblemon.legendaryspawner;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class LegendaryCommand {

    private static final int PAGE_SIZE = 10;

    private static final List<String> VALID_TIMEOFDAY  = List.of("any", "day", "night");
    private static final List<String> VALID_WEATHER    = List.of("any", "clear", "rain", "thunder");
    private static final List<String> VALID_DIMENSION  = List.of("any", "overworld", "nether", "end");
    private static final List<String> VALID_SET_PARAMS = List.of(
            "weight", "minlevel", "maxlevel", "cooldown",
            "timeofday", "weather", "dimension", "displayname");

    // ---- Permission helper ----

    private static Predicate<ServerCommandSource> perm(String node) {
        return src -> PermissionManager.check(src, node, 2);
    }

    // ---- Suggestion providers ----

    private static final SuggestionProvider<ServerCommandSource> LEGENDARY_SUGGESTIONS =
            (ctx, builder) -> {
                LegendarySpawnerMod.getInstance().getLegendaryConfig().getAll().keySet()
                        .forEach(builder::suggest);
                return builder.buildFuture();
            };

    private static final SuggestionProvider<ServerCommandSource> PARAM_SUGGESTIONS =
            (ctx, builder) -> { VALID_SET_PARAMS.forEach(builder::suggest); return builder.buildFuture(); };

    private static final SuggestionProvider<ServerCommandSource> VALUE_SUGGESTIONS =
            (ctx, builder) -> {
                // Resolve param: try context first, fall back to scanning raw input tokens
                String param = null;
                try {
                    param = StringArgumentType.getString(ctx, "param").toLowerCase();
                } catch (Exception ignored) {}
                if (param == null) {
                    for (String token : builder.getInput().trim().split("\\s+"))
                        if (VALID_SET_PARAMS.contains(token.toLowerCase()))
                            param = token.toLowerCase();
                }
                if (param == null) return builder.buildFuture();

                String remaining = builder.getRemaining().toLowerCase();
                switch (param) {
                    case "timeofday" -> VALID_TIMEOFDAY.stream()
                            .filter(v -> v.startsWith(remaining)).forEach(builder::suggest);
                    case "weather"   -> VALID_WEATHER.stream()
                            .filter(v -> v.startsWith(remaining)).forEach(builder::suggest);
                    case "dimension" -> {
                        VALID_DIMENSION.stream()
                                .filter(v -> v.startsWith(remaining)).forEach(builder::suggest);
                        ctx.getSource().getServer().getWorldRegistryKeys().stream()
                                .map(k -> k.getValue().toString())
                                .filter(v -> remaining.isEmpty() || v.contains(remaining))
                                .forEach(builder::suggest);
                    }
                    case "weight"              -> List.of("1","2","5","10").stream()
                            .filter(v -> v.startsWith(remaining)).forEach(builder::suggest);
                    case "cooldown"            -> List.of("0","30","60","120").stream()
                            .filter(v -> v.startsWith(remaining)).forEach(builder::suggest);
                    case "minlevel","maxlevel" -> List.of("-1","50","70","100").stream()
                            .filter(v -> v.startsWith(remaining)).forEach(builder::suggest);
                }
                return builder.buildFuture();
            };

    private static final SuggestionProvider<ServerCommandSource> BIOME_SUGGESTIONS =
            (ctx, builder) -> {
                String remaining = builder.getRemaining().toLowerCase();
                ctx.getSource().getServer().getRegistryManager()
                        .get(RegistryKeys.BIOME).getIds().stream()
                        .map(id -> id.toString())
                        .filter(id -> remaining.isEmpty() || id.contains(remaining))
                        .sorted().forEach(builder::suggest);
                return builder.buildFuture();
            };

    // ---- Enregistrement ----

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            dispatcher.register(
                literal("legendaryspawner")
                .requires(perm(PermissionManager.FORCE_SPAWN))

                // /ls forcespawn [joueur] / /ls forcespawn pokemon <nom> [joueur]
                .then(literal("forcespawn")
                    .requires(perm(PermissionManager.FORCE_SPAWN))
                    .executes(ctx -> {
                        SpawnController ctrl = LegendarySpawnerMod.getInstance().getSpawnController();
                        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
                        if (ctrl == null) { send(ctx.getSource(), lang.get("command.not_initialized")); return 0; }
                        send(ctx.getSource(), lang.get("command.force_spawn"));
                        ctrl.forceSpawn(null);
                        return 1;
                    })
                    .then(literal("pokemon")
                        .then(argument("pokemonname", StringArgumentType.word())
                            .suggests(LEGENDARY_SUGGESTIONS)
                            .executes(ctx -> {
                                SpawnController ctrl = LegendarySpawnerMod.getInstance().getSpawnController();
                                LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
                                if (ctrl == null) { send(ctx.getSource(), lang.get("command.not_initialized")); return 0; }
                                String name = StringArgumentType.getString(ctx, "pokemonname");
                                if (!LegendarySpawnerMod.getInstance().getLegendaryConfig().exists(name)) {
                                    send(ctx.getSource(), lang.get("command.legendary_not_found", "pokemon", name)); return 0;
                                }
                                send(ctx.getSource(), lang.get("command.force_spawn_player", "player", name));
                                ctrl.forceSpawnSpecific(name, null);
                                return 1;
                            })
                            .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> {
                                    SpawnController ctrl = LegendarySpawnerMod.getInstance().getSpawnController();
                                    LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
                                    if (ctrl == null) { send(ctx.getSource(), lang.get("command.not_initialized")); return 0; }
                                    String name = StringArgumentType.getString(ctx, "pokemonname");
                                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                    if (!LegendarySpawnerMod.getInstance().getLegendaryConfig().exists(name)) {
                                        send(ctx.getSource(), lang.get("command.legendary_not_found", "pokemon", name)); return 0;
                                    }
                                    ctrl.forceSpawnSpecific(name, target);
                                    return 1;
                                })
                            )
                        )
                    )
                    .then(argument("player", EntityArgumentType.player())
                        .executes(ctx -> {
                            SpawnController ctrl = LegendarySpawnerMod.getInstance().getSpawnController();
                            LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
                            if (ctrl == null) { send(ctx.getSource(), lang.get("command.not_initialized")); return 0; }
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                            send(ctx.getSource(), lang.get("command.force_spawn_player",
                                    "player", target.getName().getString()));
                            ctrl.forceSpawn(target);
                            return 1;
                        })
                    )
                )

                // /ls reload
                .then(literal("reload")
                    .requires(perm(PermissionManager.RELOAD))
                    .executes(ctx -> {
                        LegendarySpawnerMod.getInstance().reloadConfig();
                        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
                        send(ctx.getSource(), lang.get("command.reload_success"));
                        return 1;
                    })
                )

                // /ls setinterval <minutes>
                .then(literal("setinterval")
                    .requires(perm(PermissionManager.SET_INTERVAL))
                    .then(argument("minutes", IntegerArgumentType.integer(1, 1440))
                        .executes(ctx -> {
                            int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                            ModConfig cfg = LegendarySpawnerMod.getInstance().getConfig();
                            cfg.intervalMinutes = minutes;
                            cfg.save();
                            LegendarySpawnerMod.getInstance().reloadConfig();
                            LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
                            send(ctx.getSource(), lang.get("command.set_interval",
                                    "minutes", String.valueOf(minutes)));
                            return 1;
                        })
                    )
                )

                // /ls timer
                .then(literal("timer")
                    .requires(perm(PermissionManager.TIMER))
                    .executes(ctx -> {
                        SpawnController ctrl = LegendarySpawnerMod.getInstance().getSpawnController();
                        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
                        if (ctrl == null) { send(ctx.getSource(), lang.get("command.not_initialized")); return 0; }
                        long s = ctrl.getTicksRemaining() / 20;
                        send(ctx.getSource(), lang.get("command.timer",
                                "minutes", String.valueOf(s / 60),
                                "seconds", String.valueOf(s % 60)));
                        return 1;
                    })
                )

                // /ls stats
                .then(literal("stats")
                    .requires(perm(PermissionManager.STATS))
                    .executes(ctx -> handleStats(ctx.getSource()))
                )

                // /ls legendary ...
                .then(literal("legendary")

                    // /ls legendary list [page]
                    .then(literal("list")
                        .requires(perm(PermissionManager.LEGENDARY_LIST))
                        .executes(ctx -> handleList(ctx.getSource(), 1))
                        .then(argument("page", IntegerArgumentType.integer(1))
                            .executes(ctx -> handleList(ctx.getSource(),
                                    IntegerArgumentType.getInteger(ctx, "page")))
                        )
                    )

                    // /ls legendary info <pokemon>
                    .then(literal("info")
                        .requires(perm(PermissionManager.LEGENDARY_INFO))
                        .then(argument("pokemon", StringArgumentType.word())
                            .suggests(LEGENDARY_SUGGESTIONS)
                            .executes(ctx -> handleInfo(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "pokemon")))
                        )
                    )

                    // /ls legendary enable <pokemon>
                    .then(literal("enable")
                        .requires(perm(PermissionManager.LEGENDARY_MANAGE))
                        .then(argument("pokemon", StringArgumentType.word())
                            .suggests(LEGENDARY_SUGGESTIONS)
                            .executes(ctx -> setEnabled(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "pokemon"), true))
                        )
                    )

                    // /ls legendary disable <pokemon>
                    .then(literal("disable")
                        .requires(perm(PermissionManager.LEGENDARY_MANAGE))
                        .then(argument("pokemon", StringArgumentType.word())
                            .suggests(LEGENDARY_SUGGESTIONS)
                            .executes(ctx -> setEnabled(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "pokemon"), false))
                        )
                    )

                    // /ls legendary set <pokemon> <param> <valeur>
                    .then(literal("set")
                        .requires(perm(PermissionManager.LEGENDARY_MANAGE))
                        .then(argument("pokemon", StringArgumentType.word())
                            .suggests(LEGENDARY_SUGGESTIONS)
                            .then(argument("param", StringArgumentType.word())
                                .suggests(PARAM_SUGGESTIONS)
                                .then(argument("value", StringArgumentType.greedyString())
                                    .suggests(VALUE_SUGGESTIONS)
                                    .executes(LegendaryCommand::handleSet)
                                )
                            )
                        )
                    )

                    // /ls legendary all <enable|disable|reset|biome>
                    .then(literal("all")
                        .requires(perm(PermissionManager.LEGENDARY_MANAGE))
                        .then(literal("enable")
                            .executes(ctx -> handleAllEnabled(ctx.getSource(), true)))
                        .then(literal("disable")
                            .executes(ctx -> handleAllEnabled(ctx.getSource(), false)))
                        .then(literal("reset")
                            .executes(ctx -> handleAllReset(ctx.getSource())))
                        .then(literal("biome")
                            .then(literal("clear")
                                .executes(ctx -> handleAllBiomeClear(ctx.getSource())))
                            .then(argument("biome", StringArgumentType.greedyString())
                                .suggests(BIOME_SUGGESTIONS)
                                .executes(ctx -> handleAllBiomeSet(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "biome"))))
                        )
                    )

                    // /ls legendary biome <add|remove|clear> <pokemon> [biome]
                    .then(literal("biome")
                        .requires(perm(PermissionManager.LEGENDARY_MANAGE))
                        .then(literal("add")
                            .then(argument("pokemon", StringArgumentType.word())
                                .suggests(LEGENDARY_SUGGESTIONS)
                                .then(argument("biome", StringArgumentType.greedyString())
                                    .suggests(BIOME_SUGGESTIONS)
                                    .executes(ctx -> handleBiomeAdd(ctx.getSource(),
                                            StringArgumentType.getString(ctx, "pokemon"),
                                            StringArgumentType.getString(ctx, "biome")))
                                )
                            )
                        )
                        .then(literal("remove")
                            .then(argument("pokemon", StringArgumentType.word())
                                .suggests(LEGENDARY_SUGGESTIONS)
                                .then(argument("biome", StringArgumentType.word())
                                    .suggests((ctx, builder) -> {
                                        try {
                                            String poke = StringArgumentType.getString(ctx, "pokemon");
                                            LegendaryEntry e = LegendarySpawnerMod.getInstance()
                                                    .getLegendaryConfig().get(poke);
                                            if (e != null) e.biomes.forEach(builder::suggest);
                                        } catch (Exception ignored) {}
                                        return builder.buildFuture();
                                    })
                                    .executes(ctx -> handleBiomeRemove(ctx.getSource(),
                                            StringArgumentType.getString(ctx, "pokemon"),
                                            StringArgumentType.getString(ctx, "biome")))
                                )
                            )
                        )
                        .then(literal("clear")
                            .then(argument("pokemon", StringArgumentType.word())
                                .suggests(LEGENDARY_SUGGESTIONS)
                                .executes(ctx -> handleBiomeClear(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "pokemon")))
                            )
                        )
                    )
                )

                // /ls log [lines] / /ls log clear
                .then(literal("log")
                    .requires(perm(PermissionManager.LOG_VIEW))
                    .executes(ctx -> handleLog(ctx.getSource(), 10))
                    .then(literal("clear")
                        .executes(ctx -> handleLogClear(ctx.getSource())))
                    .then(argument("lines", IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> handleLog(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "lines"))))
                )

                // /ls help
                .then(literal("help")
                    .executes(ctx -> {
                        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
                        send(ctx.getSource(), lang.get("command.help_header"));
                        send(ctx.getSource(), lang.get("command.help_forcespawn"));
                        send(ctx.getSource(), lang.get("command.help_forcespawn_pokemon"));
                        send(ctx.getSource(), lang.get("command.help_reload"));
                        send(ctx.getSource(), lang.get("command.help_setinterval"));
                        send(ctx.getSource(), lang.get("command.help_timer"));
                        send(ctx.getSource(), lang.get("command.help_legendary"));
                        send(ctx.getSource(), lang.get("command.help_log"));
                        send(ctx.getSource(), lang.get("command.help_log_clear"));
                        send(ctx.getSource(), lang.get("command.help_stats"));
                        return 1;
                    })
                )
            );

            // Alias /ls
            dispatcher.register(
                literal("ls")
                .requires(perm(PermissionManager.FORCE_SPAWN))
                .redirect(dispatcher.getRoot().getChild("legendaryspawner"))
            );
        });
    }

    // ---- Handlers ----

    private static int handleList(ServerCommandSource source, int page) {
        LegendaryConfig legendaryConfig = LegendarySpawnerMod.getInstance().getLegendaryConfig();
        ModConfig cfg = LegendarySpawnerMod.getInstance().getConfig();
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();

        List<Map.Entry<String, LegendaryEntry>> entries = new ArrayList<>(legendaryConfig.getAll().entrySet());
        int totalPages = (int) Math.ceil((double) entries.size() / PAGE_SIZE);
        int p    = Math.max(1, Math.min(page, totalPages));
        int from = (p - 1) * PAGE_SIZE;
        int to   = Math.min(from + PAGE_SIZE, entries.size());

        send(source, lang.get("command.legendary_list_header",
                "page", String.valueOf(p), "total", String.valueOf(totalPages)));

        for (Map.Entry<String, LegendaryEntry> e : entries.subList(from, to)) {
            String name = e.getKey();
            LegendaryEntry entry = e.getValue();
            if (entry.enabled) {
                send(source, lang.get("command.legendary_list_entry_enabled",
                        "pokemon", name,
                        "weight", String.valueOf(entry.weight),
                        "level", buildLevelString(entry, cfg.legendaryLevel),
                        "cooldown", String.valueOf(entry.cooldownMinutes)));
            } else {
                send(source, lang.get("command.legendary_list_entry_disabled", "pokemon", name));
            }
        }
        if (totalPages > 1) send(source, lang.get("command.legendary_list_page_hint"));
        return 1;
    }

    private static int handleInfo(ServerCommandSource source, String name) {
        LegendaryConfig legendaryConfig = LegendarySpawnerMod.getInstance().getLegendaryConfig();
        ModConfig cfg = LegendarySpawnerMod.getInstance().getConfig();
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();

        LegendaryEntry entry = legendaryConfig.get(name);
        if (entry == null) { send(source, lang.get("command.legendary_not_found", "pokemon", name)); return 0; }

        send(source, lang.get("command.legendary_info_header", "pokemon", SpawnController.getDisplayName(name, entry)));
        send(source, lang.get("command.legendary_info_enabled",   "value", String.valueOf(entry.enabled)));
        send(source, lang.get("command.legendary_info_weight",    "value", String.valueOf(entry.weight)));
        send(source, lang.get("command.legendary_info_level",     "value", buildLevelString(entry, cfg.legendaryLevel)));
        send(source, lang.get("command.legendary_info_cooldown",  "value", String.valueOf(entry.cooldownMinutes)));
        send(source, lang.get("command.legendary_info_timeofday", "value", entry.timeOfDay));
        send(source, lang.get("command.legendary_info_weather",   "value", entry.weather));
        send(source, lang.get("command.legendary_info_dimension", "value", entry.dimension));
        send(source, lang.get("command.legendary_info_biomes",    "value",
                entry.biomes.isEmpty() ? "any" : String.join(", ", entry.biomes)));
        if (entry.displayName != null && !entry.displayName.isEmpty())
            send(source, "§eNom affiché: §a" + entry.displayName);
        return 1;
    }

    private static int setEnabled(ServerCommandSource source, String name, boolean enabled) {
        LegendaryConfig legendaryConfig = LegendarySpawnerMod.getInstance().getLegendaryConfig();
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
        LegendaryEntry entry = legendaryConfig.get(name);
        if (entry == null) { send(source, lang.get("command.legendary_not_found", "pokemon", name)); return 0; }
        entry.enabled = enabled;
        legendaryConfig.save(name);
        send(source, lang.get(enabled ? "command.legendary_enabled" : "command.legendary_disabled", "pokemon", name));
        return 1;
    }

    private static int handleSet(CommandContext<ServerCommandSource> ctx) {
        String name  = StringArgumentType.getString(ctx, "pokemon");
        String param = StringArgumentType.getString(ctx, "param").toLowerCase();
        String value = StringArgumentType.getString(ctx, "value").trim();

        LegendaryConfig legendaryConfig = LegendarySpawnerMod.getInstance().getLegendaryConfig();
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
        ServerCommandSource src = ctx.getSource();

        LegendaryEntry entry = legendaryConfig.get(name);
        if (entry == null) { send(src, lang.get("command.legendary_not_found", "pokemon", name)); return 0; }

        try {
            switch (param) {
                case "weight"      -> { int w = Integer.parseInt(value); if (w < 1) throw new IllegalArgumentException(); entry.weight = w; }
                case "minlevel"    -> { int l = Integer.parseInt(value); if (l < -1 || l > 100) throw new IllegalArgumentException(); entry.minLevel = l; }
                case "maxlevel"    -> { int l = Integer.parseInt(value); if (l < -1 || l > 100) throw new IllegalArgumentException(); entry.maxLevel = l; }
                case "cooldown"    -> { int c = Integer.parseInt(value); if (c < 0) throw new IllegalArgumentException(); entry.cooldownMinutes = c; }
                case "timeofday"   -> { if (!VALID_TIMEOFDAY.contains(value.toLowerCase())) throw new IllegalArgumentException(); entry.timeOfDay = value.toLowerCase(); }
                case "weather"     -> { if (!VALID_WEATHER.contains(value.toLowerCase())) throw new IllegalArgumentException(); entry.weather = value.toLowerCase(); }
                case "dimension"   -> {
                    String v = value.toLowerCase();
                    boolean validDim = VALID_DIMENSION.contains(v)
                            || src.getServer().getWorldRegistryKeys().stream()
                                .anyMatch(k -> k.getValue().toString().equals(v));
                    if (!validDim) throw new IllegalArgumentException();
                    entry.dimension = v;
                }
                case "displayname" -> entry.displayName = value;
                default -> { send(src, lang.get("command.legendary_set_unknown_param", "param", param)); return 0; }
            }
        } catch (IllegalArgumentException e) {
            send(src, lang.get("command.legendary_set_invalid", "param", param, "value", value));
            return 0;
        }

        legendaryConfig.save(name);
        send(src, lang.get("command.legendary_set_success", "pokemon", name, "param", param, "value", value));
        return 1;
    }

    private static int handleBiomeAdd(ServerCommandSource src, String name, String biome) {
        LegendaryConfig legendaryConfig = LegendarySpawnerMod.getInstance().getLegendaryConfig();
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
        LegendaryEntry entry = legendaryConfig.get(name);
        if (entry == null) { send(src, lang.get("command.legendary_not_found", "pokemon", name)); return 0; }
        if (entry.biomes.contains(biome)) { send(src, lang.get("command.legendary_biome_already", "biome", biome, "pokemon", name)); return 0; }
        entry.biomes.add(biome);
        legendaryConfig.save(name);
        send(src, lang.get("command.legendary_biome_added", "biome", biome, "pokemon", name));
        return 1;
    }

    private static int handleBiomeRemove(ServerCommandSource src, String name, String biome) {
        LegendaryConfig legendaryConfig = LegendarySpawnerMod.getInstance().getLegendaryConfig();
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
        LegendaryEntry entry = legendaryConfig.get(name);
        if (entry == null) { send(src, lang.get("command.legendary_not_found", "pokemon", name)); return 0; }
        if (!entry.biomes.remove(biome)) { send(src, lang.get("command.legendary_biome_not_found", "biome", biome, "pokemon", name)); return 0; }
        legendaryConfig.save(name);
        send(src, lang.get("command.legendary_biome_removed", "biome", biome, "pokemon", name));
        return 1;
    }

    private static int handleBiomeClear(ServerCommandSource src, String name) {
        LegendaryConfig legendaryConfig = LegendarySpawnerMod.getInstance().getLegendaryConfig();
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
        LegendaryEntry entry = legendaryConfig.get(name);
        if (entry == null) { send(src, lang.get("command.legendary_not_found", "pokemon", name)); return 0; }
        entry.biomes.clear();
        legendaryConfig.save(name);
        send(src, lang.get("command.legendary_biome_cleared", "pokemon", name));
        return 1;
    }

    private static int handleAllEnabled(ServerCommandSource source, boolean enabled) {
        LegendaryConfig legendaryConfig = LegendarySpawnerMod.getInstance().getLegendaryConfig();
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
        legendaryConfig.getAll().values().forEach(e -> e.enabled = enabled);
        legendaryConfig.saveAll();
        send(source, lang.get(enabled ? "command.all_enabled" : "command.all_disabled",
                "count", String.valueOf(legendaryConfig.getAll().size())));
        return 1;
    }

    private static int handleAllReset(ServerCommandSource source) {
        LegendaryConfig legendaryConfig = LegendarySpawnerMod.getInstance().getLegendaryConfig();
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
        legendaryConfig.getAll().values().forEach(e -> {
            e.biomes.clear();
            e.timeOfDay = "any";
            e.weather = "any";
            e.dimension = "any";
        });
        legendaryConfig.saveAll();
        send(source, lang.get("command.all_reset", "count", String.valueOf(legendaryConfig.getAll().size())));
        return 1;
    }

    private static int handleAllBiomeSet(ServerCommandSource source, String biome) {
        LegendaryConfig legendaryConfig = LegendarySpawnerMod.getInstance().getLegendaryConfig();
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
        legendaryConfig.getAll().values().forEach(e -> { if (!e.biomes.contains(biome)) e.biomes.add(biome); });
        legendaryConfig.saveAll();
        send(source, lang.get("command.all_biome_set",
                "biome", biome, "count", String.valueOf(legendaryConfig.getAll().size())));
        return 1;
    }

    private static int handleAllBiomeClear(ServerCommandSource source) {
        LegendaryConfig legendaryConfig = LegendarySpawnerMod.getInstance().getLegendaryConfig();
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
        legendaryConfig.getAll().values().forEach(e -> e.biomes.clear());
        legendaryConfig.saveAll();
        send(source, lang.get("command.all_biome_cleared", "count", String.valueOf(legendaryConfig.getAll().size())));
        return 1;
    }

    private static int handleStats(ServerCommandSource source) {
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
        SpawnStats stats = LegendarySpawnerMod.getInstance().getStats();
        send(source, lang.get("command.stats_header"));
        if (stats.totalSpawns == 0) {
            send(source, lang.get("command.stats_empty"));
            return 1;
        }
        send(source, lang.get("command.stats_total",       "count", String.valueOf(stats.totalSpawns)));
        send(source, lang.get("command.stats_top_pokemon", "value", stats.topPokemon()));
        send(source, lang.get("command.stats_top_player",  "value", stats.topPlayer()));
        if (stats.lastPokemon != null) {
            send(source, lang.get("command.stats_last",
                    "pokemon", SpawnController.formatName(stats.lastPokemon),
                    "player",  stats.lastPlayer != null ? stats.lastPlayer : "?",
                    "time",    stats.lastTime != null   ? stats.lastTime   : "?"));
        }
        return 1;
    }

    // ---- Log ----

    private static final Pattern LOG_PATTERN = Pattern.compile(
        "^\\[(\\d{4}-\\d{2}-\\d{2}) (\\d{2}:\\d{2}:\\d{2})\\] (.+) \\(niv\\.(\\d+)\\) spawné près de (.+) à \\((-?\\d+), (-?\\d+), (-?\\d+)\\) dans (.+)$"
    );

    private static int handleLog(ServerCommandSource source, int lines) {
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
        if (!LegendarySpawnerMod.getInstance().getConfig().logSpawns) {
            send(source, lang.get("command.log_disabled"));
            return 0;
        }
        List<String> entries = SpawnLogger.getLastLines(lines);
        if (entries.isEmpty()) { send(source, lang.get("command.log_empty")); return 1; }
        send(source, lang.get("command.log_header", "count", String.valueOf(entries.size())));
        for (String raw : entries) send(source, formatLogLine(raw));
        return 1;
    }

    private static int handleLogClear(ServerCommandSource source) {
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
        if (SpawnLogger.clear()) { send(source, lang.get("command.log_cleared")); return 1; }
        send(source, lang.get("command.log_clear_failed")); return 0;
    }

    private static String formatLogLine(String raw) {
        Matcher m = LOG_PATTERN.matcher(raw.strip());
        if (!m.matches()) return "§7" + raw.strip();
        return String.format("§8[§7%s §e%s§8] §c§l%s §7niv.§f%s §8│ §b§l%s §8│ §7%s, %s, %s §8(%s)",
                m.group(1), m.group(2).substring(0, 5),
                m.group(3), m.group(4), m.group(5),
                m.group(6), m.group(7), m.group(8),
                shortDim(m.group(9)));
    }

    private static String shortDim(String dim) {
        return switch (dim) {
            case "minecraft:overworld"  -> "overworld";
            case "minecraft:the_nether" -> "nether";
            case "minecraft:the_end"    -> "end";
            default -> dim;
        };
    }

    // ---- Utilitaires ----

    private static String buildLevelString(LegendaryEntry entry, int globalLevel) {
        int min = entry.minLevel, max = entry.maxLevel;
        if (min < 0 && max < 0) return globalLevel + "(global)";
        if (min < 0) return String.valueOf(max);
        if (max < 0) return String.valueOf(min);
        return min == max ? String.valueOf(min) : min + "-" + max;
    }

    private static void send(ServerCommandSource source, String message) {
        source.sendMessage(Text.literal(message));
    }
}
