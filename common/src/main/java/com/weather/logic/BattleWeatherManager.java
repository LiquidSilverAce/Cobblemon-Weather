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

    private static final int WEATHER_DURATION_TICKS = 20 * 60; // 60 seconds

    /**
     * Apply weather directly by type (used by debug command and battle logic).
     */
    public static void applyMinecraftWeather(ServerWorld world, BattleWeatherType type) {
        switch (type) {
            case CLEAR, SUN -> world.setWeather(WEATHER_DURATION_TICKS, 0, false, false);
            case RAIN -> world.setWeather(0, WEATHER_DURATION_TICKS, true, false);
            case SAND, SNOW -> world.setWeather(0, WEATHER_DURATION_TICKS, true, false); // Stage 1: treat as rain; refine in Stage 2
        }
    }
}
