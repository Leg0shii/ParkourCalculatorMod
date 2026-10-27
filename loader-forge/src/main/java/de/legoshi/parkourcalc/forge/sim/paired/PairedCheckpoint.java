package de.legoshi.parkourcalc.forge.sim.paired;

import de.legoshi.parkourcalc.core.DebugFlags;
import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.forge.sim.SimulatorEntity;
import net.minecraft.entity.player.EntityPlayerMP;
//? if >=1.12 {
import net.minecraft.network.play.server.SPacketEntityVelocity;
//?} else {
/*import net.minecraft.network.play.server.S12PacketEntityVelocity;
*///?}

public final class PairedCheckpoint implements Checkpoint {

    public final SimulatorEntity.Checkpoint client;
    public final PairedServerSim.ServerCheckpoint server;

    public PairedCheckpoint(SimulatorEntity.Checkpoint client, PairedServerSim.ServerCheckpoint server) {
        this.client = client;
        this.server = server;
    }

    public static SimulatorEntity.Checkpoint clientPart(Checkpoint checkpoint) {
        if (checkpoint instanceof PairedCheckpoint) return ((PairedCheckpoint) checkpoint).client;
        if (checkpoint instanceof SimulatorEntity.Checkpoint) return (SimulatorEntity.Checkpoint) checkpoint;
        return null;
    }

    public static double[] serverVelocity(Checkpoint checkpoint) {
        if (!(checkpoint instanceof PairedCheckpoint)) return null;
        PairedServerSim.ServerCheckpoint s = ((PairedCheckpoint) checkpoint).server;
        return new double[]{s.velX, s.velY, s.velZ};
    }

    public static String describeServer(Checkpoint checkpoint) {
        if (!(checkpoint instanceof PairedCheckpoint)) return "pairSrv=unavailable";
        PairedServerSim.ServerCheckpoint s = ((PairedCheckpoint) checkpoint).server;
        return "pairSrv=" + s.velX + "," + s.velY + "," + s.velZ
                + " fall=" + s.fallDistance
                + " g=" + s.onGround
                + " spr=" + s.sprinting
                + " sneak=" + s.sneaking
                + " velChanged=" + s.velocityChanged
                + " hurtResist=" + s.hurtResistantTime
                + " hurtTime=" + s.hurtTime
                + " lastDmg=" + s.lastDamage
                + " fire=" + s.fire
                //? if >=1.12 {
                + " awaitingTeleport=" + (s.targetPos != null)
                + " floating=" + s.floatingTickCount
                + " lastGood=" + s.lastGoodX + "," + s.lastGoodY + "," + s.lastGoodZ;
                //?} else {
                /*+ " hasMoved=" + s.hasMoved
                + " floating=" + s.floatingTickCount
                + " lastPos=" + s.lastPosX + "," + s.lastPosY + "," + s.lastPosZ;
                *///?}
    }

    public static double[] pendingVelocity(Checkpoint checkpoint) {
        if (!(checkpoint instanceof PairedCheckpoint)) return null;
        java.util.List<net.minecraft.network.Packet> pending = ((PairedCheckpoint) checkpoint).server.pendingClientbound;
        if (pending == null) return null;
        for (net.minecraft.network.Packet p : pending) {
            //? if >=1.12 {
            if (p instanceof SPacketEntityVelocity) {
                SPacketEntityVelocity v = (SPacketEntityVelocity) p;
            //?} else {
            /*if (p instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity v = (S12PacketEntityVelocity) p;
            *///?}
                return new double[]{v.getMotionX() / 8000.0, v.getMotionY() / 8000.0, v.getMotionZ() / 8000.0};
            }
        }
        return null;
    }

    public static void applyRestartState(EntityPlayerMP sp, Checkpoint checkpoint) {
        sp.velocityChanged = false;
        sp.respawnInvulnerabilityTicks = 0;
        //? if >=1.12 {
        SimulatorEntity.Checkpoint clientPart = clientPart(checkpoint);
        de.legoshi.parkourcalc.forge.sim.ElytraGlide.setFallFlying(sp, clientPart != null && clientPart.fallFlying);
        //?}
        if (checkpoint instanceof PairedCheckpoint) {
            PairedServerSim.ServerCheckpoint c = ((PairedCheckpoint) checkpoint).server;
            sp.fire = c.fire;
            sp.hurtResistantTime = c.hurtResistantTime;
            sp.hurtTime = c.hurtTime;
            sp.lastDamage = c.lastDamage;
            sp.motionX = c.velX;
            sp.motionY = c.velY;
            sp.motionZ = c.velZ;
            sp.fallDistance = c.fallDistance;
            sp.onGround = c.onGround;
            sp.setSprinting(c.sprinting);
            sp.setSneaking(c.sneaking);
            //? if >=1.12 {
            if (sp.connection != null) {
                sp.connection.floatingTickCount = c.floatingTickCount;
            }
            //?} else {
            /*if (sp.playerNetServerHandler != null) {
                sp.playerNetServerHandler.floatingTickCount = c.floatingTickCount;
            }
            *///?}
        } else {
            sp.extinguish();
            sp.hurtResistantTime = 0;
            sp.hurtTime = 0;
            sp.fallDistance = 0.0F;
        }
        if (DebugFlags.PAIRED_DIAGNOSTICS) {
            System.out.println("[PC-NET] restart state fire=" + sp.fire
                    + " hurtResist=" + sp.hurtResistantTime
                    + " fallDistance=" + sp.fallDistance
                    + " onGround=" + sp.onGround
                    + " serverMotion=" + sp.motionX + "," + sp.motionY + "," + sp.motionZ
                    + " paired=" + (checkpoint instanceof PairedCheckpoint));
        }
    }
}
