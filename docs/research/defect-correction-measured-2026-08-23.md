# Defect-correction polish vs the margin ladder: measured result 2026-08-23

Follow-up to `smooth-tas-handoff-2026-08-23.md` section 8 (the "dual plus inertia, nothing else"
target). Section 8 predicted the whole margin ladder collapses into one dual solve at margin 0 plus a
Newton defect-correction polish, on the belief the margins absorb linear-model error. This session built
that polish and measured it. **The prediction is wrong. The margin ladder is not a bandaid; it is
load-bearing regularization of the degenerate margin-0 recovery, and no single-margin-plus-polish comes
close to replacing it.** Do not re-attempt the section 8 architecture.

## What was built

`DefectCorrectionPolish` (core solver package). Residual of every X/Z/F constraint from the byte-exact
`ExactJumpModel.forward` via `JumpConstraintCompiler.evaluate`; analytic Jacobian from `JumpLinearModel`
(`d(addX)/dy = -addZ`, `d(addZ)/dy = addX`, chained through `coefAxis`; F walls gradient +/-1). Newton
over the `FacingPrefold` group variables, least-norm step on the active walls, damped line search on the
exact model, stop when a step no longer reduces the exact violation (the sine-bucket floor). Wired into
`ClosedFormSolve.runLadder` at the margin-0 rung, behind `ClosedFormSolve.POLISH` (default off,
`-Dpkc.cf.polish=1` to enable). A/B harness: `HpkCertifyScreen` (`PKC_SCREENS=1`).

## The numbers (58 hpk captures, ascending certified count)

Polish as an add-on to the full ladder, then the ladder cut to a single margin:

| margin set | no polish | + polish |
| --- | --- | --- |
| full ladder `{0 .. 1e-2}` | 25 | 26 |
| `{0.0}` only | 1 | 6 |
| `{1e-4}` | 10 | 10 |
| `{3e-4}` | 9 | 9 |
| `{1e-3}` | 9 | 9 |
| `{3e-3}` | 7 | 7 |
| `{5e-3}` | 3 | 3 |
| `{1e-2}` | 2 | 2 |

Robust certified: full ladder 20 without polish, 24 with. Zero regressions from the polish in every
configuration (no capture that certified without it stopped certifying with it). `:core:test -PslowTests`
`ProblemsTest` green with the polish on; fast suite green with it off.

## What the numbers say

1. **Cutting the ladder to margin 0 collapses certification 25 to 6, polish and all.** That is the
   section 8 proposal measured directly, and it fails hard.
2. **No single margin replaces the ladder.** The best single rung (`1e-4`) reaches 10 of 25. Each capture
   certifies at its *own* margin; the ladder's power is trying all of them. There is no universal margin,
   with or without the polish.
3. **The polish only helps at margin 0** (1 to 6). At every nonzero single margin the polish adds nothing
   (off equals on). This is the tell for the real mechanism below.

## Why the ladder is load-bearing, not a bandaid

Section 4 already had the key fact: at the optimum `c = A^T lambda`, so the costate `g = c - A^T lambda`
vanishes (measured `~1e-7` on 49 of 49 ticks), and `atan2` of a vanishing vector is noise. A positive
inward margin moves `lambda` off that exact stationary face, so the costate is `O(margin)` rather than
`~1e-7`, and the recovered direction is a real direction rather than noise. **The margin is what makes the
recovery non-degenerate.** That is regularization, not error absorption, and it explains every row of the
table: the polish helps only at margin 0 because that is the only rung whose seed is degenerate enough for
a Newton nudge to matter; at nonzero rungs the recovery is already well-posed and the question is purely
whether that particular margin's active set lands the quantized path feasible, which the polish cannot
change by moving to a different margin.

