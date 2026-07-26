# Sheepram optimizer: port-ready algorithm specification

Extracted 2026-07-08 from the Odin source at C:\Users\benja\Desktop\Coding\06 C++\Sheepram (all quotes re-verified against source line ranges). This is the normative reference for the AlmBfgsCore and SnapRepairPolish ports; where this document and any summary (including nix-solver-handoff.md section 2) disagree, THIS document follows the source and wins. Section 9 lists the known discrepancies.

Files under src/optimizer/: optimizer.odin, discrete.odin, exact_sim.odin, trig.odin, comp_expr.odin, raw_expr.odin, util.odin, trig_test.odin. Phase-1 driver and seeds live in src/app/solve.odin.

Odin semantics that matter for the port: `int(f32)` truncates toward zero (not round); `&` is bitwise-and; `do X` is a single-statement body; module-level `NAME :: value` is a compile-time constant.

## 1. ALM outer loop

File optimizer.odin, proc optimize_from_seed, lines 377-444 (outer loop 400-428).

- Penalty (rho) initial value: line 394, `pen := 1.0`.
- Multipliers initialized to 0: lines 390-392 (Odin zero-inits).
- Outer iteration cap: line 399, `max_outer :: 25`.
- Multiplier update (exact code), lines 409-418:

```odin
for i in 0..<len(problem.ineq_cons) {
    gi := eval(problem.ineq_cons[i], thetas[:], &work)
    lamb[i] = max(0.0, lamb[i]+pen*gi)
    max_gi = max(max_gi, max(0.0, gi))
}
for j in 0..<len(problem.eq_cons) {
    hj := eval(problem.eq_cons[j], thetas[:], &work)
    nu[j] += pen*hj
    max_hj = max(max_hj, math.abs(hj))
}
max_vio = max(max_gi, max_hj)
```

  Inequality multiplier: `lamb[i] = max(0, lamb[i] + pen*g_i)`. Equality multiplier: `nu[j] += pen*h_j` (no clamp).
- Feasibility tolerance: `ACCEPT_TOL :: 1e-5` (line 6). Checked at line 422: `if max_vio < ACCEPT_TOL do break`. The violation measure max_vio is max over all max(0,g_i) and all |h_j| (lines 412/417/419).
- rho update rule and stall trigger, lines 424-427:

```odin
if max_vio > 0.5 * prev_max_vio do pen *= 2
prev_max_vio = max_vio
```

  "Violation stalls" = the new max violation did not drop below HALF the previous outer iteration's violation; then pen doubles.
- Stopping conditions: (a) max_vio < ACCEPT_TOL breaks (line 422); (b) otherwise the loop runs to max_outer = 25. No other early stop. Each outer iteration first runs bfgs(...) (line 402) to convergence on the augmented Lagrangian, then updates multipliers/penalty.
- After the loop the solution is written (lines 431-441): objective and positions re-evaluated at the final thetas.

Augmented Lagrangian value+gradient used by the inner loop: compute_aug_l, optimizer.odin lines 124-158. Inequalities (Hestenes-Powell-Rockafellar form), lines 138-146:

```odin
t := max(0.0, lamb[i]+v_ineq*pen)
value += 0.5/pen*(t*t-lamb[i]*lamb[i])
add_scaled(g_out, work.temp_g[:], t)
```

Equalities, lines 148-156:

```odin
value += nu[j]*v_eq
value += 0.5*pen*v_eq*v_eq
add_scaled(g_out, work.temp_g[:], nu[j]+pen*v_eq)
```

## 2. BFGS inner loop

File optimizer.odin, proc bfgs, lines 269-338.

- Iteration cap: line 288, `max_inner :: 80`.
- Inverse-Hessian init: dense n x n identity, lines 277-279. matrix_* helpers are dense (util.odin 3-54).
- Convergence/exit criterion: gradient-norm-squared threshold, lines 287 and 291:

```odin
tar_grad :: 1e-6
...
if dot(grad_vec[:], grad_vec[:]) < tar_grad*tar_grad do break
```

  Exit when ||g||^2 < (1e-6)^2. No step-size stop; no explicit H restart.
- Search direction + descent guard, lines 293-301:

