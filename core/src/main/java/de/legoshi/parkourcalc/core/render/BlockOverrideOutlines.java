package de.legoshi.parkourcalc.core.render;

import de.legoshi.parkourcalc.core.BlockOverrideWorld;
import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.BlockOverride;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import de.legoshi.parkourcalc.core.ui.Settings;

import java.util.List;

public final class BlockOverrideOutlines {

    public static final double PREVIEW_SCALE = 1.75;
    private static final double INSET = 0.002;
    private static final int DASHES_PER_EDGE = 4;
    private static final double DASH_FILL = 0.55;

    private static final int[][] EDGES = {
            {0, 1}, {1, 3}, {3, 2}, {2, 0},
            {4, 5}, {5, 7}, {7, 6}, {6, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    private BlockOverrideOutlines() {
    }

    public static boolean shouldRender(Settings settings, BlockOverrideWorld world) {
        if (world == null || !world.isAvailable()) return false;
        if (world.isReplaying() && !settings.blockOverrideOutlinesDuringReplay) return false;
        return world.preview() != null || (settings.viewBlockOverrides && !world.rows().isEmpty());
    }

    public static void render(BoxRenderer faces, Settings settings, BlockOverrideWorld world) {
        if (!shouldRender(settings, world)) return;
        double thickness = BoxStyle.blockOverrideOutlineWidth(settings);
        int[] preview = world.preview();
        if (preview != null) {
            cube(faces, preview[0], preview[1], preview[2], world.previewArgb(), false, thickness * PREVIEW_SCALE);
        }
        if (!settings.viewBlockOverrides) return;
        int row = world.viewRow();
        int placed = BoxStyle.blockOverridePlacedArgb(settings);
        int ahead = BoxStyle.blockOverrideAheadArgb(settings);
        int selected = BoxStyle.blockOverrideSelectedArgb(settings);
        BlockOverride chosen = world.selected();
        for (BlockOverride b : world.rows()) {
            if (!b.spec().valid()) continue;
            boolean active = b.activeAtRow(row);
            int argb = b == chosen ? selected : active ? placed : ahead;
            shape(faces, world.shape(b.block), b.x, b.y, b.z, argb, !active, thickness);
        }
    }

    private static void shape(BoxRenderer faces, List<AABB> boxes, int bx, int by, int bz, int argb, boolean dashed, double thickness) {
        for (AABB box : boxes) {
            edges(faces, bx + box.min.x, by + box.min.y, bz + box.min.z, bx + box.max.x, by + box.max.y, bz + box.max.z,
                    argb, dashed, thickness);
        }
    }

    private static void cube(BoxRenderer faces, int bx, int by, int bz, int argb, boolean dashed, double thickness) {
        edges(faces, bx, by, bz, bx + 1, by + 1, bz + 1, argb, dashed, thickness);
    }

    private static void edges(BoxRenderer faces, double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                              int argb, boolean dashed, double thickness) {
        double x0 = minX - INSET;
        double y0 = minY - INSET;
        double z0 = minZ - INSET;
        double x1 = maxX + INSET;
        double y1 = maxY + INSET;
        double z1 = maxZ + INSET;
        double[][] c = {
                {x0, y0, z0}, {x1, y0, z0}, {x0, y1, z0}, {x1, y1, z0},
                {x0, y0, z1}, {x1, y0, z1}, {x0, y1, z1}, {x1, y1, z1}
        };
        for (int[] e : EDGES) {
            double[] a = c[e[0]];
            double[] d = c[e[1]];
            if (!dashed) {
                segment(faces, a, d, 0, 1, argb, thickness);
                continue;
            }
            for (int i = 0; i < DASHES_PER_EDGE; i++) {
                double t0 = (double) i / DASHES_PER_EDGE;
                segment(faces, a, d, t0, t0 + DASH_FILL / DASHES_PER_EDGE, argb, thickness);
            }
        }
    }

    private static void segment(BoxRenderer faces, double[] a, double[] d, double t0, double t1, int argb, double thickness) {
        double h = thickness * 0.5;
        double[] p = new double[3];
        double[] q = new double[3];
        for (int k = 0; k < 3; k++) {
            boolean along = a[k] != d[k];
            double lo = along ? a[k] + (d[k] - a[k]) * t0 : a[k] - h;
            double hi = along ? a[k] + (d[k] - a[k]) * t1 : a[k] + h;
            p[k] = Math.min(lo, hi);
            q[k] = Math.max(lo, hi);
        }
        faces.drawBox(new AABB(new Vec3dCore(p[0], p[1], p[2]), new Vec3dCore(q[0], q[1], q[2])), argb);
    }
}
