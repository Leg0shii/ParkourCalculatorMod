package de.legoshi.parkourcalc.core.anglesolver.solver;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

public interface VelocityPacketCodec {

    double FIXED_POINT_SCALE = 8000.0;
    double FIXED_POINT_CLAMP = 3.9;

    VelocityPacketCodec FIXED_POINT = new VelocityPacketCodec() {
        @Override
        public Vec3dCore quantize(Vec3dCore v) {
            return new Vec3dCore(fixedPointAxis(v.x), fixedPointAxis(v.y), fixedPointAxis(v.z));
        }
    };

    Vec3dCore quantize(Vec3dCore velocity);

    static double fixedPointAxis(double value) {
        double clamped = value < -FIXED_POINT_CLAMP ? -FIXED_POINT_CLAMP : (value > FIXED_POINT_CLAMP ? FIXED_POINT_CLAMP : value);
        return (double) (int) (clamped * FIXED_POINT_SCALE) / FIXED_POINT_SCALE;
    }
}
