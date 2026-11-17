# Cell-route experiment

Implementation branch: `research/ipopt-cell-routes`, isolated worktree `/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes`, baseline `99ecfe9`. Bounds remain deferred. No production routing default changes. No cross-thread communication is part of this work.

The implementation follows [the experiment plan](../../CELL_ROUTE_EXPERIMENT_PLAN.md). All results below concern the continuous standalone model, not actual game replay.

## Milestone 1: geometry

Implemented deterministic expanded-AABB subtraction, cuboid merging, partial adjacency portals, physical face provenance, climbable memberships, exact boundary strata, segment coverage and horizontal SVG slices. Boundary strata preserve free configurations with no positive-volume neighborhood, including a floor and ceiling exactly one body height apart. Geometry limits/deadlines fail explicitly instead of returning an incomplete partition.

Validation: six focused geometry tests pass. They cover randomized obstacle-union agreement, disjoint volume interiors, exact contact boundaries, unequal neighbor interfaces, split/reordered/overlapping/nested boxes, and swept segment coverage. Java helper built inside this worktree using `:core:prepareRunTicksCompiler`; no numerical solves or mod test suite in this milestone.

## Milestone 2: spatial routes

Implemented bounded geometric best-first walks, finite revisit/depth/expansion/route limits, duplicate grouping and prescribed-spatial-route extraction. Search uses geometric length plus an ordering penalty for reversals/revisits, never a time bound. Geometric prefix representatives and finite limits make this a heuristic shortlist, not complete enumeration. Completed goal walks stop expanding; earlier returns remain permitted. All omissions/limits are reported.

The first broad-family representation projects a 3D route's cells onto X/Z and groups paths by ordered obstacle-midplane crossings (left/right/through plus crossing direction). The crossing axis is the dominant start-to-goal horizontal axis. Adjacent source AABBs that form one cuboid are merged before landmark extraction. This is deliberately an initial corridor signature, not a general topological classification. It can miss distinctions for U-shaped/branching routes whose meaningful choices do not cross these planes. Broad projection allows different Y schedules and support interactions without freezing a particular flight. Large projected cells can weaken filtering; benchmark this instead of assuming reduction. Signature checks prevent a grouped/projection corridor from accepting the opposite side silently.

Ordinary Y/X/Z phase tracing accounts for several crossings in one tick, while repeated states need no extra graph hop. Corridor acceptance uses the horizontal projection and ordered crossings, with physical Y/step acceptance delegated to the existing shared scene checker. This does not claim a reconstructed exact 3D step-assist phase trace.

Validation: 11 geometry/route tests pass, including all three obstacle passage sides, finite limits, invalid prescribed paths, crossing order and multiple crossings per tick. The scene coverage bounds are eroded by the player body dimensions, matching the requirement to keep the whole body inside the supplied scene.

## Milestone 3: corridor-conditioned local solving

Added optional context/candidate/work hooks to the existing generic composition and contact-search path. Defaults remain unchanged. The adapter filters candidate blocks before normal/ceiling/stepping generation, filters ladder regions before schedule generation, and filters mixed-contact opportunities before their combinations while preserving original solid indices. All numerical problems retain the original position/velocity and free earlier facings. Full scene geometry remains available to physical acceptance.

Candidates must pass existing scalar/support/vertical/shared-scene checks and a horizontal corridor/crossing audit. A wrong corridor can add timed rectangle or gate-side rows to the existing local contact queue. These lazy repairs are heuristic and explicitly not a complete corridor constraint enumeration; a failure is unknown. Counts distinguish early opportunity removal from late numerical repairs.

Refined corridor construction after the first smoke: automatic corridors use the X/Z envelopes of full adjacent portal patches and finite endpoint support patches inside each visited cell, rather than projecting an entire large cell. Prescribed corridors clip selected cell projections to the envelope of the supplied spatial points and finite endpoint support patches. This allows local motion in regions, not fixed center waypoints, but is still a heuristic family restriction. Duplicate families union their envelopes. Large portals/grouping may still weaken filtering. Broad signatures remain limited to dominant-axis obstacle-midplane crossings.

The final horizontal movement after a pre-horizontal goal landing is outside the broad route's completed interaction. Audit ends at that landing's Y phase, preserving legal post-landing travel and objective quality; physical acceptance still checks the entire input. Without this correction, an open control lost objective distance through an artificial goal-footprint cap.

Added numerical proposal/call counters, retries included, compiler/scene counters, per-route local program and selected-unit counts, generation records and accepted-branch timing. The open-space smoke accepted the same 12-input trajectory/objective in all three arms, one actual solver call each. Focused corridor/geometry tests pass. A 39-test numerical-enabled regression run passed its existing generic/contact/search cases; its new saved-witness check initially exposed suffixed route IDs and was corrected. The four-test experiment suite, including all three numerical arms and independently replaying the saved result, then passed.

The wider no-environment suite ran 319 tests with 71 skips and one failure: `test_repeated_ladder_ceiling_events_regenerate_the_complete_schedule`. The same failure was reproduced on unchanged canonical `99ecfe9`; it is not introduced here. No unrelated ladder fix is included.

## Forward propagation within a route

The user emphasized that a fixed broad route should allow decisions/constraints to be evaluated in order, since arrival ticks vary more than the spatial path. Added ordered cell-stage variants to each family and an optional forward prefix pass, enabled by default in the experiment. It intersects known X/Z rows and finite-contact footprints/planes at their actual Y/X/Z phases, then propagates earliest compatible route progress. Keeping the earliest compatible stage implicitly retains all later compatible stages, since stage jumps have no upper bound; no numerical endpoint is selected. It can cross several cells in a tick, stay within one cell for repeated interactions, or revisit cells explicitly present later in the route. Duplicate spatial variants are alternatives, not a forced single trace. Geometry-only misses remain unknown.

This pass runs after local choice construction and before prefix queuing/Java screening; existing box/ladder opportunity filters still run before schedule products. Therefore it may reduce queued/numerical work without eliminating the cost of building a ladder schedule pool. The benchmark must distinguish these effects. `--no-forward` runs corridor filtering/auditing without this pass. Counters include prefix checks/rejections, cached decisions and phase updates. Tests cover forward-order rejection, declared returns and same-cell backward motion.
