# Ceiling and stepping decisions change the transition law

Goal clarification: [REFERENCE_STRATEGY_EVALUATION.md](REFERENCE_STRATEGY_EVALUATION.md) makes the intended use explicit. All covered methods/timings must remain unresolved in the initial mixed baseline. Selecting a method and applying its transition is a later conditional comparison or a provisional-draft initialization, not a prerequisite for that mixed solve. The old passage-only pilot has not tested this full baseline.

2026-10-31. User emphasized that passage timing with unchanged physics is the easy case. The important local decisions include ceiling contacts and alternative stepping methods, which alter vertical motion, ground/input state and subsequent horizontal coefficients. This note extends the no-threshold design; it is not an implemented SOCP extension or a new benchmark.

## The prototype's actual limitation

The current route-guidance pilot supplies a fixed support sequence and ordinary jump profiles. It can change departures, run allocation and horizontal passage gates, but cannot choose to skip an intermediate support, land on it and run off, grind under a ceiling, or use a stepping variant instead of an ordinary jump. Existing generic movement code can propose and physically validate those methods; the convex decision model does not yet represent their union.

The next model must accept a broad spatial corridor with optional contact/support opportunities. Do not freeze a support itinerary or one ordinary Y schedule before comparing these methods. Both alternatives must actually belong to the declared corridor family. A family whose spatial definition excludes one alternative cannot claim to optimize over it.

## A decision selects guards, a transition and a successor phase

Represent an alternative by method, tick, relevant block/face and successor movement state. Its contract contains:

- geometric/kinematic eligibility, including collision-order timing;
- the current input's acceleration/friction and jump impulse;
- resolved position and carried velocity;
- resulting ground/input/sprint state and subsequent allowed transitions.

The guard answers whether an interaction can happen. The transition states what it does. The successor determines later physics. Selecting only a face constraint while retaining the old flight schedule is insufficient.

Keep continuous state s=(x,y,z,vx,vy,vz). Carry momentum normally with no small-velocity thresholds. Ground/contact/input flags live in discrete phase nodes; do not feed an averaged ground flag into the movement compiler. Every mode has fixed coefficients compiled for its discrete state/input policy. All facings remain free, including earlier inputs after a later contact choice is fixed.

## Examples

An ordinary ceiling hit is an upward movement whose swept Y reaches an underside while X/Z overlap the finite ceiling footprint at the Y-resolution phase. With expanded feet plane C, its vertical reset is y_next=C and the resolved vertical carry is zero before gravity/drag. The next stored vertical velocity is therefore -g*d. Avoiding the ceiling instead retains ordinary vertical movement. These alternatives can change landing time, run allocation and the next jump even if the current horizontal acceleration is identical.

Preserve Y-before-X-before-Z geometry: a normal ceiling collision is not justified merely by ending the horizontal move under that ceiling. The pre-horizontal footprint matters. A grind is a distinct stepping/ceiling pattern with outside-before/under-after geometry and zero jump rise; it is not represented by forcing an ordinary ceiling hit on the same tick.

| Alternative | Current input | Result and continuation |
| --- | --- | --- |
| Ordinary flight | Air or grounded jump as appropriate | Ordinary Y evolution; contact timing remains unresolved |
| Ceiling hit | The original air/jump regime | Feet at underside plane, vertical reset; ordinary falling/landing follows |
| Normal step | Ground/run | Rise to higher surface within step height, remain grounded |
| Slab/stair boost | Ground/jump | Keep jump acceleration/boost, change Y outcome, then the following air input |
| Blip | Air into the hit state | Land/step to higher top; next input is grounded |
| Blip-up | Air into the hit state | Retain the incoming feet height, become grounded; optional elevated jump or normal run-off follows |
| Grind/corner jump cancel | Ground/jump | Preserve appropriate jump horizontal motion, cancel rise, then ordinary air continuation |

Blip-up and corner cases must not invent a support at the retained height. A grounded flag carried from the previous collision can permit the next input's ground regime without a persistent platform at that Y. The air input producing a landing/step result must not be retroactively assigned ground acceleration or drag. The authoritative distinctions remain in STEPPING_CASES.md.

## How the relaxed SOCP represents changed dynamics

