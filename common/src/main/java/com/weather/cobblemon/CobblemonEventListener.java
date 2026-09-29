package com.weather.cobblemon;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.interpreter.Effect;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.ShowdownInterpreter;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.weather.ExampleMod;
import com.weather.config.ServerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Called in Cobblemon's dispatch queue, on the server thread, in battle-action order. */
public final class CobblemonEventListener {
    private CobblemonEventListener() {}

    public static boolean isBattleActive(java.util.UUID id) {
        PokemonBattle battle = BattleRegistry.INSTANCE.getBattle(id);
        return battle != null && !battle.getEnded();
    }

    public static boolean isAllowed(BattlePokemon source, ServerConfig config) {
        if (config.isWhitelistEmpty()) return true;
        if (source == null || !(source.getActor() instanceof PlayerBattleActor actor)) return false;
        ServerPlayer player = actor.getEntity();
        return config.allowsPlayer(actor.getUuid(), player == null ? null : player.getGameProfile().getName());
    }

    public static void handleWeatherInstruction(PokemonBattle battle, BattleMessage message) {
        if (battle.getEnded() || message.hasOptionalArgument("upkeep")) return;
        Effect weather = message.effectAt(0);
        if (weather == null) return;
        if ("none".equals(weather.getId())) {
            ServerLevel world = getBattleWorld(battle, null);
            if (world != null) ExampleMod.getWeatherManager().releasePriority(world, battle.getBattleId());
            return;
        }
        Effect from = message.effect("from");
        boolean ability = from != null && from.getType() == Effect.Type.ABILITY;
        BattlePokemon source = message.battlePokemonFromOptional(battle, "of");
        if (source == null && !ability && !message.hasOptionalArgument("of")) {
            // Move weather has no [of]. Resolve its actual causer, not the first player in the battle.
            BattleMessage cause = ShowdownInterpreter.INSTANCE.getLastCauser().get(battle.getBattleId());
            if (cause != null && "move".equals(cause.getId())) {
                Effect move = cause.effectAt(1);
                if (move != null && WeatherRegistry.forMove(move.getId()).isPresent()) {
                    source = cause.battlePokemon(0, battle);
                }
            }
        }
        ServerConfig config = ExampleMod.getConfig();
        if (!isAllowed(source, config)) return;
        ServerLevel world = getBattleWorld(battle, source);
        if (world == null) return;
        var entry = ability ? WeatherRegistry.forAbility(from.getId()) : WeatherRegistry.forWeather(weather.getId());
        // Do not invent priority for an unknown ability or treat Wildbolt Storm as a Showdown weather.
        entry.ifPresent(e -> ExampleMod.getWeatherManager().applyWeatherFromBattle(
                world, battle.getBattleId(), e.type(), e.priority(), world.getGameTime(), config));
    }

    public static void handleMoveUsed(PokemonBattle battle, BattlePokemon source, String moveId) {
        if (battle.getEnded() || !isAllowed(source, ExampleMod.getConfig())) return;
        ServerLevel world = getBattleWorld(battle, source);
        if (world == null) return;
        // Regular weather moves are handled only by actual |-weather| messages. A failed move
        // must not change the world. These three electrical moves are this addon's explicit effects.
        ExampleMod.getWeatherManager().applyThunderstorm(world, battle.getBattleId(),
                WeatherRegistry.normalizeId(moveId), world.getGameTime(), ExampleMod.getConfig());
    }

    private static ServerLevel getBattleWorld(PokemonBattle battle, BattlePokemon source) {
        if (source != null) {
            if (source.getActor() instanceof PlayerBattleActor actor && actor.getEntity() != null) {
                return overworld(actor.getEntity().serverLevel());
            }
            if (source.getEntity() != null && source.getEntity().level() instanceof ServerLevel level) {
                return overworld(level);
            }
        }
        for (var actor : battle.getActors()) {
            if (actor instanceof PlayerBattleActor playerActor && playerActor.getEntity() != null) {
                return overworld(playerActor.getEntity().serverLevel());
            }
        }
        return null;
    }

    private static ServerLevel overworld(ServerLevel world) {
        return world.dimension().equals(Level.OVERWORLD) ? world : null;
    }
}
