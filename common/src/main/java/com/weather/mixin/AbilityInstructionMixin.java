package com.weather.mixin;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.interpreter.Effect;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.AbilityInstruction;
import com.weather.ExampleMod;
import com.weather.cobblemon.CobblemonEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into Cobblemon's {@code AbilityInstruction.invoke()} which is the server-side handler
 * for the Showdown {@code |-ability|POKEMON|ABILITY} protocol message.
 *
 * <p>This message fires in two scenarios that are both relevant to us:
 * <ol>
 *   <li><b>Switch-in announcement</b> — a Pokémon enters the field and its ability
 *       (e.g. Drizzle, Drought, Primordial Sea, Delta Stream) has a long-term overworld effect.
 *       Crucially, this fires <em>after</em> {@code SwitchInstruction} has completed its async
 *       dispatch and {@code activePokemon.battlePokemon} already points to the new Pokémon,
 *       making it the correct and only reliable point to read the switched-in Pokémon's ability.</li>
 *   <li><b>Mid-battle ability change</b> — includes Rayquaza's Mega Evolution, which grants
 *       Delta Stream.  The {@code |-ability|} message is emitted for the new ability, so we
 *       catch Delta Stream here too without needing a separate mega-evolution hook.</li>
 * </ol>
 *
 * <p>We inject at {@code TAIL} so Cobblemon's own ability broadcast has already run.
 *
 * <p>{@code @Pseudo} + {@code require = 0}: if Cobblemon is absent or the class is renamed the
 * mixin is silently skipped rather than crashing the server.
 */
@Pseudo
@Mixin(value = AbilityInstruction.class, remap = false)
public abstract class AbilityInstructionMixin {

    /**
     * Shadows the Kotlin {@code val message: BattleMessage} constructor parameter field.
     * The message carries the ability name at argument index 1 (0-indexed after the Pokémon token).
     */
    @Shadow
    private BattleMessage message;

    @Inject(method = "invoke", at = @At("TAIL"), require = 0)
    private void cobbleweather_onAbilityActivated(PokemonBattle battle, CallbackInfo ci) {
        try {
            if (message == null) return;
            // Argument 1 is the ability effect (e.g. "drizzle", "deltastream").
            Effect effect = message.effectAt(1);
            if (effect == null) return;
            CobblemonEventListener.handleAbilityTriggered(battle, effect.getId());
        } catch (Exception e) {
            ExampleMod.LOGGER.error("[CobblemonWeather] Error in AbilityInstruction mixin", e);
        }
    }
}
