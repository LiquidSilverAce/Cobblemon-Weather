package com.weather.cobblemon;

import com.weather.logic.BattleWeatherType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeatherRegistryTest {
    @Test void actualShowdownIdsAndOldAliasesWork() {
        for (String id : new String[]{"RainDance", "rainweather", "cobblemon:raindance"}) {
            assertEquals(BattleWeatherType.RAIN, WeatherRegistry.forWeather(id).orElseThrow().type());
        }
        assertEquals(BattleWeatherType.SUN, WeatherRegistry.forWeather("SunnyDay").orElseThrow().type());
        assertEquals(BattleWeatherType.SNOW, WeatherRegistry.forWeather("Hail").orElseThrow().type());
        assertEquals(2, WeatherRegistry.forAbility("primordialsea").orElseThrow().priority());
        assertEquals(BattleWeatherType.CLEAR, WeatherRegistry.forAbility("deltastream").orElseThrow().type());
    }

    @Test void electricalMovesCannotMasqueradeAsWeatherMessages() {
        for (String id : new String[]{"wildboltstorm", "thunder", "thunderbolt"}) {
            assertTrue(WeatherRegistry.forWeather(id).isEmpty());
            assertTrue(WeatherRegistry.forMove(id).isEmpty());
        }
        assertTrue(WeatherRegistry.forWeather("none").isEmpty());
        assertTrue(WeatherRegistry.forAbility(null).isEmpty());
    }
}
