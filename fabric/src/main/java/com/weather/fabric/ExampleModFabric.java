package com.weather.fabric;

import com.weather.ExampleMod;
import com.weather.command.WeatherDebugCommand;
import com.weather.fabric.cobblemon.CobblemonEventListener;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

public final class ExampleModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // Common initialization (config + manager)
        ExampleMod.init();

        // Register Cobblemon battle event hooks (ability/battle-end detection)
        CobblemonEventListener.register();

        // Register debug command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                WeatherDebugCommand.register(dispatcher));

        // Server tick: expire stale battle weather records per world
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            for (ServerWorld world : server.getWorlds()) {
                long tick = world.getTime();
                ExampleMod.getWeatherManager().tick(world, tick, ExampleMod.getConfig());
            }
        });
    }
}