For each fixed collision/control branch k, seek an affine transition and affine eligibility rows:

```text
s_next = A_k*s + B_k*u + b_k
G_k*s + H_k*u <= h_k
norm(u) <= m_k
```

Each edge e has weight z_e and weighted incoming/output state/control copies:

```text
s_out_e = A_e*s_in_e + B_e*u_e + b_e*z_e
G_e*s_in_e + H_e*u_e <= h_e*z_e
norm(u_e) <= m_e*z_e
```

State bounds scale with z_e. Weighted states and mass balance at each phase node. Finite-face alternatives and other activation rows may use valid bounded Big-M or disaggregated hull guards. The matrices differ between ceiling-hit, avoidance, grounded-step, jump and landing modes. No product of a selector with an unknown shared state is introduced.

Recompute conservative position/velocity domains for the expanded movement family. Ordinary-jump height limits or prefix-specific bounds cannot be reused if they exclude a real chain of steps, a retained-height jump or another newly allowed transition.

At integral selections, the chosen branch applies its complete physical transition and successor state. Fractional alternatives can blend reset/no-reset histories and ground/air modes. This extra optimism is expected. Keeping Y/VY in the state prevents each branch from pretending it starts from an unrelated precomputed vertical history.

One-input arcs suffice for the general formulation. A short profile may compress several arcs only when its entry-state domain and transitions cover every intended incoming state. A numerical Y/VY trace derived from one prefix is not automatically a valid template for all unresolved prefixes. Grounded phases with identical contact/input obligations can share graph structure while continuous state is propagated through copies; no separate graph node for every complete history is required.

## The exact stepping difficulty

Once individual clipping/contact branches are fixed, many resets and movement equations are affine. However the complete step resolver is not just affine cases with simple rectangular guards. StaticScene.move compares squared horizontal travel to select between two step constructions and between stepping and normal movement. Those winner comparisons are generally nonconvex.

An initial optimistic SOCP may include all potentially applicable step outcomes while omitting or outer-approximating those winner comparisons. It must still retain the needed trigger, height, finite-overlap, ground and collision-phase information. All real winning outcomes must remain included. Keeping an unphysical alternative is extra optimism; omitting a real winning alternative would invalidate the bound.

The named stepping case is an orchestration label, not a complete encoding of every low-level clipping branch. The model needs either those branches or a conservative envelope that contains their real state transitions. It must not assert that every named case is automatically an exact SOC-representable physical mode.

Such outer validity rows can remain optimistic even after a method/tick is fixed. Final scalar/shared-scene validation must determine whether that contact actually occurs and whether the step resolver selects the proposed result. Intentional wall-hit routes remain excluded, but the temporary horizontal obstruction that triggers a step is still part of step eligibility.

## Next useful implementation/benchmark

Keep the user's no-threshold assumption throughout decision selection. First compare ordinary flight versus an optional ceiling hit, and ordinary jumping versus normal step/slab boost/grind, within the same broad corridor. Then extend retained-height blip-up and corner cases, preserving their exact input/ground semantics. The existing scalar/profile/scene machinery supplies source definitions and acceptance checks; its prefix-specific traces cannot be copied as globally valid unresolved histories.

For each method/tick alternative, impose its transition and guards, leave future methods relaxed and reoptimize the entire trajectory from the original initial state. Compare conditional values and reference shape against fully optimized accepted alternatives. Report any omitted/nonconvex step guards and assess whether their optimism ruins ranking after the contact is selected. Evaluate optional landing versus overflight through different successor phases rather than forcing both to follow one supplied support itinerary.

No new solver code, tests or measurements were added in this explanatory turn. The current measured pilot has not established reference quality or decision-ranking performance for ceiling/step dynamics. Inertia thresholds, tolerance calibration and ladder/intentional-wall-hit experiments remain deferred.

## Repository source checks

Reviewed `ceiling_primitives.py`, `ceiling_generation.py`, `case_profiles.py`, `stepping_generation.py`, `movement_composition.py`, `standalone/ipopt/ladder.py`, and `core/.../replay/StaticScene.java`. The first group already defines/proposes profiles and checks named vertical outcomes; the shared scene checker implements the actual clipping and step selection. Those implementations establish which equations/guards must be preserved, not an already implemented convex formulation for them.
