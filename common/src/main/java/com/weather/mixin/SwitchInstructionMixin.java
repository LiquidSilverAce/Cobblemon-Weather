package com.weather.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.SwitchInstruction;
import com.weather.ExampleMod;
import com.weather.cobblemon.CobblemonEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into Cobblemon's {@code SwitchInstruction.invoke()} which is the server-side handler
 * for the Showdown {@code |switch|} and {@code |drag|} protocol messages.  This fires each
 * time a Pokemon enters the field — at battle start (initial send-out) and on mid-battle
 * switches — which is the correct moment to evaluate weather-setting abilities such as
 * Drizzle, Drought, Primordial Sea, Desolate Land, Orichalcum Pulse, and Delta Stream.
 *
 * <p>We inject at {@code TAIL} so the switch is fully resolved and the new Pokemon is
 * already reflected in the actor's active-Pokemon list.
 *
 * <p>{@code @Pseudo} + {@code require = 0}: if Cobblemon is absent (or its internal class
 * structure changes) the mixin is silently skipped rather than crashing the server.
 */
@Pseudo
@Mixin(value = SwitchInstruction.class, remap = false)
public abstract class SwitchInstructionMixin {

    @Inject(method = "invoke", at = @At("TAIL"), require = 0)
    private void cobbleweather_onSwitchIn(PokemonBattle battle, CallbackInfo ci) {
        try {
            CobblemonEventListener.handleSwitchIn(battle);
        } catch (Exception e) {
            ExampleMod.LOGGER.error("[CobblemonWeather] Error in SwitchInstruction mixin", e);
        }
    }
}
