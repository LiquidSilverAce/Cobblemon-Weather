package com.weather.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ServerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon_weather");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "cobblemon_weather.json";

    private boolean enableWeatherIntegration = true;
    private int minDurationTicks = 600;
    private boolean allowPrimalOverride = true;
    private boolean allowCrossBattleOverride = false;
    private boolean clearWeatherOnBattleEnd = false;
    private boolean enableThunderstormIntegration = true;
    private int normalStormDurationTicks = 1800;
    private int thundurusFastTrackTicks = 900;

    public boolean isEnableWeatherIntegration() { return enableWeatherIntegration; }
    public int getMinDurationTicks() { return minDurationTicks; }
    public boolean isAllowPrimalOverride() { return allowPrimalOverride; }
    public boolean isAllowCrossBattleOverride() { return allowCrossBattleOverride; }
    public boolean isClearWeatherOnBattleEnd() { return clearWeatherOnBattleEnd; }
    public boolean isEnableThunderstormIntegration() { return enableThunderstormIntegration; }
    public int getNormalStormDurationTicks() { return normalStormDurationTicks; }
    public int getThundurusFastTrackTicks() { return thundurusFastTrackTicks; }

    public static ServerConfig load() {
        Path configDir = Platform.getConfigFolder();
        Path configFile = configDir.resolve(CONFIG_FILE);
        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile)) {
                ServerConfig loaded = GSON.fromJson(reader, ServerConfig.class);
                if (loaded != null) {
                    LOGGER.info("[CobblemonWeather] Loaded config from {}", configFile);
                    return loaded;
                }
            } catch (IOException e) {
                LOGGER.error("[CobblemonWeather] Failed to read config, using defaults", e);
            }
        }
        ServerConfig defaults = new ServerConfig();
        defaults.save(configFile);
        return defaults;
    }

    private void save(Path configFile) {
        try (Writer writer = Files.newBufferedWriter(configFile)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            LOGGER.error("[CobblemonWeather] Failed to save default config", e);
        }
    }
}
