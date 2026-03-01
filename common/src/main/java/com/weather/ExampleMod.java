package com.weather;

import com.weather.config.ServerConfig;
import com.weather.logic.BattleWeatherManager;
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
        LOGGER.info("[CobblemonWeather] Initialized. Weather integration enabled: {}",
                config.isEnableWeatherIntegration());
    }

    public static ServerConfig getConfig() { return config; }
    public static BattleWeatherManager getWeatherManager() { return weatherManager; }
}
