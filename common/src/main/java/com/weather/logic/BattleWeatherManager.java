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
                                       ServerConfig config) {
        if (!config.isEnableWeatherIntegration()) return;

        RegistryKey<World> dimKey = world.getRegistryKey();
        ActiveBattleWeather existing = activeWeather.get(dimKey);

        boolean shouldApply;
        if (existing == null) {
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
            ActiveBattleWeather record = new ActiveBattleWeather(type, battleId, priority);
            activeWeather.put(dimKey, record);
            applyMinecraftWeather(world, type);
            LOGGER.debug("[CobblemonWeather] Applied {} in {} (battle={}, priority={})",
                    type, dimKey.getValue(), battleId, priority);
        }
    }

    public void onBattleEnd(ServerWorld world, UUID battleId, ServerConfig config) {
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
            // Otherwise let Minecraft's own rainTime/thunderTime counters run down naturally
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

        int rainDuration = world.getRandom().nextBetween(12000, 24000);
        lastThunderstormTick.put(dimKey, currentTick);
        world.setWeather(0, rainDuration, true, true);
        LOGGER.debug("[CobblemonWeather] Applied THUNDERSTORM in {} (fastTrack={}, rainDuration={})",
                dimKey.getValue(), fastTrack, rainDuration);
    }

    /**
     * Apply weather directly by type (used by debug command and battle logic).
     */
    public static void applyMinecraftWeather(ServerWorld world, BattleWeatherType type) {
        int rainDuration = world.getRandom().nextBetween(12000, 24000);
        switch (type) {
            case CLEAR, SUN -> world.setWeather(rainDuration, 0, false, false);
            case RAIN -> world.setWeather(0, rainDuration, true, false);
            // THUNDERSTORM: raining + thundering
            case THUNDERSTORM -> world.setWeather(0, rainDuration, true, true);
            // SAND and SNOW also set vanilla raining=true.  Particle Rain reads isRaining() and
            // then selects the correct visual effect per biome: sandstorm particles in hot/dry
            // biomes (Precipitation.NONE + high temp) and snowstorm particles in cold biomes
            // (Precipitation.SNOW).  No additional server-side call is needed.
            case SAND, SNOW -> world.setWeather(0, rainDuration, true, false);
        }
    }
}
