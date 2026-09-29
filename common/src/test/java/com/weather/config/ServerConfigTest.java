package com.weather.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServerConfigTest {
    @TempDir Path directory;

    @Test void createsDefaultsAndAllowsEveryone() throws Exception {
        Path file = directory.resolve("nested/weather.json");
        ServerConfig config = ServerConfig.load(file);
        assertTrue(Files.readString(file).contains("weatherWhitelist"));
        assertTrue(config.allowsPlayer(UUID.randomUUID(), "Anyone"));
        assertEquals(24000, config.getBattleWeatherDurationTicks());
        assertEquals(0, config.getWeatherChangeCooldownTicks());
        assertTrue(ServerConfig.load(file).isWhitelistEmpty());
    }

    @Test void existingConfigGetsDefaultsForMissingFields() {
        ServerConfig config = ServerConfig.parse("{\"battleWeatherDurationTicks\":1200}");
        assertEquals(1200, config.getBattleWeatherDurationTicks());
        assertTrue(config.isWhitelistEmpty());
        assertEquals(0, config.getWeatherChangeCooldownTicks());
    }

    @Test void namesAndUuidsMatchWithoutLocaleOrCaseDependence() {
        UUID id = UUID.randomUUID();
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            ServerConfig config = ServerConfig.parse("{\"weatherWhitelist\":[\" LiquidSilverAce \",\"" + id.toString().toUpperCase(Locale.ROOT) + "\"]}");
            assertTrue(config.allowsPlayer(UUID.randomUUID(), "LIQUIDSILVERACE"));
            assertTrue(config.allowsPlayer(id, "RenamedPlayer"));
            assertFalse(config.allowsPlayer(UUID.randomUUID(), "OtherPlayer"));
            assertFalse(config.allowsPlayer(null, null));
        } finally { Locale.setDefault(original); }
    }

    @Test void durationAndCooldownAreIndependent() {
        ServerConfig config = ServerConfig.parse("{\"battleWeatherDurationTicks\":400,\"weatherChangeCooldownTicks\":0}");
        assertEquals(400, config.getBattleWeatherDurationTicks());
        assertEquals(0, config.getWeatherChangeCooldownTicks());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{", "{\"weatherWhitelist\":null}", "{\"weatherWhitelist\":\"Alice\"}",
            "{\"weatherWhitelist\":[null]}", "{\"weatherWhitelist\":[42]}", "{\"weatherWhitelist\":[\"\"]}",
            "{\"weatherWhitelist\":[\"*\"]}", "{\"weatherWhitelist\":[\"not a player\"]}",
            "{\"battleWeatherDurationTicks\":0}", "{\"battleWeatherDurationTicks\":-1}",
            "{\"battleWeatherDurationTicks\":1.5}", "{\"battleWeatherDurationTicks\":2147483648}",
            "{\"weatherChangeCooldownTicks\":-1}", "{\"weatherChangeCooldownTicks\":\"600\"}",
            "{\"weatherChangeCooldownTicks\":null}", "{\"enableWeatherIntegration\":\"true\"}",
            "{\"enableWeatherIntegration\":null}", "{\"whitelist\":[]}"})
    void invalidConfigNeverFallsBackToOpenAccessOrOverwrites(String json) throws Exception {
        Path file = directory.resolve("weather.json");
        Files.writeString(file, json);
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> ServerConfig.load(file));
        assertTrue(error.getMessage().contains(file.toString()));
        assertEquals(json, Files.readString(file));
    }
}
