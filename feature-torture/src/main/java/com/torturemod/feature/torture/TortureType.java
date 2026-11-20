package com.torturemod.feature.torture;

import com.torturemod.api.Platform;
import com.torturemod.api.items.TortureTypes;
import com.torturemod.api.model.DamageKind;
import com.torturemod.api.model.TargetId;

import java.util.Map;
import java.util.Optional;

/** Само воздействие пытки на цель через ванильные механики. */
interface TortureType {
    /** @return false, если игра не применила эффект: цель к нему невосприимчива */
    boolean apply(Platform platform, TargetId target, TargetId actor, double strength, TypeSettings settings);

    Map<String, TortureType> BUILT_IN = Map.of(
            TortureTypes.STRIKE, (platform, target, actor, strength, settings) -> platform.combat()
                    .damage(target, DamageKind.BLUNT, (float) (settings.healthDamage() * strength), Optional.of(actor)),
            TortureTypes.FIRE, (platform, target, actor, strength, settings) -> platform.combat()
                    .ignite(target, settings.fireTicks()),
            TortureTypes.DROWN, (platform, target, actor, strength, settings) -> platform.combat()
                    .drainAir(target, settings.airTicks()),
            TortureTypes.POISON, (platform, target, actor, strength, settings) -> {
                boolean poisoned = platform.combat().applyEffect(target, "minecraft:poison", settings.poisonTicks(), 0);
                if (poisoned) {
                    platform.combat().drainFood(target, settings.hungerPoints());
                }
                return poisoned;
            });
}
