package de.legoshi.parkourcalc.core.render;

public final class ConstraintStyle {

    public final boolean expandByHitbox;
    public final double frontWidth;
    public final double frontHeight;
    public final double frontLength;
    public final double backWidth;
    public final double backHeight;
    public final double backLength;

    public ConstraintStyle(boolean expandByHitbox,
                           double frontWidth, double frontHeight, double frontLength,
                           double backWidth, double backHeight, double backLength) {
        this.expandByHitbox = expandByHitbox;
        this.frontWidth = frontWidth;
        this.frontHeight = frontHeight;
        this.frontLength = frontLength;
        this.backWidth = backWidth;
        this.backHeight = backHeight;
        this.backLength = backLength;
    }
}