```odin
step := matrix_mul(&h, grad_vec[:])
scale_vector(step[:], -1)
deri := dot(grad_vec[:], step[:])
if deri >= 0 {
    set_scaled(step[:], grad_vec[:], -1)
    deri = dot(grad_vec[:], step[:])
}
```

  If the quasi-Newton direction is not a descent direction (g.p >= 0), fall back to steepest descent. H is NOT reset to identity.
- Gradient computation: analytic. compute_aug_l (line 285 initial, line 308 per step) calls grad(...) from comp_expr.odin. grad, comp_expr.odin lines 94-101:

```odin
out[i] = expr.theta_coeff[i] +
         expr.sin_coeff[i]*work.cos_cache[i] -
         expr.cos_coeff[i]*work.sin_cache[i]
```

  i.e. df/dtheta_i = a_i + b_i*cos(theta_i) - d_i*sin(theta_i), from compiled coefficients plus cached sin/cos (update_trig_cache, comp_expr.odin 55-60). No finite differences.
- Curvature (s.y) skip rule, lines 308-324:

```odin
val_new := compute_aug_l(grad_new[:], thetas, problem, lamb, nu, pen, work)
curv := make([dynamic]f64, n)
for i in 0..<n do curv[i] = grad_new[i]-grad_vec[i]
a := dot(step[:], curv[:])
ss := dot(step[:], step[:])
cc := dot(curv[:], curv[:])
eps :: 1e-12
if a*a <= (eps*eps)*ss*cc {
    copy(grad_vec[:], grad_new[:]); val = val_new
    delete(curv); delete(step); continue
}
```

  The H update is SKIPPED (H kept) when (s.y)^2 <= eps^2*(s.s)(y.y), i.e. near-orthogonal s,y. Note: a large NEGATIVE s.y passes this guard (see section 9 item 2); port the code, not the comment.
- BFGS inverse-Hessian update (exact code), lines 326-330:

```odin
a = 1/a
step_approx := matrix_mul(&h, curv[:])
matrix_add_symmetrical_outer(&h, step[:], step_approx[:], -a)
b := a*(1+a*dot(step_approx[:], curv[:]))
matrix_add_outer_product(&h, step[:], step[:], b)
```

  With a = 1/(s.y) this is the standard BFGS inverse update H+ = H - a(s(Hy)^T + (Hy)s^T) + a(1 + a*y^T H y) s s^T. matrix_add_symmetrical_outer adds s*(a_i b_j + a_j b_i) (util.odin 48-54); matrix_add_outer_product adds s*a_i b_j (40-46).
- s = step after scale_vector(step, alpha) (line 304), so s = alpha*p; thetas updated by add_scaled(thetas, step, 1) (line 306).

## 3. Line search

File optimizer.odin: line_search (215-267), line_search_zoom (189-212), line_search_phi (174-187). Header comment (line 214): strong Wolfe, weaker version of scipy scalar_search_wolfe2.

- Constants: `c1 :: 1e-4`, `c2 :: 0.9` (lines 190-191 and 242-243).
- Initial step: alpha = 1.0 (line 241); base = 0.0 (line 240).
- Bracketing loop, `max_bracket_iter :: 20` (line 246), lines 247-265:

```odin
val_alpha := line_search_phi(&ctx, alpha, ctx.temp_grad[:])
if val_alpha > val+c1*alpha*deri do return line_search_zoom(&ctx, base, alpha)
if base > 0 && val_alpha >= val_prev do return line_search_zoom(&ctx, base, alpha)
deri_alpha := dot(ctx.temp_grad[:], step)
if math.abs(deri_alpha) <= -c2*deri do return alpha
if deri_alpha >= 0 do return line_search_zoom(&ctx, base, alpha)
val_prev = val_alpha; base = alpha; alpha *= 2
```

- Zoom (binary bisection only, no polynomial interpolation), `max_zoom_iter :: 20` (line 194), lines 195-211:

```odin
mid := 0.5*(lo+hi)
val_mid := line_search_phi(ctx, mid, ctx.temp_grad[:])
if val_mid > ctx.val+c1*mid*ctx.deri || val_mid >= val_lo {
    hi = mid
} else {
    deri_mid := dot(ctx.temp_grad[:], ctx.step)
    if math.abs(deri_mid) <= -c2*ctx.deri do return mid
    lo = mid; val_lo = val_mid
}
```

