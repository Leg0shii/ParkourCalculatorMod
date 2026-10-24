package de.legoshi.parkourcalc.forge.sim;

import de.legoshi.parkourcalc.core.ui.MovementEffect;
import net.minecraft.entity.EntityLivingBase;
//? if >=1.12
import net.minecraft.init.MobEffects;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

public final class MovementEffects {

    private MovementEffects() {
    }

    public static List<PotionEffect> instances(int[] levels, int duration) {
        List<PotionEffect> out = new ArrayList<PotionEffect>();
        for (MovementEffect effect : MovementEffect.all()) {
            int level = level(levels, effect);
            Potion type = type(effect);
            if (level <= 0 || type == null) continue;
            out.add(make(type, duration, MovementEffect.clientAmplifier(level, true)));
        }
        return out;
    }

    public static void removeAll(EntityLivingBase entity) {
        for (MovementEffect effect : MovementEffect.all()) {
            Potion type = type(effect);
            if (type != null) remove(entity, type);
        }
    }

    public static void applyClient(EntityLivingBase entity, int[] levels, int duration) {
        for (MovementEffect effect : MovementEffect.all()) {
            Potion type = type(effect);
            if (type == null) continue;
            remove(entity, type);
            type.removeAttributesModifiersFromEntity(entity, entity.getAttributeMap(), 0);
        }
        for (MovementEffect effect : MovementEffect.all()) {
            int level = level(levels, effect);
            Potion type = type(effect);
            if (level <= 0 || type == null) continue;
            int amplifier = MovementEffect.clientAmplifier(level, true);
            entity.addPotionEffect(make(type, duration, amplifier));
            type.applyAttributesModifiersToEntity(entity, entity.getAttributeMap(), amplifier);
        }
    }

    private static int level(int[] levels, MovementEffect effect) {
        return effect.ordinal() < levels.length ? levels[effect.ordinal()] : 0;
    }

    //? if >=1.12 {
    private static Potion type(MovementEffect effect) {
        switch (effect) {
            case SPEED: return MobEffects.SPEED;
            case SLOWNESS: return MobEffects.SLOWNESS;
            case JUMP_BOOST: return MobEffects.JUMP_BOOST;
            case LEVITATION: return MobEffects.LEVITATION;
            case BLINDNESS: return MobEffects.BLINDNESS;
            default: return null;
        }
    }

    private static PotionEffect make(Potion type, int duration, int amplifier) {
        return new PotionEffect(type, duration, amplifier, false, false);
    }

    private static void remove(EntityLivingBase entity, Potion type) {
        entity.removePotionEffect(type);
    }
    //?} else {
    /*private static Potion type(MovementEffect effect) {
        switch (effect) {
            case SPEED: return Potion.moveSpeed;
            case SLOWNESS: return Potion.moveSlowdown;
            case JUMP_BOOST: return Potion.jump;
            case BLINDNESS: return Potion.blindness;
            default: return null;
        }
    }

    private static PotionEffect make(Potion type, int duration, int amplifier) {
        return new PotionEffect(type.id, duration, amplifier, false, false);
    }

    private static void remove(EntityLivingBase entity, Potion type) {
        entity.removePotionEffect(type.id);
    }
    *///?}
}
