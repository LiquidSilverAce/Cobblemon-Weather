package com.weather.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.weather.ExampleMod;
import com.weather.logic.BattleWeatherType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Locale;

public final class WeatherDebugCommand {
    private WeatherDebugCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobbleweather")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("debug")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (BattleWeatherType type : BattleWeatherType.values()) {
                                        builder.suggest(type.name().toLowerCase(Locale.ROOT));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    try {
                                        return apply(ctx.getSource(), BattleWeatherType.valueOf(
                                                StringArgumentType.getString(ctx, "type").toUpperCase(Locale.ROOT)));
                                    } catch (IllegalArgumentException e) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "Valid weather types: clear, sun, rain, sand, snow, thunderstorm"));
                                        return 0;
                                    }
                                })))
                .then(Commands.literal("thunderstorm")
                        .executes(ctx -> apply(ctx.getSource(), BattleWeatherType.THUNDERSTORM)))
                .then(Commands.literal("reload").executes(ctx -> {
                    try {
                        ExampleMod.reloadConfig();
                        ctx.getSource().sendSuccess(() -> Component.literal("Weather configuration reloaded."), false);
                        return 1;
                    } catch (IllegalStateException e) {
                        ctx.getSource().sendFailure(Component.literal(e.getMessage()));
                        return 0;
                    }
                })));
    }

    private static int apply(CommandSourceStack source, BattleWeatherType type) {
        if (source.getEntity() instanceof ServerPlayer player
                && !ExampleMod.getConfig().allowsPlayer(player.getUUID(), player.getGameProfile().getName())) {
            source.sendFailure(Component.literal("You are not on the weather whitelist."));
            return 0;
        }
        if (!source.getLevel().dimension().equals(Level.OVERWORLD)) {
            source.sendFailure(Component.literal("Weather controls only work in the Overworld."));
            return 0;
        }
        ExampleMod.getWeatherManager().applyDebugWeather(source.getLevel(), type, ExampleMod.getConfig());
        source.sendSuccess(() -> Component.literal("Applied " + type + " for "
                + ExampleMod.getConfig().getBattleWeatherDurationTicks() + " ticks."), true);
        return 1;
    }
}
