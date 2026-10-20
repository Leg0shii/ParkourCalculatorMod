#define SLOTS 36
#define PI_F 3.14159265358979f
#define TWO_PI_F 6.28318530717959f
#define REF 36
#define UNIT_STEPS 12
#define UNIT_REFINE 4
#define UNIT_LEVELS 2
#define UNIT_LEVELS_FINE 4
#define SWEEP_PASSES 3
#define THETA_LEVELS 4
#define POLISH_MAX_DEFICIT 0.08f
#define POLISH_MAX_TRIALS 20
#define THETA_FINE 2.0e-5f
#define RESTART_EVERY 6
#define WINDOW_CELLS 2
#define WINDOW_CELLS_MULTI 4
#define MAX_THETAS 9
#define COARSE_CUT 0.35f
#define GW (G * W)

inline int isRun(int c) {
    return c == 1 || c == 2 || c == 3;
}

inline int sprintOf(int t, int run, int v, int firstRun) {
    if (!run) return 0;
    if (v == 1 && t == firstRun) return 0;
    if (v == 2 && t < firstRun + 2) return 0;
    return 1;
}

inline int variantValid(int v, int firstRun) {
    if (v == 1) return firstRun >= 0;
    if (v == 2) return firstRun >= 0 && firstRun + 2 < N;
    return 1;
}

typedef struct {
    float A[GW];
    float B[GW];
    float oA[G];
    float oB[G];
    float cs[G];
    float sn[G];
    float shiftAcc[W];
    float shiftNoU[W];
    float shiftJ[W];
    float s0[W];
    int wk[W];
    float4 box;
} St;

inline float needsOf(const float* shift, const int* wk, float4 box, float4* out) {
    float lox = box.x, hix = box.y, loz = box.z, hiz = box.w;
    for (int w = 0; w < W; w++) {
        float s = shift[w];
        int k = wk[w];
        if (k == 0) hix = fmin(hix, s);
        else if (k == 1) hiz = fmin(hiz, s);
        else if (k == 2) lox = fmax(lox, s);
        else if (k == 3) loz = fmax(loz, s);
    }
    if (out) *out = (float4)(lox, hix, loz, hiz);
    return fmax(0.0f, fmax(lox - hix, loz - hiz));
}

inline float violAt(const float* shiftNoU, const float* Ag, const float* Bg, float c, float s, const int* wk, float4 box) {
    float lox = box.x, hix = box.y, loz = box.z, hiz = box.w;
    for (int w = 0; w < W; w++) {
        float sh = shiftNoU[w] - Ag[w] * c - Bg[w] * s;
        int k = wk[w];
        if (k == 0) hix = fmin(hix, sh);
        else if (k == 1) hiz = fmin(hiz, sh);
        else if (k == 2) lox = fmax(lox, sh);
        else if (k == 3) loz = fmax(loz, sh);
    }
    return fmax(0.0f, fmax(lox - hix, loz - hiz));
}

inline void recomputeShift(St* st) {
    for (int w = 0; w < W; w++) {
        float s = st->s0[w];
        for (int g = 0; g < G; g++) s -= st->A[g * W + w] * st->cs[g] + st->B[g * W + w] * st->sn[g];
        st->shiftAcc[w] = s;
    }
}

inline void jointCoarse(St* st) {
#if FREE == 2
    int g1 = FREE_G0;
    int g2 = FREE_G1;
    const float* A1 = st->A + g1 * W;
    const float* B1 = st->B + g1 * W;
    const float* A2 = st->A + g2 * W;
    const float* B2 = st->B + g2 * W;
    for (int w = 0; w < W; w++) {
        st->shiftNoU[w] = st->shiftAcc[w] + A1[w] * st->cs[g1] + B1[w] * st->sn[g1] + A2[w] * st->cs[g2] + B2[w] * st->sn[g2];
    }
    float bestV = needsOf(st->shiftAcc, st->wk, st->box, 0);
    int bi = -1;
    int bj = -1;
    for (int i = 0; i < UNIT_STEPS; i++) {
        float c1 = STEP_COS[i];
        float s1 = STEP_SIN[i];
        for (int w = 0; w < W; w++) st->shiftJ[w] = st->shiftNoU[w] - A1[w] * c1 - B1[w] * s1;
        for (int j = 0; j < UNIT_STEPS; j++) {
            float v = violAt(st->shiftJ, A2, B2, STEP_COS[j], STEP_SIN[j], st->wk, st->box);
            if (v < bestV) {
                bestV = v;
                bi = i;
                bj = j;
            }
        }
    }
    if (bi < 0) return;
    st->cs[g1] = STEP_COS[bi];
    st->sn[g1] = STEP_SIN[bi];
    st->cs[g2] = STEP_COS[bj];
    st->sn[g2] = STEP_SIN[bj];
    for (int w = 0; w < W; w++) {
        st->shiftAcc[w] = st->shiftNoU[w] - A1[w] * st->cs[g1] - B1[w] * st->sn[g1] - A2[w] * st->cs[g2] - B2[w] * st->sn[g2];
    }
#endif
}

