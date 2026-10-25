package de.legoshi.parkourcalc.core.anglesolver.solver;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

public interface VelocityPacketCodec {

    double FIXED_POINT_SCALE = 8000.0;
    double FIXED_POINT_CLAMP = 3.9;

    double LP_ABS_MIN = 3.051944088384301E-5;
    double LP_ABS_MAX = 1.7179869183E10;
    double LP_MAX_QUANTIZED = 32766.0;

    VelocityPacketCodec FIXED_POINT = new VelocityPacketCodec() {
        @Override
        public Vec3dCore quantize(Vec3dCore v) {
            return new Vec3dCore(fixedPointAxis(v.x), fixedPointAxis(v.y), fixedPointAxis(v.z));
        }
    };

    VelocityPacketCodec LP_VEC3 = new VelocityPacketCodec() {
        @Override
        public Vec3dCore quantize(Vec3dCore v) {
            double x = lpSanitize(v.x);
            double y = lpSanitize(v.y);
            double z = lpSanitize(v.z);
            double chessboard = Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
            if (chessboard < LP_ABS_MIN) return Vec3dCore.ZERO;
            long scale = (long) Math.ceil(chessboard);
            return new Vec3dCore(lpAxis(x, scale), lpAxis(y, scale), lpAxis(z, scale));
        }
    };

    Vec3dCore quantize(Vec3dCore velocity);

    static double fixedPointAxis(double value) {
        double clamped = value < -FIXED_POINT_CLAMP ? -FIXED_POINT_CLAMP : (value > FIXED_POINT_CLAMP ? FIXED_POINT_CLAMP : value);
        return (double) (int) (clamped * FIXED_POINT_SCALE) / FIXED_POINT_SCALE;
    }

    static double lpSanitize(double value) {
        if (Double.isNaN(value)) return 0.0;
        return value < -LP_ABS_MAX ? -LP_ABS_MAX : (value > LP_ABS_MAX ? LP_ABS_MAX : value);
    }

    static double lpAxis(double value, long scale) {
        long bits = Math.round((value / scale * 0.5 + 0.5) * LP_MAX_QUANTIZED);
        return (Math.min(bits & 32767L, LP_MAX_QUANTIZED) * 2.0 / LP_MAX_QUANTIZED - 1.0) * scale;
    }

    static VelocityPacketCodec forMcVersion(String mcVersion) {
        return mcVersion != null && !mcVersion.startsWith("1.") ? LP_VEC3 : FIXED_POINT;
    }
}
