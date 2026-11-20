package com.torturemod.forge;

import com.torturemod.api.model.DamageKind;
import com.torturemod.api.model.TargetId;
import com.torturemod.api.port.Combat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Воздействие через ванильные механики: игра сама решает, восприимчива ли цель. */
final class ForgeCombat implements Combat {
    private static final float DROWN_DAMAGE = 2;

    private final MinecraftServer server;
    private final ForgeEntities entities;

    ForgeCombat(MinecraftServer server, ForgeEntities entities) {
        this.server = server;
        this.entities = entities;
    }

    @Override
    public boolean damage(TargetId target, DamageKind kind, float amount, Optional<TargetId> source) {
        return entities.find(target).map(living -> {
            DamageSources sources = living.level().damageSources();
            Optional<ServerPlayer> attacker = source.map(id -> server.getPlayerList().getPlayer(id.value()));
            DamageSource damage = switch (kind) {
                case BLUNT, CUTTING -> attacker.map(sources::playerAttack).orElse(sources.generic());
                case FIRE -> sources.onFire();
                case SUFFOCATION -> sources.drown();
                case POISON -> sources.magic();
            };
            // Пытки идут чаще, чем обычная неуязвимость после удара: сбрасываем её, чтобы урон дошёл.
            living.invulnerableTime = 0;
            // Урон от игрока отбрасывает цель и вытолкнул бы пленного из клетки: возвращаем прежнюю скорость
            // в том же тике, до того как сервер отправит её клиенту.
            Vec3 motion = living.getDeltaMovement();
            boolean hurt = living.hurt(damage, amount);
            living.setDeltaMovement(motion);
            return hurt;
        }).orElse(false);
    }

    @Override
    public boolean ignite(TargetId target, int ticks) {
        return entities.find(target).map(living -> {
            if (living.fireImmune() || living.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                return false;
            }
            living.setSecondsOnFire(Math.max(1, ticks / 20));
            return true;
        }).orElse(false);
    }

    @Override
    public boolean drainAir(TargetId target, int ticks) {
        return entities.find(target).map(living -> {
            if (living.canBreatheUnderwater() || living.hasEffect(MobEffects.WATER_BREATHING)) {
                return false;
            }
            int air = living.getAirSupply() - ticks;
            if (air <= 0) {
                living.setAirSupply(0);
                living.invulnerableTime = 0;
                living.hurt(living.level().damageSources().drown(), DROWN_DAMAGE);
            } else {
                living.setAirSupply(air);
            }
            return true;
        }).orElse(false);
    }

    @Override
    public boolean drainFood(TargetId target, int points) {
        ServerPlayer player = server.getPlayerList().getPlayer(target.value());
        if (player == null) {
            return false;
        }
        FoodData food = player.getFoodData();
        food.setFoodLevel(Math.max(0, food.getFoodLevel() - points));
        food.setSaturation(0);
        return true;
    }

    @Override
    public boolean applyEffect(TargetId target, String effectId, int ticks, int amplifier) {
        ResourceLocation id = ResourceLocation.tryParse(effectId);
        MobEffect effect = id == null ? null : BuiltInRegistries.MOB_EFFECT.get(id);
        if (effect == null) {
            return false;
        }
        Optional<LivingEntity> living = entities.find(target);
        // Нежить к яду невосприимчива: игра откажет в эффекте сама.
        return living.isPresent() && living.get().addEffect(new MobEffectInstance(effect, ticks, amplifier));
    }

    @Override
    public void kill(TargetId target) {
        entities.find(target).ifPresent(LivingEntity::kill);
    }
}
