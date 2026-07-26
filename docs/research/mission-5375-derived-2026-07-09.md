# Derived mission: rung 5.375, first legal solve or impossibility dossier

## EXECUTION RECORD 2026-07-09/10 (overnight session; read before continuing)

Step 0 DONE: suite green; NormCellProbe (tag nc0709a) replicated all 39 ILS scan lines exactly, probe-validated the signed region table at 13 wrap bases to +-2880 (envelope law only approximate per-degree, excess to ~1.7e-6), emitted two-sided caps (normcell-caps-nc0709a.json) for +-30 and +-2 deg windows.

Lever S EXECUTED (user re-directed to prepend mid-sweep): StructureVariantDump harness (PKC_SVAR_*) authors trust-gated variant dumps; control byte-exact. Mid-chain insertions certified dead (pre38-k1 partial bound ~212.6095, friction drains carried speed x0.546 per grounded tick). Prepend: circle feascenter t* = -1.431e-5 (k=1) vs -1.428e-5 (k=2): SATURATES at one tick. Reface (mirror strafe, diagonal t14-24, new arm in the harness): per-tick-capped annulus t* -3.535e-6 alone, -3.484e-6 with prepend: REDUNDANT with prepend, not additive. Base control capped t* -3.722e-6. ALL structures converge ~-3.6e-6; binding is a certified 7-wall vise (pad, raised rung Z, platform X pair, tail Z/Z/X). PRIMARY (viol<=0) certified dead in-tube (+-30/+-60) for all swept structures; capped model is a relaxation so the negative binds byte-exact reality. build_model.py gained: --goal-wall, --tube-wide-ticks/deg, --v0-eps, --annulus-caps (per-tick realizable caps from the probe regions), --feascenter usage as THE cheap decisive probe (1e-8 ceiling grinds on pad-straddling gaps waste hours; feascenter certifies in minutes).

SECONDARY pivot: capped legal ceilings certified in 25-129s each: base shortfall 3.101e-5, ref 2.935e-5, preref 2.909e-5 (9x inside community 2.74e-4). Lever 1 RUN AND CLOSED: legal MOVE-MILP (move_milp.py modes legal/padless, new) INFEASIBLE at span +-16 AND +-64 (8642 moves); the X-vise (X@37lo vs X@42, 2.45e-5 overlap deficit) survives all simultaneous non-flipping cell moves; padless minimax = EMPTY SELECTION optimal at -1.2247e-5 at both spans: the ILS point is a balanced 5-wall pinch, locally optimal even with the pad deleted. legalIls (new MiqcpClose harness, PKC_MIQCP_LEGALILS_*) from the smooth legal optimum collapsed to legal-but-1.38e-2 (third reproduction of the smooth-then-snap architecture verdict, now on the legal metric).

NEXT (in order): (1) Lever 2 exact-cell-set MILP with 3-state gate disjunctions: the 4803 gate-flipping moves discarded from the span-64 table are the one unexplored direction, and the legal objective (R1) is now the primary target, not just the ceiling; (2) basin diversity for legal realization (perturbed objectives, kick seeds); (3) dossier assembly has most of its PRIMARY-side evidence already certified. v0-eps ladder skipped as answered by prepend saturation (flag stands in build_model.py if wanted). User rulings this session: prepend legal if start stays in the original tick-0 box; mid-chain arms skipped by directive.

