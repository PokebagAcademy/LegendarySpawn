package fr.combblemon.legendaryspawner;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class LegendaryCommand {

    private static final int PAGE_SIZE = 10;

    private static final List<String> VALID_TIMEOFDAY  = List.of("any", "day", "night");
    private static final List<String> VALID_WEATHER    = List.of("any", "clear", "rain", "thunder");
    private static final List<String> VALID_DIMENSION  = List.of("any", "overworld", "nether", "end");
    private static final List<String> VALID_SET_PARAMS = List.of(
            "weight", "minlevel", "maxlevel", "cooldown", "timeofday", "weather", "dimension");

    // ---- Providers d'autocomplétion ----

    private static final SuggestionProvider<ServerCommandSource> LEGENDARY_SUGGESTIONS =
            (ctx, builder) -> {
                LegendarySpawnerMod.getInstance().getConfig().legendaries.keySet()
                        .forEach(builder::suggest);
                return builder.buildFuture();
            };

    private static final SuggestionProvider<ServerCommandSource> PARAM_SUGGESTIONS =
            (ctx, builder) -> {
                VALID_SET_PARAMS.forEach(builder::suggest);
                return builder.buildFuture();
            };

    private static final SuggestionProvider<ServerCommandSource> VALUE_SUGGESTIONS =
            (ctx, builder) -> {
                try {
                    String param = StringArgumentType.getString(ctx, "param").toLowerCase();
                    switch (param) {
                        case "timeofday"  -> VALID_TIMEOFDAY.forEach(builder::suggest);
                        case "weather"    -> VALID_WEATHER.forEach(builder::suggest);
                        case "dimension"  -> VALID_DIMENSION.forEach(builder::suggest);
                        case "weight"     -> List.of("1", "2", "5", "10").forEach(builder::suggest);
                        case "cooldown"   -> List.of("0", "30", "60", "120").forEach(builder::suggest);
                        case "minlevel", "maxlevel" -> List.of("-1", "50", "70", "100").forEach(builder::suggest);
                    }
                } catch (Exception ignored) {}
                return builder.buildFuture();
            };

    // ---- Enregistrement ----

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            dispatcher.register(
                literal("legendaryspawner")
                .requires(src -> src.hasPermissionLevel(2))

                // /ls forcespawn [joueur]
                .then(literal("forcespawn")
                    .executes(ctx -> {
                        SpawnController ctrl = LegendarySpawnerMod.getInstance().getSpawnController();
                        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
                        if (ctrl == null) { send(ctx.getSource(), lang.get("command.not_initialized")); return 0; }
                        send(ctx.getSource(), lang.get("command.force_spawn"));
                        ctrl.forceSpawn(null);
                        return 1;
                    })
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
                    .executes(ctx -> {
                        LegendarySpawnerMod.getInstance().reloadConfig();
                        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
                        send(ctx.getSource(), lang.get("command.reload_success"));
                        return 1;
                    })
                )

                // /ls setinterval <minutes>
                .then(literal("setinterval")
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
                    .executes(ctx -> {
                        SpawnController ctrl = LegendarySpawnerMod.getInstance().getSpawnController();
                        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
                        if (ctrl == null) { send(ctx.getSource(), lang.get("command.not_initialized")); return 0; }
                        long ticks = ctrl.getTicksRemaining();
                        long seconds = ticks / 20;
                        send(ctx.getSource(), lang.get("command.timer",
                                "minutes", String.valueOf(seconds / 60),
                                "seconds", String.valueOf(seconds % 60)));
                        return 1;
                    })
                )

                // /ls legendary ...
                .then(literal("legendary")

                    // /ls legendary list [page]
                    .then(literal("list")
                        .executes(ctx -> handleList(ctx.getSource(), 1))
                        .then(argument("page", IntegerArgumentType.integer(1))
                            .executes(ctx -> handleList(ctx.getSource(),
                                    IntegerArgumentType.getInteger(ctx, "page")))
                        )
                    )

                    // /ls legendary info <pokemon>
                    .then(literal("info")
                        .then(argument("pokemon", StringArgumentType.word())
                            .suggests(LEGENDARY_SUGGESTIONS)
                            .executes(ctx -> handleInfo(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "pokemon")))
                        )
                    )

                    // /ls legendary enable <pokemon>
                    .then(literal("enable")
                        .then(argument("pokemon", StringArgumentType.word())
                            .suggests(LEGENDARY_SUGGESTIONS)
                            .executes(ctx -> setEnabled(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "pokemon"), true))
                        )
                    )

                    // /ls legendary disable <pokemon>
                    .then(literal("disable")
                        .then(argument("pokemon", StringArgumentType.word())
                            .suggests(LEGENDARY_SUGGESTIONS)
                            .executes(ctx -> setEnabled(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "pokemon"), false))
                        )
                    )

                    // /ls legendary set <pokemon> <param> <valeur>
                    .then(literal("set")
                        .then(argument("pokemon", StringArgumentType.word())
                            .suggests(LEGENDARY_SUGGESTIONS)
                            .then(argument("param", StringArgumentType.word())
                                .suggests(PARAM_SUGGESTIONS)
                                .then(argument("value", StringArgumentType.greedyString())
                                    .suggests(VALUE_SUGGESTIONS)
                                    .executes(ctx -> handleSet(ctx))
                                )
                            )
                        )
                    )

                    // /ls legendary biome <add|remove|clear> <pokemon> [biome]
                    .then(literal("biome")
                        .then(literal("add")
                            .then(argument("pokemon", StringArgumentType.word())
                                .suggests(LEGENDARY_SUGGESTIONS)
                                .then(argument("biome", StringArgumentType.greedyString())
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
                                            String pokemon = StringArgumentType.getString(ctx, "pokemon");
                                            LegendaryEntry e = LegendarySpawnerMod.getInstance()
                                                    .getConfig().legendaries.get(pokemon);
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

                // /ls help
                .then(literal("help")
                    .executes(ctx -> {
                        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
                        ServerCommandSource src = ctx.getSource();
                        send(src, lang.get("command.help_header"));
                        send(src, lang.get("command.help_forcespawn"));
                        send(src, lang.get("command.help_reload"));
                        send(src, lang.get("command.help_setinterval"));
                        send(src, lang.get("command.help_timer"));
                        send(src, lang.get("command.help_legendary"));
                        return 1;
                    })
                )
            );

            // Alias /ls
            dispatcher.register(
                literal("ls")
                .requires(src -> src.hasPermissionLevel(2))
                .redirect(dispatcher.getRoot().getChild("legendaryspawner"))
            );
        });
    }

    // ---- Handlers ----

    private static int handleList(ServerCommandSource source, int page) {
        ModConfig cfg = LegendarySpawnerMod.getInstance().getConfig();
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();

        List<Map.Entry<String, LegendaryEntry>> entries = new ArrayList<>(cfg.legendaries.entrySet());
        int totalPages = (int) Math.ceil((double) entries.size() / PAGE_SIZE);
        int clampedPage = Math.max(1, Math.min(page, totalPages));
        int from = (clampedPage - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, entries.size());

        send(source, lang.get("command.legendary_list_header",
                "page", String.valueOf(clampedPage),
                "total", String.valueOf(totalPages)));

        for (Map.Entry<String, LegendaryEntry> e : entries.subList(from, to)) {
            String name = e.getKey();
            LegendaryEntry entry = e.getValue();
            if (entry.enabled) {
                String levelStr = buildLevelString(entry, cfg.legendaryLevel);
                send(source, lang.get("command.legendary_list_entry_enabled",
                        "pokemon", name,
                        "weight", String.valueOf(entry.weight),
                        "level", levelStr,
                        "cooldown", String.valueOf(entry.cooldownMinutes)));
            } else {
                send(source, lang.get("command.legendary_list_entry_disabled", "pokemon", name));
            }
        }

        if (totalPages > 1) {
            send(source, lang.get("command.legendary_list_page_hint"));
        }
        return 1;
    }

    private static int handleInfo(ServerCommandSource source, String name) {
        ModConfig cfg = LegendarySpawnerMod.getInstance().getConfig();
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();

        LegendaryEntry entry = cfg.legendaries.get(name);
        if (entry == null) {
            send(source, lang.get("command.legendary_not_found", "pokemon", name));
            return 0;
        }

        String display = SpawnController.formatName(name);
        send(source, lang.get("command.legendary_info_header", "pokemon", display));
        send(source, lang.get("command.legendary_info_enabled",  "value", String.valueOf(entry.enabled)));
        send(source, lang.get("command.legendary_info_weight",   "value", String.valueOf(entry.weight)));
        send(source, lang.get("command.legendary_info_level",    "value", buildLevelString(entry, cfg.legendaryLevel)));
        send(source, lang.get("command.legendary_info_cooldown", "value", String.valueOf(entry.cooldownMinutes)));
        send(source, lang.get("command.legendary_info_timeofday","value", entry.timeOfDay));
        send(source, lang.get("command.legendary_info_weather",  "value", entry.weather));
        send(source, lang.get("command.legendary_info_dimension","value", entry.dimension));
        String biomes = entry.biomes.isEmpty() ? "any" : String.join(", ", entry.biomes);
        send(source, lang.get("command.legendary_info_biomes",   "value", biomes));
        return 1;
    }

    private static int setEnabled(ServerCommandSource source, String name, boolean enabled) {
        ModConfig cfg = LegendarySpawnerMod.getInstance().getConfig();
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();

        LegendaryEntry entry = cfg.legendaries.get(name);
        if (entry == null) {
            send(source, lang.get("command.legendary_not_found", "pokemon", name));
            return 0;
        }
        entry.enabled = enabled;
        cfg.save();
        send(source, lang.get(enabled ? "command.legendary_enabled" : "command.legendary_disabled",
                "pokemon", name));
        return 1;
    }

    private static int handleSet(CommandContext<ServerCommandSource> ctx) {
        String name  = StringArgumentType.getString(ctx, "pokemon");
        String param = StringArgumentType.getString(ctx, "param").toLowerCase();
        String value = StringArgumentType.getString(ctx, "value").trim();

        ModConfig cfg = LegendarySpawnerMod.getInstance().getConfig();
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
        ServerCommandSource src = ctx.getSource();

        LegendaryEntry entry = cfg.legendaries.get(name);
        if (entry == null) {
            send(src, lang.get("command.legendary_not_found", "pokemon", name));
            return 0;
        }

        try {
            switch (param) {
                case "weight" -> {
                    int w = Integer.parseInt(value);
                    if (w < 1) throw new IllegalArgumentException();
                    entry.weight = w;
                }
                case "minlevel" -> {
                    int l = Integer.parseInt(value);
                    if (l < -1 || l > 100) throw new IllegalArgumentException();
                    entry.minLevel = l;
                }
                case "maxlevel" -> {
                    int l = Integer.parseInt(value);
                    if (l < -1 || l > 100) throw new IllegalArgumentException();
                    entry.maxLevel = l;
                }
                case "cooldown" -> {
                    int c = Integer.parseInt(value);
                    if (c < 0) throw new IllegalArgumentException();
                    entry.cooldownMinutes = c;
                }
                case "timeofday" -> {
                    if (!VALID_TIMEOFDAY.contains(value.toLowerCase())) throw new IllegalArgumentException();
                    entry.timeOfDay = value.toLowerCase();
                }
                case "weather" -> {
                    if (!VALID_WEATHER.contains(value.toLowerCase())) throw new IllegalArgumentException();
                    entry.weather = value.toLowerCase();
                }
                case "dimension" -> {
                    if (!VALID_DIMENSION.contains(value.toLowerCase())) throw new IllegalArgumentException();
                    entry.dimension = value.toLowerCase();
                }
                default -> {
                    send(src, lang.get("command.legendary_set_unknown_param", "param", param));
                    return 0;
                }
            }
        } catch (IllegalArgumentException e) {
            send(src, lang.get("command.legendary_set_invalid", "param", param, "value", value));
            return 0;
        }

        cfg.save();
        send(src, lang.get("command.legendary_set_success", "pokemon", name, "param", param, "value", value));
        return 1;
    }

    private static int handleBiomeAdd(ServerCommandSource source, String name, String biome) {
        ModConfig cfg = LegendarySpawnerMod.getInstance().getConfig();
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();

        LegendaryEntry entry = cfg.legendaries.get(name);
        if (entry == null) {
            send(source, lang.get("command.legendary_not_found", "pokemon", name));
            return 0;
        }
        if (entry.biomes.contains(biome)) {
            send(source, lang.get("command.legendary_biome_already", "biome", biome, "pokemon", name));
            return 0;
        }
        entry.biomes.add(biome);
        cfg.save();
        send(source, lang.get("command.legendary_biome_added", "biome", biome, "pokemon", name));
        return 1;
    }

    private static int handleBiomeRemove(ServerCommandSource source, String name, String biome) {
        ModConfig cfg = LegendarySpawnerMod.getInstance().getConfig();
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();

        LegendaryEntry entry = cfg.legendaries.get(name);
        if (entry == null) {
            send(source, lang.get("command.legendary_not_found", "pokemon", name));
            return 0;
        }
        if (!entry.biomes.remove(biome)) {
            send(source, lang.get("command.legendary_biome_not_found", "biome", biome, "pokemon", name));
            return 0;
        }
        cfg.save();
        send(source, lang.get("command.legendary_biome_removed", "biome", biome, "pokemon", name));
        return 1;
    }

    private static int handleBiomeClear(ServerCommandSource source, String name) {
        ModConfig cfg = LegendarySpawnerMod.getInstance().getConfig();
        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();

        LegendaryEntry entry = cfg.legendaries.get(name);
        if (entry == null) {
            send(source, lang.get("command.legendary_not_found", "pokemon", name));
            return 0;
        }
        entry.biomes.clear();
        cfg.save();
        send(source, lang.get("command.legendary_biome_cleared", "pokemon", name));
        return 1;
    }

    // ---- Utilitaires ----

    private static String buildLevelString(LegendaryEntry entry, int globalLevel) {
        int min = entry.minLevel;
        int max = entry.maxLevel;
        if (min < 0 && max < 0) return String.valueOf(globalLevel) + " (global)";
        if (min < 0) return String.valueOf(max);
        if (max < 0) return String.valueOf(min);
        return min == max ? String.valueOf(min) : min + "-" + max;
    }

    private static void send(ServerCommandSource source, String message) {
        source.sendMessage(Text.literal(message));
    }

}
