package com.weather.logic;

import java.util.UUID;

public final class ActiveBattleWeather {
    private final BattleWeatherType type;
    private final UUID sourceBattleId;
    private final int priority;

    public ActiveBattleWeather(BattleWeatherType type, UUID sourceBattleId, int priority) {
        this.type = type;
        this.sourceBattleId = sourceBattleId;
        this.priority = priority;
    }

    public BattleWeatherType getType() { return type; }
    public UUID getSourceBattleId() { return sourceBattleId; }
    public int getPriority() { return priority; }
}