- Max evals: <= 20 bracket steps plus <= 20 zoom bisections; each phi eval computes value+gradient via compute_aug_l.
- On failure the returned alpha is NEVER forced to 0: bracket exhaustion returns the last doubled alpha (line 266); zoom exhaustion returns 0.5*(lo+hi) (line 211).

## 4. Compiled expression form and constraint/objective encoding

Compiled_Expr (comp_expr.odin 5-10), per-tick coefficient vectors over the movement angle theta:

```odin
Compiled_Expr :: struct { constant: f64, theta_coeff, sin_coeff, cos_coeff: [dynamic]f64 }
```

Represents f(theta) = constant + sum_i theta_coeff[i]*theta_i + sum_i sin_coeff[i]*sin(theta_i) + sum_i cos_coeff[i]*cos(theta_i). eval (72-80) and grad (94-101) are the value/gradient.

Raw_Expr (raw_expr.odin 5-11), the user-facing expression over positions and facings (degrees):

```odin
Raw_Expr :: struct { constant: f64, x_coeff, z_coeff, f_coeff: [dynamic]f64 }
```

combine_raw_expr (97-144) enforces linearity: `*` allowed only if one side is constant; `/` only by a nonzero constant.

Model compilation (positions to per-tick (theta,sin,cos) coefficients): compile_model, optimizer.odin 85-122. Velocity recurrence (104-112):

```odin
model.vx[0].sin_coeff[0] = model.accel[0]
model.vz[0].cos_coeff[0] = model.accel[0]
add_scaled_expr(&model.vx[t], model.vx[t-1], model.drag_x[t-1]); model.vx[t].sin_coeff[t] = model.accel[t]
add_scaled_expr(&model.vz[t], model.vz[t-1], model.drag_z[t-1]); model.vz[t].cos_coeff[t] = model.accel[t]
```

Position accumulation pos[0]=0, pos[t]=pos[t-1]+v[t-1], lines 116-121.

Reduction Raw to Compiled (reduce_expr, raw_expr.odin 221-243): substitutes compiled X[t], Z[t] for x_coeff/z_coeff and converts facing degrees to movement radians:

```odin
if expr.x_coeff[t] != 0 do add_scaled_expr(&out, model.x[t], expr.x_coeff[t])
if expr.z_coeff[t] != 0 do add_scaled_expr(&out, model.z[t], expr.z_coeff[t])
out.theta_coeff[t] += expr.f_coeff[t] * 180.0 / math.PI
out.constant -= expr.f_coeff[t] * angle_offset[t]
```

So facing_deg = theta*180/pi - angle_offset_deg; angle_offset is a per-tick fixed strafe/geometry offset relating player facing to movement direction.

Constraint encoding: Raw_Constraint (raw_expr.odin 20-25) is lhs cmp {Less|Equal} meaning lhs < 0 or lhs = 0; make_raw_problem (179-198) sorts into ineq_cons/eq_cons; reduce_problem (200-219) reduces to the compiled Problem. All constraints canonicalized to g(theta) <= 0 / h(theta) = 0 (ALM treats max(0,g) and |h|).

Translation invariance: not done by the optimizer; the raw problem is authored as differences (X[m] - X[0] > 7/16, objective X[n] - X[mm]). Positions are anchored X[0]=Z[0]=0 in both models (compile_model leaves x[0]/z[0] all zero; exact_simulation sets xs[0]=zs[0]=0, exact_sim.odin 105-106).

## 5. Discrete local search

File discrete.odin, proc local_search, lines 225-577. Constants block 176-182:

```odin
MAX_ROUND_CANDIDATES :: 32
MAX_2_OPT_ATTEMPTS :: 4096
FAST_ERR :: 5e-7
WORSE_ACCEPT_THRESHOLD :: 256
MAX_DROP :: ACCEPT_TOL
MAX_DOWN_HILLS :: 128
CANCEL_CHECK_SEC :: 0.25
```

(MAX_2_OPT_ATTEMPTS is dead code, see section 9 item 6. MAX_DROP = ACCEPT_TOL = 1e-5.)

### Snap (continuous angle to u16 bucket index), lines 251-255

```odin
for i in 0..<ilen {
    t := i+1
    facing := sol.thetas[t] - model.angle_offset[t]
    trial.indices[i] = index(f32(facing))
}
```

