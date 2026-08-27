# IMPLEMENTATION PROMPT: ARCH-2 engine fix (issue #422), the M2 batch

Paste this into a fresh session. M0, stage 1, and the FULL M1 batch (M1a-M1f + wrap-up) are DONE, gated, posted (issuecomment-5433258957), and committed (91b78396 core, 6d764632 research, plus the docs commit carrying this file). This session executes M2a (certified arc + gate B&B) and M2b (mandatory cleanup), then the M2 battery, the definition-of-done diff, and the final user touchpoints. The previous session prompt (`422-m1-session-prompt.md`) is superseded by this one.

## Mission

Close issue #422 to its binding definition of done (the G1-G5 checkboxes in the issue body). M2a delivers the certified byte-exact global search (gap=0 termination, the running gap mapped onto FAST/OPTIMIZE); M2b deletes the subsumed recovery stack, recovers the FAST floor, and locks the one-path/zero-flags/gap-contract structure. Scope unchanged: fixed inputs, find facings; NO input search anywhere (stratfinding is #424, parked).

## Execution mode (binding user rulings, recorded in issuecomment-5430476974 + amendment)

- Batched autonomous build: M2a then M2b in one pass. Orchestrate via ONE Fable subagent per task, each reporting back; the orchestrator keeps only plan state, gate results, and inter-task contracts, and runs and judges all gates ITSELF (never delegate run supervision of gradle/benchmark runs).
- Every numeric gate stays binding. A red gate is fixed and re-gated autonomously; escalate to the user ONLY for a design ruling the plan does not answer.
- Full battery at M2-complete, plus a checkbox diff against the issue body's definition of done. Post gate results to #422 as records, not approval requests.
- User touchpoints at M2-complete: one commit (ask; on 2026-08-27 the user granted a one-time direct-commit go for the M1 batch, that grant does NOT carry forward) and one in-game byte-exact QA pass (SimulatorEntity, 1.8.9 + Fabric: thousand, j003, one knife-edge capture, the sphere-snap yaw-lock set).

## State at handoff (2026-08-27, branch feature/solver-rework-arch1-cutover)

- M1 battery ALL GREEN except the declared FAST-floor red, posted as issuecomment-5433258957. Headlines: j003 FAST -31.299999992 at 0.9 s and THOROUGH-10s -31.299999974 in-deadline (STEP-9 hang gone); thousand-1-dup2 THOROUGH-10s 6523.307713024 byte-exact; j154-noturn-ja-inner FAST -1599.7002134 (beats the in-game F3 witness -1599.7001161289918); j1150-noturn-inner THOROUGH -2805.2952 (beats the p2 witness -2805.2990460856336); corpus 0 feasibility regressions at both tiers vs the STEP-9 baselines, FAST 62W/3R/44T vs OLD (all 3 pre-existing cutover dips), gains loopmm-tight-t39 + synth-legal-shortfall + j1150-hpk-at-THOROUGH.
- FAST floor RED and M2b-coupled: 109-jump basis median 125 ms (target <= ~80; released G1 bar ~59), p90 539 (ok), sum 41.6 s (OLD 29.2). M1f's sound levers are exhausted; the floor is the unconditional legacy recovery stack that M2b deletes.
- Wiring: FoldDriverNode = primary recovery on both tiers (BnbNode remains as fallback until M2b); HomotopyLadderNode = knife-edge/no-solution arm; driver rounds feed ClosestMiss + SolverTrace; kernel-certified infeasibility routes to no-solution; dF-chain specs decline to the existing nodes; sphere-snap yaw-lock path untouched.
- New solver components in core (all in `core/.../anglesolver/solver/` unless noted): FoldReplayDriver (fold fixed point + narrowing + polish ladder + polishFromAnchor), WallHomotopyLadder, AnchorSlp, BucketWalk, DiskSocpKernel.solveChords (two-axis chord rows + jitter ladder + weak-duality infeasibility certificate), graph/nodes/FoldDriverNode + HomotopyLadderNode. Tests: FoldReplayDriverTest, WallHomotopyLadderTest, BucketWalkTest, DiskSocpKernelChordTest, FoldDriverEngineTest, probes FoldDriverProbe/HomotopyProbe/KernelDiagProbe. Fixtures `captures/hpk_precise/` with witness provenance in TESTS.md; expects thousand-1-dup2 (TH10 >= 6523.3076), j1150-noturn-inner (TH10 >= -2805.2990460856336), j154-noturn-ja-inner (FAST <= -1599.7001161289918).
- USER RULING (2026-08-27): on j154 the X GE -1599.2 plate belongs at t34; the D2 number -1599.7237570 is spec-invalid (violates X@34 by 1.75e-2) and is dropped everywhere; F3 (-1599.7001161289918, in-game verified) is the witness.