inline int sweepOnce(St* st, int glob, int levels) {
    int moved = 0;
    for (int fi = 0; fi < FREE; fi++) {
        int g = FREE_LIST[fi];
        const float* Ag = st->A + g * W;
        const float* Bg = st->B + g * W;
        float cc = st->cs[g];
        float ss = st->sn[g];
        for (int w = 0; w < W; w++) st->shiftNoU[w] = st->shiftAcc[w] + Ag[w] * cc + Bg[w] * ss;
        float bestC = cc;
        float bestS = ss;
        float bestV = violAt(st->shiftNoU, Ag, Bg, cc, ss, st->wk, st->box);
        if (glob) {
            for (int kk = 0; kk < UNIT_STEPS; kk++) {
                float v = violAt(st->shiftNoU, Ag, Bg, STEP_COS[kk], STEP_SIN[kk], st->wk, st->box);
                if (v < bestV) {
                    bestV = v;
                    bestC = STEP_COS[kk];
                    bestS = STEP_SIN[kk];
                }
            }
        }
        int maxLevels = levels < UNIT_LEVELS_FINE ? levels : UNIT_LEVELS_FINE;
        for (int level = 0; level < maxLevels; level++) {
            float centerC = bestC;
            float centerS = bestS;
            for (int j = 0; j <= 2 * UNIT_REFINE; j++) {
                if (j == UNIT_REFINE) continue;
                float rc = REFINE_COS[level * (2 * UNIT_REFINE + 1) + j];
                float rs = REFINE_SIN[level * (2 * UNIT_REFINE + 1) + j];
                float c = centerC * rc - centerS * rs;
                float s = centerS * rc + centerC * rs;
                float v = violAt(st->shiftNoU, Ag, Bg, c, s, st->wk, st->box);
                if (v < bestV) {
                    bestV = v;
                    bestC = c;
                    bestS = s;
                }
            }
        }
        if (bestC != cc || bestS != ss) moved = 1;
        st->cs[g] = bestC;
        st->sn[g] = bestS;
        for (int w = 0; w < W; w++) st->shiftAcc[w] = st->shiftNoU[w] - Ag[w] * bestC - Bg[w] * bestS;
    }
    return moved;
}

inline float solveFrom(St* st, float theta, float startC, float startS, const float* fromCs, const float* fromSn,
                       int glob, int fine) {
    for (int g = 0; g < G; g++) {
        if (g == MAINGROUP) {
            st->cs[g] = cos(theta);
            st->sn[g] = sin(theta);
        } else if (fromCs) {
            st->cs[g] = fromCs[g];
            st->sn[g] = fromSn[g];
        } else {
            st->cs[g] = startC;
            st->sn[g] = startS;
        }
    }
    recomputeShift(st);
    int joint = glob && FREE == 2;
    if (joint) jointCoarse(st);
    int levels = fine ? UNIT_LEVELS_FINE : UNIT_LEVELS;
    for (int pass = 0; pass < SWEEP_PASSES; pass++) {
        if (!sweepOnce(st, glob && !joint, levels)) break;
    }
    return needsOf(st->shiftAcc, st->wk, st->box, 0);
}

inline void keepFacings(const St* st, float* cs, float* sn) {
    for (int g = 0; g < G; g++) {
        cs[g] = st->cs[g];
        sn[g] = st->sn[g];
    }
}

