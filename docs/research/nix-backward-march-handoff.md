# Nix backward-march solver: handoff

Goal for next session: solve the full Nix route cold from t1 with a **single backward march from the landing to rest** (no windows, no seams). This doc is the self-contained plan + context. Written 2026-07-06. Companion detail: `docs/research/nix-full-freestart.md` §9-§9h (the full investigation this grew out of); memory `project_nix_multijump_freestart`.

Route file: `C:/Users/benja/Desktop/Games/MultiMC/instances/1.8.9/.minecraft/parkourcalculator/nix-full-works.json` (its `debug` block IS the proven human landing run; forward `debug[k+1].yaw` through `ExactJumpModel` to get proven per-tick state, byte-exact to 8.9e-16). MC 1.8.9, 54 ticks, starts from rest, 4 jumps (t5/18/30/43), per-axis 0.005 inertia gate, free-start footprint `X[9.7,11.3] Z[1.2,4.3]`, objective X-MAX@54, landing pad `X@54 in [8.7,10.3] Z@54 in [9.3,10.3]`, walls `X@47<=8.7`, `Z@46<=7.7`, `Z@53>=9.3`, corridor `X in [9.7,11.3]` through t0-41.

## The plan (this is the thing to build/refine)

**Step 0 - anchor: minimal landable takeoff velocity.**
- The final jump takes off at **t43, which is OFF the block (coyote)**; the tick before it, **t42 (X=9.7, on the block)**, is the on-block constraint. (Confirmed in the proven run: t42 onGround X=9.7000; t43 onGround X=9.4220 < 9.7 = coyote, jump fires here; t44 airborne.)
- Anchor at **t42**. Solve from t42 to the landing with the **t42 velocity free (BOTH vx and vz)** and **position free** (translation), walls+pad constrained. Find the **lowest velocity that still lands**. That `(pos, vx, vz)` is the anchor.
- Built: `NixBackwardMarch.java` `step0()` (env `PKC_BM_FILE`, `PKC_BM_TAKEOFF=42`) - a coarse (vx,vz) grid, position-free landability via `SolveCore`+`recoverStart`. Refine to the true minimum (finer grid / a real minimize). ANCHOR RESULT: [run `NixBackwardMarch` to fill in; grid was still running at handoff].

**Step 1 - backward march, tick by tick, t42 -> 0 (NOT built yet).**
- State = `(px, pz, vx, vz)` at a tick; frontier starts as the anchor.
- One tick back: for each candidate input (facing + keys), invert one physics tick to get the previous tick's state: undo friction (`v = v_next / f4`), undo move (`p = p_next - v`), subtract that input's acceleration, undo the jump boost on jump ticks, undo the gate (`v_prev = v_before` if `|v_before| >= 0.005`, else it is in the `(-0.005, 0.005)` band -> heading to zero).
- **Branch** = the input each tick. **Bound/prune** = drop any previous state whose position leaves the block/corridor ON THE TICKS THAT MUST BE ON IT (coyote + airborne ticks are exempt: only walls/corridors apply there); drop states that can no longer decay to zero. Grid-dedup the frontier (bound memory - use the packed-`long` action encoding trick from `NixSolve` if storing sequences).
- Repeat. Velocity shrinks going back (it was built up from 0 forward; friction `x0.91` forward => `/0.91` grows backward, but the accel + gate bring it down). **Stop when a state reaches vx=vz=0 on the block** - the start position and #ticks-back fall out.

**Step 2 - forward-verify byte-exact.**
- Replay the recovered input sequence forward from the rest start through all 54 ticks; confirm `viol=0`. Step 1's inversion is float-approximate (float ops are NOT exactly reversible), used ONLY to steer branch/prune; the byte-exact guarantee comes only from this forward replay. If drift prunes the true path, widen the inversion to a small interval.

**Why no seam:** after the Step-0 anchor, every remaining tick is a single backward step. Nothing downstream is a separate window, so there is no boundary to reconcile. (The prior forward approach cut at t30/t42 and had to make two SolveCore windows agree = seams. This does not.)

## Why this approach (what the whole prior investigation established)

