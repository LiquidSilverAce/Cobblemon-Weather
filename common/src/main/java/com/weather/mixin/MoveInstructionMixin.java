package com.weather.mixin;

import com.cobblemon.mod.common.api.battles.interpreter.Effect;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.MoveInstruction;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.weather.ExampleMod;
import com.weather.cobblemon.CobblemonEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into Cobblemon's {@code MoveInstruction.invoke()} which is the server-side handler for
 * the Showdown {@code |move|POKEMON|MOVE|TARGET} protocol message.  This fires once per
 * successfully dispatched move use and is the canonical way to observe regular move use in
 * Cobblemon 1.7.x (no public {@code MOVE_USED} event exists in the Cobblemon API).
 *
 * <p>We inject at {@code TAIL} so that {@code userPokemon} (set at the very start of
 * {@code invoke()}) is guaranteed to be populated.
 *
 * <p>{@code remap = false} is required because {@code MoveInstruction} is a Cobblemon class,
 * not a vanilla Minecraft class, and must not go through the Loom/Mixin remapper.
 */
@Pseudo
@Mixin(value = MoveInstruction.class, remap = false)
public abstract class MoveInstructionMixin {

    /**
     * Shadows the Kotlin {@code val effect} property's backing field.
     * {@code Effect.getId()} returns the lowercase Showdown move identifier (e.g. "thunder").
     */
    @Shadow
    private Effect effect;

    /**
     * Shadows the Kotlin {@code lateinit var userPokemon} backing field.
     * Populated at the very start of {@code invoke()} before any dispatch calls.
     */
    @Shadow
    public BattlePokemon userPokemon;

    @Inject(method = "invoke", at = @At("TAIL"),
            // require=0: paired with @Pseudo above — if Cobblemon is absent the injection point
            // doesn't exist; requiring 0 matches prevents a MixinException crash at load time.
            require = 0)
    private void cobbleweather_onMoveUsed(PokemonBattle battle, CallbackInfo ci) {
        try {
            if (userPokemon == null || effect == null) return;

            // Effect.id is already lowercase per Cobblemon's Effect.parse() implementation.
            String moveId = effect.getId();
            String speciesId = userPokemon.getOriginalPokemon().getSpecies().showdownId();

            // Regular weather moves (raindance, sunnyday, sandstorm, snowscape)
            CobblemonEventListener.handleMoveUsed(battle, moveId);

            // Thunderstorm upgrade moves (thunder, thunderbolt, wildboltstorm+thundurus)
            CobblemonEventListener.handleThunderstormMove(battle, moveId, speciesId);
        } catch (Exception e) {
            ExampleMod.LOGGER.error("[CobblemonWeather] Error in MoveInstruction mixin", e);
        }
    }
}
