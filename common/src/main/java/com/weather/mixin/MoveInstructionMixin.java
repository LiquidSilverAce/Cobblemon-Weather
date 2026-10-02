package com.weather.mixin;

import com.cobblemon.mod.common.api.battles.interpreter.Effect;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.MoveInstruction;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.weather.cobblemon.CobblemonEventListener;
import com.weather.cobblemon.MoveOutcome;
import kotlin.Unit;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = MoveInstruction.class, remap = false)
public abstract class MoveInstructionMixin {
    @Shadow @Final private Effect effect;
    @Shadow public BattlePokemon userPokemon;

    @Inject(method = "invoke", at = @At("TAIL"))
    private void cobbleweather$onMoveUsed(PokemonBattle battle, CallbackInfo ci) {
        BattlePokemon source = userPokemon;
        String moveId = effect.getId();
        battle.dispatchGo(() -> {
            // A |move| announces an attempt. Its result instructions are populated before
            // the dispatch queue runs, so require a hit before applying addon weather.
            if (MoveOutcome.hasHit((MoveInstruction) (Object) this)) {
                CobblemonEventListener.handleMoveUsed(battle, source, moveId);
            }
            return Unit.INSTANCE;
        });
    }
}