index (trig.odin 31-34): `int(rad * INDEX_PER_RAD) & SINE_TABLE_MASK` with INDEX_PER_RAD = 10430.378, SINE_TABLE_MASK = 0xffff. This is TRUNCATION toward zero, not round-to-nearest (matches MC `(int)(f*10430.378f)&65535`). Only ticks 1..n-2 are search variables: discrete_angle_len = n-2 (lines 103-106); tick 0 uses init_theta (continuous), the last facing is inert.

### Modes and initial grade, lines 248-297

Starts in Repair (line 248). Grades the snapped state with the FAST grader (grading, line 278). If already fast-feasible in Repair, it exact-checks; if exact-feasible it switches to Polish and adopts the exact grade (286-293). best is seeded from current; has_best = (mode == Polish).

### 1-opt phase (greedy +-1), lines 329-417

Round loop. Each round scans EVERY search tick t in [0,ilen) and both signs delta in {+1,-1} (loops 343-345), applying a single-bucket offset via incremental backtrack (offset_index, 349-350), grading with the FAST grader (352-353). Tracked per neighbor:
- improveQ best single neighbor -> local_current / local_improved (355-359), the Repair fallback.
- good_candQ promising neighbors inserted into a top-K = 32 list kept sorted best to worst (insert_one_opt_cand, 632-658; cap MAX_ROUND_CANDIDATES).

Acceptance, lines 377-416:

```odin
if len(cands) > 0 {
    for c in cands {
        ...offset trial by c...
        exact_grading(&exact_grade, model, exact_p, trial, &exact_work)
        if !exact_grade.feasible { continue }
        if mode == .Polish && !improveQ(&exact_grade, &current.grade, mode) { continue }
        accept = true; copy_discrete_state(&current.state, trial); current.grade = exact_grade
        mode = .Polish; break
    }
}
if !accept && mode == .Repair && local_improved {
    copy_discrete_cand(&current, local_current); accept = true
}
if !accept do break
```

Repair accepts the first exact-feasible top-K candidate (any objective); if none, falls back to the best fast-improving (violation-reducing) neighbor. Polish accepts the first top-K candidate that is exact-feasible AND objective-improving. Any accepted exact-feasible move flips mode to Polish. The round loop ends when a round accepts nothing; then 2-opt.

