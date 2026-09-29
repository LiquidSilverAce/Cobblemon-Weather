package com.weather;

import com.weather.cobblemon.CobblemonEventListener;
import com.weather.command.WeatherDebugCommand;
import com.weather.config.ServerConfig;
import com.weather.logic.BattleWeatherManager;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExampleMod {
    public static final String MOD_ID = "cobblemon_weather";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static ServerConfig config = new ServerConfig();
    private static BattleWeatherManager weatherManager;

    public static void init() {
        LifecycleEvent.SERVER_BEFORE_START.register(server -> {
            reloadConfig();
            weatherManager = new BattleWeatherManager(Platform.isModLoaded("cobblemon")
                    ? CobblemonEventListener::isBattleActive : id -> false);
        });
        LifecycleEvent.SERVER_STOPPED.register(server -> weatherManager = null);
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, environment) ->
                WeatherDebugCommand.register(dispatcher));
        TickEvent.SERVER_LEVEL_POST.register(world -> {
            if (weatherManager != null) weatherManager.tick(world, world.getGameTime(), config);
        });
    }

    public static void reloadConfig() {
        // Assignment happens only after successful validation; reload errors retain the previous config.
        config = ServerConfig.load();
        LOGGER.info("Weather integration enabled={}, whitelist entries={}",
                config.isEnableWeatherIntegration(), config.getWeatherWhitelist().size());
    }

    public static ServerConfig getConfig() { return config; }
    public static BattleWeatherManager getWeatherManager() { return weatherManager; }
}
