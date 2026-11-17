# Convex decision model with normal momentum carry

Ceiling/stepping follow-up: [DYNAMIC_CONTACT_DECISIONS.md](DYNAMIC_CONTACT_DECISIONS.md) extends the proposed decision model to choices that alter Y/VY, ground state and subsequent acceleration/friction. A contact method must select its transition and successor, not only a clearance constraint. The current prototype has not implemented this extension.

2026-10-31. User clarification: omit only inertia thresholds during input-decision search. Keep stored velocity, friction, acceleration and ordinary momentum propagation. The user expects the threshold's physical effect to be small and wants later closed-form/SLP/B&B passes to handle inertia patterns. This supersedes the previous recommendation to prioritize strengthening the carry hull.

This turn explains the existing implementation and records the revised model direction. No runtime source or benchmark was changed. Existing reported measurements used the carry hull; they are historical evidence, not measurements of the proposed no-threshold model.

## Decisions and assumptions

The current prototype receives a supplied support sequence, ordinary vertical flight profiles, allowed run/departure timing and horizontal passage choices. A fixed T gives the number of inputs; different T values are compared separately for elapsed-ticks-first ordering. The initial horizontal position and velocity stay fixed to the real original state. Facings remain variable at every input, including earlier inputs after a later decision is fixed.

The implemented input policy is W/sprint or the existing Force 45 policy. The current discrete choices are run allocation, jump departure timing and two-face horizontal passage timing. General stepping/ceiling histories and alternative support sequences are not yet included by this prototype.

Movement choices are edges of a time-expanded phase graph. Integral edge masses z_e select one consistent timing history from start to finish. Incoming and outgoing masses balance at every intermediate node, with one unit entering at the start and leaving at the finish. The relaxation permits 0 <= z_e <= 1. These are optimization weights, not probabilities or executable partial key presses.

For each passage, alternative gate choices have weights w_j >= 0 with sum_j w_j=1. Integral w_j picks a crossing tick/method. Each alternative supplies the appropriate before/after signed face inequalities, with the existing Y-X-Z collision order deciding whether face clearance is simultaneous or consecutive.

## Affine movement and disk controls

For one real movement mode k, let p and v be horizontal position and stored velocity, a its acceleration, m_k its authoritative acceleration magnitude and f_k its friction coefficient. With thresholds omitted:

```text
p_next = p + v + a
v_next = f_k * (v + a)
```

Momentum is carried normally. The only omitted rule is snapping a small velocity component to zero. There is no carry-hull variable or inertia-regime selector in the proposed decision model.

The physical free-facing acceleration circle becomes the disk norm(a)<=m_k. To retain convexity while movement modes are fractional, each edge owns weighted copies of its incoming/outgoing state and acceleration:

```text
L*z_e <= state_in_e <= U*z_e
p_out_e = p_in_e + v_in_e + a_e
v_out_e = f_e * (v_in_e + a_e)
norm(a_e) <= m_e*z_e
```

At z_e=0 its bounded copies are zero. At z_e=1 they represent a full movement transition. Intermediate values represent weighted alternatives. All coefficients m_e and f_e are fixed for that mode. No product of an unknown mode weight with an unknown shared velocity is needed.

At a phase node, incoming weighted output states sum to outgoing weighted input states. This preserves weighted position and momentum but relaxes which complete hypothetical history owns which state. Fractional branches may split and recombine state and choose different directions. Their displayed aggregate need not be a physically realizable trajectory.

The objective is a linear terminal coordinate. Flow, transitions, domains and guards are affine; norm(a_e)<=m_e*z_e is a second-order cone constraint. Together they form the convex SOCP.

## Collision guards and obstacle phasing

An obstacle interaction is encoded through conditional inequalities, not a physical collision response applied to the displayed mean trajectory. A typical weighted guard is:

```text
h * p_e <= b*z_e + M*(1-w_j)
```

Here h selects/signs a coordinate, b is the face boundary and M is a valid bound from conservative state domains. With w_j=1 the face constraint is active on that edge's normalized state. Fractional w_j weakens it; several fractional alternatives can permit a mean path through an obstacle. Mixing phase/state alternatives can also give an unrealizable aggregate even when individual local guards are stronger.

Fixing a candidate crossing removes its alternative weights and activates its constraints. Fixing the relevant movement timing also removes competing phases. Reoptimizing all state/control variables then generally makes the reference resemble the feasible local interaction more closely. Other unresolved decisions remain relaxed; fixing one event does not automatically establish whole-world feasibility.

This explains the large obstacle-case shape errors in the earlier benchmark. Cross-passage position RMSE fell from about 1.11 to 0.15 blocks after committing its crossing choice. However, the old benchmark also had accumulated carry-hull optimism and fractional state/mode effects, so not all error was obstacle phasing. Removing the carry hull is a separate simplification whose effects need fresh measurement.

## Remaining relaxations and later inertia repair

The revised decision model retains fractional movement/phase choices, fractional passage choices, disk controls, state splitting/recombination and conditional collision guards. It omits the old inertia hull entirely.

Every supported no-threshold real trajectory still has a one-hot lift, so the SOCP is an upper model for that no-threshold family. Omitting the threshold rule is a changed physics assumption, not automatically a superset of all thresholded game trajectories. The previous game-physics inclusion claim must not be copied unchanged to this revised model.

Use the result to shortlist input/interaction decisions. Later passes can reoptimize every facing using actual inertia patterns and the existing closed-form/SLP/B&B machinery. Retain nearly feasible decisions for that later repair when appropriate; no violation tolerance has been chosen or validated yet. The user's working assumption about the usually small physical threshold effect guides the staging, not a new measured guarantee.

Next implementation work should remove carry-hull machinery from the decision phase, preserve exact normal momentum carry, and rerun model/reference/ranking comparisons. Inertia-pattern search and tolerance calibration are deferred. Existing fixed-pattern diagnostics remain historical diagnostics rather than the proposed primary model.
