package com.weather.cobblemon;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.PokemonBattle;
import com.cobblemon.mod.common.battles.actor.BattleActor;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.weather.ExampleMod;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kotlin.Unit;

import java.util.Optional;
import java.util.Set;
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

        // Move events are wired via MoveInstructionMixin (see com.weather.mixin).

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
     * Called when any move is used in the given battle.
     * Wired via {@link com.weather.mixin.MoveInstructionMixin} which injects into
     * Cobblemon's {@code MoveInstruction.invoke()} — the handler for the Showdown
     * {@code |move|} protocol message.
     * Move ID is already lowercase (from {@code Effect.id}).
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

    private static final Set<String> THUNDER_MOVES =
            Set.of("thunder", "thunderbolt");
    private static final Set<String> THUNDURUS_SPECIES =
            Set.of("thundurus", "thundurustherian");

    /**
     * Handles thunderstorm upgrade logic triggered by a move.
     *
     * <ul>
     *   <li>{@code thunder} / {@code thunderbolt}: upgrades to THUNDERSTORM if the overworld
     *       is already raining (subject to 30-second cooldown).</li>
     *   <li>{@code wildboltstorm} used by Thundurus/Thundurus-Therian: always triggers
     *       THUNDERSTORM (fast-track, bypasses cooldown and rain requirement).</li>
     * </ul>
     *
     * Called from {@link com.weather.mixin.MoveInstructionMixin}.
     *
     * @param battle    the active battle
     * @param moveId    lowercase Cobblemon move identifier (from {@code Effect.id})
     * @param speciesId lowercase Showdown species identifier (from {@code Species.showdownId()})
     */
    public static void handleThunderstormMove(PokemonBattle battle, String moveId, String speciesId) {
        ServerWorld world = getBattleWorld(battle);
        if (world == null) return;
        if (!world.getRegistryKey().equals(World.OVERWORLD)) return;

        long currentTick = world.getTime();
        boolean fastTrack = "wildboltstorm".equals(moveId) && THUNDURUS_SPECIES.contains(speciesId);
        boolean isThunderMove = THUNDER_MOVES.contains(moveId);

        if (!fastTrack && !isThunderMove) return;

        // Note: wildboltstorm only fast-tracks when the user IS Thundurus/Thundurus-Therian.
        // If another species somehow uses wildboltstorm it falls through to the normal thunder
        // check (which requires rain), intentionally preventing unintended triggering.
        if ("wildboltstorm".equals(moveId) && !fastTrack) {
            LOGGER.debug("[CobblemonWeather] wildboltstorm used by non-Thundurus species '{}'; no fast-track.",
                    speciesId);
            return;
        }

        LOGGER.debug("[CobblemonWeather] {} used {} → THUNDERSTORM{} (battle={})",
                speciesId, moveId, fastTrack ? " (fast)" : "", battle.getBattleId());

        ExampleMod.getWeatherManager().applyThunderstorm(world, fastTrack, currentTick, ExampleMod.getConfig());
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
     * Returns null if no valid player actor with a loaded world is found.
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
