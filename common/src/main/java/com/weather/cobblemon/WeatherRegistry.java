package com.weather.cobblemon;

import com.weather.logic.BattleWeatherType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Central registry mapping Cobblemon move/ability/weather identifiers to BattleWeatherType
 * and priority.  All identifiers are lower-case and match the Cobblemon internal ID format.
 *
 * <p>Priority levels:
 * <ul>
 *   <li>0 — regular weather moves (Rain Dance, Sunny Day, Sandstorm, Snowscape)</li>
 *   <li>1 — standard weather abilities (Drizzle, Drought, Sand Stream, Snow Warning, Orichalcum Pulse)</li>
 *   <li>2 — priority weather (Primordial Sea, Desolate Land, Delta Stream, Wildbolt Storm):
 *       can only be replaced by another priority-2 effect</li>
 * </ul>
 *
 * To add a new move:    MOVE_REGISTRY.put("moveid",    new WeatherEntry(BattleWeatherType.X, 0))
 * To add a new ability: ABILITY_REGISTRY.put("abilityid", new WeatherEntry(BattleWeatherType.X, priority))
 * To add a weather ID:  WEATHER_REGISTRY.put("weatherid", new WeatherEntry(BattleWeatherType.X, priority))
 */
public final class WeatherRegistry {

    public record WeatherEntry(BattleWeatherType type, int priority) {}

    private static final Map<String, WeatherEntry> MOVE_REGISTRY = new HashMap<>();
    private static final Map<String, WeatherEntry> ABILITY_REGISTRY = new HashMap<>();

    /**
     * Maps Showdown weather effect IDs (from {@code |-weather|WEATHER}) to a
     * {@link WeatherEntry}.  Used by {@link com.weather.mixin.WeatherInstructionMixin} when
     * the weather change was not caused by an ability (i.e. no {@code [from] ability:} tag).
     *
     * <p>Priority-2 entries here cannot be overridden by priority-0/1 effects once applied.
     */
    private static final Map<String, WeatherEntry> WEATHER_REGISTRY = new HashMap<>();

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

        // Weather IDs (Showdown |-weather| message argument 0, lowercased).
        // Used by WeatherInstructionMixin for move-triggered or unnamed-source weather.
        // Move-triggered weather (priority 0)
        WEATHER_REGISTRY.put("rainweather",   new WeatherEntry(BattleWeatherType.RAIN,        0));
        WEATHER_REGISTRY.put("sunnyweather",  new WeatherEntry(BattleWeatherType.SUN,         0));
        WEATHER_REGISTRY.put("sandstorm",     new WeatherEntry(BattleWeatherType.SAND,        0));
        WEATHER_REGISTRY.put("snow",          new WeatherEntry(BattleWeatherType.SNOW,        0));
        // Wildbolt Storm (Thundurus signature move) — priority 2 thunderstorm.
        // Once applied, only another priority-2 effect can change the overworld weather.
        WEATHER_REGISTRY.put("wildboltstorm", new WeatherEntry(BattleWeatherType.THUNDERSTORM, 2));
        // Primal/special weather IDs (priority 2) — also covered via ABILITY_REGISTRY when
        // the [from] ability: tag is present, but included here as a fallback.
        WEATHER_REGISTRY.put("primordialsea", new WeatherEntry(BattleWeatherType.RAIN,        2));
        WEATHER_REGISTRY.put("desolateland",  new WeatherEntry(BattleWeatherType.SUN,         2));
        WEATHER_REGISTRY.put("deltastream",   new WeatherEntry(BattleWeatherType.CLEAR,       2));
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
     * Looks up a {@link WeatherEntry} by the Showdown weather effect ID (the value of
     * {@code effectAt(0)} in a {@code |-weather|} message after normalisation).
     *
     * @param weatherId the raw or already-normalised weather ID (e.g. {@code "rainweather"},
     *                  {@code "wildboltstorm"}, {@code "deltastream"})
     * @return the matching {@link WeatherEntry}, or empty if the weather ID is unknown
     */
    public static Optional<WeatherEntry> forWeather(String weatherId) {
        if (weatherId == null) return Optional.empty();
        return Optional.ofNullable(WEATHER_REGISTRY.get(normalizeId(weatherId)));
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