good_candQ gate (608-629): candidate must have violation_sqr <= viosqr_tol(p) where viosqr_tol = max(1, #constraints)*FAST_ERR^2 (603-606); in Polish it must also beat the champion objective by margin FAST_ERR (Regular) or MAX_DROP = 1e-5 (Cooking). improveQ (660-676): Repair prefers feasible, then lower violation_sqr, then lower objective; Polish requires feasible and lower objective.

### 2-opt phase, lines 419-566

Exact delta set TWO_OPT_DELTAS, lines 439-445, all four sign combinations of magnitude pairs (1,1),(1,2),(1,3),(2,1),(3,1) = 20 deltas:

```odin
{ 1, 1},{ 1,-1},{-1, 1},{-1,-1},
{ 1, 2},{ 1,-2},{-1, 2},{-1,-2},
{ 1, 3},{ 1,-3},{-1, 3},{-1,-3},
{ 2, 1},{ 2,-1},{-2, 1},{-2,-1},
{ 3, 1},{ 3,-1},{-3, 1},{-3,-1},
```

If ilen < 2, 2-opt is skipped (447-450). pair_count = ilen*(ilen-1)/2; pairs = ranks 0..pair_count (create_pair_orders, 205-211; get_pair maps rank to ordered tick pair, 190-203).

Round loop (462-566). Per round:
- Regular: max_attempts = pair_count; Fisher-Yates shuffle of pairs (475-481); each pair tried once via get_pair(pairs[attempts-1], ilen) (500).
- Cooking: max_attempts = 512 * model.n (line 473; model.n = FULL tick count, not ilen); pairs sampled WITH replacement (490-498).

For each pair all 20 deltas are tried (506): fast-graded (511-512); Repair tracks the best fast-improving pair move (local_pair_improved, 514-518); a good_candQ neighbor is exact-checked (520-521). Acceptance, lines 523-553:

```odin
if !exact_grade.feasible do continue
exact_improved := improveQ(&exact_grade, &current.grade, mode)
accept_worse := false
if !exact_improved {
    if search_mode != .Cooking do continue
    if mode != .Polish do continue
    if attempts < WORSE_ACCEPT_THRESHOLD do continue
    if down_hills >= MAX_DOWN_HILLS do continue
    if exact_grade.objective >= current.grade.objective + MAX_DROP do continue
    accept_worse = true
}
copy_discrete_state(&current.state, trial); current.grade = exact_grade; mode = .Polish; accept = true
if exact_improved { ...update best... }
if accept_worse { down_hills += 1 }
break
```

Repair fallback identical to 1-opt (556-559). A round exits early on the first accept (561). The outer 2-opt loop ends when a whole round produces no accept (565).

Cooking exact thresholds (constants plus 526-534):
- Random pairs per round: 512 * model.n.
- Worse moves allowed only after attempts >= WORSE_ACCEPT_THRESHOLD = 256 (attempts resets each round at 469), only in Polish, only in Cooking.
- Global worse-move budget: down_hills < MAX_DOWN_HILLS = 128; down_hills is declared ONCE at line 460 outside the round loop, so it is a whole-phase budget, not per-round.
- Per worse move the objective may increase by strictly less than MAX_DROP = 1e-5 (reject when new_obj >= current_obj + MAX_DROP), and the move must be exact-feasible.

Termination: 1-opt ends when a round accepts nothing, then 2-opt; 2-opt ends when a round accepts nothing (or cancelled). best is returned (568-576); best is only updated with exact-feasible Polish improvements (295, 452-455, 541-546, 568-573).

## 6. Fast vs exact grader

Fast grader: grading, discrete.odin 579-601. Evaluates the compiled discrete expression in f64 at LUT-quantized angles: eval_discrete_expr (discrete.odin 132-157) uses index_to_radians(index)+angle_offset for the theta term and the sin/cos cache built by update_discrete_trig_cache (trig.odin 64-85), which uses the f32 LUT values sin_index/cos_index (promoted to f64) combined with the analytic offset rotation. Feasibility standards (585-600): inequalities, violation = max(0,value), infeasible if violation > FAST_ERR = 5e-7; equalities, violation = abs(value), infeasible if violation > ACCEPT_TOL = 1e-5; in Repair mode any violation_sqr > 0 marks infeasible.

Exact grader: exact_grading, exact_sim.odin 149-181. Runs exact_simulation (94-147), evaluates Raw_Expr on the simulated positions (eval_raw_expr, raw_expr.odin 147-177). Feasibility standards (166-180): inequalities STRICT (violation > 0 infeasible); equalities tol ACCEPT_TOL = 1e-5.

Arithmetic nuance: positions AND velocities accumulate in f64; only the per-tick movement deltas (forward, strafe, sin_value, cos_value, sprint-jump 0.2) are f32, then promoted to f64 (exact_sim.odin 65, 82-83, 135-145). A mixed-precision replica of MC, not a pure-f32 sim.

Claimed accuracy (TECHNICAL.md line 393): error per expression between fast and exact simulation tested to be e-7 level; that is the rationale for FAST_ERR = 5e-7 as the fast-feasibility slack.

## 7. Verified: no inertia gate; sprint delay manual

Whole-repo search for 0.005|inertia|threshold|momentum over src/:
- exact_sim.odin has NO inertia/momentum-threshold gate anywhere. get_exact_movement (32-92) computes drag/accel/forward/strafe with no 0.005/0.003 velocity-zeroing gate; exact_simulation (94-147) applies velocity deltas unconditionally.
- The only 0.005 hits are the sine-table facing quantization step (trig.odin:96 `step :: 0.005` in compute_index_facing, and trig_test.odin:9); app/gui.odin:1051 uses *0.005 for display rounding.
- `inertia` hits are Mothball DSL op names only (ForceInertiaX/ForceInertiaZ), user-driven ops, not a physics gate.

Sprint-delay comment verbatim, exact_sim.odin line 51:

```odin
// Sheepram intentional has user to do sprint delay manually
```

Sprint boost applied immediately when sprint is set (lines 52-58, accel *= 1+f64(f32(0.3)) grounded / accel += accel*0.3 airborne); no automatic 1-tick lag.

## 8. Multistart / seeding

Phase-1 seed list, src/app/solve.odin 224-229 (scan mode):

```odin
sample_count := clamp(state.continuous_initial_angle_samples, 8, 256)
seeds := make([dynamic]f64, sample_count)
for i in 0..<sample_count {
    seeds[i] = 2 * math.PI * f64(i) / f64(sample_count)
}
solution^, best_seed_index = opt.optimize_best_of(&model, &problem, seeds[:])
```

Uniform full-circle sweep 2*pi*i/N, N in [8,256]; each seed sets ALL ticks to that one constant angle (optimize_from_seed line 389). Non-scan default: single seed pi/4 (optimize, optimizer.odin 470-472).

optimize_best_of, optimizer.odin 446-468: runs optimize_from_seed per seed, keeps the best by solution_better (356-363): feasible beats infeasible (feasibility = violation < ACCEPT_TOL); among feasible lower objective; among infeasible lower violation then lower objective.

Phase-2 multistart, src/app/solve.odin 261-266 and 278-348: Regular = 1 start; Cooking = clamp(chefs, 1, 1000) independent local_search runs, best kept by feasibility then objective then violation (308-317). RNG inside local_search is Xoshiro256 (discrete.odin 267-268) used for the 2-opt shuffle (Regular) and random pair sampling (Cooking), seeded from default zero state each call.

## 9. Discrepancies vs earlier summaries (the port follows THIS list)

1. "exact grader = f32 sim" is imprecise: positions and velocities accumulate in f64; only per-tick movement deltas are f32 promoted to f64. Port the mixed precision.
2. The BFGS curvature guard does NOT reject negative curvature despite its comment; the test is (s.y)^2 <= eps^2*(s.s)(y.y) (near-orthogonality only). A large negative s.y passes and yields a = 1/(s.y) < 0. Port the code, not the comment.
3. Cooking 512*n uses model.n = TOTAL tick count, not the search length ilen = n-2.
4. MAX_DOWN_HILLS = 128 is a whole-2-opt-phase budget (declared outside the round loop, never reset), not per-round.
5. Worse-move bound: reject when new_obj >= current_obj + MAX_DROP (1e-5), i.e. accepted worse moves increase the minimized objective by strictly less than 1e-5.
6. MAX_2_OPT_ATTEMPTS :: 4096 is dead code, never referenced; do not port it.
7. Snap is truncation toward zero, MC-style (int)(f*10430.378f)&65535, not round-to-nearest.
8. Multistart seeds are a uniform full-circle sweep 2*pi*i/N with N in [8,256] (default single seed pi/4), each seed constant across all ticks.
9. Fast-feasibility ineq tolerance is FAST_ERR = 5e-7, and Repair additionally rejects any violation_sqr > 0. The good-candidate gate uses viosqr_tol = max(1,#constraints)*FAST_ERR^2.
10. Line-search failure returns a nonzero alpha (last doubled alpha on bracket exhaustion; midpoint on zoom exhaustion), never 0. A non-descent BFGS direction triggers a steepest-descent fallback without resetting H.

## Key source files (absolute paths)

- C:\Users\benja\Desktop\Coding\06 C++\Sheepram\src\optimizer\optimizer.odin (ALM, BFGS, line search, compile_model, optimize_best_of)
- C:\Users\benja\Desktop\Coding\06 C++\Sheepram\src\optimizer\discrete.odin (local search, 1-opt/2-opt, grading, Cooking)
- C:\Users\benja\Desktop\Coding\06 C++\Sheepram\src\optimizer\exact_sim.odin (exact sim, no inertia gate, sprint-delay comment)
- C:\Users\benja\Desktop\Coding\06 C++\Sheepram\src\optimizer\trig.odin (sine LUT, snap index, index/facing conversion)
- C:\Users\benja\Desktop\Coding\06 C++\Sheepram\src\optimizer\comp_expr.odin (Compiled_Expr, eval/grad)
- C:\Users\benja\Desktop\Coding\06 C++\Sheepram\src\optimizer\raw_expr.odin (Raw_Expr, reduce_expr, linearity enforcement)
- C:\Users\benja\Desktop\Coding\06 C++\Sheepram\src\optimizer\util.odin (dense matrix helpers)
- C:\Users\benja\Desktop\Coding\06 C++\Sheepram\src\app\solve.odin (phase wiring, seed sweep, Regular/Cooking selection)
