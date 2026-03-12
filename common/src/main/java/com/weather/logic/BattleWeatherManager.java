package com.weather.logic;

import com.weather.config.ServerConfig;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BattleWeatherManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon_weather");

    private final Map<RegistryKey<World>, ActiveBattleWeather> activeWeather = new ConcurrentHashMap<>();

    /** Battles that are currently ongoing (started but not yet ended). */
    private final Set<UUID> activeBattleIds = ConcurrentHashMap.newKeySet();

    /** Tick at which the last thunderstorm was applied per dimension, for cooldown enforcement. */
    private final Map<RegistryKey<World>, Long> lastThunderstormTick = new ConcurrentHashMap<>();

    /** Minimum ticks between thunderstorm triggers (30 seconds), bypassed by Thundurus fast-track. */
    private static final int THUNDERSTORM_COOLDOWN_TICKS = 600;

    public void onBattleStart(UUID battleId) {
        activeBattleIds.add(battleId);
        LOGGER.debug("[CobblemonWeather] Battle started: {}", battleId);
    }

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
            // Same battle: weather changes within a battle are always allowed
            shouldApply = true;
        } else if (!activeBattleIds.contains(existing.getSourceBattleId())) {
            // The battle that originally set the weather has since ended: allow the new battle in
            shouldApply = true;
        } else {
            // Different battle still active: first-setter gets priority
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
            int durationTicks = config.getBattleWeatherDurationTicks();
            long expiresAt = currentTick + durationTicks;
            ActiveBattleWeather record = new ActiveBattleWeather(type, battleId, priority, expiresAt);
            activeWeather.put(dimKey, record);
            applyMinecraftWeather(world, type, durationTicks);
            LOGGER.debug("[CobblemonWeather] Applied {} in {} (battle={}, priority={}, expiresAt={})",
                    type, dimKey.getValue(), battleId, priority, expiresAt);
        }
    }

    public void onBattleEnd(ServerWorld world, UUID battleId, long currentTick, ServerConfig config) {
        if (!config.isEnableWeatherIntegration()) return;

        activeBattleIds.remove(battleId);

        RegistryKey<World> dimKey = world.getRegistryKey();
        ActiveBattleWeather existing = activeWeather.get(dimKey);
        if (existing != null && existing.getSourceBattleId().equals(battleId)) {
            if (config.isClearWeatherOnBattleEnd()) {
                activeWeather.remove(dimKey);
                applyMinecraftWeather(world, BattleWeatherType.CLEAR, config.getBattleWeatherDurationTicks());
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

    /** One full Minecraft day = 24000 ticks (20 ticks/sec x 1200 sec). */
    private static final int DEFAULT_WEATHER_DURATION_TICKS = 24000;

    /**
     * Apply weather directly by type (used by debug command and battle logic).
     *
     * @param durationTicks how long the vanilla weather effect should last
     */
    public static void applyMinecraftWeather(ServerWorld world, BattleWeatherType type, int durationTicks) {
        switch (type) {
            case CLEAR, SUN -> world.setWeather(durationTicks, 0, false, false);
            case RAIN -> world.setWeather(0, durationTicks, true, false);
            // THUNDERSTORM: raining + thundering
            case THUNDERSTORM -> world.setWeather(0, durationTicks, true, true);
            // SAND and SNOW also set vanilla raining=true.  Particle Rain reads isRaining() and
            // then selects the correct visual effect per biome: sandstorm particles in hot/dry
            // biomes (Precipitation.NONE + high temp) and snowstorm particles in cold biomes
            // (Precipitation.SNOW).  No additional server-side call is needed.
            case SAND, SNOW -> world.setWeather(0, durationTicks, true, false);
        }
    }

    /**
     * Apply weather directly by type using the default duration.
     * Convenience overload used by the debug command.
     */
    public static void applyMinecraftWeather(ServerWorld world, BattleWeatherType type) {
        applyMinecraftWeather(world, type, DEFAULT_WEATHER_DURATION_TICKS);
    }
}