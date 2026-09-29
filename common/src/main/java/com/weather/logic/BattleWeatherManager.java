package com.weather.logic;

import com.weather.config.ServerConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Server-thread-owned weather state. No state is shared between server sessions. */
public final class BattleWeatherManager {
    private final Map<ResourceKey<Level>, ActiveBattleWeather> activeWeather = new HashMap<>();
    private final Map<ResourceKey<Level>, Long> lastWeatherChangeTick = new HashMap<>();
    private final Predicate<UUID> battleActive;

    public BattleWeatherManager(Predicate<UUID> battleActive) {
        this.battleActive = battleActive;
    }

    public boolean applyWeatherFromBattle(ServerLevel world, UUID battleId, BattleWeatherType type,
                                         int priority, long currentTick, ServerConfig config) {
        if (!config.isEnableWeatherIntegration() || !world.dimension().equals(Level.OVERWORLD)) return false;
        if (type == BattleWeatherType.THUNDERSTORM && !config.isEnableThunderstormIntegration()) return false;
        Long last = lastWeatherChangeTick.get(world.dimension());
        if (last != null && currentTick - last < config.getWeatherChangeCooldownTicks()) return false;
        ActiveBattleWeather existing = activeWeather.get(world.dimension());
        if (!canReplace(existing, battleId, priority, currentTick, config)) return false;
        activeWeather.put(world.dimension(), new ActiveBattleWeather(type, battleId, priority,
                currentTick + config.getBattleWeatherDurationTicks()));
        applyMinecraftWeather(world, type, config.getBattleWeatherDurationTicks());
        lastWeatherChangeTick.put(world.dimension(), currentTick);
        return true;
    }

    private boolean canReplace(ActiveBattleWeather existing, UUID battleId, int priority,
                               long currentTick, ServerConfig config) {
        if (existing == null || existing.isExpired(currentTick)) return true;
        if (!battleActive.test(existing.getSourceBattleId())) return true;
        if (existing.getSourceBattleId().equals(battleId)) {
            return existing.getPriority() < 2 || priority >= 2;
        }
        return config.isAllowCrossBattleOverride()
                && (priority > existing.getPriority() || (priority == 2 && config.isAllowPrimalOverride()));
    }

    /** Only Wildbolt Storm can start thunder without rain; all storms share the normal arbitration. */
    public boolean applyThunderstorm(ServerLevel world, UUID battleId, String moveId,
                                     long currentTick, ServerConfig config) {
        boolean wildbolt = "wildboltstorm".equals(moveId);
        if (!wildbolt && !"thunder".equals(moveId) && !"thunderbolt".equals(moveId)) return false;
        if (!config.isEnableWeatherIntegration() || !config.isEnableThunderstormIntegration()) return false;
        // Check authoritative weather, not the rain animation's fading intensity. This permits
        // immediate rain -> thunder changes and prevents clear -> thunder during a rain fade-out.
        if (!wildbolt && !world.getLevelData().isRaining()) return false;
        return applyWeatherFromBattle(world, battleId, BattleWeatherType.THUNDERSTORM,
                wildbolt ? 2 : 0, currentTick, config);
    }

    /** Battle weather ending releases primal protection without cutting short the world weather duration. */
    public void releasePriority(ServerLevel world, UUID battleId) {
        ActiveBattleWeather existing = activeWeather.get(world.dimension());
        if (existing != null && existing.getSourceBattleId().equals(battleId)) {
            activeWeather.put(world.dimension(), new ActiveBattleWeather(existing.getType(), battleId,
                    0, existing.getExpiresAtTick()));
        }
    }

    public void tick(ServerLevel world, long currentTick, ServerConfig config) {
        ActiveBattleWeather existing = activeWeather.get(world.dimension());
        if (existing == null) return;
        if (existing.isExpired(currentTick)) {
            activeWeather.remove(world.dimension());
        } else if (!battleActive.test(existing.getSourceBattleId())) {
            // Includes fleeing, capture, disconnect, draws and forced stops, not just victories.
            activeWeather.remove(world.dimension());
            if (config.isEnableWeatherIntegration() && config.isClearWeatherOnBattleEnd()) {
                applyMinecraftWeather(world, BattleWeatherType.CLEAR, config.getBattleWeatherDurationTicks());
                lastWeatherChangeTick.put(world.dimension(), currentTick);
            }
        }
    }

    public void applyDebugWeather(ServerLevel world, BattleWeatherType type, ServerConfig config) {
        activeWeather.remove(world.dimension());
        lastWeatherChangeTick.put(world.dimension(), world.getGameTime());
        applyMinecraftWeather(world, type, config.getBattleWeatherDurationTicks());
    }

    public static void applyMinecraftWeather(ServerLevel world, BattleWeatherType type, int durationTicks) {
        switch (type) {
            case CLEAR, SUN -> world.setWeatherParameters(durationTicks, 0, false, false);
            case THUNDERSTORM -> world.setWeatherParameters(0, durationTicks, true, true);
            // Precipitation visuals remain biome-dependent; this does not force snow/sand in every biome.
            case RAIN, SAND, SNOW -> world.setWeatherParameters(0, durationTicks, true, false);
        }
    }
}
