# The re-pasteable per-session implementation prompt

Paste the block below into a FRESH Claude Code session at the repo root each time. It self-locates via
BUILD-LOG.md, implements ONE stage, verifies, writes a handoff, and stops, so you can clear the session and
paste it again for the next stage. The agent also writes the exact next prompt to
docs/research/solver-rework-2026-08/NEXT-SESSION-PROMPT.md (usually identical to this one), if you prefer to
copy that.

---

# MISSION: implement the next stage of the re-founded angle-solver pipeline (ARCH-1), then hand off

You are Claude Code at the repo root of ParkourCalculatorMod (Windows; PowerShell primary, Bash tool available). A prior research campaign proved the angle solver's continuous problem is a linearly-constrained constant-modulus program that reduces to a convex solve plus a low-dimensional nonconvex residual over the "vanishing-costate" ticks, globally optimal and fast for the sizes the tool hits, recovering measured objective the shipped solver misses on coupled multi-jump. You are implementing the full pipeline ONE STAGE PER SESSION so context stays small.

## Step 1: locate the stage
Read docs/research/solver-rework-2026-08/BUILD-LOG.md. Find the next stage that is NOT DONE (continue an IN PROGRESS stage from its latest handoff entry; otherwise start the first NOT STARTED stage in order). You will implement ONLY that one stage this session. Mark it IN PROGRESS in the table now.

## Step 2: read the references for that stage
- docs/research/solver-rework-2026-08/IMPLEMENTATION-GUIDE.md (build order in section 0; the stage's detail in section 2 for P0, sections 3 and 3.5-3.10 for P1-P7; the reuse API map in section 1; acceptance targets + verify commands in section 4; load-bearing gotchas in section 5)
- docs/research/solver-rework-2026-08/SPEC.md (math in section 4, ARCH-1 in section 6), RESEARCH-DOSSIER.md (the method + citations for this stage), RESULTS.md + stageE/{poc-residual-validation,byte-exact-roundtrip}.md (measured benchmarks + refinements), stage0-copt/FINDINGS.md (COPT reference optima)
- AGENTS.md (repo rules, tick indexing). For any defect anchor referenced (F1..F15, SB1..SB8) see stageA/SYNTHESIS.md and stageB/SYNTHESIS.md.
- The BUILD-LOG handoff entries above the table (what prior stages did, decisions, flags, gotchas).

## Step 3: implement ONLY the located stage
Follow IMPLEMENTATION-GUIDE for that stage exactly. The overall target architecture (context, do not build ahead): one flow = compile once -> converged convex disk kernel (returns bound + active set + costates g_t) -> closed-form non-degenerate ticks u_t=m_t g_t/|g_t| -> residual solve over the degenerate set D dispatched by k=|D| (k=0 none, k=1 golden-section, k=2 nested, k=3-4 tiny spatial B&B, large-k Riemannian on the product of circles), each candidate re-optimizing the rest convexly and byte-exact-scored -> objective-aware sphere-decode snap + ExactJumpModel certify -> one give-back-constrained trend-filter smoothing. Free-start = 2 p0 vars + translation; dF = phase constraints; inertia gate = ~2n big-M indicators (hybrid); single/half-angle jumps keep the shipped fast path. Build stages P0..P7 in order; the reused dual/SLP lets P1 run before the P3 IPM exists, then P3 swaps the convex step under a stable interface.

## Hard rules (violating any fails the session)
- Never git commit/push/branch/stage. Leave changes in the working tree.
- No code comments anywhere (no javadocs/inline notes; pick clearer names; leave existing comments). No em dashes in any writing.
- Shipped path GREEN on ./gradlew :core:test -PslowTests at every handoff. Run the fast suite (./gradlew :core:test) after each change and the full slow suite (~4 min, long timeout) before finishing. Tag new corpus-driving tests @Category(de.legoshi.parkourcalc.SlowSolverTests.class). New stages default-off (a system property flag) until proven; main-side additions behavior-neutral when off / getters unused.
- core/ Minecraft-free. Do not break Application.runSimulation(). No shipped numeric-solver dependency (pure-Java dependency-free; no redistributable pure-Java SOCP exists and adding one is net-negative across the 3 loaders; COPT is a research oracle only, NEVER shipped or imported by any module).
- Honor the measured gotchas (IMPLEMENTATION-GUIDE section 5): hold-rest-rigid is INFEASIBLE (re-optimize per pinned angle); the convex path bails on facing constraints (pin via SlpSolve/YawTies until P3); snapping a continuous optimum is byte-exact suboptimal (always score byte-exact); do not route half-angle single jumps through the convex core; gate captures need P4; FEAS_TOL=0; verify pin tick-indexing by replay.
- Verify against the COPT references and byte-exact through ExactJumpModel (commands in IMPLEMENTATION-GUIDE section 4). Determinism must hold. Run the formatter/linter and affected tests; fix failures before finishing.

## Step 4: HANDOFF (mandatory, at the end of the session; this is what lets the next session continue cleanly)
1. Confirm the slow suite is green.
2. Append a dated handoff entry to BUILD-LOG.md (newest first) containing: the stage; whether it is now DONE or still IN PROGRESS; the files added/changed and any new flag names; the MEASURED before/after per capture (objective + wall-clock) against the acceptance targets; the key decisions and any gotcha hit; and, if IN PROGRESS, the precise NEXT STEP to resume (what is done, what remains). Update the stage status in the table.
3. Overwrite docs/research/solver-rework-2026-08/NEXT-SESSION-PROMPT.md with the exact prompt to paste next (normally this same prompt verbatim; if the located stage is still IN PROGRESS, add a one-line note at its top naming the stage and pointing at its handoff entry).
4. STOP. Do NOT start the next stage. Report in your final message: the stage completed, the measured results vs targets, the green-gate result, the files changed, and that the handoff + next prompt are written. Do not commit.

If the located stage is genuinely blocked, do not half-implement it: record the exact measured blocker in the handoff entry, leave the stage IN PROGRESS with the blocker as the next step, write the next prompt, and stop.
