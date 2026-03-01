package com.weather.logic;

import java.util.UUID;

public final class ActiveBattleWeather {
    private final BattleWeatherType type;
    private final UUID sourceBattleId;
    private final int priority;
    private final long expiresAtTick;

    public ActiveBattleWeather(BattleWeatherType type, UUID sourceBattleId, int priority, long expiresAtTick) {
        this.type = type;
        this.sourceBattleId = sourceBattleId;
        this.priority = priority;
        this.expiresAtTick = expiresAtTick;
    }

    public BattleWeatherType getType() { return type; }
    public UUID getSourceBattleId() { return sourceBattleId; }
    public int getPriority() { return priority; }
    public long getExpiresAtTick() { return expiresAtTick; }

    public boolean isExpired(long currentTick) {
        return expiresAtTick <= currentTick;
    }
}