The section 8 reading ("the margins run to 1e-2, a hundred times the 1e-4 sine perturbation, so they must
be absorbing linear-model error") mistook the symptom for the cause. The rungs run to 1e-2 because
different captures need different regularization strengths to pull `lambda` far enough off the flat face
to recover cleanly, not because 1e-2 of quantization headroom is ever needed.

## Consequence for the target architecture

The dual's own answer (margin 0) has no unique recovered yaw sequence to land on: the Lagrangian is flat
in every tick direction there and the costate is noise. There is nothing for a local polish to converge
to. So "the optimum and the smoothest yaw sequence live at the dual's own answer" is geometrically true of
the *value* and false of the *recovery*: the answer is a face, not a point, and picking the point needs
either a positive margin (the ladder) or the section 6 null-space face walk. A single dual solve plus
Newton is not enough, measured. Keep the margin ladder.

The polish remains as an opt-in add-on: +1 ascending, +4 robust, zero regressions, off by default. It is a
small deterministic gain on top of the ladder, not a replacement for it.

## The direction that did work: primal face recovery

The margin-0 recovery is degenerate because the costate vanishes on a flat optimal FACE, not a point. A
local Newton polish cannot pick a point on that face. What can is a primal recovery that walks the face
directly: `SmoothFaceRecovery` (ported from the section-6 `SmoothRecoveryProbe`). It drops the dual's
recovered direction and instead drives a seed onto the active walls with a Gauss-Newton restore taken
under a second-difference metric (smoothest correction, not smallest), multi-starts when one basin
stalls, then walks along the null space of the active walls toward flattened turn runs. Every accept is
gated byte-exact. It works in absolute-yaw space (game facings derived per evaluation, no lossy round
trip) and carries facing (F) walls as first-order +/-1 rows, so it needs no prefold.

Measured over the 58 hpk captures, as a fallback after the ladder:

| | ladder alone | ladder + face recovery |
| --- | --- | --- |
| ascending certified | 25 | 40 |
| robust certified | 20 | 35 |

Zero regressions, and it beats the `repairRecovery` numbers (37/35) this file's section 5 recorded.

**And it is smoother, measured.** On the 25 captures the ladder certifies, seeding the face walk from the
ladder result: 14 get fewer turn reversals, 11 unchanged, 0 rougher; total reversals 130 to 98. On the
captures where production falls to SLP, the face result is far smoother than SLP (j345 6 vs 21, j153 13 vs
26, j298 4 vs 17). So the recovery delivers both goals the dual could not: more certifications and a
smoother yaw, at the dual's own optimal face.

### Wiring (preserves the microsecond fast path)

The user's constraint: keep the microsecond ladder for bruteforce; smooth only the final TAS. So the
smoothing runs once, in `AngleSolverEngine.runJob`, on the winning result of a user-facing solve
(`smoothFacing` -> `ClosedFormSolve.recoverFace` with no multistart), gated by the existing **Smooth (TAS)**
checkbox (`objective.smoothLambda > 0`) and by a per-solve `smoothFinal` flag that run-ticks search
candidates pass false. With Smooth (TAS) off the whole solver is byte-identical and microsecond-fast; the
ladder, velocity finder, `LongRunSolver`, and every sweep are never touched. Robustness that the first
default-on run forced (all four corpus failures traced to these, each fixed and test-verified):

- **time budget** (`SMOOTH_BUDGET_NANOS`, 400ms) plus a `try`/`catch`, so a large-n walk can never hang or
  break a solve (n=100 `taser-100t` was the tell);
- **objective guard**: a one-sided wall at the achieved value (`SMOOTH_OBJ_SLACK`) lets the walk keep or
  improve the objective, never trade it (j022's 5e-4 gap was the tell);
- **pinned facings frozen**: ticks `FacingPrefold` treats as pinned are held fixed, so a facing the user
  pinned is never drifted (`DeltaFacingConstraintTest` was the tell).

The final smoother only ever replaces the result with a byte-exact-feasible, not-rougher sequence.

The multi-start certify path (the 25 to 40 above) was **removed**, not just left dormant. Ruling: the
production chain (closed form, then SLP, then relaxation) already solves everything the user hits, so the
extra certifications are not wanted. Re-measured honestly: of the 18 jumps face recovery solved that the
closed form alone could not, SLP alone already solves 16 (just far rougher: e.g. j345 at 6 reversals vs
SLP's 21); only j147 and j330 does even SLP fail, and those were never checked against the full chain. So
the certify number was a benchmark artifact against the fast path, not a production gain. `SmoothFaceRecovery`
is now smooth-only: it takes a feasible sequence and walks it to fewer reversals, nothing more. The earlier
`DefectCorrectionPolish` dead end was also removed.
