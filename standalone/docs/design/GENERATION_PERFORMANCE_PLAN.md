# Schedule-generation performance follow-ups

2026-10-29. User accepts the first two behavior-preserving improvements below. Multiplier-based schedule pruning is recorded for the later pruning pass, after movement-type coverage. The user remains unconvinced about resumable generation: retain it only as a potential improvement, with no agreement to implement it. It is not required to finish the common movement-type interface.

## Profile

A representative first-region append from the five-climbing-region fixture, with mixed contacts disabled, generated 256 schedules and returned eight continuations. Under cProfile it took 8.417 seconds, including 24,510 scheduler Y traces and roughly 1.6 million simulated tick updates. Y replay accounted for about 74 percent of cumulative profiled time. 22,126 traces were ultimately rejected for occupied states outside the ladder's Y interval. These are instrumented timings, not an unprofiled speed benchmark.

## Immediate improvements

1. Move the existing occupied-Y validity check into simulation so a schedule already known to fail does not continue through its remaining ticks. Preserve the same valid schedules, scalar arithmetic, input/contact semantics and enumeration/work limits. Compare emitted candidate contents with the current implementation; different diagnostic rejection precedence is not permission to change acceptance.
2. Skip the extra `vertical_move` call used to predict floor crossings when there is no target floor. `vertical_step` already computes the movement. Open airborne prefix generation therefore does not need that first evaluation. Landing detection must retain its current exact-clipping behavior.

Both changes are implemented. The representative append's median unprofiled time fell from 2.849 to 1.849 seconds across three measurements per version, about 35 percent less time. Both versions generated 256 schedules, consumed 24,767 mask-work steps and returned eight continuations with identical serialized contents (SHA-256 `ef7eaf7aac4a6ae44329b1a42382fc1e85d11f6bf01c651c026dba41587b29d2`). Fifty-five focused tests pass, including 250 random trace cross-checks, full-versus-short-circuit generator equivalence, exact floor clipping and legitimate top/bottom exits. They remove repeated work within the existing validation policy; they add no route-specific pruning.

## Deferred pruning proposal

The user proposes retaining the input schedule with the best movement multiplier instead of repeatedly considering equivalent Y outcomes. Record this for the pruning pass. Existing user guidance compares schedules with the same total airtime and time after leaving the ladder, using pure straight-line horizontal progress from zero momentum and the first-clamped-input Sneak exception.

Define the comparison class carefully before implementation: preserve required timing/Y outcomes, contacts, entry/exit geometry and relevant input-state assumptions. Compare the complete compiled movement effect, including sprint cancellation and carried air factors, rather than one scalar acceleration coefficient. Decide explicitly whether the rule is a heuristic reduction or establishes dominance for the full problem; incoming velocity, component caps and obstacle requirements can matter.

An entry face is a separate geometric alternative. Front and side channels with identical inputs, occupancy and hit history have identical movement multipliers but different positional constraints. Multiplier equality alone does not select a feasible entry face. One input representative may be reused for several entry-face alternatives without discarding those alternatives. A face reduction requires equivalent geometry or a separately justified rule.

The earlier exact-Y caching suggestion is deferred with this discussion. No cache or new multiplier/face pruning has been added.

## Optional idea: resumable generation

Currently an expansion finishes a batch of local schedule generation and selection before returning its continuations to the global queue. A resumable producer would emit a small batch, retain its enumeration state, and let the scheduler alternate that producer with other pending work. Unseen alternatives remain available instead of being discarded.

Its purpose is shorter latency to usable continuations and finer budget allocation. It can reduce time spent producing children that the search never gets to use before its deadline. It does not automatically reduce the work required for exhaustive enumeration, and it does not by itself resolve the preference for short partial histories.

Preserve total elapsed ticks before objective. Resuming a generator is a search-work task; it is not an additional Minecraft movement tick. If generators compete with concrete prefixes in the queue, retain an admissible time bound for their unseen work. Do not turn batching into an undocumented depth-first/first-feasible policy or claim a minimum while shorter unresolved alternatives remain. Existing bounded-search proof limitations still apply.

This is a larger scheduling refactor than the first two changes. Keep it separate from input-family pruning and from any movement-model changes.

Do not promote this proposal into the implementation sequence without further agreement or evidence that its scheduling benefit is useful. The first two implementation efficiencies and the common movement-type refactor can proceed independently.
