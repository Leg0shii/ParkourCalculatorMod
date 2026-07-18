# Solver graph systems survey and learning spec

Date: 2026-07-18. Feeds issue #221 follow-up work: redesigning the objective/violation surface of the graph editor and building toward a model that configures solver graphs from an objective. Companion to `solver-node-graph-design.md` (as-shipped M1-M5 record).

Provenance: deep-research sweep over 25 primary sources, 125 extracted claims, top 25 adversarially verified with 3 independent votes each; 23 confirmed, 2 refuted. Sections below are marked **[verified]** (survived 3-0 verification against primary sources) or **[background]** (extracted from primary docs with verbatim quotes but not adversarially verified). Two claims were refuted and must not be reused without fresh verification: the detailed SATzilla2012 architecture description (pairwise cost-sensitive forests, 31 solvers, 138 features, feature-cost gate) and Auto-Sklearn 2.0's two-meta-feature policy-selector description.

## 0. The headline, up front

1. **[verified]** Mature systems do not represent objectives as dataflow nodes. In every winning system the objective is a swappable scoring function applied to run telemetry, or a declarative model property, never a mid-graph operator. Our objective/violation surface should become run-level evaluation configuration plus per-node attribution, not nodes.
2. **[verified]** Learning full pipeline topology per instance is the empirically losing move; learning to pick among a portfolio of fixed graphs, and to set parameters within a fixed skeleton, is the proven path. The quantified payoff band for per-instance selection is 25-96% of the gap between the single best default and the per-instance oracle.
3. **[verified]** Simple methods repeatedly match sophisticated ones in this field: per-preset random-forest regression over cheap instance features is the evidence-backed first model, and static offline-built portfolios are the load-bearing component of every winning system before any learning happens.

## 1. What we have today (baseline for the spec)

The shipped graph runtime (M1-M5): `NodeType` with typed branches carrying feasibility guarantees (`Branch.feasible/unknown/preserves` + `Guarantee`), a declared `fallback` branch, entry/emit markers, params of kind INT/DOUBLE/BOOL/ENUM/STRING with a designated `budgetParam`, and a factory producing the node body. Edges carry one `Candidate` (yaws, violation, objective, feasible). Ambient state lives on `GraphContext` (spec, scenario, model, freeBox, feasTol, budgets, eval counters, chain label, settled/stageLocked). `RouterPredicate` is the only objective-aware graph element (VIOLATION_AT_MOST, AT_OBJECTIVE_CAP, HAS_REACH_HEADROOM, CANDIDATE_FEASIBLE_*). Presets serialize with FORMAT_VERSION 1; type ids, param keys, and Guarantee names are a stable contract.

The objective itself (`spec.objective`: axis, tick, sense) and the constraint set live entirely outside the graph. The M4 live viz shows per-node status (running timer, budget bar, taken branch, visit count) plus two global sparklines (best objective, best violation) fed from `SolveProgress`. The user-reported pain is exactly the global sparklines: they attribute nothing to nodes, show no per-stage convergence, and the graph never sees them.

## 2. Specification writeups of existing systems

### 2a. SATzilla, per-instance algorithm selection **[verified]**

- Operator model: a staged pipeline, not a graph: presolvers (fixed schedule), feature computation, a learned selector, a chosen main solver, backup solvers for failure cases.
- The selector: empirical hardness models map cheap instance features to predicted per-solver performance; pick the argmax per instance.
- Objective representation: an explicit input parameter to portfolio construction (mean runtime, percent solved, competition score). Later versions switched the predicted quantity from runtime to competition score without changing the architecture. This is the swappable-scoring-function pattern.
- The static presolver schedule solved 32-62% of competition instances in at most 7 CPU seconds before feature computation was even invoked (March_dl04 5s + SAPS 2s, manually chosen).
- Independently validated: 3 gold, 1 silver, 1 bronze in the 2007 SAT Competition (JAIR 2008, arXiv:1111.2249).

### 2b. Hydra and ISAC, per-instance configuration patterns **[verified]**

- Hydra (AAAI 2010): boosting-style portfolio growth from a single parameterized solver. Each iteration re-runs a configurator with the performance metric rewritten to `min(candidate performance, incumbent portfolio performance)` per instance, so a candidate is scored only on its marginal contribution. Built this way from one solver, it matched or exceeded (2 of 4 distributions) portfolios built from 17 state-of-the-art solvers at under one third of the CPU cost (~70 vs ~240 CPU days). Concrete choices worth copying: ridge-regression EPMs over 40 very cheap features (avg 0.04s), PAR-10 metric (capped runs counted at 10x cutoff).
- ISAC (ECAI 2010): cluster instances by feature vector (g-means), run a configurator per cluster, assign new instances to the nearest cluster's configuration with a default fallback. Parallelizes; Hydra targets weak regions better but builds sequentially.