inline float polish(St* st, float theta, float step, float* bestCs, float* bestSn) {
    float v = solveFrom(st, theta, 0.0f, 0.0f, st->cs, st->sn, 1, 1);
    keepFacings(st, bestCs, bestSn);
    float maxStep = step;
    int trials = 0;
    while (step > THETA_FINE && trials < POLISH_MAX_TRIALS) {
        int moved = 0;
        for (int sign = -1; sign <= 1; sign += 2) {
            float cand = theta + sign * step;
            trials++;
            float cv = solveFrom(st, cand, 0.0f, 0.0f, bestCs, bestSn, 0, 1);
            if (cv < v) {
                v = cv;
                theta = cand;
                keepFacings(st, bestCs, bestSn);
                moved = 1;
                break;
            }
        }
        step = moved ? fmin(maxStep, step * 2.0f) : step * 0.5f;
    }
    solveFrom(st, theta, 0.0f, 0.0f, bestCs, bestSn, 0, 1);
    return theta;
}

inline void accumulate(__global const uchar* c, int v, int firstRun, __global const float* tab,
                       __global const float* coefW, __global const int* groupOf, __global const float* objCoef, St* st) {
    for (int i = 0; i < GW; i++) {
        st->A[i] = 0.0f;
        st->B[i] = 0.0f;
    }
    for (int g = 0; g < G; g++) {
        st->oA[g] = 0.0f;
        st->oB[g] = 0.0f;
    }
    for (int t = 0; t < N; t++) {
        int cc = c[t];
        int sp = sprintOf(t, isRun(cc), v, firstRun);
        int slot = cc * 4 + sp * 3;
        float gc = tab[(t * SLOTS + slot) * 3];
        float gs = tab[(t * SLOTS + slot) * 3 + 1];
        int g = groupOf[t];
        float* Ag = st->A + g * W;
        float* Bg = st->B + g * W;
        for (int w = 0; w < W; w++) {
            float cf = coefW[w * N + t];
            int k = st->wk[w];
            if (k == 0 || k == 2) {
                Ag[w] += cf * gc;
                Bg[w] += cf * gs;
            } else {
                Ag[w] -= cf * gs;
                Bg[w] += cf * gc;
            }
        }
        float oc = objCoef[t];
#if OBJ_AXIS_X
        st->oA[g] += oc * gc;
        st->oB[g] += oc * gs;
#else
        st->oA[g] -= oc * gs;
        st->oB[g] += oc * gc;
#endif
    }
}

inline void loadWalls(__global const int* wallAxisX, __global const int* wallKind, __global const float* shift0, St* st) {
    for (int w = 0; w < W; w++) {
        int k = wallKind[w];
        int ax = wallAxisX[w];
        st->wk[w] = k == 0 ? (ax ? 0 : 1) : k == 1 ? (ax ? 2 : 3) : 4;
        st->s0[w] = shift0[w];
    }
}

