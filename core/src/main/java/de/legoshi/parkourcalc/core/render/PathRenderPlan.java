package de.legoshi.parkourcalc.core.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;
import de.legoshi.parkourcalc.core.ui.BoxColorPicker;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import de.legoshi.parkourcalc.core.ui.SelectionManager;
import de.legoshi.parkourcalc.core.ui.Settings;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Loader-agnostic per-frame inputs for the cached geometry (hash, selection, bake emitters, patch spec).
 *
 * <p><b>Constraint plates (gh-145).</b> When {@code showConstraints} is on, the face/line emitters append
 * a constraint region after the existing geometry (faces: main|hitbox|arrows|<b>constraints</b>; lines:
 * main|subtick|<b>constraints</b>). The region bakes into the same buffers the loaders already build, with
 * no change to the per-box offsets selection patching depends on. Plates vary in vertex count (a pad is
 * one box; other constraints add a front box and a back box), so
 * {@link #constraintFaceVerts}/{@link #constraintLineVerts} are counted by replaying the exact constraint
 * emit through a {@link CountingBoxRenderer} ({@code BoxController.constraintFaceVertexCount}/
 * {@code constraintLineVertexCount}). Each loader draws the region as the trailing slice
 * {@code [faceTotal - constraintFaceVerts, faceTotal)} (and the line equivalent); the region is
 * camera-run-independent, so it is drawn once, not per run.
 */
public final class PathRenderPlan {

    private static final double ALL = Double.POSITIVE_INFINITY;

    /**
     * Constraint plates to overlay (gh-145), statically wired so the loaders' existing
     * {@code build(boxController, settings, selection)} call needs no new argument (mirrors
     * {@link de.legoshi.parkourcalc.core.anglesolver.ConstraintText}'s static wiring). The owner
     * (Application) sets this once; defaults to {@link ConstraintBoxSource#NONE} for headless/tests.
     */
    private static volatile ConstraintBoxSource constraintSource = ConstraintBoxSource.NONE;

    public static void setConstraintSource(ConstraintBoxSource source) {
        constraintSource = source != null ? source : ConstraintBoxSource.NONE;
    }

    private static ConstraintBoxSource countedSource;
    private static long countedRevision = Long.MIN_VALUE;
    private static long countedGeometryRev = Long.MIN_VALUE;
    private static int countedFaceVerts;
    private static int countedLineVerts;

    public final int structuralHash;
    public final Set<Integer> selection;
    public final Consumer<BoxRenderer> faceEmitter;
    public final Consumer<BoxRenderer> lineEmitter;
    public final SelectionPatchSpec patch;
    public final int constraintFaceVerts;
    public final int constraintLineVerts;

    private PathRenderPlan(int structuralHash, Set<Integer> selection, Consumer<BoxRenderer> faceEmitter, Consumer<BoxRenderer> lineEmitter, SelectionPatchSpec patch, int constraintFaceVerts, int constraintLineVerts) {
        this.structuralHash = structuralHash;
        this.selection = selection;
        this.faceEmitter = faceEmitter;
        this.lineEmitter = lineEmitter;
        this.patch = patch;
        this.constraintFaceVerts = constraintFaceVerts;
        this.constraintLineVerts = constraintLineVerts;
    }

    public static PathRenderPlan build(BoxController boxController, Settings settings, SelectionManager selection) {
        Set<Integer> selectedBoxes = selection.getSelectedBoxes();
        BoxColorPicker face = (i, s) -> BoxStyle.tickFaceArgb(settings, s, selectedBoxes.contains(i));
        BoxColorPicker line = (i, s) -> BoxStyle.tickLineArgb(settings, s, selectedBoxes.contains(i));
        BoxColorPicker hitbox = (i, s) -> BoxStyle.hitboxLineArgb(settings, selectedBoxes.contains(i));

        // Drop the hitbox (not the path) when it would overflow a buffer, e.g. full hitbox + subtick at huge counts.
        int edges = PathVertexLayout.hitboxEdges(settings.showHitbox, settings.showFullHitbox);
        boolean drawHitbox = edges != 0 && boxController.facePassFitsBudget(edges, settings.showSubtick, settings.showYawArrows);
        boolean full = drawHitbox && settings.showFullHitbox;
        boolean floor = drawHitbox && !settings.showFullHitbox;

        // Constraint plates ride a region appended after the path/hitbox/arrow geometry, so they bake
        // into the same buffers without disturbing the per-box offsets selection patching depends on.
        ConstraintBoxSource source = constraintSource;
        boolean drawConstraints = settings.showConstraints && source != ConstraintBoxSource.NONE;
        ConstraintPalette palette = new ConstraintPalette(
                BoxStyle.constraintSatisfiedArgb(settings),
                BoxStyle.constraintViolatedArgb(),
                BoxStyle.constraintFillArgb(settings),
                BoxStyle.constraintBackArgb(settings),
                BoxStyle.constraintHighlightArgb(settings));

        Consumer<BoxRenderer> faceEmitter = faces -> {
            boxController.render(faces, face, 0, 0, 0, ALL);
            if (floor) boxController.renderHitboxFloorOutline(faces, hitbox, settings.showSubtick, 0, 0, 0, ALL);
            if (full) boxController.renderHitboxFullWireframe(faces, hitbox, settings.showSubtick, 0, 0, 0, ALL);
            if (settings.showYawArrows) boxController.renderYawArrows(faces, BoxStyle.yawArrowArgb(settings), 0, 0, 0, ALL);
            if (drawConstraints) boxController.renderConstraints(faces, source, palette, false, 0, 0, 0, ALL);
        };

        Consumer<BoxRenderer> lineEmitter = lines -> {
            boxController.render(lines, line, 0, 0, 0, ALL);
            if (settings.showSubtick) boxController.renderPath(lines, BoxStyle.subtickPathArgb(settings), 0, 0, 0, ALL);
            if (drawConstraints) boxController.renderConstraints(lines, source, palette, true, 0, 0, 0, ALL);
        };

        SelectionPatchSpec patch = new SelectionPatchSpec(face, line, hitbox, drawHitbox, full, settings.showSubtick);

        long constraintRevision = drawConstraints ? source.revision() : 0L;
        int constraintFaceVerts = 0;
        int constraintLineVerts = 0;
        if (drawConstraints) {
            long geometryRev = boxController.getGeometryRev();
            if (source != countedSource || constraintRevision != countedRevision || geometryRev != countedGeometryRev) {
                countedFaceVerts = boxController.constraintFaceVertexCount(source, palette);
                countedLineVerts = boxController.constraintLineVertexCount(source, palette);
                countedSource = source;
                countedRevision = constraintRevision;
                countedGeometryRev = geometryRev;
            }
            constraintFaceVerts = countedFaceVerts;
            constraintLineVerts = countedLineVerts;
        }
        return new PathRenderPlan(structuralHash(settings, constraintRevision),
                selectedBoxes, faceEmitter, lineEmitter, patch,
                constraintFaceVerts, constraintLineVerts);
    }

    /** Colors and overlay toggles, but NOT selection (which is patched in place). The constraint
     *  revision is mixed in so editing constraints (which leaves path positions untouched) still
     *  invalidates the cached buffers. */
    private static int structuralHash(Settings settings, long constraintRevision) {
        int h = 1;
        h = 31 * h + Arrays.hashCode(settings.tickDefault);
        h = 31 * h + Arrays.hashCode(settings.tickSelected);
        h = 31 * h + Arrays.hashCode(settings.tickSoftCollision);
        h = 31 * h + Arrays.hashCode(settings.tickWall);
        h = 31 * h + Arrays.hashCode(settings.tickAir);
        h = 31 * h + Arrays.hashCode(settings.tickSneak);
        h = 31 * h + Arrays.hashCode(settings.hitboxDefault);
        h = 31 * h + Arrays.hashCode(settings.hitboxSelected);
        h = 31 * h + Arrays.hashCode(settings.yawArrow);
        h = 31 * h + Arrays.hashCode(settings.subtickPath);
        h = 31 * h + Arrays.hashCode(settings.constraintOutline);
        h = 31 * h + Arrays.hashCode(settings.constraintFill);
        h = 31 * h + Arrays.hashCode(settings.constraintBack);
        h = 31 * h + Arrays.hashCode(settings.constraintHighlight);
        h = 31 * h + (settings.showHitbox ? 1 : 0);
        h = 31 * h + (settings.showFullHitbox ? 2 : 0);
        h = 31 * h + (settings.showYawArrows ? 4 : 0);
        h = 31 * h + (settings.showSubtick ? 8 : 0);
        h = 31 * h + (settings.showConstraints ? 16 : 0);
        h = 31 * h + (settings.constraintExpandByHitbox ? 32 : 0);
        h = 31 * h + Float.hashCode(settings.constraintFrontWidth);
        h = 31 * h + Float.hashCode(settings.constraintFrontHeight);
        h = 31 * h + Float.hashCode(settings.constraintFrontLength);
        h = 31 * h + Float.hashCode(settings.constraintBackWidth);
        h = 31 * h + Float.hashCode(settings.constraintBackHeight);
        h = 31 * h + Float.hashCode(settings.constraintBackLength);
        h = 31 * h + Long.hashCode(constraintRevision);
        return h;
    }
}
