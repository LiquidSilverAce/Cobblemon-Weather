package com.weather.mixin;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.interpreter.Effect;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.WeatherInstruction;
import com.weather.ExampleMod;
import com.weather.cobblemon.CobblemonEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into Cobblemon's {@code WeatherInstruction.invoke()} which is the server-side handler
 * for the Showdown {@code |-weather|WEATHER} protocol message.
 *
 * <p>This is the canonical integration point for all battle-triggered weather changes because:
 * <ul>
 *   <li>It fires for <em>every</em> new weather application — whether caused by a move
 *       (Rain Dance, Sunny Day, …) or by an ability (Drizzle, Primordial Sea, Delta Stream, …).</li>
 *   <li>It carries both the <strong>weather ID</strong> ({@code effectAt(0)}) and, when the
 *       cause is an ability, the <strong>ability ID</strong> in the {@code [from]} optional
 *       argument — allowing us to look up the correct priority from the registry.</li>
 *   <li>It fires <em>after</em> {@code SwitchInstruction} has completed its async dispatch and
 *       the newly switched-in Pokémon is fully active, making it reliable for mid-battle
 *       switch-ins (unlike hooks on SwitchInstruction itself).</li>
 *   <li>It covers Mega Evolution ability grants (e.g. Rayquaza → Delta Stream) since Showdown
 *       emits {@code |-weather|DeltaStream|[from] ability: Delta Stream} at that point too.</li>
 * </ul>
 *
 * <p><b>Upkeep vs. new weather:</b> The {@code |-weather|…} message is sent every turn the
 * weather is active; upkeep turns carry an {@code [upkeep]} optional argument.  We only act
 * when there is <em>no</em> upkeep flag, meaning the weather has just been applied or changed.
 *
 * <p><b>Move-caused weather:</b> Regular weather moves (Rain Dance, Sunny Day, Sandstorm,
 * Snowscape) also trigger this instruction — they have no {@code [from] ability:} tag and their
 * weather IDs are mapped in {@link com.weather.cobblemon.WeatherRegistry#forWeather}.
 * {@link MoveInstructionMixin} continues to fire for those moves too; the resulting double-call
 * to {@code applyWeatherFromBattle} is idempotent and harmless.
 *
 * <p><b>Wildbolt Storm:</b> Thundurus's Wildbolt Storm sets the {@code wildboltstorm} weather
 * condition in Showdown without a {@code [from] ability:} tag.  This mixin maps that weather ID
 * to {@code THUNDERSTORM} at priority 2, ensuring it participates in the overworld priority system.
 *
 * <p>{@code @Pseudo} + {@code require = 0}: if Cobblemon is absent or the class is renamed the
 * mixin is silently skipped rather than crashing the server.
 */
@Pseudo
@Mixin(value = WeatherInstruction.class, remap = false)
public abstract class WeatherInstructionMixin {

    /**
     * Shadows the Kotlin {@code val message: BattleMessage} constructor parameter field.
     * The message carries the weather ID at argument index 0 and the optional {@code [from]}
     * argument whose value is the causing ability or move.
     */
    @Shadow
    private BattleMessage message;

    @Inject(method = "invoke", at = @At("TAIL"), require = 0)
    private void cobbleweather_onWeatherChange(PokemonBattle battle, CallbackInfo ci) {
        try {
            if (message == null) return;

            // Skip upkeep messages — the weather is still ongoing from a previous turn, not new.
            if (message.hasOptionalArgument("upkeep")) return;

            // Get the weather effect at position 0 (e.g. "rainweather", "deltastream", "none").
            Effect weatherEffect = message.effectAt(0);
            if (weatherEffect == null) return;
            String weatherId = weatherEffect.getId();

            // "none" signals that weather has ended; nothing to apply.
            if ("none".equals(weatherId)) return;

            // Inspect the [from] optional argument to detect ability-caused weather.
            // BattleMessage.effect(String) requires an explicit argument from Java because
            // Kotlin default parameters are not exposed as no-arg overloads in the JVM API.
            // If present and of ABILITY type, the ability ID carries the correct priority.
            Effect from = message.effect("from"); // retrieves the "[from]" optional argument
            String abilityId = (from != null && from.getType() == Effect.Type.ABILITY)
                    ? from.getId()
                    : null;

            CobblemonEventListener.handleWeatherInstruction(battle, abilityId, weatherId);
        } catch (Exception e) {
            ExampleMod.LOGGER.error("[CobblemonWeather] Error in WeatherInstruction mixin", e);
        }
    }
}