__kernel void disk(const int count, __global const uchar* combos, __global const float* tab, __global const float* coefW,
                   __global const float* shift0, __global const int* wallAxisX, __global const int* wallKind,
                   __global const int* groupOf, __global const float* params, __global const float* cosG,
                   __global const float* sinG, __global float* outViol, __global float* outTheta) {
    int gid = get_global_id(0);
    if (gid >= count * 3) return;
    int si = gid / 3;
    int v = gid - si * 3;
    __global const uchar* c = combos + (size_t) si * N;
    int firstRun = -1;
    for (int t = 0; t < N; t++) {
        if (isRun(c[t])) {
            firstRun = t;
            break;
        }
    }
    if (!variantValid(v, firstRun)) {
        outViol[gid] = INFINITY;
        outTheta[gid] = 0.0f;
        return;
    }
    float Am[W];
    float Bm[W];
    float Sl[W];
    int wk[W];
    for (int w = 0; w < W; w++) {
        Am[w] = 0.0f;
        Bm[w] = 0.0f;
        Sl[w] = 0.0f;
        int k = wallKind[w];
        int ax = wallAxisX[w];
        wk[w] = k == 0 ? (ax ? 0 : 1) : k == 1 ? (ax ? 2 : 3) : 4;
    }
    for (int t = 0; t < N; t++) {
        int cc = c[t];
        int sp = sprintOf(t, isRun(cc), v, firstRun);
        int slot = cc * 4 + sp * 3;
        float gc = tab[(t * SLOTS + slot) * 3];
        float gs = tab[(t * SLOTS + slot) * 3 + 1];
        float mg = tab[(t * SLOTS + slot) * 3 + 2];
        int main = groupOf[t] == MAINGROUP;
        for (int w = 0; w < W; w++) {
            float cf = coefW[w * N + t];
            if (main) {
                int k = wk[w];
                if (k == 0 || k == 2) {
                    Am[w] += cf * gc;
                    Bm[w] += cf * gs;
                } else {
                    Am[w] -= cf * gs;
                    Bm[w] += cf * gc;
                }
            } else {
                Sl[w] += fabs(cf) * mg;
            }
        }
    }
    float4 box = (float4)(params[0], params[1], params[2], params[3]);
    float earlyOut = params[7];
    float minV = INFINITY;
    int minC = -1;
    for (int pass = 0; pass < 3; pass++) {
        int lo, hi, stride, center;
        if (pass == 0) {
            lo = 0;
            hi = DISKGRID - 1;
            stride = CSTRIDE;
            center = 0;
        } else if (pass == 1) {
            if (minV > earlyOut) break;
            center = minC;
            lo = -CSTRIDE;
            hi = CSTRIDE;
            stride = MSTRIDE;
        } else {
            center = minC;
            lo = -MSTRIDE;
            hi = MSTRIDE;
            stride = 1;
        }
        for (int d = lo; d <= hi; d += stride) {
            int g = ((center + d) % DISKGRID + DISKGRID) % DISKGRID;
            float cg = cosG[g];
            float sg = sinG[g];
            float lox = box.x, hix = box.y, loz = box.z, hiz = box.w;
            for (int w = 0; w < W; w++) {
                float setupLin = Am[w] * cg + Bm[w] * sg;
                int k = wk[w];
                float s = shift0[w] - setupLin;
                if (k == 0) hix = fmin(hix, s + Sl[w]);
                else if (k == 1) hiz = fmin(hiz, s + Sl[w]);
                else if (k == 2) lox = fmax(lox, s - Sl[w]);
                else if (k == 3) loz = fmax(loz, s - Sl[w]);
            }
            float vv = fmax(lox - hix, loz - hiz);
            if (vv < minV || (vv == minV && g < minC)) {
                minV = vv;
                minC = g;
            }
        }
    }
    outViol[gid] = minV;
    outTheta[gid] = -PI_F + minC * (TWO_PI_F / DISKGRID);
}

