# Generic movement composition

2026-10-30. The user requires normal, stepping, ceiling and the existing ladder climb/fall phases to compose through one interface. A registered label on a complete support-to-support profile is insufficient. The prior one-step-event-per-leg limit is an implementation limitation to remove.

Implemented checkpoint: common append/replay, open generators, registry-driven queue and full continuous acceptance are in place. See GENERIC_COMPOSITION.md for current entry points, tests and bounded-search limits.

## Contract

A movement choice consumes the current scalar state and emits an open sequence of input units, controls, medium membership, finite contacts, vertical outcome events and relative position constraints. Normal air leading to an event may be part of a choice. The event's actual resulting state is the boundary; it does not require a later floor landing. Its X/Z position and velocity remain symbolic.

The composer retimes each choice once, replays the accumulated Y/control/contact history from the original state, and preserves all prior constraints and movement units. It accepts grounded-but-unsupported boundaries. Boundary ladder membership is resolved by the incoming/outgoing medium contract, including entry-producing horizontal hits and their vertical carry. Ladder phase grammar and omitted endpoint hits remain owned by the existing generators.

One queue extends an open prefix using every enabled registered type. Time orders the queue; complete original-state numerical solves compare objective at equal time. A normal floor landing can finish a route or be followed by more movement. No fixed sequence, type-pair adapter, interaction count of one, or mandatory intermediate floor is part of the composition contract. Existing work limits and the 200-input model horizon remain explicit and do not certify global optimality after truncation.

## Implementation

1. Add generic program creation, append/replay, scalar boundary validation and conversion to the existing whole-route continuous problem.
2. Add open generation to normal, stepping and ceiling, ending at their own events. Preserve existing support-profile adapters while migrating callers.
3. Adapt existing ladder phase/schedule generation to the same choices and composer, preserving finite walls, Sneak, stops, entry/exit and carry semantics.
4. Add one bounded movement-program queue and production standalone entry point that dispatches through the type registry. Reuse the existing full-history IPOPT/compiler/scene acceptance path.
5. Verify arbitrary repeated/mixed sequences, entry and exit state continuity, all retiming, grounded unsupported transitions, and numerical scene witnesses. Keep ladder-ceiling ownership changes deferred.

## Validation scope

Use focused standalone scalar/generation/composition tests during implementation. Include fresh complete IPOPT solves with shared scene checks for mixed stepping/ceiling sequences and ladder transfers, with no fixed intermediate X/Z endpoints. Do not run Gradle Java core tests or game replay tests for this standalone work.
