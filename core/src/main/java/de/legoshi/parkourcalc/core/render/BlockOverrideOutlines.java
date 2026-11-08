package de.legoshi.parkourcalc.core.render;

import de.legoshi.parkourcalc.core.BlockOverrideWorld;
import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.sim.BlockOverride;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import de.legoshi.parkourcalc.core.ui.Settings;

public final class BlockOverrideOutlines {

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
        return settings.viewBlockOverrides && world != null && world.isAvailable() && !world.rows().isEmpty();
    }

    public static void render(BoxRenderer lines, Settings settings, BlockOverrideWorld world) {
        if (!shouldRender(settings, world)) return;
        int row = world.viewRow();
        int placed = BoxStyle.blockOverridePlacedArgb(settings);
        int ahead = BoxStyle.blockOverrideAheadArgb(settings);
        for (BlockOverride b : world.rows()) {
            if (!b.spec().valid()) continue;
            if (b.activeAtRow(row)) {
                cube(lines, b, placed, false);
            } else if (b.aheadOfRow(row)) {
                cube(lines, b, ahead, true);
            }
        }
    }

    private static void cube(BoxRenderer lines, BlockOverride b, int argb, boolean dashed) {
        double x0 = b.x - INSET;
        double y0 = b.y - INSET;
        double z0 = b.z - INSET;
        double x1 = b.x + 1 + INSET;
        double y1 = b.y + 1 + INSET;
        double z1 = b.z + 1 + INSET;
        double[][] c = {
                {x0, y0, z0}, {x1, y0, z0}, {x0, y1, z0}, {x1, y1, z0},
                {x0, y0, z1}, {x1, y0, z1}, {x0, y1, z1}, {x1, y1, z1}
        };
        for (int[] e : EDGES) {
            double[] a = c[e[0]];
            double[] d = c[e[1]];
            if (!dashed) {
                lines.drawLine(a[0], a[1], a[2], d[0], d[1], d[2], argb);
                continue;
            }
            for (int i = 0; i < DASHES_PER_EDGE; i++) {
                double t0 = (double) i / DASHES_PER_EDGE;
                double t1 = t0 + DASH_FILL / DASHES_PER_EDGE;
                lines.drawLine(
                        a[0] + (d[0] - a[0]) * t0, a[1] + (d[1] - a[1]) * t0, a[2] + (d[2] - a[2]) * t0,
                        a[0] + (d[0] - a[0]) * t1, a[1] + (d[1] - a[1]) * t1, a[2] + (d[2] - a[2]) * t1,
                        argb);
            }
        }
    }
}
