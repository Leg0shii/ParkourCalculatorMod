package de.legoshi.parkourcalc.core.sim;

import java.util.Collections;
import java.util.List;

public final class TickState {

    public final Vec3dCore position;
    public final boolean onGround;
    public final boolean sneaking;
    public final boolean wallCollision;
    public final float yaw;
    public final List<Vec3dCore> subtickPath;

    public TickState(Vec3dCore position, boolean onGround, boolean sneaking, boolean wallCollision,
                     float yaw, List<Vec3dCore> subtickPath) {
        this.position = position;
        this.onGround = onGround;
        this.sneaking = sneaking;
        this.wallCollision = wallCollision;
        this.yaw = yaw;
        this.subtickPath = subtickPath != null ? subtickPath : Collections.<Vec3dCore>emptyList();
    }
}
