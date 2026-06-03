package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.List;

/** Static debug flags. Flip in code to enable verbose state dumps. */
public final class DebugFlags {

    public static boolean DUMP_TICK_STATE = false;
    public static boolean COMPARE_PARTIAL_SIM = false;

    public static List<String> simTickSink = null;

    private static final double EPS_POS = 1.0e-9;
    private static final float EPS_YAW = 1.0e-6f;

    private DebugFlags() {}

    public static void compareAndLog(List<TickState> partial, List<TickState> full, int dirtyTick) {
        if (partial.size() != full.size()) {
            System.out.println("[PC-DIFF] dirtyTick=" + dirtyTick + " size mismatch: partial=" + partial.size() + " full=" + full.size());
            return;
        }
        for (int i = 0; i < partial.size(); i++) {
            TickState p = partial.get(i);
            TickState f = full.get(i);
            StringBuilder diff = new StringBuilder();
            appendVecDiff(diff, "pos", p.position, f.position);
            appendFloatDiff(diff, "yaw", p.yaw, f.yaw);
            appendBoolDiff(diff, "onGround", p.onGround, f.onGround);
            appendBoolDiff(diff, "sneaking", p.sneaking, f.sneaking);
            appendBoolDiff(diff, "wallCollision", p.wallCollision, f.wallCollision);
            appendVecDiff(diff, "vel", p.velocity, f.velocity);
            appendBoolDiff(diff, "softCollision", p.softCollision, f.softCollision);
            appendDoubleDiff(diff, "collisionAngle", p.collisionAngleDegrees, f.collisionAngleDegrees);
            if (diff.length() > 0) {
                System.out.println("[PC-DIFF] dirtyTick=" + dirtyTick + " tick=" + i + diff);
                return;
            }
        }
        System.out.println("[PC-DIFF] dirtyTick=" + dirtyTick + " size=" + partial.size() + " OK (no divergence)");
    }

    private static void appendVecDiff(StringBuilder sb, String name, Vec3dCore a, Vec3dCore b) {
        if (Math.abs(a.x - b.x) > EPS_POS || Math.abs(a.y - b.y) > EPS_POS || Math.abs(a.z - b.z) > EPS_POS) {
            sb.append(' ').append(name).append('=').append(a).append(" vs ").append(b);
        }
    }

    private static void appendFloatDiff(StringBuilder sb, String name, float a, float b) {
        if (Math.abs(a - b) > EPS_YAW) {
            sb.append(' ').append(name).append('=').append(a).append(" vs ").append(b);
        }
    }

    private static void appendDoubleDiff(StringBuilder sb, String name, double a, double b) {
        boolean aNan = Double.isNaN(a);
        boolean bNan = Double.isNaN(b);
        if (aNan != bNan || (!aNan && Math.abs(a - b) > EPS_POS)) {
            sb.append(' ').append(name).append('=').append(a).append(" vs ").append(b);
        }
    }

    private static void appendBoolDiff(StringBuilder sb, String name, boolean a, boolean b) {
        if (a != b) {
            sb.append(' ').append(name).append('=').append(a).append(" vs ").append(b);
        }
    }
}
