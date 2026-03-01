package com.weather.logic;

import com.weather.config.ServerConfig;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BattleWeatherManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon_weather");

    private final Map<RegistryKey<World>, ActiveBattleWeather> activeWeather = new ConcurrentHashMap<>();

    /** Tick at which the last thunderstorm was applied per dimension, for cooldown enforcement. */
    private final Map<RegistryKey<World>, Long> lastThunderstormTick = new ConcurrentHashMap<>();

    /** Minimum ticks between thunderstorm triggers (30 seconds), bypassed by Thundurus fast-track. */
    private static final int THUNDERSTORM_COOLDOWN_TICKS = 600;

    public void applyWeatherFromBattle(ServerWorld world,
                                       UUID battleId,
                                       BattleWeatherType type,
                                       int priority,
                                       long currentTick,
                                       ServerConfig config) {
        if (!config.isEnableWeatherIntegration()) return;

        RegistryKey<World> dimKey = world.getRegistryKey();
        ActiveBattleWeather existing = activeWeather.get(dimKey);

        boolean shouldApply;
        if (existing == null || existing.isExpired(currentTick)) {
            shouldApply = true;
        } else if (existing.getSourceBattleId().equals(battleId)) {
            shouldApply = true;
        } else {
            // Different battle still active
            if (!config.isAllowCrossBattleOverride()) {
                shouldApply = false;
            } else {
                // Primal moves (priority 2) always override if allowPrimalOverride is true
                if (priority == 2 && config.isAllowPrimalOverride()) {
                    shouldApply = true;
                } else {
                    shouldApply = priority > existing.getPriority();
                }
            }
        }

        if (shouldApply) {
            long expiresAt = currentTick + config.getMinDurationTicks();
            ActiveBattleWeather record = new ActiveBattleWeather(type, battleId, priority, expiresAt);
            activeWeather.put(dimKey, record);
            applyMinecraftWeather(world, type);
            LOGGER.debug("[CobblemonWeather] Applied {} in {} (battle={}, priority={}, expiresAt={})",
                    type, dimKey.getValue(), battleId, priority, expiresAt);
        }
    }

    public void onBattleEnd(ServerWorld world, UUID battleId, long currentTick, ServerConfig config) {
        if (!config.isEnableWeatherIntegration()) return;

        RegistryKey<World> dimKey = world.getRegistryKey();
        ActiveBattleWeather existing = activeWeather.get(dimKey);
        if (existing != null && existing.getSourceBattleId().equals(battleId)) {
            if (config.isClearWeatherOnBattleEnd()) {
                activeWeather.remove(dimKey);
                applyMinecraftWeather(world, BattleWeatherType.CLEAR);
                LOGGER.debug("[CobblemonWeather] Cleared weather in {} after battle {} ended",
                        dimKey.getValue(), battleId);
            }
            // Otherwise let expiresAtTick decay naturally
        }
    }

    public void tick(ServerWorld world, long currentTick, ServerConfig config) {
        if (!config.isEnableWeatherIntegration()) return;

        RegistryKey<World> dimKey = world.getRegistryKey();
        ActiveBattleWeather existing = activeWeather.get(dimKey);
        if (existing != null && existing.isExpired(currentTick)) {
            activeWeather.remove(dimKey);
            LOGGER.debug("[CobblemonWeather] Battle weather expired in {}", dimKey.getValue());
        }
    }

    /**
     * Attempts to apply a THUNDERSTORM to the given world.
     *
     * <p>For thunder/thunderbolt this requires the world to already be raining.
     * For Thundurus Wildbolt Storm (fastTrack=true) it always applies and bypasses the
     * cooldown so the storm starts faster.
     *
     * @param world     the ServerWorld to affect
     * @param fastTrack {@code true} for the Thundurus Wildbolt Storm special case
     * @param currentTick current world time in ticks
     * @param config    server config
     */
    public void applyThunderstorm(ServerWorld world, boolean fastTrack, long currentTick, ServerConfig config) {
        if (!config.isEnableThunderstormIntegration()) return;

        RegistryKey<World> dimKey = world.getRegistryKey();

        if (!fastTrack) {
            // Normal thunder/thunderbolt: requires rain and respects cooldown
            if (!world.isRaining()) return;

            Long last = lastThunderstormTick.get(dimKey);
            if (last != null && (currentTick - last) < THUNDERSTORM_COOLDOWN_TICKS) {
                LOGGER.debug("[CobblemonWeather] Thunderstorm blocked by cooldown in {} ({} ticks remaining)",
                        dimKey.getValue(), THUNDERSTORM_COOLDOWN_TICKS - (currentTick - last));
                return;
            }
        }

        int durationTicks = fastTrack ? config.getThundurusFastTrackTicks() : config.getNormalStormDurationTicks();
        lastThunderstormTick.put(dimKey, currentTick);
        world.setWeather(0, durationTicks, true, true);
        LOGGER.debug("[CobblemonWeather] Applied THUNDERSTORM in {} (fastTrack={}, duration={})",
                dimKey.getValue(), fastTrack, durationTicks);
    }

    private static final int WEATHER_DURATION_TICKS = 20 * 60; // 60 seconds

    /**
     * Apply weather directly by type (used by debug command and battle logic).
     */
    public static void applyMinecraftWeather(ServerWorld world, BattleWeatherType type) {
        switch (type) {
            case CLEAR, SUN -> world.setWeather(WEATHER_DURATION_TICKS, 0, false, false);
            case RAIN -> world.setWeather(0, WEATHER_DURATION_TICKS, true, false);
            // THUNDERSTORM: raining + thundering
            case THUNDERSTORM -> world.setWeather(0, WEATHER_DURATION_TICKS, true, true);
            // SAND and SNOW also set vanilla raining=true.  Particle Rain reads isRaining() and
            // then selects the correct visual effect per biome: sandstorm particles in hot/dry
            // biomes (Precipitation.NONE + high temp) and snowstorm particles in cold biomes
            // (Precipitation.SNOW).  No additional server-side call is needed.
            case SAND, SNOW -> world.setWeather(0, WEATHER_DURATION_TICKS, true, false);
        }
    }
}
