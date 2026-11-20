package com.torturemod.forge;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.Level;

/**
 * Невидимая опора для верёвки поводка. Ванильный клиент рисует верёвку только от моба, поэтому
 * к палачу привязана эта летучая мышь, а мод каждый тик ставит её у пленного. В мир она не сохраняется
 * и при обрыве не роняет поводок.
 */
final class LeashAnchor extends Bat {
    LeashAnchor(Level level) {
        super(EntityType.BAT, level);
        setInvisible(true);
        setNoAi(true);
        setSilent(true);
        setInvulnerable(true);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    public void dropLeash(boolean broadcast, boolean dropItem) {
        super.dropLeash(broadcast, false);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }
}
