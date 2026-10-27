package de.legoshi.parkourcalc.fabric.sim;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//? if <1.21.2 {
/*import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ElytraItem;
*///?}

public final class ElytraGlide {

    private ElytraGlide() {
    }

    public static boolean wearing(Player p) {
        return p.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
    }

    public static void equip(Player p, boolean equipped) {
        if (equipped == wearing(p)) {
            if (equipped) p.getItemBySlot(EquipmentSlot.CHEST).setDamageValue(0);
            return;
        }
        p.setItemSlot(EquipmentSlot.CHEST, equipped ? new ItemStack(Items.ELYTRA) : ItemStack.EMPTY);
    }

    public static boolean tryDeploy(Player p, boolean jump, boolean wasJumping) {
        if (!jump || wasJumping || p.getAbilities().flying || p.isPassenger() || p.onClimbable()) return false;
        return p.tryToStartFallFlying();
    }

    public static boolean canKeepGliding(Player p) {
        //? if >=1.21.2 {
        return p.canGlide();
        //?} else {
        /*if (p.onGround() || p.isPassenger() || p.hasEffect(MobEffects.LEVITATION)) return false;
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        return chest.is(Items.ELYTRA) && ElytraItem.isFlyEnabled(chest);
        *///?}
    }

    public static void setFallFlying(Player p, boolean fallFlying) {
        if (fallFlying == p.isFallFlying()) return;
        if (fallFlying) {
            p.startFallFlying();
        } else {
            p.stopFallFlying();
        }
    }

    public static final class Lag {

        private boolean glidingAtTickStart;
        private boolean pendingStop;

        public void reset(Player p) {
            pendingStop = false;
            glidingAtTickStart = false;
            setFallFlying(p, false);
        }

        public void beginTick(Player p) {
            if (pendingStop) {
                pendingStop = false;
                setFallFlying(p, false);
            }
            glidingAtTickStart = p.isFallFlying();
        }

        public boolean endTick(Player p) {
            if (!glidingAtTickStart) return false;
            if (p.isFallFlying()) {
                if (!canKeepGliding(p)) pendingStop = true;
                return false;
            }
            setFallFlying(p, true);
            pendingStop = true;
            return true;
        }

        public void forceStop() {
            pendingStop = true;
        }

        public boolean pendingStop() {
            return pendingStop;
        }

        public void restore(Player p, boolean fallFlying, boolean pendingStop) {
            setFallFlying(p, fallFlying);
            this.pendingStop = pendingStop;
            this.glidingAtTickStart = false;
        }
    }
}
