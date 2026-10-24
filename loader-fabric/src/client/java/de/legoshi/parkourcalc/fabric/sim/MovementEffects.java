package de.legoshi.parkourcalc.fabric.sim;

import de.legoshi.parkourcalc.core.ui.MovementEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

public final class MovementEffects {

    //? if <1.20.5 {
    /*private static final boolean BYTE_AMPLIFIER = true;
    *///?} else {
    private static final boolean BYTE_AMPLIFIER = false;
    //?}

    private MovementEffects() {
    }

    public static List<MobEffectInstance> instances(int[] levels, int duration, boolean clientSide) {
        List<MobEffectInstance> out = new ArrayList<>();
        for (MovementEffect effect : MovementEffect.all()) {
            int level = effect.ordinal() < levels.length ? levels[effect.ordinal()] : 0;
            if (level <= 0 || !available(effect)) continue;
            int amplifier = clientSide ? MovementEffect.clientAmplifier(level, BYTE_AMPLIFIER) : level - 1;
            out.add(new MobEffectInstance(type(effect), duration, amplifier, false, false, true));
        }
        return out;
    }

    public static void removeAll(LivingEntity entity) {
        for (MovementEffect effect : MovementEffect.all()) {
            if (available(effect)) entity.removeEffect(type(effect));
        }
    }

    public static int amplifier(LivingEntity entity, MovementEffect effect) {
        if (!available(effect)) return -1;
        MobEffectInstance instance = entity.getEffect(type(effect));
        return instance == null ? -1 : instance.getAmplifier();
    }

    public static boolean available(MovementEffect effect) {
        return type(effect) != null;
    }

    //? if >=26.1 {
    private static net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> type(MovementEffect effect) {
        switch (effect) {
            case SPEED: return MobEffects.SPEED;
            case SLOWNESS: return MobEffects.SLOWNESS;
            case JUMP_BOOST: return MobEffects.JUMP_BOOST;
            case LEVITATION: return MobEffects.LEVITATION;
            case SLOW_FALLING: return MobEffects.SLOW_FALLING;
            case DOLPHINS_GRACE: return MobEffects.DOLPHINS_GRACE;
            case BLINDNESS: return MobEffects.BLINDNESS;
            case WEAVING: return MobEffects.WEAVING;
            default: return null;
        }
    }
    //?} elif >=1.20.5 {
    /*private static net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> type(MovementEffect effect) {
        switch (effect) {
            case SPEED: return MobEffects.MOVEMENT_SPEED;
            case SLOWNESS: return MobEffects.MOVEMENT_SLOWDOWN;
            case JUMP_BOOST: return MobEffects.JUMP;
            case LEVITATION: return MobEffects.LEVITATION;
            case SLOW_FALLING: return MobEffects.SLOW_FALLING;
            case DOLPHINS_GRACE: return MobEffects.DOLPHINS_GRACE;
            case BLINDNESS: return MobEffects.BLINDNESS;
            case WEAVING: return MobEffects.WEAVING;
            default: return null;
        }
    }
    *///?} else {
    /*private static net.minecraft.world.effect.MobEffect type(MovementEffect effect) {
        switch (effect) {
            case SPEED: return MobEffects.MOVEMENT_SPEED;
            case SLOWNESS: return MobEffects.MOVEMENT_SLOWDOWN;
            case JUMP_BOOST: return MobEffects.JUMP;
            case LEVITATION: return MobEffects.LEVITATION;
            case SLOW_FALLING: return MobEffects.SLOW_FALLING;
            case DOLPHINS_GRACE: return MobEffects.DOLPHINS_GRACE;
            case BLINDNESS: return MobEffects.BLINDNESS;
            default: return null;
        }
    }
    *///?}
}
