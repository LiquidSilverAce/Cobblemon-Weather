package com.weather.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.weather.logic.BattleWeatherManager;
import com.weather.logic.BattleWeatherType;
import com.weather.ExampleMod;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;

public final class WeatherDebugCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("cobbleweather")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("debug")
                    .then(CommandManager.argument("type", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (BattleWeatherType t : BattleWeatherType.values()) {
                                builder.suggest(t.name().toLowerCase());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String typeName = StringArgumentType.getString(ctx, "type");
                            BattleWeatherType type;
                            try {
                                type = BattleWeatherType.valueOf(typeName.toUpperCase());
                            } catch (IllegalArgumentException e) {
                                ctx.getSource().sendError(
                                    Text.literal("Unknown weather type: " + typeName +
                                        ". Valid: clear, sun, rain, sand, snow, thunderstorm"));
                                return 0;
                            }

                            ServerWorld world = ctx.getSource().getWorld();
                            RegistryKey<World> dimKey = world.getRegistryKey();
                            if (!dimKey.equals(World.OVERWORLD)) {
                                ctx.getSource().sendError(
                                    Text.literal("Debug command only works in the Overworld."));
                                return 0;
                            }

                            BattleWeatherManager.applyMinecraftWeather(world, type);
                            ctx.getSource().sendFeedback(
                                () -> Text.literal("[CobblemonWeather] Applied debug weather: " + type),
                                true);
                            return 1;
                        })
                    )
                )
                .then(CommandManager.literal("thunderstorm")
                    .requires(src -> src.hasPermissionLevel(2))
                    .executes(ctx -> applyDebugThunderstorm(ctx.getSource(), false))
                    .then(CommandManager.argument("fast", BoolArgumentType.bool())
                        .executes(ctx -> applyDebugThunderstorm(
                                ctx.getSource(),
                                BoolArgumentType.getBool(ctx, "fast")))
                    )
                )
        );
    }

    private static int applyDebugThunderstorm(ServerCommandSource source, boolean fast) {
        ServerWorld world = source.getWorld();
        if (!world.getRegistryKey().equals(World.OVERWORLD)) {
            source.sendError(Text.literal("Thunderstorm command only works in the Overworld."));
            return 0;
        }
        // Force-apply regardless of rain state and cooldown (debug bypass).
        int durationTicks = fast
                ? ExampleMod.getConfig().getThundurusFastTrackTicks()
                : ExampleMod.getConfig().getNormalStormDurationTicks();
        world.setWeather(0, durationTicks, true, true);
        source.sendFeedback(
                () -> Text.literal("[CobblemonWeather] Applied THUNDERSTORM"
                        + (fast ? " (fast/Thundurus)" : "") + " for " + durationTicks + " ticks."),
                true);
        return 1;
    }

    private WeatherDebugCommand() {}
}
