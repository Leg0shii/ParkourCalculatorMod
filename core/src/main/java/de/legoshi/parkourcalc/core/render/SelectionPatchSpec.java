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

    public SelectionPatchSpec(BoxColorPicker facePicker, BoxColorPicker linePicker, BoxColorPicker hitboxPicker,
                              boolean showHitbox, boolean showFullHitbox, boolean showSubtick) {
        this.facePicker = facePicker;
        this.linePicker = linePicker;
        this.hitboxPicker = hitboxPicker;
        this.showHitbox = showHitbox;
        this.showFullHitbox = showFullHitbox;
        this.showSubtick = showSubtick;
    }

    public int hitboxEdges() {
        return PathVertexLayout.hitboxEdges(showHitbox, showFullHitbox);
    }
}