## Required reading, in order

1. Issue #422: body banner + definition of done first, then the plan comment (issuecomment-5428709916), M0 result (5430366350), execution mode (5430476974), inventory (5430559757), and the M1 battery (5433258957) which records every M1 judgment call.
2. `docs/research/global-solver-2026-08/ARCH2-STEP1-SIMPLIFY.md` (verdicts; the B&B design constraints live in the reduction stack).
3. Code: FoldReplayDriver, DiskSocpKernel (solveChords + certificate), BucketWalk, AnchorSlp, WallHomotopyLadder, FoldDriverNode, BuiltinGraphs, then the delete set (BoundPrunedRecovery, SeamSweepRecovery, GateMip, RelaxationRecovery, SphereDecodeSnap, ResidualRescue) and the inventory comment's migration notes.
4. `core/src/test/.../anglesolver/TESTS.md` (suite map incl. the M1 additions) and `docs/research/solver-rework-2026-08/BENCHMARK-STEP9.md` (method).

## M2a: certified arc + gate B&B (size L)

Nodes = arc brackets + gate fixings; relaxation = DiskSocpKernel + chord rows (the SAME cut family the M1e greedy narrowing uses: FAST = greedy narrowing, CERTIFIED = branching the same cuts) + cheap endpoint tangent cuts; branch order from the disk solve's own throttled-tick geography (ResidualRescue's costate-degeneracy detector migrates here); 3-way gate split only on replay-falsified folds + the low-velocity tail (fold first, always); incumbents from the M1 driver + costate dive at depth + tail pinning; CostateDualSolver dual value as the anytime prune bound (never gate on pgres); round-and-simulate through ExactJumpModel; gap=0 termination; the running gap maps onto FAST/OPTIMIZE (this is also the G4 OPTIMIZE contract: best incumbent + certified bound gap at every deadline, gap may be nonzero at budget, a hang or empty return is a fail).

GATES: certified equal-or-better on the expect corpus; first certified byte-exact global optima on small captures; root bounds validated against the COPT research oracle (research/copt, FeasTol 1e-9). Certification targets carried from M1: thousand 6523.30772 (measured bucket-lattice local optimum 6523.3077130 at M1, 7e-6 short) and the j1150 FAST witness-family reach (-2805.2990460856336; cross-basin for anchor-local machinery, H2). The fold encodes only the zero side of gate patterns; a certified gate branch needs BOTH sides encoded (keepAliveWall exists) or the node bound has a hole. n=176 kernel cost (~30-60 IPM solves, ~9 s THOROUGH j003) is the declared k-gon-on-measured-need trigger; build the k-gon LP node engine only if B&B node throughput measures as the wall.

## M2b: mandatory cleanup (the ticket's agreed step 3)

- DELETE after migrating the inventory's listed keepers: BoundPrunedRecovery 1253 LOC (keepAlivePatterns, carryProfile band ordering, search/polish watchdog split, ClosestMiss offers), SeamSweepRecovery 628, GateMip 582 (critical-tick band + pattern enumeration + per-pattern bound/certificate machinery are M2a ingredients; its infeasibility certification is now sound via the kernel certificate), RelaxationRecovery 383, SphereDecodeSnap 202 (the M1d leaf supersedes; PRESERVE the yaw-locked adoption contract and the save/playback path, STEP-8 QA class), ResidualRescue. Measure-then-decide: LevelSetAscent, IlsPolish, BucketAscentPolish. Net LOC reduction is binding (~3400 LOC of delete candidates vs the M1+M2a additions).
- FAST floor: with the unconditional stack gone, hit the M1 ship target (median <= ~80 ms, p90 <= 562) AND the G1 bar (strictly faster than BOTH releases on median, p90, AND total; released median ~59 ms). Regenerate release baselines from tags v1.9.0 (ac1b35e2) and v1.10.0 (08b013ad): worktree per tag, drop CorpusBench in (STEP-9 method), FAST + TH10 over the full set including the new captures, commit as corpus-REL190-{FAST,TH10}.tsv and corpus-REL1100-{FAST,TH10}.tsv (the STEP-9 OLD TSVs are from 3d19c9ff, NEITHER release).
- SlpSolve default-seed disposition (the M1f revert stands unless re-measured better), CostateDualSolver final role (bound + dive + tail pin + ClosedFormSolve/FreeStartSolve call sites only).
- G3: exactly one solve pipeline, enforced by a committed corpus-wide SolverTrace shape test (one distinct stage sequence across all captures at both tiers, modulo loop counts and B&B depth).
- G2: zero flags (`grep -rn "pkc\." core/src/main` = trace logging only), zero unreachable classes under anglesolver/ (jdeps or IDE inspection clean).
- GATE: STEP-9 rubric PASS-on-structure; full suite + corpus green; zero feasibility regressions vs the union of both release baselines; G1 objective dominance (never worse than max(REL190, REL1100) within 1e-4 anywhere, strictly better wherever certification shows headroom, equal to the certificate wherever it completes).

