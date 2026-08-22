package de.legoshi.parkourcalc.forge8.sim.paired;

import de.legoshi.parkourcalc.core.DebugFlags;
import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.forge8.sim.SimulatorEntity;
import net.minecraft.entity.player.EntityPlayerMP;

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
                + " hasMoved=" + s.hasMoved
                + " floating=" + s.floatingTickCount
                + " lastPos=" + s.lastPosX + "," + s.lastPosY + "," + s.lastPosZ;
    }

    public static double[] pendingVelocity(Checkpoint checkpoint) {
        if (!(checkpoint instanceof PairedCheckpoint)) return null;
        java.util.List<net.minecraft.network.Packet> pending = ((PairedCheckpoint) checkpoint).server.pendingClientbound;
        if (pending == null) return null;
        for (net.minecraft.network.Packet p : pending) {
            if (p instanceof net.minecraft.network.play.server.S12PacketEntityVelocity) {
                net.minecraft.network.play.server.S12PacketEntityVelocity v =
                        (net.minecraft.network.play.server.S12PacketEntityVelocity) p;
                return new double[]{v.getMotionX() / 8000.0, v.getMotionY() / 8000.0, v.getMotionZ() / 8000.0};
            }
        }
        return null;
    }

    public static void applyRestartState(EntityPlayerMP sp, Checkpoint checkpoint) {
        sp.velocityChanged = false;
        sp.respawnInvulnerabilityTicks = 0;
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
            if (sp.playerNetServerHandler != null) {
                sp.playerNetServerHandler.floatingTickCount = c.floatingTickCount;
            }
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
