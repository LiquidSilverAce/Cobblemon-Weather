package com.weather.cobblemon;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.weather.ExampleMod;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        });

        // Battle ended (victory): clean up weather
        CobblemonEvents.BATTLE_VICTORY.subscribe(event -> {
            try {
                handleBattleEnd(event.getBattle());
            } catch (Exception e) {
                LOGGER.error("[CobblemonWeather] Error handling battle end", e);
            }
        });

        // Move events are wired via MoveInstructionMixin (see com.weather.mixin).

        LOGGER.info("[CobblemonWeather] Registered Cobblemon battle event listeners.");
    }

    private static void handleBattleStart(PokemonBattle battle) {
        ServerWorld world = getBattleWorld(battle);
        if (world == null) return;
        if (!world.getRegistryKey().equals(World.OVERWORLD)) return;

        UUID battleId = battle.getBattleId();
        ExampleMod.getWeatherManager().onBattleStart(battleId);

        for (BattleActor actor : battle.getActors()) {
            for (var activePokemon : actor.getActivePokemon()) {
                BattlePokemon bp = activePokemon.getBattlePokemon();
                if (bp == null) continue;
                Pokemon pokemon = bp.getOriginalPokemon();
                String abilityName = pokemon.getAbility().getName();

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
     * Called by {@link com.weather.mixin.WeatherInstructionMixin} for every new weather change
     * that Cobblemon processes from the Showdown {@code |-weather|} protocol message.
     *
     * <p>When the weather was caused by an ability (switch-in with Drizzle, Primordial Sea,
     * Delta Stream, Orichalcum Pulse, or a mid-battle ability change such as Rayquaza Mega
     * Evolution), {@code abilityId} is the lowercased Showdown ability ID extracted from the
     * {@code [from] ability:} optional argument.  The ability registry provides the correct
     * priority for that ability.
     *
     * <p>When the weather was caused by a move (Rain Dance, Sunny Day, Sandstorm, Snowscape,
     * or Thundurus Wildbolt Storm), {@code abilityId} is {@code null} and {@code weatherId}
     * is used instead.  Wildbolt Storm maps to {@code THUNDERSTORM} at priority 2 via the
     * weather registry.
     *
     * @param battle    the ongoing Pokémon battle
     * @param abilityId lowercased ability ID when the weather was ability-triggered, else null
     * @param weatherId lowercased Showdown weather effect ID (e.g. {@code "rainweather"},
     *                  {@code "wildboltstorm"}, {@code "deltastream"})
     */
    public static void handleWeatherInstruction(PokemonBattle battle, String abilityId, String weatherId) {
        ServerWorld world = getBattleWorld(battle);
        if (world == null) return;
        if (!world.getRegistryKey().equals(World.OVERWORLD)) return;

        // Prefer the ability lookup: ability IDs carry the correct priority (e.g. Drizzle = 1,
        // Primordial Sea = 2).  Fall back to the weather-ID lookup for move-triggered weather
        // (e.g. wildboltstorm → THUNDERSTORM priority 2, rainweather → RAIN priority 0).
        Optional<WeatherRegistry.WeatherEntry> entry = Optional.empty();
        if (abilityId != null) {
            entry = WeatherRegistry.forAbility(abilityId);
        }
        if (entry.isEmpty()) {
            entry = WeatherRegistry.forWeather(weatherId);
        }
        if (entry.isEmpty()) return;

        WeatherRegistry.WeatherEntry e = entry.get();
        UUID battleId = battle.getBattleId();
        // Ensure the battle is tracked (idempotent — safe to call multiple times).
        ExampleMod.getWeatherManager().onBattleStart(battleId);
        long currentTick = world.getTime();
        ExampleMod.getWeatherManager().applyWeatherFromBattle(
                world, battleId, e.type(), e.priority(), currentTick, ExampleMod.getConfig());
        LOGGER.debug("[CobblemonWeather] Weather instruction: ability={}, weather={} -> {} priority={} (battle={})",
                abilityId, weatherId, e.type(), e.priority(), battleId);
    }

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

    public static void handleThunderstormMove(PokemonBattle battle, String moveId, String speciesId) {
        ServerWorld world = getBattleWorld(battle);
        if (world == null) return;
        if (!world.getRegistryKey().equals(World.OVERWORLD)) return;

        long currentTick = world.getTime();
        boolean fastTrack = "wildboltstorm".equals(moveId) && THUNDURUS_SPECIES.contains(speciesId);
        boolean isThunderMove = THUNDER_MOVES.contains(moveId);

        if (!fastTrack && !isThunderMove) return;

        if ("wildboltstorm".equals(moveId) && !fastTrack) {
            LOGGER.debug("[CobblemonWeather] wildboltstorm used by non-Thundurus species '{}'; no fast-track.",
                    speciesId);
            return;
        }

        LOGGER.debug("[CobblemonWeather] {} used {} -> THUNDERSTORM{} (battle={})",
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