package de.legoshi.parkourcalc.fabric.sim.paired;

import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.fabric.sim.SimulatorEntity;
import net.minecraft.server.level.ServerPlayer;

public final class PairedCheckpoint implements Checkpoint {

    public final SimulatorEntity.Checkpoint client;
    public final PairedServerSim.ServerCheckpoint server;

    public PairedCheckpoint(SimulatorEntity.Checkpoint client, PairedServerSim.ServerCheckpoint server) {
        this.client = client;
        this.server = server;
    }

    public static SimulatorEntity.Checkpoint clientPart(Checkpoint checkpoint) {
        if (checkpoint instanceof PairedCheckpoint paired) return paired.client;
        if (checkpoint instanceof SimulatorEntity.Checkpoint plain) return plain;
        return null;
    }

    public static net.minecraft.world.phys.Vec3 pendingVelocity(Checkpoint checkpoint) {
        if (!(checkpoint instanceof PairedCheckpoint paired)) return null;
        java.util.List<net.minecraft.network.protocol.Packet<?>> pending = paired.server.pendingClientbound;
        if (pending == null) return null;
        for (net.minecraft.network.protocol.Packet<?> p : pending) {
            if (p instanceof net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket motion) {
                return PairedServerSim.quantizeVelocity(motion.movement());
            }
        }
        return null;
    }

    public static net.minecraft.world.phys.Vec3 serverVelocity(Checkpoint checkpoint) {
        if (!(checkpoint instanceof PairedCheckpoint paired)) return null;
        return new net.minecraft.world.phys.Vec3(paired.server.velX, paired.server.velY, paired.server.velZ);
    }

    public static String describeServer(Checkpoint checkpoint) {
        if (!(checkpoint instanceof PairedCheckpoint paired)) return "pairSrv=unavailable";
        PairedServerSim.ServerCheckpoint s = paired.server;
        return "pairSrv=" + s.velX + "," + s.velY + "," + s.velZ
                + " fall=" + s.fallDistance
                + " g=" + s.onGround
                + " vCollB=" + s.verticalCollisionBelow
                + " spr=" + s.sprinting
                + " sneak=" + s.shiftKeyDown
                + " hurtMarked=" + s.hurtMarked
                + " invuln=" + s.invulnerableTime
                + " hurtTime=" + s.hurtTime
                + " fire=" + s.remainingFireTicks
                + " in=" + PairedServerSim.describeInput(s.serverLastClientInput)
                + " tp=" + (s.awaitingPositionFromClient != null)
                + " tpId=" + s.awaitingTeleport
                + " recv=" + s.receivedMovePacketCount + "/" + s.knownMovePacketCount
                + " aboveG=" + s.aboveGroundTickCount
                + " lastGood=" + s.lastGoodX + "," + s.lastGoodY + "," + s.lastGoodZ;
    }

    public static void applyRestartState(ServerPlayer sp, Checkpoint checkpoint) {
        sp.hurtMarked = false;
        net.minecraft.world.phys.Vec3 beforeMotion = sp.getDeltaMovement();
        if (checkpoint instanceof PairedCheckpoint paired) {
            sp.setRemainingFireTicks(paired.server.remainingFireTicks);
            sp.invulnerableTime = paired.server.invulnerableTime;
            sp.hurtTime = paired.server.hurtTime;
            sp.fallDistance = paired.server.fallDistance;
            sp.setOnGround(paired.server.onGround);
            sp.verticalCollisionBelow = paired.server.verticalCollisionBelow;
            sp.setDeltaMovement(paired.server.velX, paired.server.velY, paired.server.velZ);
            sp.setSprinting(paired.server.sprinting);
            sp.setShiftKeyDown(paired.server.shiftKeyDown);
            if (paired.server.serverLastClientInput != null) {
                sp.setLastClientInput(paired.server.serverLastClientInput);
            }
        } else {
            sp.clearFire();
            sp.invulnerableTime = 0;
            sp.hurtTime = 0;
            sp.fallDistance = 0.0;
        }
        if (de.legoshi.parkourcalc.core.DebugFlags.PAIRED_DIAGNOSTICS) {
            System.out.println("[PC-NET] restart state fire=" + sp.getRemainingFireTicks()
                    + " invuln=" + sp.invulnerableTime
                    + " fallDistance=" + sp.fallDistance
                    + " serverMotion=" + sp.getDeltaMovement()
                    + " wasServerMotion=" + beforeMotion
                    + " paired=" + (checkpoint instanceof PairedCheckpoint));
        }
    }
}
