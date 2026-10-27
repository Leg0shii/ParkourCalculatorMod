package de.legoshi.parkourcalc.forge.sim;

//? if >=1.12 {
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemElytra;
import net.minecraft.item.ItemStack;
//?}

public final class ElytraGlide {

    private ElytraGlide() {
    }

    //? if >=1.12 {
    private static final int FALL_FLYING_FLAG = 7;

    public static boolean wearing(EntityLivingBase p) {
        return p.getItemStackFromSlot(EntityEquipmentSlot.CHEST).getItem() == Items.ELYTRA;
    }

    public static boolean usable(EntityLivingBase p) {
        ItemStack chest = p.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        return chest.getItem() == Items.ELYTRA && ItemElytra.isUsable(chest);
    }

    public static void equip(EntityLivingBase p, boolean equipped) {
        if (equipped == wearing(p)) {
            if (equipped) p.getItemStackFromSlot(EntityEquipmentSlot.CHEST).setItemDamage(0);
            return;
        }
        p.setItemStackToSlot(EntityEquipmentSlot.CHEST, equipped ? new ItemStack(Items.ELYTRA) : ItemStack.EMPTY);
    }

    public static void setFallFlying(EntityLivingBase p, boolean fallFlying) {
        if (fallFlying != p.isElytraFlying()) SimulatorEntity.setEntityFlag(p, FALL_FLYING_FLAG, fallFlying);
    }

    public static boolean canKeepGliding(EntityLivingBase p) {
        return !p.onGround && !p.isRiding() && usable(p);
    }

    public static boolean requestsDeploy(EntityPlayer p, boolean jump, boolean wasJumping) {
        return jump && !wasJumping && !p.onGround && p.motionY < 0.0D && !p.isElytraFlying()
                && !p.capabilities.isFlying && usable(p);
    }

    public static final class Lag {

        private boolean glidingAtTickStart;
        private boolean pendingStop;
        private boolean pendingStart;

        public void reset(EntityLivingBase p) {
            pendingStop = false;
            pendingStart = false;
            glidingAtTickStart = false;
            setFallFlying(p, false);
        }

        public void request(EntityPlayer p) {
            pendingStart = !p.isInWater();
        }

        public void beginTick(EntityLivingBase p) {
            if (pendingStop) {
                pendingStop = false;
                setFallFlying(p, false);
            }
            if (pendingStart) {
                pendingStart = false;
                if (!p.onGround && !p.isElytraFlying()) setFallFlying(p, true);
            }
            glidingAtTickStart = p.isElytraFlying();
        }

        public boolean endTick(EntityLivingBase p) {
            if (!glidingAtTickStart) return false;
            if (p.isElytraFlying()) {
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

        public void cancelStart() {
            pendingStart = false;
        }

        public boolean pendingStop() {
            return pendingStop;
        }

        public boolean pendingStart() {
            return pendingStart;
        }

        public void restore(EntityLivingBase p, boolean fallFlying, boolean pendingStop, boolean pendingStart) {
            setFallFlying(p, fallFlying);
            this.pendingStop = pendingStop;
            this.pendingStart = pendingStart;
            this.glidingAtTickStart = false;
        }
    }
    //?}
}
