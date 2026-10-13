package de.legoshi.parkourcalc.core.anglesolver.solver;

public final class PhysicsVariant {

    public static final PhysicsVariant VANILLA = new PhysicsVariant(null, null, true, null);

    public final Double inertiaThreshold;
    public final Boolean perAxisInertia;
    public final boolean strafeBoost;
    public final String source;

    public PhysicsVariant(Double inertiaThreshold, Boolean perAxisInertia, boolean strafeBoost, String source) {
        this.inertiaThreshold = inertiaThreshold;
        this.perAxisInertia = perAxisInertia;
        this.strafeBoost = strafeBoost;
        this.source = source;
    }

    public boolean isVanilla() {
        return inertiaThreshold == null && perAxisInertia == null && strafeBoost;
    }

    public String describe() {
        if (isVanilla()) return "vanilla physics";
        StringBuilder sb = new StringBuilder(source == null ? "modded physics" : source);
        sb.append(": inertia ");
        if (inertiaThreshold == null) {
            sb.append("vanilla");
        } else if (inertiaThreshold == 0.0) {
            sb.append("off");
        } else {
            sb.append("threshold ").append(inertiaThreshold);
            if (perAxisInertia != null) sb.append(perAxisInertia ? " per axis" : " combined");
        }
        sb.append(", 45 strafe ").append(strafeBoost ? "on" : "off");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PhysicsVariant)) return false;
        PhysicsVariant v = (PhysicsVariant) o;
        return strafeBoost == v.strafeBoost
                && (inertiaThreshold == null ? v.inertiaThreshold == null : inertiaThreshold.equals(v.inertiaThreshold))
                && (perAxisInertia == null ? v.perAxisInertia == null : perAxisInertia.equals(v.perAxisInertia))
                && (source == null ? v.source == null : source.equals(v.source));
    }

    @Override
    public int hashCode() {
        int h = strafeBoost ? 1 : 0;
        h = 31 * h + (inertiaThreshold == null ? 0 : inertiaThreshold.hashCode());
        h = 31 * h + (perAxisInertia == null ? 0 : perAxisInertia.hashCode());
        h = 31 * h + (source == null ? 0 : source.hashCode());
        return h;
    }

    @Override
    public String toString() {
        return describe();
    }
}