__kernel void screen(const int m, __global const int* idx, __global const uchar* combos, __global const float* tab,
                     __global const float* coefW, __global const float* shift0, __global const int* wallAxisX,
                     __global const int* wallKind, __global const int* groupOf, __global const float* objCoef,
                     __global const float* params, __global const float* diskTheta, __global float* outViol,
                     __global float* outTheta, __global float* outObj) {
    int i = get_global_id(0);
    if (i >= m) return;
    int gid = idx[i];
    int si = gid / 3;
    int v = gid - si * 3;
    __global const uchar* c = combos + (size_t) si * N;
    int firstRun = -1;
    for (int t = 0; t < N; t++) {
        if (isRun(c[t])) {
            firstRun = t;
            break;
        }
    }
    St st;
    st.box = (float4)(params[0], params[1], params[2], params[3]);
    loadWalls(wallAxisX, wallKind, shift0, &st);
    accumulate(c, v, firstRun, tab, coefW, groupOf, objCoef, &st);

    float coarse = TWO_PI_F / REF;
    float refRad = diskTheta[gid];
    int cells = FREE > 1 ? WINDOW_CELLS_MULTI : WINDOW_CELLS;
    int count = 1 + 2 * cells;
    float thetas[MAX_THETAS];
    float coarseV[MAX_THETAS];
    float coarseCs[MAX_THETAS * G];
    float coarseSn[MAX_THETAS * G];
    int done[MAX_THETAS];
    thetas[0] = refRad;
    for (int w = 1; w <= cells; w++) {
        thetas[2 * w - 1] = refRad - w * coarse;
        thetas[2 * w] = refRad + w * coarse;
    }
    for (int k = 0; k < count; k++) {
        float theta = thetas[k];
        float best = INFINITY;
        int from = -1;
        float bestDist = INFINITY;
        for (int j = 0; j < k; j++) {
            float dist = fabs(thetas[j] - theta);
            if (dist < bestDist) {
                bestDist = dist;
                from = j;
            }
        }
        if (from < 0) {
            int restarts = FREE == 2 ? 1 : 4;
            for (int o = 0; o < restarts; o++) {
                float off = RESTART_OFF[o];
                float vv = solveFrom(&st, theta, cos(theta + off), sin(theta + off), 0, 0, 1, 0);
                if (vv < best) {
                    best = vv;
                    keepFacings(&st, coarseCs + k * G, coarseSn + k * G);
                }
            }
        } else {
            best = solveFrom(&st, theta, 0.0f, 0.0f, coarseCs + from * G, coarseSn + from * G, 0, 0);
            keepFacings(&st, coarseCs + k * G, coarseSn + k * G);
            if (k % RESTART_EVERY == 0) {
                float vv = solveFrom(&st, theta, cos(theta), sin(theta), 0, 0, 1, 0);
                if (vv < best) {
                    best = vv;
                    keepFacings(&st, coarseCs + k * G, coarseSn + k * G);
                }
            }
        }
        coarseV[k] = best;
        done[k] = 0;
    }

    float coarseBest = INFINITY;
    int coarseK = 0;
    for (int k = 0; k < count; k++) {
        if (coarseV[k] < coarseBest) {
            coarseBest = coarseV[k];
            coarseK = k;
        }
    }
    if (coarseBest > COARSE_CUT) {
        outViol[i] = coarseBest;
        float th0 = thetas[coarseK];
        while (th0 > PI_F) th0 -= TWO_PI_F;
        while (th0 < -PI_F) th0 += TWO_PI_F;
        outTheta[i] = th0;
        outObj[i] = params[4];
        return;
    }
    float bestAll = INFINITY;
    float bestTheta = refRad;
    int any = 0;
    float winCs[G];
    float winSn[G];
    float bestCs[G];
    float bestSn[G];
    for (int r = 0; r < 2; r++) {
        int bk = -1;
        float bv = INFINITY;
        for (int k = 0; k < count; k++) {
            if (!done[k] && coarseV[k] < bv) {
                bv = coarseV[k];
                bk = k;
            }
        }
        if (bk < 0) break;
        done[bk] = 1;
        float theta = thetas[bk];
        float vv = solveFrom(&st, theta, 0.0f, 0.0f, coarseCs + bk * G, coarseSn + bk * G, 1, 0);
        keepFacings(&st, bestCs, bestSn);
        float step = coarse * 0.5f;
        for (int level = 0; level < THETA_LEVELS; level++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                float cand = theta + sign * step;
                float cv = solveFrom(&st, cand, 0.0f, 0.0f, bestCs, bestSn, 0, 0);
                if (cv < vv) {
                    vv = cv;
                    theta = cand;
                    keepFacings(&st, bestCs, bestSn);
                    break;
                }
            }
            step *= 0.5f;
        }
        if (vv < bestAll) {
            bestAll = vv;
            bestTheta = theta;
            for (int g = 0; g < G; g++) {
                winCs[g] = bestCs[g];
                winSn[g] = bestSn[g];
            }
            any = 1;
        }
    }
    if (!any) {
        solveFrom(&st, bestTheta, cos(bestTheta), sin(bestTheta), 0, 0, 1, 0);
        keepFacings(&st, winCs, winSn);
    }
    for (int g = 0; g < G; g++) {
        st.cs[g] = winCs[g];
        st.sn[g] = winSn[g];
    }
    float viol;
    if (bestAll <= POLISH_MAX_DEFICIT) {
        bestTheta = polish(&st, bestTheta, coarse / 32.0f, bestCs, bestSn);
    } else {
        solveFrom(&st, bestTheta, 0.0f, 0.0f, winCs, winSn, 0, 0);
    }
    float4 needs;
    viol = needsOf(st.shiftAcc, st.wk, st.box, &needs);
    float dp;
#if MAXIMIZE
    dp = OBJ_AXIS_X ? needs.y : needs.w;
#else
    dp = OBJ_AXIS_X ? needs.x : needs.z;
#endif
    if (viol > 0.0f) dp = 0.0f;
    float obj = params[4];
    for (int g = 0; g < G; g++) obj += st.oA[g] * st.cs[g] + st.oB[g] * st.sn[g];
    obj += dp;
    outViol[i] = viol;
    float th = bestTheta;
    while (th > PI_F) th -= TWO_PI_F;
    while (th < -PI_F) th += TWO_PI_F;
    outTheta[i] = th;
    outObj[i] = obj;
}