Written 2026-07-09 (evening session) after independent artifact verification per next-session-bootstrap-5375.md Phase 1, then revised after one Opus adversarial review (section 8 records its findings and dispositions; the review falsified this plan's original headline law and reversed one refutation into the lead solve lever). This is the Phase 2 deliverable. Nothing below has been executed; execution starts only on user approval.

## 0. Binding rules (inherited, not re-derivable)

1. Records are LEGAL-METRIC only: every wall satisfied except the landing constraint; the community's 2.74e-4 legal attempt is the standing record.
2. Nothing is claimed solved or a record until it replays clean in the user's live tool; headless byte-exact verification is necessary, not sufficient.
3. All operational traps and user rulings in razor-campaign-2026-07-09-handoff.md are binding: locked RAW rows only, no wrapAll in verify paths, display tick = internal + 1, in-memory rung patch (RazorFixtures.applyRung5375Patch, count-asserted), gradle --rerun and --no-daemon on env-gated runs, applied: line grep, watchdog from minute one, never edit ExactJumpModel/McSineTable/Constants.

Definition of done, in order of value (unchanged): (1) PRIMARY viol <= 0 on the patched rung, delivered as locked-RAW-row TAS, fresh-reparse byte-exact, user in-tool confirmed; (2) SECONDARY a legal run with shortfall < 2.74e-4, same delivery bar; (3) TERTIARY a certified realizable ceiling below the pad, two independent solvers, assembled as an impossibility dossier with its scope conditions stated.

## 1. Verified evidence base (checked against artifacts this session, not inherited)

- Circle (norm=1) ceiling, goal removed: COPT 212.69988284925836 (optimal), SCIP 212.69988451088642; agreement 1.7e-6 (note: SCIP ran at gap 1e-6, the coarse setting); pad 212.699999988079; ceiling = pad minus 1.17e-4.
- Annulus (norm within 1 +- 9.594e-5 at every tick) ceiling, goal removed: COPT 212.70005099250744 optimal at gap 1e-8 = pad plus 5.1e-5; SCIP stopped at gaplimit with bound 212.700158578034, 1.08e-4 away: the 2e-6 cross-solver standard is UNMET for the annulus family, and the artifacts give no reason to expect SCIP spatial branch-and-bound to converge on these 49-quadratic nonconvex models.
- Feascenter (goal HARD, maximize uniform slack): t* = 6.276056834e-06, COPT only, no SCIP twin; the annulus incumbent's own norm exceeds its cap by ~1e-6 (default FeasTol slack), so t* sits at solver-tolerance scale: indicative, not certified.
- Best byte-exact point: viol 1.2246666e-5 (rung5375-ils-point.json, delivered as ATTEMPT_5.375bm_closest.json, fresh-reparse dViol=0). It violates FIVE walls (X@49lo, X@37lo, Z@48, Z@41, X@42, each ~1.22e-5): NOT legal.
- MOVE-MILP certificate coverage, exactly: per-tick candidate cells within +-16 sin buckets of the ILS facings, wrap bases {0, +-360, +-720}, cap 250/tick, single-cell gate-flip moves excluded at dump time, at most one cell change per tick, tx/tz continuous over the authored box; objective = maximize min slack over all 20 walls (minimax ONLY, move_milp.py:44); optimum selects ZERO moves. The combined-move additivity check in the delivered artifact is vacuous (k=0); any multi-move MILP winner must be exact-resimmed and gate-pattern-checked before its number is used.
- Kick-cycle local optima: ten kicks clustered 1.227e-5..1.279e-5 (floor stability), gf vectors NOT persisted (log deltas only).
- Legal-metric state: warm-chained ALM+snap legal run 2.005419172e-3; cold 2.674e-3; community 2.74e-4. The proof-yaw warm reaches X@49 = 212.7001881826 (1.88e-4 ABOVE the pad) but is illegal at Z@12lo by 6.25e-2: the shape has pad-reaching power, legality is the binder.
- Realization: locked RAW rows, fresh-process reparse, working at facings to 718 deg; rung's raised walls exist only as the harness patch.
- Architecture verdict (smooth-then-snap cannot cold-solve razors) re-confirmed: ALM+snap legal collapses to 2.005e-3 while the lattice holds 1.22e-5-class points.

## 2. The norm-cell structure (corrected by adversarial review; probe-validated version is Step 0's deliverable)

Cast-chain facts (verified in McSineTable.java:19-25, FacingLattice.java): sin index = (int)(x), cos index = (int)(x + 16384.0f), both from the SAME float x = rad*10430.378f; misalignment (the only source of |norm-1| beyond ~1e-7 table noise) can come only from rounding the +16384.0f add, i.e. binade-straddle zones of x. Envelope law VERIFIED to 4 significant figures against scan data: |norm - 1| <= (2*pi/65536)*|sin 2*theta| ~ 9.594e-5 at theta = +-45/135 deg.

Signed region structure (reviewer's complete enumeration, matching the ILS scan counts exactly; Step 0 re-derives and pins this):
- facings in (0, 90): only norm<1 cells (down to 1 - 9.59e-5 near 45); ZERO norm>1 cells at any wrap base to +-2880.
- facings in (90, 180): norm>1 cells up to 1 + 9.594e-5 (max near 135).
- facings in (180, 270): NO perturbed cells at all.
- facings in (270, 360): norm>1 cells up to 1 + 9.594e-5 (max near 315).
The original draft of this plan claimed elevated cells exist only in (270,360); that was FALSE (the scan's passNormHigh counts only the positive side, and the draft misread it). Deep wrap bases do not change the region structure (verified at the diagonal to +-2880): they only enrich cell choice inside the windows.

Consequences:
1. The annulus certificates ride norm ~1.0000969 at every diagonal tick t14-24 (facing ~45). At facing 45 there are ZERO realizable norm>1 cells, so the +5.1e-5 annulus ceiling is unrealizable UNDER THE CURRENT KEY PATTERN, and the realizable ceiling for that pattern is strictly lower.
2. THE REFACE LEVER (found by the adversarial reviewer; the draft had wrongly refuted key re-authoring): the diagonal's movement heading phi = baseArg + facing is preserved by the mirror strafe key (baseArg -45 instead of +45, identical mMag), which moves the required facing from 45 to 135, INSIDE a norm>1 window at the envelope maximum (computed: max cell norm 1.0000959214 within +-1 deg of 135). Re-authoring the strafe key on the diagonal ticks makes norm-riding available exactly where the annulus model wanted it. This is the most concrete unexplored solve path, and it means no impossibility dossier can claim pattern generality without certifying the refaced pattern too.

## 3. Diff against next-session-prompt-5375.md

Agreements: per-tick realizable norm re-certification is the fork-deciding move (their lever a); DoD ordering; all orchestration rules; legal push exists there as lever (f).
Disagreements, justified:
1. Their (f) names no concrete legal machinery; the ALM+snap legal path measurably collapses (2.005e-3). The concrete move is the legal-objective MOVE-MILP over the exact move table around the 1.22e-5 point, which was never run (move_milp.py is minimax-only). Promoted to first action, with tempered expectations (section 4, Lever 1).
2. Their "that point is a dead end by local moves; do not burn compute re-searching its neighborhood" is correct only for minimax; the legal objective over the same neighborhood is unsearched. Overridden with evidence.
3. Their (d) "deeper wrap ranges (+-1080)" cannot open the diagonal at its CURRENT facing (verified to +-2880); deep wraps are enrichment inside windows, not a new-window mechanism. Downgraded accordingly.
4. Their (b) "~30 s each end to end" for kick-optima certificates omits that the optima are not persisted; a small normIls persistence change plus a re-run (or log replay) comes first.
5. Their (c) gate-pattern alternatives and (e) structure sweep: (e)'s key-release binaries gain a sharper sibling, the reface arm (section 2 consequence 2), which is promoted to a primary lever; the rest of (c)/(e) is scheduled inside the hunt fork or as dossier robustness.
6. Method pivot beyond their list: the annulus-MIQCP cross-check standard is unmeetable by SCIP on this class (artifact-verified), so certification pivots to an exact-cell-set MILP (linear, both solvers converge, byte-exact-consistent cell values); the held-in-reserve Gurobi 30-day eval is the designated second engine for the final dossier-grade bound if SCIP cannot match COPT even on the MILP.

## 4. The plan (re-ranked 2026-07-09 late: user directive puts structure variants first; section 4.S)

### Lever S: input-tick structure sweep (NEW RANK 1, user-directed; budget 1 day to a saturation curve; kill: every admissible variant's circle ceiling stays below pad)

Why this outranks everything: the rung is razor-class BECAUSE the inherited 49-tick structure's ideal smooth ceiling sits 1.17e-4 BELOW the pad (certified); all lattice/norm work fights for ~1.7e-4 of scrap. The structure facts (miqcp-dump-rung5375.json): initialVelocity = rest, t0 is already the first sprint-jump (zero run-up), jumps at internal t0/t13/t25/t38, run ticks exist at t12/t37 only, the t25 land rejumps immediately, and the tail arc must average 0.229/tick +Z (Z@41 LE 4.95 to Z@48 GE 6.55) while its warm vz decays 0.23 to 0.19. One well-placed grounded tick changes velocities at the 1e-1 scale; the deficit is 1.2e-5. Precedent: the uncorrected 5.4375 was closed exactly by a run-tick structure edit (user's insight), and the momentum ladder unlocked via jump-tick shifts.

S1. Variant family: insert k in {1,2} grounded run ticks at each insertion point independently: (i) before jump 1 (prepend; needs per-tick platform-bounds walls from the start box), (ii) after the t25 land (rejump becomes land, run, jump), (iii) extra tick before jump 4 at t38 (tail feed, the speed-starved segment), (iv) extra tick at t12. Combinations of the two best single insertions afterward. Every variant shifts all downstream constraint ticks by +k (walls belong to their arcs; land grid = space + 11 internal preserved); run ticks get standability walls derived from the land-tick walls.
S2. Per variant: authored fixture (KEEP-mode rows), plumbing check (score the shifted warm and verify the expected signature, the campaign's 6.2486e-2-style check, count-asserted patch discipline; the false-solve trap is the main risk here), MiqcpDump, circle-model MIQCP ceiling (goal removed, gap 1e-8, tube widened to +-60 deg on ticks near the insertion), ~30-60 min each mostly automated.
S3. Decision rule on the saturation curve: any variant ceiling >= pad + 1e-3: the razor pathology is gone; solve THAT variant with standard machinery (warm-chain from shifted proof/ILS shapes + existing closers), byte-exact verify, deliver, user in-tool check. Ceiling between pad and pad + 1e-3: still razor-ish; bring Lever 2's cell-set machinery to the best variant. All ceilings below pad: structure lever dead; proceed to Levers 1/2 unchanged, and the dossier gains the certified-variants appendix.
S4. Physics expectation stated up front (so the sweep can prove it wrong): the tail insertions (iii)/(ii) look strongest (direct feed to the starved t39-48 arc); the prepend (i) looks weakest (the lead is a momentum reversal: minus-Z entry speed deepens the first hop against the RAISED Z@12lo wall, and carried speed dies at the t13 reversal). The certificates decide, not this paragraph.
S5. Legality assumption (user to confirm): extra grounded run ticks are a route choice available to any player, hence record-legal; the jump's geometry (blocks, pad) is untouched.

### Step 0: validation and baseline (budget 2.5 h, blocking; runs alongside S1 authoring)

0a. Signed norm-cell probe (env-gated test-source probe reusing FacingLattice.cellRepresentatives, which does exact adaptive enumeration; NOT a fixed-sample grid): for each facing bucket over the full circle and each wrap base in {0, +-360, +-720, +-1080, +-1440, +-2160, +-2880}, enumerate ALL joint cells and record signed min and max norm deviation. Output: JSON cap table + report. Acceptance: (1) reproduces the ILS scan counts on both sides (t14 189-cell count; t42-48 positive-side counts; t28 sliver) and the reviewer's region table (section 2); (2) covers the FULL +-30 deg tube around every incumbent facing (the prior scan only covered +-2.8 deg); (3) two-sided caps per (tick, window, base-set) emitted for Lever 2. If the region structure fails validation, Lever 2 still proceeds on the empirical per-tick two-sided caps; the law is explanatory, not load-bearing.
0b. Suite baseline: ./gradlew :core:test --rerun --no-daemon green; report mtimes verified.
0c. Watchdog armed before any long run (section 6).

### Lever 1: legal MOVE-MILP on the existing table (budget 4 h to a verified number; kill: verified legal shortfall >= 2.74e-4 on the existing AND one widened AND one re-dumped-around-candidate table)

1a. Add mode "legal" to move_milp.py: maximize pad eval E(X@49lo) subject to every other wall's slack >= 0, one move per tick, tx/tz in the authored domain, solution pool >= 10.
1b. Exact verification (moveApply extension): full resim per pool solution, gate-pattern equality asserted (reject flips), translated legal scoring, additivity error |predicted - exact| reported. EXPECTATION SET BY REVIEW: the legal fix must resolve an X-axis vise (X@37lo/X@49lo want +tx, X@42/X@24hi want -tx) and a Z-axis vise (Z@48 GE vs Z@41 LE), so coupled k>1 selections are required and additivity may degrade; candidates above 1e-7 additivity error trigger a RE-DUMP of the table around that candidate (re-linearization) and one MILP iteration from there, not acceptance and not silent rejection.
1c. If a verified legal point beats 2.74e-4: widen once (span +-64, cap 500) and re-run legal AND minimax (a widened empty-selection minimax also extends the certified neighborhood). Best verified legal point becomes the SECONDARY deliverable candidate: locked RAW rows, fresh reparse, ATTEMPT_5.375bm_legal.json, user in-tool check before any record language.
This lever is genuinely cheap to TRY (the table exists; solver minutes); it is not guaranteed cheap to WIN (the vise coupling may need the iteration loop). Its result also calibrates Lever 2's certified legal ceiling from below.

### Lever 2: exact-cell-set certification, the decisive fork (budget 10 h to a verdict; primary method pivoted per review)

Primary method: EXACT-CELL-SET MILP. Per tick, the finite set of realizable (s_val, c_val) pairs (real LUT doubles, rotated per baseArg) over the facing window and base set from 0a; one binary per cell, SOS1 per tick; positions linear in the choice; tx/tz continuous; gates handled by restricting to the base gate pattern first (matching the MOVE-MILP condition) with the 3-state disjunction arm as extension. This is tighter than any annulus (no over-relaxation between cells), linear (no spatial branching: both COPT and SCIP can prove it), and byte-exact-consistent up to double-recurrence fidelity, which is spot-checked (section 5). Window sizing arms: +-2 deg (cell-faithful, small model) then +-30 deg (tube-comparable; if the cell count explodes, bucket-coarsen the tails of the window where |sin 2*theta| envelope caps the effect, and state it).
Runs, each with three objectives: (R1) max pad eval, other walls hard, goal removed = the certified LEGAL ceiling; (R2) feasibility / max min-slack with pad included = the solve question; (R3) minimax as continuity check against the MOVE-MILP.
Corroboration arm: the interval-cap (two-sided per-tick annulus) MIQCP from the 0a table, COPT-only if SCIP cannot converge, reported as secondary evidence only. Gurobi 30-day eval is requested ONLY if the dossier fork is reached and SCIP cannot reproduce COPT's MILP bound (the eval is a one-shot resource; user sign-off before requesting).
Patterns certified, in order:
- P0: current key pattern (ILS incumbent facings).
- P1: THE REFACE ARM: mirror strafe on the diagonal ticks t14-24 (baseArg sign flip, facing 135-class windows, same mMag/f4), dump regenerated from re-authored rows (MiqcpDump on a KEEP-mode variant fixture; jump/boost ticks inside the window handled per the legacy boost-cast rules); cell-set MILP with the same three objectives. Also any other tick whose facing sits in a normless region and has an in-window mirror.
Fork logic (wording per review):
- If R2 shows a feasible cell selection with min slack > 0: exact-resim the selection (byte-exact arbiter), then wrap-ILS polish, then delivery path. A model-feasible selection is a CANDIDATE, never a claim, until byte-exact and then user-confirmed in-tool.
- If R1 ceilings for BOTH P0 and P1 (and kick-optima seeds, Lever 3) land below the pad with two-solver agreement <= 2e-6 on the MILP: dossier fork. t* > 0 in any relaxation is never read as "solve exists"; only the negative direction certifies.
- Straddle or solver disagreement after one tightening iteration: report to user with numbers; no silent grinding.

### Lever 3: corroboration and diversity (budget 3 h, overlapping Lever 2 wall time)

3a. Persist kick-cycle optima (small normIls addition, re-run or log replay); MOVE-MILP (both objectives) at the 3 best distinct optima; expected minimax empty-selection replication is dossier corroboration, any legal winner is a seed.
3b. Perturbed-objective incumbents (3-5 linear tilts on the cell-set model) for basin diversity, each wrap-ILS'd.

### Dossier assembly (only on the dossier fork; budget 6 h)

Contents: two-solver cell-set MILP certificates per pattern (P0, P1, kick seeds) with tolerances and logs; scope conditions stated as hypotheses: facing windows/tube, key-pattern set actually certified (P0, P1, swept variants), gate-active band (ticks outside |v|<0.1+-2 carry unconditionally), wrap-base set and |gf| cap, authored start box, VMAX velocity box, double-recurrence fidelity spot-check bound, solver feasibility tolerances; the signed norm-region structure with probe data and mechanism; byte-exact corroboration (ILS floor, MOVE-MILP optimality at ILS point + kick optima, proof-neighborhood census, best verified legal point vs certified legal ceiling); refuted alternatives WITH their evidence (deep wraps at fixed facing, ALM+snap architecture verdict, prior campaigns' structural edits); residual unknowns (basin families outside all tubes, key patterns beyond the swept set, runway/jump-timing variants, |gf| beyond the probed cap, full-circle bound open). Written to docs/research/rung5375-impossibility-dossier.md; handoff updated.

## 5. Deliverables and claim discipline

- Any SOLVED/ATTEMPT file: locked RAW rows, fresh-process reparse verify, game-folder delivery, then USER in-tool confirmation before any claim; display ticks in user communication; rung patch caveat in the file notes.
- Every reported number carries its verification path (MILP predicted vs exact resim vs fresh reparse).
- Double-recurrence fidelity: at every certified optimum and every candidate selection, the model-predicted positions are checked against ExactJumpModel resim; the observed bound goes into the report (expected ~1e-13 per the MOVE-MILP additivity evidence; anything above 1e-9 halts that arm for diagnosis).
- The community 2.74e-4 is beaten only by a verified LEGAL shortfall; maxViolation numbers are never compared to it.

## 6. Delegation, instrumentation, watchdog

- Roles: this session is architect/critic/validator; code increments and long runs go to Opus subagents with exact deliverable + acceptance test + file paths; extraction to cheaper agents; reports without acceptance output are rejected.
- Watchdog from minute one: background loop snapshots mtimes under tools/miqcp and core/build/reports every 2 min; during flagged long runs, no new artifact for 6 min = STALL event in the watchdog log, checked between steps. Long solver/gradle runs are direct background commands, never subagent-supervised.
- ANTI-STALE PROTOCOL (the prior campaigns lost 10+ multi-hour blocks to stale java runs; this is the fix, mandatory): every run writes to a run-TAGGED artifact path (fresh tag per invocation in the filename), so a stale artifact cannot masquerade as this run's output; before any number is read, the trust gate runs: tag matches, applied: lines grepped, parameter echo header present; gradle env-gated runs always --rerun --no-daemon. Run supervision is NEVER delegated to a subagent: long runs are direct background commands in the main session (the harness notifies on exit), with the deterministic watchdog (no new artifact for 6 min during an active run = stall event) armed from minute one.
- Subagent roles: Opus-class for bounded code increments (exact deliverable + acceptance test + file paths; reports without acceptance output rejected); Sonnet-class for bulk log extraction and bookkeeping ONLY from artifacts the main loop has already trust-gated; the driver reads load-bearing verdict/slack lines itself.
- Python solver runs: direct, tee'd to tools/miqcp/*.log with parameter echo headers.
- No new code in core/src/main except additive test-source probes/harnesses; :core:test green before first change and after each increment.

## 7. Budget summary and sequencing

Step 0 (2.5 h) -> Lever 1 (4 h) -> Lever 2 (10 h, P0 then P1, with Lever 3's 3 h overlapping solver wall time) -> fork verdict presented to user BEFORE dossier (6 h) or extended hunt is committed. Total to fork verdict: roughly two working days. Global kill rule: any watchdog stall or twice-failed gate escalates to the user with the artifact trail instead of silent retries.

## 8. Adversarial review record (2026-07-09, one Opus reviewer, per bootstrap Phase 2)

Verified correct by the reviewer: cast-chain premise; envelope law (4 sig figs vs scan); the (180,270) dead region; deep wraps cannot open the diagonal at fixed facing (checked to +-2880); annulus certificates ride unrealizable diagonal norms under the current pattern; MOVE-MILP minimax-only and k=0; SCIP annulus cross-check unmet (1.08e-4 gap); feascenter tolerance caveat real (incumbent norm exceeds its own cap by ~1e-6); legal baselines; zig-zag arithmetic (but aimed at a strawman).
Findings and dispositions (all accepted):
1. BLOCKER, reface lever falsely refuted: mirror strafe preserves phi and mMag while moving the diagonal facing to 135, where max cell norm 1.0000959 exists; the draft's "key re-authoring cannot rescue the diagonal" is deleted; P1 reface arm added as a primary lever; no dossier may claim pattern generality without certifying P1.
2. MAJOR, norm-window law mis-stated: elevated cells exist in (0,90) negative-only, (90,180) positive, (270,360) positive, none in (180,270); passNormHigh is positive-side-only and the draft misread it. Section 2 rewritten signed; probe acceptance now two-sided.
3. MAJOR, 0a acceptance baked in the positive-only blindness and +-2.8 deg coverage: probe now complete-enumeration, signed, full +-30 deg tube.
4. MAJOR, fork logic treated existence as certifiable: rewritten; only the negative direction certifies; feasible selections are candidates for byte-exact verification.
5. MAJOR, SCIP cannot cross-check annulus MIQCPs: Lever 2 pivoted to exact-cell-set MILP as primary; interval MIQCP demoted to corroboration; Gurobi eval named as the fallback second certifier with user sign-off.
6. MAJOR, Lever 1 optimism: X/Z vise structure means coupled k>1 moves and possible additivity degradation; re-dump iteration loop added; framing corrected to cheap-to-try.
7. MINOR: double-recurrence fidelity added to scope list and section 5; VMAX added to scope list; feastol interactions noted (cell-set MILP sidesteps quadratic feastol).
