package de.legoshi.parkourcalc.core.render;

public final class ConstraintPalette {

    private final int satisfiedOutline;
    private final int violatedOutline;
    private final int frontFill;
    private final int backFill;
    private final int highlight;

    public ConstraintPalette(int satisfiedOutline, int violatedOutline, int frontFill, int backFill, int highlight) {
        this.satisfiedOutline = satisfiedOutline;
        this.violatedOutline = violatedOutline;
        this.frontFill = frontFill;
        this.backFill = backFill;
        this.highlight = highlight;
    }

    public int outlineArgb(boolean satisfied) {
        return satisfied ? satisfiedOutline : violatedOutline;
    }

    public int highlightArgb() {
        return highlight;
    }

    public int frontArgb() {
        return frontFill;
    }

    public int backArgb() {
        return backFill;
    }
}