## Then: battery, DoD diff, ship

Full battery (slow suite; CorpusBench FAST + THOROUGH-4s + TH10 suspects vs the release baselines AND the STEP-9 TSVs; named targets; determinism OI-17; floor stats 109-jump basis; zero-flags grep), a per-checkbox diff against the issue body's G1-G5, post to #422, PAUSE for the user's commit + in-game QA (thousand, j003, one knife-edge, the sphere-snap yaw-lock set). After QA: DESIGN.md status banner update, #424 stays the only open follow-up.

## Traps and facts measured in M1 (do not rediscover)

- M0's python driver ran chord narrowing in EVERY leg (`--decode-mode chord` default); fold-alone anchors on momentum-interior captures are costate noise. Decode from the primal u, never from the costate g (boost ticks have |g| ~ 5e-13 while u carries the direction).
- `Wall` is single-axis; chords couple ux and uz of one tick; use DiskSocpKernel.solveChords (native two-axis rows). The pre-M1 kernel FAKE-CONVERGED on some infeasible specs; the weak-duality certificate (FAIL_UNBOUNDED) is what makes infeasibility claims sound. bfsetup2 is kernel-certified infeasible in the clamp-free model up to 0.05 relax (feasible only near 0.3); whether it is byte-feasible at all is unestablished; nix-full-t1 stalls at 4.6e-5.
- Cross-round chord persistence measured HARMFUL on pattern-thrashing captures (re-derive per round). Uniform 2e-6 clearance in the objective walk measured DEAD (forbids legal pad-edge rides); clearance applies to `WallHomotopyLadder.collisionWalls` only. MARGIN_ANCHOR_CAP = 0.5 (margins from insane anchors distort walls). Bucket moves must be bucket-INDEXED and era-aware (FacingLattice is legacy-only; 26.x uses sinStep262 double indexing).
- SlpSolve default-seed switch to the driver decode measured HARMFUL (j021 -2.0e-2 FAST + latency) and was reverted; do not redo it without fresh measurement.
- Deterministic box-corner multi-start mostly fails cold on thousand (viol 0.8-1.9); the 6523.30772-class point is reachable by polish only from the right family anchor. That family selection is exactly what M2a certification must provide.
- The j1149 FAST/TH10 regression (-6e-4 class) and the j318/gh386-j335 FAST dips pre-date M1 (cutover-era); the 4 M1e dips (deserthard x2 -3.0e-4, j321 -1.8e-4, j001 -1.0e-4) trade against the j003 +1.03 win and slower window-path colds; all recorded in the battery comment.
- dF-chain specs (F-mode walls) are not linear-model-compilable; the driver declines and the graph falls through (gh313-j121-dfneo class). FacingPrefold integration remains unbuilt; do not silently regress these captures when deleting recovery nodes.
- hpk_precise fixture provenance (TESTS.md has the map): DF constraints stripped (inner problem = free facings), j154 debug synthesized from the D2 artifacts (the save had zero debug ticks; row-fallback would force sprint=true and break the human latch), j154-inner carries the t34 plate per the user ruling.
- PKC_CORPUS_FILTER is comma-separated substring tokens, not regex. CorpusBench writes core/build/reports/corpus-<tag>.tsv; comparator docs/research/solver-rework-2026-08/benchmark/compare.py; floor stats on the 109-jump both-success basis excluding 29s+ timeouts. Probe runs: `java -cp "$(cat core/build/test-classpath.txt)" org.junit.runner.JUnitCore de.legoshi.parkourcalc.anglesolver.<Probe>` from the repo root; `./gradlew :core:processTestResources` after fixture edits.
- Full solver suite: `./gradlew :core:test -PslowTests` (~7 min). COPT FeasTol trap on the research side: always 1e-9 and check max|u^2-mMag^2|.
- Memory files for continuity: project_422_m1_batch.md (this batch), project_422_engine_fix_m0.md, project_arch2_step1_simplify.md.

## Hard rules

- Never git commit, push, or branch; the user handles all git (the M1-batch direct-commit go was one-time).
- No code comments, no em dashes in repo writing, core stays Java 8 and Minecraft-free, COPT stays research-side only.
- Byte-exact verification through ExactJumpModel replay only; self-agreement is not verification; wall-face constraints keep >= 2e-6 clearance in solves (pad/floor edges exempt).
- Do not run :runClient while Minecraft is open.
- Deadline/budget behavior is part of every gate: OPTIMIZE returns best-so-far plus a certified gap at the deadline, never hangs.
- A permission denial is probabilistic: retry 3-4x before reporting blocked.
