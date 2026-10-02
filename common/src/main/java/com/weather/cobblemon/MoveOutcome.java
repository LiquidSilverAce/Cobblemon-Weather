package com.weather.cobblemon;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.battles.dispatch.CauserInstruction;
import com.cobblemon.mod.common.battles.interpreter.instructions.ActivateInstruction;
import com.cobblemon.mod.common.battles.interpreter.instructions.DamageInstruction;
import com.cobblemon.mod.common.battles.interpreter.instructions.EndInstruction;
import com.cobblemon.mod.common.battles.interpreter.instructions.MoveInstruction;

/** Positive hit confirmation for the addon's damaging thunderstorm moves. */
public final class MoveOutcome {
    private MoveOutcome() {}

    public static boolean hasHit(MoveInstruction move) {
        String moveId = WeatherRegistry.normalizeId(move.getEffect().getId());
        if (!"thunder".equals(moveId) && !"thunderbolt".equals(moveId)
                && !"wildboltstorm".equals(moveId)) return false;
        for (var instruction : move.getInstructionSet().getSubsequentInstructions(move)) {
            // Substitute absorbs a successful hit without a Pokemon HP damage message.
            // Target activations (Protect, absorption, Substitute) are also causers in
            // Cobblemon, but a spread attack may still hit a later target.
            if (instruction instanceof ActivateInstruction activate) {
                if (isSubstitute(activate.getMessage(), move)) return true;
                continue;
            }
            if (instruction instanceof CauserInstruction) break;
            if (instruction instanceof EndInstruction end
                    && isSubstitute(end.getMessage(), move)) return true;
            if (instruction instanceof DamageInstruction damage
                    && damage.getExpectedTarget() != null
                    && damage.getExpectedTarget() != move.getUserPokemon()
                    && !damage.getPrivateMessage().hasOptionalArgument("from")) return true;
        }
        // Misses, failures, protection, immunity and ability absorption have no direct
        // damage or substitute hit. A spread move needs only one successful target.
        return false;
    }

    private static boolean isSubstitute(BattleMessage message, MoveInstruction move) {
        var effect = message.effectAt(1);
        return effect != null && "substitute".equals(effect.getId())
                && message.argumentAt(0) != null
                && !message.argumentAt(0).equals(move.getMessage().argumentAt(0));
    }
}
