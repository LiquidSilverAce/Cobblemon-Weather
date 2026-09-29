package com.weather.mixin;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.WeatherInstruction;
import com.weather.cobblemon.CobblemonEventListener;
import kotlin.Unit;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = WeatherInstruction.class, remap = false)
public abstract class WeatherInstructionMixin {
    @Shadow @Final private BattleMessage message;

    @Inject(method = "invoke", at = @At("TAIL"))
    private void cobbleweather$onWeatherChange(PokemonBattle battle, CallbackInfo ci) {
        // invoke() queues actions; applying immediately races earlier switch/move dispatches.
        battle.dispatchGo(() -> {
            CobblemonEventListener.handleWeatherInstruction(battle, message);
            return Unit.INSTANCE;
        });
    }
}
