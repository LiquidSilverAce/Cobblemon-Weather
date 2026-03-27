package com.weather.cobblemon;

import com.weather.logic.BattleWeatherType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Central registry mapping Cobblemon move/ability identifiers to BattleWeatherType and priority.
 * All identifiers are lower-case and match the Cobblemon internal ID format.
 *
 * To add a new move: put(MOVE_REGISTRY, "moveid", BattleWeatherType.X, 0)
 * To add a new ability: put(ABILITY_REGISTRY, "abilityid", BattleWeatherType.X, priority)
 */
public final class WeatherRegistry {

    public record WeatherEntry(BattleWeatherType type, int priority) {}

    private static final Map<String, WeatherEntry> MOVE_REGISTRY = new HashMap<>();
    private static final Map<String, WeatherEntry> ABILITY_REGISTRY = new HashMap<>();

    static {
        // Moves — priority 0
        MOVE_REGISTRY.put("sunnyday",   new WeatherEntry(BattleWeatherType.SUN,  0));
        MOVE_REGISTRY.put("raindance",  new WeatherEntry(BattleWeatherType.RAIN, 0));
        MOVE_REGISTRY.put("sandstorm",  new WeatherEntry(BattleWeatherType.SAND, 0));
        MOVE_REGISTRY.put("snowscape",  new WeatherEntry(BattleWeatherType.SNOW, 0));

        // Abilities — priority 1 (normal), priority 2 (primal/special)
        ABILITY_REGISTRY.put("drizzle",         new WeatherEntry(BattleWeatherType.RAIN,  1));
        ABILITY_REGISTRY.put("drought",         new WeatherEntry(BattleWeatherType.SUN,   1));
        ABILITY_REGISTRY.put("sandstream",      new WeatherEntry(BattleWeatherType.SAND,  1));
        ABILITY_REGISTRY.put("snowwarning",     new WeatherEntry(BattleWeatherType.SNOW,  1));
        ABILITY_REGISTRY.put("orichalcumpulse", new WeatherEntry(BattleWeatherType.SUN,   1));
        ABILITY_REGISTRY.put("primordialsea",   new WeatherEntry(BattleWeatherType.RAIN,  2));
        ABILITY_REGISTRY.put("desolateland",    new WeatherEntry(BattleWeatherType.SUN,   2));
        // Delta Stream clears all overworld weather when the Pokemon switches in.
        // Priority 2 so only Desolate Land or Primordial Sea can override it.
        ABILITY_REGISTRY.put("deltastream",     new WeatherEntry(BattleWeatherType.CLEAR, 2));
    }

    public static Optional<WeatherEntry> forMove(String moveId) {
        if (moveId == null) return Optional.empty();
        return Optional.ofNullable(MOVE_REGISTRY.get(normalizeId(moveId)));
    }

    public static Optional<WeatherEntry> forAbility(String abilityId) {
        if (abilityId == null) return Optional.empty();
        return Optional.ofNullable(ABILITY_REGISTRY.get(normalizeId(abilityId)));
    }

    /**
     * Lower-cases and strips any resource-location namespace prefix
     * (e.g. {@code "cobblemon:drizzle"} → {@code "drizzle"}).
     */
    private static String normalizeId(String id) {
        String lower = id.toLowerCase();
        // Minecraft resource locations have exactly one colon ("namespace:path").
        int colon = lower.indexOf(':');
        return colon >= 0 ? lower.substring(colon + 1) : lower;
    }

    private WeatherRegistry() {}
}