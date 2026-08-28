# Decision record: one authoritative time budget per solver node (issue #428)

Written 2026-08-28. Records the budget-model rework for the node-graph solver and the decision on budget-source vs plain per-node seconds.

## The bug

Each heavy stage carried two budget-like knobs: the per-node time ceiling `budgetSec`
(read by `GraphRunner.budgetNanos` into the node's `deadlineNanos`) and a separate
optimize-mode search-size cap. Once the incumbent was feasible the stage ran in optimize
mode and returned immediately if that cap was zero, before it ever looked at the time
budget:

- `certBnb`: `if (optNodeCap <= 0) return` (catalog default 0)
- `bnb`: `if (optSec <= 0) return` (catalog default 0)
- `ilsPolish`: `if (roundCap <= 0) return`

So a user who set `budgetSec: 60` on every node of a custom graph (real report: capture
`thousand/j2.json` + `graphs/test_solver.json`) saw every optimize stage exit in ~0s,
because the optimize caps defaulted to 0. `ffSec`/`ffNodeCap` were never the problem: they
only gate the first-feasible/rescue phase, which fires while the incumbent is still
infeasible.

## The fix (Direction A: plain per-node seconds)

`budgetSec` (resolved through `GraphRunner` to `min(per-node budgetSec, remaining overall
deadline)` as `deadlineNanos`) is now the single authoritative control of the optimize
phase. The optimize stages are all anytime already (ILS loops rounds, both B&B variants
expand nodes), so they run until the deadline:

- The redundant `optSec` (a literal second time budget) is removed from `certBnb` and `bnb`.
- `optNodeCap` (`certBnb`) is removed as a user knob and demoted to an internal safety
  backstop, `OPT_SAFETY_NODE_CAP = 500_000` (the value `CertifiedBnbEngineTest` already
  proves safe under a 60s deadline). It never gates the stage.
- `roundCap` (`ilsPolish`) is removed; the node runs to the deadline (round loop capped at
  `Integer.MAX_VALUE`, the deadline stops it). `roundCap` on the separate `wrapIls` node is
  untouched: that node is already budget-authoritative (its `minRemainingSec` gate requires
  a positive time budget) and treats `roundCap`/`evalCap` as optional unlimited-by-default
  extra caps.

**Invariant now enforced in code, not just defaults:** in optimize mode a node runs iff its
resolved time budget is positive. `deadlineNanos <= 0` (no per-node budget and no overall
deadline) is the only thing that skips it. A nonzero time budget always runs the node up to
that long.

Built-in tiers keep their intent:

- FAST is byte-for-byte identical. It has no overall deadline; the ff phase still uses
  `ffSec`/`ffNodeCap`; optimize is disabled by giving the optimize-capable nodes
  `budgetSec = 0` (previously disabled via the zero optimize caps). Verified arithmetic:
  cert/bnb ff `solveDeadline` and optimize-skip decisions are unchanged.
- OPTIMIZE (`THOROUGH`) drives optimize off `budgetSec`. `bnb` is unchanged there
  (`budgetSec` already equalled the old `optSec`). `certBnb` now runs to `budgetSec` with the
  safety node cap instead of stopping early at 4096 nodes, which only tightens its certified
  gap within the same overall deadline.

## Why not Direction B (budget-source node + %/weight allocation)

The user proposal was a start/budget node holding a total time budget, with each node taking
either explicit seconds or a percentage/weight of that total, plus guard rails
(percentages sum sanely, leftover flows to the last unfinished stage).

Rejected for now:

1. It reintroduces a global timer that the node-graph architecture deliberately does not
   have. The design record `solver-node-graph-design.md` (#6) records that per-node budgets
   replaced remaining-time arithmetic; the built-in Optimize tier already resolves static
   fractions of `optimizeSeconds` at graph-build time. A percentage node would put that
   apportionment back into the runtime and need the exact guard rails the architecture was
   built to avoid.
2. The overall deadline (the effort tier's `deadlineNanos`) already provides the "total
   budget with leftover to the last stage" behavior for free: each node runs to
   `min(its budgetSec, remaining overall)`, and when the overall deadline is hit the
   remaining nodes get `deadline == now` and pass through. Plain per-node seconds plus the
   existing overall cap covers the user's goal without a new node type.
3. Plain seconds are the simpler UX and satisfy the ticket's invariant directly. Percentages
   trade an easy-to-reason-about "this node runs 60s" for "this node runs 20% of a total that
   depends on what the other nodes leave behind," which is harder to predict, exactly the
   confusion the ticket set out to remove.

If a future need arises for one-number total-budget authoring across a custom graph, the
cleaner path is a build-time helper that distributes a total into per-node `budgetSec` values
(the same shape `BuiltinGraphs` already uses), not a runtime budget-source node.

## Two node classes, and the "Max time (s)" relabel

Auditing the rest of the graph surfaced a second, non-bug source of confusion: not every node
that carries a time budget is an anytime search.

- **Anytime optimizers** (`ilsPolish`, `certBnb` optimize, `bnb` optimize, `wrapIls`): these
  loop rounds / expand nodes until the clock runs out, so `budgetSec` is a genuine work
  target. Label stays "Budget (s)".
- **One-shot recovery/seed nodes** (`dualChain`, `freeStartImprove`, `setupPeel`, `foldDriver`,
  `homotopyLadder`): these run a bounded procedure (a convex recovery solve, a fixed round
  count, an angle sweep, a homotopy ladder) and return the instant it succeeds or its
  structure is exhausted. `budgetSec` is only a ceiling, enforced by the node watchdog
  (`GraphContext.beginNode` arms `deadlineNanos`) and by internal deadline checks. More time
  buys nothing, and many of these also guard-skip (e.g. `homotopyLadder` skips a feasible
  incumbent, `setupPeel` skips when a candidate already exists), so they legitimately show
  ~0s in the run-state view.

This is not the #428 bug repeating: the budget on those nodes is doing its job as a max. To
stop the editor from implying they are timed searches, their budget knob is relabeled
**"Max time (s)"** (key unchanged, so presets are unaffected). `bnb` also structurally cannot
fill its budget: `BoundPrunedRecovery` spends `searchShare = 0.8` of it on search and reserves
the rest for a final polish, over a finite `maxPatterns = 64` pattern tree that it returns from
once exhausted or once the objective target is hit.

`FreeStartImproveNode` additionally threaded `deadlineNanos` into `improve()`/`jointRescue()`
and then never passed it to the underlying `FreeStartSolve` calls (which take no deadline); the
dead parameters are removed. Behavior is unchanged, the watchdog cancel token still bounds the
node.

## Editor

The node-param editor is spec-driven (`GraphEditorWindow` iterates `type.params`), so removing
the three optimize-cap params removes their fields automatically and the label changes flow
through with no UI code. Old JSON presets that still carry the removed keys load fine:
`GraphPresetIO.applyParam` silently ignores unknown keys, and re-saving drops them.
