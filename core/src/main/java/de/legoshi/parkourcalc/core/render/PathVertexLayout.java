package de.legoshi.parkourcalc.core.render;

import de.legoshi.parkourcalc.core.ui.BoxController;

/** Vertex layout of the cached path buffers (faces: ticks, hitbox, arrows; lines: ticks, subtick). */
public final class PathVertexLayout {

    public static final int FACE_VERTS_PER_BOX = 36; // 12 triangles
    public static final int LINE_VERTS_PER_BOX = 24; // 12 edges
    public static final int THICK_EDGE_VERTS = 36;   // each hitbox edge is a filled box
    public static final int ARROW_VERTS_PER_BOX = 60; // 20 triangles (shaft + head)

    /** Cap per pass (~1 GiB at 16 B/vertex, under the int byte limit); the hitbox is dropped past it. */
    public static final long MAX_PASS_VERTICES = 64_000_000L;

    private PathVertexLayout() {
    }

    public static int faceMainOffset(int boxIndex) {
        return boxIndex * FACE_VERTS_PER_BOX;
    }

    public static int lineMainOffset(int boxIndex) {
        return boxIndex * LINE_VERTS_PER_BOX;
    }

    public static int hitboxRegionBase(int boxCount) {
        return boxCount * FACE_VERTS_PER_BOX;
    }

    /** edges per hitbox: 12 (full wireframe), 4 (floor outline), or 0 (hidden). */
    public static int hitboxEdges(boolean showHitbox, boolean showFullHitbox) {
        if (showFullHitbox) return 12;
        if (showHitbox) return 4;
        return 0;
    }

    /** Per-box hitbox vertex offsets relative to {@link #hitboxRegionBase}; starts[n] is the total. */
    public static int[] hitboxVertexStarts(BoxController boxController, int edges, boolean useSubtick) {
        int n = boxController.size();
        int[] starts = new int[n + 1];
        int acc = 0;
        for (int i = 0; i < n; i++) {
            starts[i] = acc;
            if (edges != 0) {
                acc += edges * THICK_EDGE_VERTS * boxController.hitboxWalkCount(i, useSubtick);
            }
        }
        starts[n] = acc;
        return starts;
    }
}
