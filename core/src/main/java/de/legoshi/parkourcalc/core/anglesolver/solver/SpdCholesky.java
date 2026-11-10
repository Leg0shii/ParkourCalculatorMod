package de.legoshi.parkourcalc.core.anglesolver.solver;

final class SpdCholesky {

    private SpdCholesky() {
    }

    static boolean factor(double[][] a, double[][] l, int n, double diagAdd) {
        for (int i = 0; i < n; i++) {
            double[] ai = a[i];
            double[] li = l[i];
            for (int j = 0; j <= i; j++) {
                double[] lj = l[j];
                double s = ai[j] + (i == j ? diagAdd : 0.0);
                for (int k = 0; k < j; k++) s -= li[k] * lj[k];
                if (i == j) {
                    if (s <= 0.0) return false;
                    li[i] = StrictMath.sqrt(s);
                } else {
                    li[j] = s / lj[j];
                }
            }
        }
        return true;
    }

    static void solveInPlace(double[][] l, double[] x, int n) {
        for (int i = 0; i < n; i++) {
            double[] li = l[i];
            double s = x[i];
            for (int k = 0; k < i; k++) s -= li[k] * x[k];
            x[i] = s / li[i];
        }
        for (int i = n - 1; i >= 0; i--) {
            double s = x[i];
            for (int k = i + 1; k < n; k++) s -= l[k][i] * x[k];
            x[i] = s / l[i][i];
        }
    }
}
