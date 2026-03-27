package com.weather;

import com.weather.cobblemon.CobblemonEventListener;
import com.weather.command.WeatherDebugCommand;
import com.weather.config.ServerConfig;
import com.weather.logic.BattleWeatherManager;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.platform.Platform;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExampleMod {
    public static final String MOD_ID = "cobblemon_weather";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ServerConfig config;
    private static BattleWeatherManager weatherManager;

    public static void init() {
        config = ServerConfig.load();
        weatherManager = new BattleWeatherManager();

        // Register Cobblemon battle events (only if Cobblemon is loaded — soft dependency)
        if (Platform.isModLoaded("cobblemon")) {
            CobblemonEventListener.register();
        } else {
            LOGGER.info("[CobblemonWeather] Cobblemon not found — battle weather integration disabled.");
        }

        // Register /cobbleweather debug command on both platforms via Architectury
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, environment) ->
                WeatherDebugCommand.register(dispatcher));

        // Expire stale battle weather records once per tick per world
        TickEvent.SERVER_LEVEL_POST.register(world -> {
            weatherManager.tick(world, world.getTime(), config);
        });

        LOGGER.info("[CobblemonWeather] Initialized. Weather integration enabled: {}",
                config.isEnableWeatherIntegration());
    }

    public static ServerConfig getConfig() { return config; }
    public static BattleWeatherManager getWeatherManager() { return weatherManager; }
}
