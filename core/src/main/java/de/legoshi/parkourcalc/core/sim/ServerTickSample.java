package de.legoshi.parkourcalc.core.sim;

public final class ServerTickSample {

    public final Vec3dCore velocity;
    public final Boolean jumpedOnPreviousMove;
    public final Vec3dCore packet;

    public ServerTickSample(Vec3dCore velocity, Boolean jumpedOnPreviousMove, Vec3dCore packet) {
        this.velocity = velocity != null ? velocity : Vec3dCore.ZERO;
        this.jumpedOnPreviousMove = jumpedOnPreviousMove;
        this.packet = packet;
    }

    public boolean hasJumpFlag() {
        return jumpedOnPreviousMove != null;
    }

    public boolean jumped() {
        return Boolean.TRUE.equals(jumpedOnPreviousMove);
    }
}
