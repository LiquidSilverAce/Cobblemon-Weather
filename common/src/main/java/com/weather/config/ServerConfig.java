package com.weather.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class ServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> BOOLEANS = Set.of("enableWeatherIntegration", "allowPrimalOverride",
            "allowCrossBattleOverride", "clearWeatherOnBattleEnd", "enableThunderstormIntegration");
    private boolean enableWeatherIntegration = true;
    private int battleWeatherDurationTicks = 24000;
    private int weatherChangeCooldownTicks = 0;
    private boolean allowPrimalOverride = true;
    private boolean allowCrossBattleOverride = false;
    private boolean clearWeatherOnBattleEnd = false;
    private boolean enableThunderstormIntegration = true;
    private List<String> weatherWhitelist = List.of();

    public boolean isEnableWeatherIntegration() { return enableWeatherIntegration; }
    public int getBattleWeatherDurationTicks() { return battleWeatherDurationTicks; }
    public int getWeatherChangeCooldownTicks() { return weatherChangeCooldownTicks; }
    public boolean isAllowPrimalOverride() { return allowPrimalOverride; }
    public boolean isAllowCrossBattleOverride() { return allowCrossBattleOverride; }
    public boolean isClearWeatherOnBattleEnd() { return clearWeatherOnBattleEnd; }
    public boolean isEnableThunderstormIntegration() { return enableThunderstormIntegration; }
    public boolean isWhitelistEmpty() { return weatherWhitelist.isEmpty(); }
    public List<String> getWeatherWhitelist() { return List.copyOf(weatherWhitelist); }

    /** Match the causing player's account, never their display name or their opponent. */
    public boolean allowsPlayer(UUID uuid, String username) {
        if (weatherWhitelist.isEmpty()) return true;
        return (uuid != null && weatherWhitelist.contains(uuid.toString()))
                || (username != null && weatherWhitelist.contains(username.toLowerCase(Locale.ROOT)));
    }

    public static ServerConfig load() {
        return load(Platform.getConfigFolder().resolve("cobblemon_weather.json"));
    }

    public static ServerConfig load(Path file) {
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.toAbsolutePath().getParent());
                ServerConfig defaults = new ServerConfig();
                Files.writeString(file, GSON.toJson(defaults) + "\n", StandardCharsets.UTF_8);
                return defaults;
            }
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            // Falling back to an empty whitelist would silently grant everyone permission.
            throw new IllegalStateException("Cannot load weather configuration " + file + ": " + e.getMessage(), e);
        }
    }

    static ServerConfig parse(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) throw new IllegalArgumentException("Expected a JSON object");
        JsonObject object = root.getAsJsonObject();
        for (var entry : object.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (BOOLEANS.contains(key)) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                    throw new IllegalArgumentException(key + " must be a boolean");
                }
            } else if ((key.equals("battleWeatherDurationTicks") || key.equals("weatherChangeCooldownTicks"))) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                        || value.getAsBigDecimal().intValueExact() < (key.equals("battleWeatherDurationTicks") ? 1 : 0)) {
                    throw new IllegalArgumentException(key + " must be an integer (duration > 0, cooldown >= 0)");
                }
            } else if (key.equals("weatherWhitelist")) {
                if (!value.isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
                for (JsonElement item : value.getAsJsonArray()) {
                    if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                        throw new IllegalArgumentException("Whitelist entries must be usernames or UUID strings");
                    }
                    String id = item.getAsString().trim();
                    if (!id.matches("[A-Za-z0-9_]{1,16}")
                            && !id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) {
                        throw new IllegalArgumentException("Invalid whitelist entry: " + id);
                    }
                }
            } else {
                throw new IllegalArgumentException("Unknown setting: " + key);
            }
        }
        ServerConfig config = GSON.fromJson(object, ServerConfig.class);
        List<String> normalized = new ArrayList<>();
        for (String entry : config.weatherWhitelist) normalized.add(entry.trim().toLowerCase(Locale.ROOT));
        config.weatherWhitelist = List.copyOf(normalized);
        return config;
    }
}