### 2c. AutoFolio, configuration over a structured selector space **[verified]**

Applying SMAC to the highly parametric claspfolio 2 framework (a layered conditional space: presolving schedules with budgets, preprocessing, choice among selection mechanisms, plus each mechanism's hyperparameters) matched the best hand-built selection systems across ASlib scenarios with no manual choices (new SOTA on 7 of 13 scenarios, statistically matched elsewhere; 3rd of 8 at ICON 2015 overall, 1st on PAR10). This is the template for exposing our graph as a conditional config space: top-level categorical = preset/topology skeleton, conditional children = per-node parameters, one configurator searches the whole thing.

### 2d. Algorithm configuration (SMAC, ParamILS, GGA) **[verified]**

- Best-case payoff of tuning parameters: ParamILS cut SPEAR's mean runtime on software-verification instances from 787.1s to 1.5s; PyDGGA cut SparrowToRiss on N-Rooks from 116s to 6.3s. Caveat: homogeneous instance sets, best cases.
- The canonical model: SMAC's random-forest surrogate over the joint (instance features x configuration) space, natively handling categorical and conditional parameters. Field-level verdict from the JAIR 2022 survey: adding learned models to configurators improved performance in every AC setting where it was attempted.
- Scope limit that shapes our plan: SMAC's EPM generalizes across training instances to pick ONE configuration for the set. A per-instance output needs a PIAC layer on top (clustering or portfolio). Per-instance configuration of parameters AND topology is explicitly flagged as an open challenge in the same literature.

### 2e. AutoML pipeline-structure search **[verified]**

- AMLB (JMLR 2024; 9 frameworks, 71 classification + 33 regression tasks): AutoGluon best-quality, a fixed pipeline with multi-layer stacking and no pipeline search or HPO, ranked best; TPOT, genetic-programming topology search, ranked worst among AutoML frameworks in almost every setting. Only 2 of 9 frameworks achieved statistically significantly better ranks than a tuned random forest baseline. Qualifier: single benchmark, TPOT's loss partly variance/stability, GP-based GAMA ranked mid-pack.
- Auto-Sklearn 2.0 (JMLR 2022): abandoned online meta-feature warmstarting for a static portfolio of complementary pipelines built offline by greedy submodular selection over a precomputed performance matrix. Portfolios beat plain Bayesian optimization in all 16 result cells; the ablation that removed them caused the clearest drop (3.58 to 5.63 PAR at 10min) while the learned policy-selector layer barely moved results.
- Bradley-Terry trees over instance meta-features **[verified, medium confidence]**: AMLB uses them to find task subsets where framework rankings flip. Reusable as a go/no-go test: fit a BT tree on jump features over per-preset results; if no subset flips the preset ranking, a single default preset suffices and a learned selector is not justified.

### 2f. Competition reality checks **[verified]**

- 2015 ICON Challenge won by zilla (0.366 avg remaining gap), 2017 OASC by ASAP.v2 (0.38); best systems closed roughly two thirds of the SBS-to-VBS gap, with little field-wide progress between the two competitions.
- Simple matched fancy, repeatedly: plain per-algorithm RF runtime regression was the single best method on 13 of 17 ASlib scenarios; two untuned single-model LLAMA baselines (no presolvers, no feature selection) ranked among the top 2015 entries; no statistically significant difference between any 2015 submissions; AutoFolio with 4x budget barely improved; ASAP.v2 with default RF hyperparameters beat its refined successor. Qualifiers: low-power significance tests, ASAP.v2's win partly rode a lucky seed.

### 2g. Solver toolchains **[background, not adversarially verified]**

- MiniZinc: the declarative model (variables, constraints, one objective declaration) compiles to FlatZinc and runs unchanged on interchangeable CP/MIP/SAT backends. Search annotations are advisory (free search may ignore them). Solver-specific extras arrive via opt-in include files. Pattern: objectives and constraints are model properties; the solving pipeline is separately configured.
- SCIP: constraints are active plugins, not passive data. A constraint handler owns semantics AND algorithms for its class; a small mandatory callback core (CHECK, ENFOLP, ENFOPS, LOCK) guarantees correctness, everything else (separation, propagation, presolving) is optional performance surface. Composition is declarative: handlers declare priorities and frequencies, the solve loop schedules them; no explicit pipeline graph. Enforcement callbacks return typed resolution actions (cutoff, add constraint, tighten domain, separate, re-solve LP, branch) instead of reporting a violation number. Handlers can run with zero constraint instances (NEEDSCONS=FALSE, e.g. integrality), i.e. global feasibility concepts need not be per-instance objects. Telemetry: the core drops thousands of typed events per solve (bitmask-subscribable), and plugins react mid-solve.

### 2h. Node UI references **[background, not adversarially verified]**

- Blender geometry nodes, Fields design (2021): the graph has two flows. Data flow runs left to right and transforms geometry. Function flow (fields) is lazily evaluated: field nodes output deferred functions that are passed INTO consuming geometry nodes and evaluated backwards, in the consumer's context. Chosen over a competing design by building both prototypes and user-testing them. Their stated open weakness: communicating a field's evaluation context in the UI, exactly the failure mode of an objective node floating in a graph with no visible binding to the stage that evaluates it.
- ComfyUI: params are just unconnected input slots (any parameter can later be driven by an upstream node); two serialized forms, a persistence workflow JSON carrying UI-layer data and an execution prompt with UI data stripped; muted/bypassed/virtual nodes are filtered at execution time rather than deleted (non-destructive stage disable); the full workflow is embedded in the metadata of every generated image, so every output self-describes the graph that produced it; the file carries an explicit schema version.

## 3. Redesigning the objective/violation surface

What the evidence says: no winning system runs the objective through the pipeline. Four convergent patterns:

| System | Objective/constraint representation |
| --- | --- |
| SATzilla/Hydra | swappable scoring function over run telemetry, an input to portfolio construction |
| MiniZinc | declarative model property, solver-independent |
| SCIP | active handler with callbacks, scheduled by declared priorities |
| Blender fields | lazily-evaluated function socket consumed by an executing stage, in that stage's context |

Direction for the mod (design sketch, to be grilled before building):

1. Keep `spec.objective` and constraints outside the graph (already the case, and correct: MiniZinc pattern). Do not add objective dataflow nodes.
2. Replace the two global sparklines with **per-node attribution**: the engine already tracks per-node visits, elapsed, branch taken, and `SolveProgress` versions; record which node was active when each incumbent improvement (objective or violation) landed, and render improvements as marks on the node and on a single incumbent-trajectory strip (time on x, obj and viol as two aligned lanes, node-colored segments). That turns "obj/viol" from decoration into "which stage is earning its budget", the Hydra marginal-contribution question asked visually.
3. Expose the run-level **evaluation config** on the graph preset (SATzilla pattern): metric = hierarchical (feasibility first at feasTol, then objective sense, then time) or a capped PAR-style scalar; this is what a portfolio builder and selector will optimize later, so it must be explicit and serialized with the preset.
4. Router predicates stay the graph-side consumers of objective state (they already are: VIOLATION_AT_MOST, AT_OBJECTIVE_CAP). If richer conditions are ever needed, follow the Blender fields pattern (a condition is a deferred expression evaluated by the Router in its context), not eager value nodes.
5. Provenance (ComfyUI pattern, doubles as the dataset logger): every solve result and applied TAS save should carry the preset name, graph hash, full param vector, seed, and metric. This is the single highest-leverage step toward training data.

## 4. Learning to configure the graph: ranked plan

Ladder, each rung gated on beating the previous one on our own corpus:

- **L0, default preset per effort tier.** Exists (Fast/Optimize/Custom).
- **L1, static portfolio + cheap pre-solve. [verified pattern]** Hydra-style greedy marginal-contribution selection over recorded runs on `problems/solve` + `problems/closedform` to pick 3-5 complementary presets; keep a cheap first-try stage (closed-form + short CMA) in front, SATzilla's 32-62%-in-7s lesson. Racing the portfolio with timeouts is already a strong baseline.
- **L2, per-instance preset selector. [verified pattern]** Per-preset random-forest performance regression over cheap jump features; pick argmin predicted metric. This is the 13-of-17-scenarios winner and the 25-96% gap-closure band. Feature candidates: numTicks, jump count, constraint count/geometry summary, free-start box area, momentum class, tick-horizon, plus probing features (stats from the cheap pre-solve stage's trajectory, the Hutter et al. probing idea). Log feature computation cost from day one; on SAT it can eat half the budget.
- **L3, conditional-space configuration. [verified pattern]** AutoFolio template: expose skeleton choice as a top-level categorical, per-node params as conditional children, run SMAC (random-forest surrogate over joint instance-features x config space) to tune per cluster (ISAC) or per portfolio slot (Hydra). This is where "the model configures parameters" lands; per-instance parameter output beyond clustering is open-challenge territory.
- **L4, research tier (unverified for our domain).** GNN over the (constraint, tick) structure, RL over stage sequencing, L2O, DARTS-style relaxation of the graph. Evidence from the closest domain (ML4CO 2021, MIP solver configuration): the config-task winner used Bayesian optimization (HEBO) over an expert-reduced space plus per-cluster tuning, not GNNs or RL; tuned params gave 1.08-2.33x; on one branching benchmark random beat the trained models; organizers concluded ML for CO is not yet practical for real-world use. Gasse-style GCNN branching (imitation of strong branching) is the one clear GNN win, and it is a within-solver policy, not a pipeline configurator. Treat L4 as exploration after L1-L3 plateau, not as the plan.

Go/no-go instruments before building L2+:

- **VBS-SBS gap on our own runs** (BenLOC criterion): only build a selector where the per-instance oracle over the portfolio meaningfully beats the single best preset.
- **BT tree on jump features**: does any feature subset flip the preset ranking at all?
- **Budget rule of thumb [background]**: small evaluation budgets favor model-based search (BBO 2020 challenge), large parallel budgets favor random + early stopping (Hyperband). Solver runs are expensive, so expect the model-based side, but baseline every learned configurator against random configs with early kill.

Objective conditioning (the user goal of "configure based on minimize time / minimize violation / maximize objective / difficulty"): the SATzilla precedent is to rebuild or retrain per metric, with the metric an explicit input to portfolio construction. Practically: log enough per run (time, violation, objective, trajectory) to recompute any metric offline, then train one selector per metric; a difficulty heuristic like angle-change rate enters as an instance feature, not a separate model.

## 5. Telemetry and dataset spec (minimal viable, ASlib template) **[verified template, mod mapping is ours]**

Per run, one JSONL record:

- config: preset name, graph hash, full resolved param vector, seed, effort tier, metric config
- problem: id/hash (reuse the problems/ capture identity), fixed feature vector, feature computation cost
- outcome: wall time, completion status (solved / timeout / cancelled / error), final max violation, final objective, incumbent trajectory samples (t, obj, viol; `sampleTimes/Objectives/Violations` already collect this in the editor, move collection engine-side)
- per-node: visits, elapsed, branch taken per visit, evals attributed (cmaesEvals/smoothingEvals exist; extend attribution), incumbent improvements credited to the active node

Scoring: PAR-10 is runtime-only; our anytime setting needs a declared combination. Open question (below) but start hierarchical: infeasible-at-cutoff runs get a capped penalty score, feasible runs rank by objective then time. Fix train/test splits up front, per ASlib; never evaluate a selector on problems whose runs trained it. Batch generation: the ProblemsTest harness already cold-solves the corpora; a run-matrix mode (every preset x every problem, seeded) is the dataset generator, and Hydra-style portfolio growth runs offline over the same matrix.

## 6. Caveats carried from verification

- All quantified gains are for SELECTION among fixed alternatives; per-instance configuration of parameters and topology remains an open challenge. The headline numbers bound preset-picking.
- Domain transfer untested: every verified source is SAT/CSP/MIP/AutoML. Nothing covers physics-simulation pipelines, continuous trajectory problems, or anytime minimize-violation objectives.
- ASlib scenarios carry selection bias (published by selection advocates); several SAT-INDU scenarios show selectors LOSING to the single best solver.
- Competition statistics are low-power; speedup figures are homogeneous-set best cases; Hydra numbers are self-reported by the SATzilla group.
- Sections 2g/2h and the L4 evidence are background, not verified; re-verify before load-bearing use.

## 7. Open questions

1. Which cheap jump features carry selector signal, and what feature budget is tolerable relative to Fast-tier solve times?
2. The PAR-10 analogue for anytime quality: how do capped runs, final violation, and objective combine into one scalar the EPM regresses on?
3. Does ISAC-style per-cluster configuration over jump features beat plain preset selection here, or is our config space small enough that clustering + SMAC-per-cluster closes real gap?
4. Can Hydra-style marginal-contribution portfolio construction over the existing problems corpora grow the preset library automatically, and does the resulting VBS-SBS gap justify a learned selector at all?

## 8. Sources

Verified core: Kerschke et al. 2019 survey (ada.liacs.nl/papers/KerEtAl19.pdf); Schede et al. JAIR 2022 AC survey; ASlib (arXiv:1506.02465); AS competitions report (arXiv:1805.01214); Hydra (cs.ubc.ca/~hoos/Publ/XuEtAl10.pdf); SATzilla (arXiv:1111.2249); AMLB (arXiv:2207.12560); Auto-Sklearn 2.0 (arXiv:2007.04074). Background: Hutter et al. AIJ 2014 EPMs (ada.liacs.nl/papers/HutEtAl14.pdf); Hyperband (arXiv:1603.06560); BBO challenge (arXiv:2104.10201); BenLOC (arXiv:2506.02752); ML4CO (arXiv:2203.02433); Gasse et al. (arXiv:1906.01629); MiniZinc handbook 2.8.7 solvers chapter; SCIP CONS/EVENT docs (scipopt.org); Blender fields (code.blender.org/2021/08/attributes-and-fields/); ComfyUI workflow docs (docs.comfy.org).