The route is a **True Nix: maximally tight, ~zero slack** (proven run lands the pad by +2.1e-5, wall-hugs X@47/Z@46/Z@53/X@42 to ~1e-7; see `NixMargin`). Consequences proven this session:
- **Monolithic forward solve fails** (SolveCore over 54 ticks -> 14/17, viol ~4.7e-3). That is why anything was decomposed.
- **Position-free is the right frame** (user's insight): the route is translation-invariant (the gate acts on velocity, position-independent), so the seam is a VELOCITY and the position falls out of one footprint translation onto the binding constraint. Docs §9d-h, `CONTEXT.md` "Axis-locked momentum".
- **The tail closes byte-exact from a good seam** (`SolveCore` on `[42,54)` or `[30,54)`), but ONLY from a razor-precise minimal-momentum seam that a cold search cannot re-find; a `+0.005` vz overshoot un-sticks it (`NixChainVz`).
- **vx/vz TRADE cracks the tail needle** (user idea #2, `NixVxTrade`): from the proven t30/t42 position the tail is stuck at 6.6e-5 with vx=0, but adding just **vx=-0.027 (a slight left curve) closes it BYTE-EXACT at the same vz**, and vx=-0.097 closes at vz=0.195 (below proven). The vx is the extra DOF that threads the byte-exact landing.
- **Forward decomposition fails on the coupling**: the setup can only produce vx by curving, which drifts it OFF the proven position; the tail lands from proven-position+vx; reconciling via translation breaks the corridor floor. So the setup-reachable seam set and the tail-landable seam set are **near-disjoint** - the maximal tightness. `NixSolve` reaches curved corridor-compliant seams cold but none near the proven position; more forward vz self-defeats (total Z-rise t17->t46 must be <= 6.5625, proven uses 6.50).
- **Idea #1 (more forward momentum via backward sprint jumps) is self-defeating** for the Z-rise bind. Coyote (jump off the block) gives extra reach but does not fix the Z-rise; the real lever is idea #2 (vx).

The backward march is the honest response: **do not decompose at all** (no seam to make disjoint sets meet); march the one hard constraint (the landing) back to the loose one (rest on the block). Search-from-the-constrained-end is better-conditioned than accumulating 54 ticks of error into the razor.

## Harnesses built this session (all env-gated, inert in the gate; `:core:test` was green with them present)

Under `core/src/test/.../anglesolver/`:
- `NixBackwardMarch.java` - **Step 0** of THIS plan (minimal takeoff velocity map). Step 1/2 to build.
- `NixTailProbe.java` (`PKC_PROBE_FILES=<;-list>`) - the cross-route §3b probe (largest BnB tail + band per route).
- `NixMinVz.java` (`PKC_MINVZ_FILE`) - min vz at FIXED vx (WRONG: pins vx; superseded by the free-velocity Step 0).
- `NixMargin.java` (`PKC_MARGIN_FILE`) - proven-run per-constraint margins + fine vz bisection.
- `NixChainVz.java` (`PKC_CHAIN_FILE`) - the +0.005 vz overshoot => tail closes byte-exact result.
- `NixVxTrade.java` (`PKC_TRADE_FILE`) - the vx/vz TRADE map (idea #2; the crack). Reuse this shape for Step 0 landability.
- `NixSolve.java` (`PKC_SOLVE2_*`) - forward assembly (key-search setup + tail + one translation). Has the packed-`long` action encoding + the corridor prune `violatesSetupCons` + the exact translation-window intersection over all 23 constraints - REUSE these for the backward march.
- `NixSetupSearch.java`, `NixTailClose.java`, `EngineFileScreen.java` (added `PKC_OPTIMIZE_SECONDS`) - supporting.

Repro (Git Bash), read stdout from the test XML `<system-out>` CDATA:
```
export PKC_BM_FILE="C:/Users/benja/Desktop/Games/MultiMC/instances/1.8.9/.minecraft/parkourcalculator/nix-full-works.json"
export PKC_BM_TAKEOFF=42
./gradlew :core:test --tests '*NixBackwardMarch' --rerun-tasks -q
```

## Key facts the next session needs

- **Model = `ExactJumpModel` (byte-exact, MC-free Java 8).** `forward(scenario, gameFacings)`; `stepRange`; per-op float/double types are THE reference for the inversion (read `stepRange` first). Legacy 1.8.9: gate per-axis 0.005; jump sets `vy=JUMP_VEL_F` (regardless), sprint boost `vx-=sin(F)*0.2, vz+=cos(F)*0.2`; accel via `moveFlying` (float); friction `v*=f4` (0.91 air, `slip*0.91` ground). Position `p+=v` (pre-gravity v). X/Z fully decoupled from Y (Y can be ignored for X/Z).
- **Movement direction** (for the inversion): `accelX = strafe*cosF - forward*sinF`, `accelZ = forward*cosF + strafe*sinF` (the raw key vector rotated by facing F). Axis-locked momentum uses fixed F=45 with W+A(+Z)/S+D(-Z); see `CONTEXT.md` "Axis-locked momentum".
- **Coyote**: jump fires on a tick AUTHORED grounded (`slipPerTick<1`) regardless of block POSITION; so t43 grounded-but-X<9.7 is fine. The block/position constraint is on the pre-jump grounded ticks and the momentum-build ground ticks, NOT the jump/coyote/airborne ticks.
- **Proven final-jump structure** (forward the debug): t42 (9.7000, 6.5047) vel(-0.16305, 0.19379) onGround; t43 (9.4220, 6.7593) vel(-0.15177, 0.13900) onGround=coyote; jump boost lands in t44 vel(-0.16952, 0.23225); pad at t54 (8.7000, 9.4885).
- **Translation invariance** for "position falls out": shift the whole route by one `(dx,dz)`; positions shift rigidly, velocities/gate unchanged. `FreeStartSolve.recoverStart`/`violationAt` translate a shape within a `StartBox` (use a large box for ~free). Constraint bounds on `(dx,dz)` intersect to a window (exact; see `NixSolve` step 5).
- **Slicing** private helpers copied from `LongRunSolver` (`sliceScenario`/`sliceConstraints`/`jumpBoundaries`); present in the harnesses.

## Open questions / risks to refine
1. **Float inversion drift** (Step 1): how far can the approximate backward march run before it must snap/interval? Measure on the proven trajectory (invert it backward, compare to the true states).
2. **Airborne branching**: airborne ticks have continuous facing -> discretize how finely, and is corridor pruning enough? The airborne arc [30,42] is ~12 ticks with weak block pruning.
3. **Anchor set vs point**: Step 0 gives a minimal velocity; the backward march may need the whole landable velocity BAND at t42 as the start frontier (not one point).
4. **Does the backward-reachable set actually reach v=0 on the block?** If it does not, that is the definitive proof the route needs an un-cut whole-route byte-exact solve (still an open hard problem) or the human's hand interleaving. Either way the march MEASURES it.

## Gate
`./gradlew :core:test -x tableStyleCheck` must stay green (all harnesses `assumeTrue`-gated, inert without env vars). No `mod_version` edits. No em dashes in repo writing. No code comments.
