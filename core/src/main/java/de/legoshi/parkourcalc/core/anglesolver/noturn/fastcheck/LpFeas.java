package de.legoshi.parkourcalc.core.anglesolver.noturn.fastcheck;

public final class LpFeas {

    private final int nVar;
    private final double[] lo;
    private final double[] hi;
    private final double[][] rowsA;
    private final double[] rowsB;
    private int rowCount;

    public LpFeas(int nVar, int maxRows) {
        this.nVar = nVar;
        this.lo = new double[nVar];
        this.hi = new double[nVar];
        this.rowsA = new double[maxRows][];
        this.rowsB = new double[maxRows];
        this.rowCount = 0;
    }

    public void setBound(int v, double l, double h) {
        lo[v] = l;
        hi[v] = h;
    }

    public void addRow(double[] a, double b) {
        rowsA[rowCount] = a;
        rowsB[rowCount] = b;
        rowCount++;
    }

    public boolean feasible(double feasTol) {
        int n = nVar;
        int mUb = 0;
        for (int v = 0; v < n; v++) if (hi[v] < Double.POSITIVE_INFINITY) mUb++;
        int m = rowCount + mUb;
        double[][] A = new double[m][n];
        double[] rhs = new double[m];
        int r = 0;
        for (int i = 0; i < rowCount; i++) {
            double[] a = rowsA[i];
            double b = rowsB[i];
            for (int v = 0; v < n; v++) {
                double c = a[v];
                A[r][v] = c;
                b -= c * lo[v];
            }
            rhs[r] = b;
            r++;
        }
        for (int v = 0; v < n; v++) {
            if (hi[v] < Double.POSITIVE_INFINITY) {
                A[r][v] = 1.0;
                rhs[r] = hi[v] - lo[v];
                r++;
            }
        }
        return phase1(A, rhs, n, m) <= feasTol;
    }

    private static double phase1(double[][] A, double[] rhs, int n, int m) {
        int nart = 0;
        for (int i = 0; i < m; i++) if (rhs[i] < 0) nart++;
        int slackBase = n;
        int artBase = n + m;
        int ncol = n + m + nart;
        double[][] T = new double[m][ncol + 1];
        int[] basis = new int[m];
        int ai = 0;
        for (int i = 0; i < m; i++) {
            double sgn = rhs[i] < 0 ? -1.0 : 1.0;
            for (int v = 0; v < n; v++) T[i][v] = sgn * A[i][v];
            T[i][slackBase + i] = sgn;
            T[i][ncol] = sgn * rhs[i];
            if (rhs[i] < 0) {
                int col = artBase + ai;
                T[i][col] = 1.0;
                basis[i] = col;
                ai++;
            } else {
                basis[i] = slackBase + i;
            }
        }
        double[] cost = new double[ncol + 1];
        for (int c = artBase; c < ncol; c++) cost[c] = 1.0;
        double[] red = new double[ncol + 1];
        for (int c = 0; c <= ncol; c++) {
            double s = cost[c];
            for (int i = 0; i < m; i++) s -= cost[basis[i]] * T[i][c];
            red[c] = s;
        }
        int iter = 0;
        int maxIter = 50 * (ncol + m) + 200;
        double eps = 1e-9;
        boolean converged = false;
        while (iter++ < maxIter) {
            int enter = -1;
            for (int c = 0; c < ncol; c++) {
                if (red[c] < -eps) {
                    enter = c;
                    break;
                }
            }
            if (enter < 0) {
                converged = true;
                break;
            }
            int leave = -1;
            double best = Double.POSITIVE_INFINITY;
            for (int i = 0; i < m; i++) {
                double a = T[i][enter];
                if (a > eps) {
                    double ratio = T[i][ncol] / a;
                    if (ratio < best - 1e-12 || (ratio < best + 1e-12 && (leave < 0 || basis[i] < basis[leave]))) {
                        best = ratio;
                        leave = i;
                    }
                }
            }
            if (leave < 0) return 0.0;
            double piv = T[leave][enter];
            for (int c = 0; c <= ncol; c++) T[leave][c] /= piv;
            for (int i = 0; i < m; i++) {
                if (i == leave) continue;
                double f = T[i][enter];
                if (f > eps || f < -eps) {
                    for (int c = 0; c <= ncol; c++) T[i][c] -= f * T[leave][c];
                }
            }
            double f = red[enter];
            if (f > eps || f < -eps) {
                for (int c = 0; c <= ncol; c++) red[c] -= f * T[leave][c];
            }
            basis[leave] = enter;
        }
        if (!converged) return 0.0;
        double obj = 0.0;
        for (int i = 0; i < m; i++) obj += cost[basis[i]] * T[i][ncol];
        return obj;
    }
}
