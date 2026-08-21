# Gate microbench: why a gateless sim mis-simulates the razor-proof family

Written 2026-07-08. The finding: a simulator without the 1.8.9 momentum gate mis-simulates the razor-proof family, drifting ~8e-2 blocks off the recorded path by the objective tick; this is the "Sheepram's model is provably wrong here" exhibit (originally benchmark 4 of the Stage A suite, alm-snap-stage-a-design.md section 5; that research line has since concluded, but this result is machinery-independent). Test: `GateMicrobenchTest.gatelessTwinMisSimulatesRazorProof`, helper `RazorFixtures`. Fixture: `core/src/test/resources/captures/razor-proof.json` (n = 49, objTick 49, mcVersion 1.8.9, axis X MAX). Deterministic, runs under plain `:core:test`.

## Setup

The recorded proof facings are replayed through two byte-identical arithmetic pipelines that differ only in the momentum gate:

- Gated (legacy 1.8.9): `ExactJumpModel.forMcVersion("1.8.9")` = `new ExactJumpModel(0.005, true, false)`. Per-axis carry velocity below 0.005 is zeroed at the top of each tick.
- Gateless twin: `new ExactJumpModel(0.0, true, false)`. Threshold 0.0, so `|v| < 0.0` never fires and no carry is ever zeroed. Everything else (sprint-jump boost, accel, moveFlying rotation, friction) is the same float chain.

## What diverges

The gated model reproduces the recorded run exactly and satisfies every constraint (viol = 0). The gateless twin keeps the sub-threshold carry velocities the real game discards, so its trajectory drifts off the recorded path and it breaks constraints the gated model meets.

| Quantity | Gated | Gateless twin |
| --- | --- | --- |
| max constraint violation | 0.000000000e+00 | 7.858468188e-02 |
| max position gap vs gated | 0 (self) | 8.016106638e-02 at t = 49 (window end) |
| final position gap (t = 49) | 0 (self) | 8.016106638e-02 |
| X-axis gate ticks fired | 14 | 0 |

The position gap is |dx| + |dz| between the two trajectories. It is 0 through t = 5, then grows monotonically once the first meaningful gate fires, reaching 8.016e-02 blocks (about 8 cm) by the objective tick. That gap is far above the 1e-6 benchmark bar and is more than a full sine-bucket's worth of the 1e-4 razor margins the constraints are pinned to, so the gateless model cannot even be rounded back onto the feasible answer.

## Per-tick table (around the meaningful gate ticks t = 5, 14, 25)

`gatedVelX` and `gatelessVelX` are the pre-gate carry velocity into each tick (`ForwardPath.velX[t]`); the gate operates on a local copy, so the two carries coincide at the gate tick itself and the divergence surfaces in the following tick's position.

| tick | gatedVelX | gatelessVelX | \|posGap\| |
| --- | --- | --- | --- |
| 4 | -2.006425197574e-02 | -2.006425197574e-02 | 0.000000000000e+00 |
| 5 | +1.085312633020e-03 | +1.085312633020e-03 | 0.000000000000e+00 |
| 6 | +2.347773196783e-02 | +2.446536649234e-02 | 1.085312633023e-03 |
| 13 | +8.871904709122e-03 | +9.178127411826e-03 | 6.388240821735e-03 |
| 14 | +4.792645214489e-03 | +4.959842829586e-03 | 6.694463524440e-03 |
| 15 | +2.268767049729e-06 | +4.515725872050e-03 | 1.165430635402e-02 |
| 24 | +0.000000000000e+00 | +1.930157189808e-03 | 4.035764710920e-02 |
| 25 | +4.997854814547e-03 | +6.754297907893e-03 | 4.228780429901e-02 |
| 26 | -6.938913418173e-02 | -6.570128709567e-02 | 4.904210220690e-02 |

The three meaningful gate carries the gated model zeroes are t = 5 (velX 1.085312633e-03), t = 14 (velX 4.792645214e-03, immediately after the hop reversal at t = 13 to 14 where velZ swings -0.144307 to +0.099969), and t = 25 (velX 4.997854815e-03). At each, the gateless twin instead injects that carry into the next move, and the gap steps up: 0 at t = 5, then +1.085e-3 at t = 6, compounding to 4.90e-2 by t = 26. The Z axis never gates besides the trivial t = 0 rest state, so the divergence is entirely an X-axis phenomenon on this fixture.

## Why a gateless sim cannot represent this trajectory family

The proof solution is built from strafe-reversal micro-corrections whose net X carry between corrections is deliberately parked below MC's 0.005 per-axis momentum cutoff, so the real game snaps that carry to exactly zero at the top of the next tick (LivingEntity's per-axis momentum cancellation). Fourteen ticks on this route sit under the cutoff and three of them (t = 5, 14, 25) carry a physically meaningful magnitude that the gate erases; the route only lands because those erasures happen. A gateless simulator has no such discontinuity: it treats a 1e-3 carry as real velocity and propagates it, so its X position drifts and, by construction, it can never reproduce the byte-exact trajectory whose feasibility depends on the carry being zeroed. The divergence is not numerical noise, it is a structural cancellation the continuous model omits, which is exactly why a solver graded on a gateless model (Sheepram's smooth pipeline) mis-simulates this class and would certify an infeasible answer as feasible. The gated `ExactJumpModel` is the only acceptor that stays correct here.
