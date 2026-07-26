package de.legoshi.parkourcalc.core.render;

import de.legoshi.parkourcalc.core.ui.BoxColorPicker;

/** Pickers and hitbox flags the cached geometry uses to recolor individual boxes in place on selection change. */
public final class SelectionPatchSpec {

    public final BoxColorPicker facePicker;
    public final BoxColorPicker linePicker;
    public final BoxColorPicker hitboxPicker;

    public final boolean showHitbox;
    public final boolean showFullHitbox;
    public final boolean showSubtick;
    public final int arrowsPerBox;
    public final boolean drawYawArrows;
    public final boolean drawCombinedArrows;
    public final int yawArrowArgb;
    public final int combinedArrowArgb;

    public SelectionPatchSpec(BoxColorPicker facePicker, BoxColorPicker linePicker, BoxColorPicker hitboxPicker, boolean showHitbox, boolean showFullHitbox, boolean showSubtick, int arrowsPerBox,
                              boolean drawYawArrows, boolean drawCombinedArrows, int yawArrowArgb, int combinedArrowArgb) {
        this.facePicker = facePicker;
        this.linePicker = linePicker;
        this.hitboxPicker = hitboxPicker;
        this.showHitbox = showHitbox;
        this.showFullHitbox = showFullHitbox;
        this.showSubtick = showSubtick;
        this.arrowsPerBox = arrowsPerBox;
        this.drawYawArrows = drawYawArrows;
        this.drawCombinedArrows = drawCombinedArrows;
        this.yawArrowArgb = yawArrowArgb;
        this.combinedArrowArgb = combinedArrowArgb;
    }

    public int hitboxEdges() {
        return PathVertexLayout.hitboxEdges(showHitbox, showFullHitbox);
    }
}
