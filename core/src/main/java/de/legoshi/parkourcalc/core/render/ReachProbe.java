package de.legoshi.parkourcalc.core.render;

public interface ReachProbe {

    ReachProbe NONE = new ReachProbe() {
        @Override
        public double eyeHeight(boolean sneaking) {
            return 1.62;
        }

        @Override
        public double hitDistance(double originX, double originY, double originZ,
                                  double dirX, double dirY, double dirZ, double maxDistance) {
            return -1.0;
        }
    };

    double eyeHeight(boolean sneaking);

    double hitDistance(double originX, double originY, double originZ,
                       double dirX, double dirY, double dirZ, double maxDistance);
}
