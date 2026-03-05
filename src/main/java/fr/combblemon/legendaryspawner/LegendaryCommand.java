package fr.combblemon.legendaryspawner;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class LegendaryCommand {

    private static final SuggestionProvider<ServerCommandSource> LEGENDARY_SUGGESTIONS =
            (ctx, builder) -> {
                ModConfig cfg = LegendarySpawnerMod.getInstance().getConfig();
                cfg.legendaries.keySet().forEach(builder::suggest);
                return builder.buildFuture();
            };

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            dispatcher.register(
                literal("legendaryspawner")
                .requires(src -> src.hasPermissionLevel(2))

                .then(literal("forcespawn")
                    .executes(ctx -> {
                        SpawnController ctrl = LegendarySpawnerMod.getInstance().getSpawnController();
                        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
                        if (ctrl == null) {
                            send(ctx.getSource(), lang.get("command.not_initialized"));
                            return 0;
                        }
                        send(ctx.getSource(), lang.get("command.force_spawn"));
                        ctrl.forceSpawn();
                        return 1;
                    })
                )

                .then(literal("reload")
                    .executes(ctx -> {
                        LegendarySpawnerMod.getInstance().reloadConfig();
                        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
                        send(ctx.getSource(), lang.get("command.reload_success"));
                        return 1;
                    })
                )

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

                .then(literal("timer")
                    .executes(ctx -> {
                        SpawnController ctrl = LegendarySpawnerMod.getInstance().getSpawnController();
                        LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
                        if (ctrl == null) {
                            send(ctx.getSource(), lang.get("command.not_initialized"));
                            return 0;
                        }
                        long ticks = ctrl.getTicksRemaining();
                        long seconds = ticks / 20;
                        long minutes = seconds / 60;
                        long secs = seconds % 60;
                        send(ctx.getSource(), lang.get("command.timer",
                                "minutes", String.valueOf(minutes),
                                "seconds", String.valueOf(secs)));
                        return 1;
                    })
                )

                .then(literal("legendary")
                    .then(literal("list")
                        .executes(ctx -> {
                            ModConfig cfg = LegendarySpawnerMod.getInstance().getConfig();
                            LangConfig lang = LegendarySpawnerMod.getInstance().getLang();
                            send(ctx.getSource(), lang.get("command.legendary_list_header"));
                            cfg.legendaries.forEach((name, entry) -> {
                                String key = entry.enabled
                                        ? "command.legendary_list_entry_enabled"
                                        : "command.legendary_list_entry_disabled";
                                send(ctx.getSource(), lang.get(key,
                                        "pokemon", name,
                                        "weight", String.valueOf(entry.weight)));
                            });
                            return 1;
                        })
                    )
                    .then(literal("enable")
                        .then(argument("pokemon", StringArgumentType.word())
                            .suggests(LEGENDARY_SUGGESTIONS)
                            .executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "pokemon");
                                return setEnabled(ctx.getSource(), name, true);
                            })
                        )
                    )
                    .then(literal("disable")
                        .then(argument("pokemon", StringArgumentType.word())
                            .suggests(LEGENDARY_SUGGESTIONS)
                            .executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "pokemon");
                                return setEnabled(ctx.getSource(), name, false);
                            })
                        )
                    )
                )

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

        String msgKey = enabled ? "command.legendary_enabled" : "command.legendary_disabled";
        send(source, lang.get(msgKey, "pokemon", name));
        return 1;
    }

    private static void send(ServerCommandSource source, String message) {
        source.sendMessage(Text.literal(message));
    }
}