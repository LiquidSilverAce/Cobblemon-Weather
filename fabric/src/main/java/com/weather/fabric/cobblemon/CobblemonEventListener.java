package com.weather.fabric.cobblemon;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.PokemonBattle;
import com.cobblemon.mod.common.battles.actor.BattleActor;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.weather.ExampleMod;
import com.weather.cobblemon.WeatherRegistry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kotlin.Unit;

import java.util.Optional;
import java.util.UUID;

public final class CobblemonEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon_weather");

    public static void register() {
        // Battle started: check abilities
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(event -> {
            try {
                handleBattleStart(event.getBattle());
            } catch (Exception e) {
                LOGGER.error("[CobblemonWeather] Error handling battle start", e);
            }
            return Unit.INSTANCE;
        });

        // Battle ended (victory): clean up weather
        CobblemonEvents.BATTLE_VICTORY.subscribe(event -> {
            try {
                handleBattleEnd(event.getBattle());
            } catch (Exception e) {
                LOGGER.error("[CobblemonWeather] Error handling battle end", e);
            }
            return Unit.INSTANCE;
        });

        // TODO: Wire move-used weather detection once the Cobblemon move event API is confirmed stable.
        // Example (API subject to change):
        // CobblemonEvents.MOVE_USED.subscribe(event -> {
        //     handleMoveUsed(event.getBattle(), event.getMoveId());
        //     return Unit.INSTANCE;
        // });

        LOGGER.info("[CobblemonWeather] Registered Cobblemon battle event listeners.");
    }

    private static void handleBattleStart(PokemonBattle battle) {
        ServerWorld world = getBattleWorld(battle);
        if (world == null) return;
        if (!world.getRegistryKey().equals(World.OVERWORLD)) return;

        UUID battleId = battle.getBattleId();

        for (BattleActor actor : battle.getActors()) {
            for (var activePokemon : actor.getActivePokemon()) {
                BattlePokemon bp = activePokemon.getBattlePokemon();
                if (bp == null) continue;
                Pokemon pokemon = bp.getOriginalPokemon();
                String abilityName = pokemon.getAbility().getName();

                // WeatherRegistry.forAbility normalises to lowercase, so either the
                // Cobblemon internal ID or a mixed-case display name will be matched.
                Optional<WeatherRegistry.WeatherEntry> entry = WeatherRegistry.forAbility(abilityName);
                entry.ifPresent(e -> {
                    long currentTick = world.getTime();
                    ExampleMod.getWeatherManager().applyWeatherFromBattle(
                            world, battleId, e.type(), e.priority(), currentTick, ExampleMod.getConfig());
                    LOGGER.debug("[CobblemonWeather] Ability {} -> {} (battle={})",
                            abilityName, e.type(), battleId);
                });
            }
        }
    }

    /**
     * Called when a weather move is successfully used in the given battle.
     * Wire this to the Cobblemon move event once the API is confirmed stable:
     * <pre>
     *   CobblemonEvents.MOVE_USED.subscribe(event -> {
     *       CobblemonEventListener.handleMoveUsed(event.getBattle(), event.getMoveId());
     *       return Unit.INSTANCE;
     *   });
     * </pre>
     * Move ID should be lowercase (e.g. "raindance", "sunnyday"). WeatherRegistry normalises
     * case internally, so the raw value from the event can be passed in directly.
     */
    public static void handleMoveUsed(PokemonBattle battle, String moveId) {
        ServerWorld world = getBattleWorld(battle);
        if (world == null) return;
        if (!world.getRegistryKey().equals(World.OVERWORLD)) return;

        UUID battleId = battle.getBattleId();
        Optional<WeatherRegistry.WeatherEntry> entry = WeatherRegistry.forMove(moveId);
        entry.ifPresent(e -> {
            long currentTick = world.getTime();
            ExampleMod.getWeatherManager().applyWeatherFromBattle(
                    world, battleId, e.type(), e.priority(), currentTick, ExampleMod.getConfig());
            LOGGER.debug("[CobblemonWeather] Move {} -> {} (battle={})", moveId, e.type(), battleId);
        });
    }

    private static void handleBattleEnd(PokemonBattle battle) {
        ServerWorld world = getBattleWorld(battle);
        if (world == null) return;
        if (!world.getRegistryKey().equals(World.OVERWORLD)) return;

        UUID battleId = battle.getBattleId();
        long currentTick = world.getTime();
        ExampleMod.getWeatherManager().onBattleEnd(world, battleId, currentTick, ExampleMod.getConfig());
    }

    /**
     * Attempts to determine the ServerWorld of a battle by inspecting its player actors.
     * Returns null if no valid overworld actor is found.
     */
    private static ServerWorld getBattleWorld(PokemonBattle battle) {
        for (BattleActor actor : battle.getActors()) {
            if (actor instanceof PlayerBattleActor playerActor) {
                ServerPlayerEntity player = playerActor.getEntity();
                if (player != null && player.getWorld() instanceof ServerWorld sw) {
                    return sw;
                }
            }
        }
        return null;
    }
}
