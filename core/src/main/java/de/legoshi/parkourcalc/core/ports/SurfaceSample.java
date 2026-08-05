package de.legoshi.parkourcalc.core.ports;

import de.legoshi.parkourcalc.core.anglesolver.Medium;
import de.legoshi.parkourcalc.core.anglesolver.Slipperiness;

public final class SurfaceSample {

    public final Slipperiness groundSlipperiness;
    public final Medium medium;

    public SurfaceSample(Slipperiness groundSlipperiness, Medium medium) {
        this.groundSlipperiness = groundSlipperiness;
        this.medium = medium;
    }
}
