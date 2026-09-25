package de.legoshi.parkourcalc.core.multireplay;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

public final class MultiReplayGeometry {

    static final int SLICES = 8;
    static final int STACKS = 4;
    public static final int TRIANGLES_PER_SPHERE = SLICES * (2 * STACKS - 2);
    public static final int VERTS_PER_SPHERE = TRIANGLES_PER_SPHERE * 3;
    public static final double UPCOMING_RADIUS_SCALE = 0.6;

    private static final double[] SIN_THETA = new double[SLICES + 1];
    private static final double[] COS_THETA = new double[SLICES + 1];
    private static final double[] SIN_PHI = new double[STACKS + 1];
    private static final double[] COS_PHI = new double[STACKS + 1];
    private static final double LIGHT_X;
    private static final double LIGHT_Y;
    private static final double LIGHT_Z;
    private static final int UPCOMING_ALPHA = 0x60;

    static {
        for (int i = 0; i <= SLICES; i++) {
            double theta = 2.0 * Math.PI * i / SLICES;
            SIN_THETA[i] = Math.sin(theta);
            COS_THETA[i] = Math.cos(theta);
        }
        for (int i = 0; i <= STACKS; i++) {
            double phi = Math.PI * i / STACKS;
            SIN_PHI[i] = Math.sin(phi);
            COS_PHI[i] = Math.cos(phi);
        }
        double lx = 0.35, ly = 0.85, lz = 0.4;
        double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
        LIGHT_X = lx / len;
        LIGHT_Y = ly / len;
        LIGHT_Z = lz / len;
    }

    private MultiReplayGeometry() {}

    public static void renderSpheres(MultiReplay state, BoxRenderer faces) {
        int tick = state.clock().tick();
        double radius = state.sphereRadius();
        for (ReplayTrack track : state.tracks()) {
            if (!track.isVisible() || track.size() == 0) continue;
            int reached = Math.min(tick, track.lastTick());
            for (int i = 0; i <= reached; i++) {
                sphere(faces, track.position(i), radius, track.argb);
            }
            if (state.isShowUpcoming()) {
                int dim = withAlpha(track.argb, UPCOMING_ALPHA);
                for (int i = reached + 1; i < track.size(); i++) {
                    sphere(faces, track.position(i), radius * UPCOMING_RADIUS_SCALE, dim);
                }
            }
        }
    }

    public static void emitAllSpheres(MultiReplay state, BoxRenderer faces, boolean upcomingStyle) {
        double radius = upcomingStyle ? state.sphereRadius() * UPCOMING_RADIUS_SCALE : state.sphereRadius();
        for (ReplayTrack track : state.tracks()) {
            int argb = upcomingStyle ? withAlpha(track.argb, UPCOMING_ALPHA) : track.argb;
            for (int i = 0; i < track.size(); i++) {
                sphere(faces, track.position(i), radius, argb);
            }
        }
    }

    public static int totalSpheres(MultiReplay state) {
        int n = 0;
        for (ReplayTrack track : state.tracks()) n += track.size();
        return n;
    }

    public static void renderLines(MultiReplay state, BoxRenderer lines) {
        int tick = state.clock().tick();
        double frac = state.clock().fraction();
        for (ReplayTrack track : state.tracks()) {
            if (!track.isVisible() || track.size() < 2) continue;
            int reached = Math.min(tick, track.lastTick());
            for (int j = 0; j < reached; j++) {
                Vec3dCore a = track.position(j);
                Vec3dCore b = track.position(j + 1);
                lines.drawLine(a.x, a.y, a.z, b.x, b.y, b.z, track.argb);
            }
            if (reached < track.lastTick() && frac > 0) {
                Vec3dCore a = track.position(reached);
                Vec3dCore b = track.position(reached + 1);
                lines.drawLine(a.x, a.y, a.z,
                        a.x + (b.x - a.x) * frac, a.y + (b.y - a.y) * frac, a.z + (b.z - a.z) * frac,
                        track.argb);
            }
        }
    }

    public static void sphere(BoxRenderer faces, Vec3dCore c, double r, int argb) {
        for (int st = 0; st < STACKS; st++) {
            for (int sl = 0; sl < SLICES; sl++) {
                double n00x = SIN_PHI[st] * COS_THETA[sl], n00y = COS_PHI[st], n00z = SIN_PHI[st] * SIN_THETA[sl];
                double n01x = SIN_PHI[st] * COS_THETA[sl + 1], n01y = COS_PHI[st], n01z = SIN_PHI[st] * SIN_THETA[sl + 1];
                double n10x = SIN_PHI[st + 1] * COS_THETA[sl], n10y = COS_PHI[st + 1], n10z = SIN_PHI[st + 1] * SIN_THETA[sl];
                double n11x = SIN_PHI[st + 1] * COS_THETA[sl + 1], n11y = COS_PHI[st + 1], n11z = SIN_PHI[st + 1] * SIN_THETA[sl + 1];
                if (st < STACKS - 1) {
                    int color = shade(argb, n00x + n10x + n11x, n00y + n10y + n11y, n00z + n10z + n11z);
                    faces.drawTriangle(
                            c.x + r * n00x, c.y + r * n00y, c.z + r * n00z,
                            c.x + r * n10x, c.y + r * n10y, c.z + r * n10z,
                            c.x + r * n11x, c.y + r * n11y, c.z + r * n11z, color);
                }
                if (st > 0) {
                    int color = shade(argb, n00x + n11x + n01x, n00y + n11y + n01y, n00z + n11z + n01z);
                    faces.drawTriangle(
                            c.x + r * n00x, c.y + r * n00y, c.z + r * n00z,
                            c.x + r * n11x, c.y + r * n11y, c.z + r * n11z,
                            c.x + r * n01x, c.y + r * n01y, c.z + r * n01z, color);
                }
            }
        }
    }

    static int shade(int argb, double nx, double ny, double nz) {
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        double dot = len > 0 ? (nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z) / len : 0;
        double k = 0.55 + 0.45 * Math.max(0.0, dot);
        int r = (int) Math.round(((argb >>> 16) & 0xFF) * k);
        int g = (int) Math.round(((argb >>> 8) & 0xFF) * k);
        int b = (int) Math.round((argb & 0xFF) * k);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    public static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
