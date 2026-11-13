# Capture-driven checks

`ProblemsTest` runs every file under `problems/<check>/`. The **folder name is the check** (one class per
folder in `anglesolver/checks/`, registered in `Checks`); each capture's optional `<name>.expect.json` tunes
it. Drop a file in a folder and it runs, no Java change. A gate on one capture is a sidecar here, never a Java
class that names the capture.

| Folder            | Validates that the capture... |
|-------------------|-------------------------------|
| `solve/`          | still solves through the live engine, within an optional time budget; optional notice, recompute, reproducibility and box-seed checks |
| `closedform/`     | closed-form-solves byte-exact feasible, on objective, and fast |
| `dualrecovery/`   | is byte-exact-solved by the deterministic dual chain (closed form, SLP, reseeded SLP, relaxation recovery) in its saved direction: no CMA-ES, no warm start; a sidecar `bnbSeconds` adds a bounded blind pattern-B&B feasibility fallback on chain miss |
| `replay/`         | replays byte-exact from its rows' facings: the recorded debug positions, feasibility and violation, objective, binding tick, or the legal shortfall against the goal wall |
| `transitions/`    | replays every tick of the recorded path through the model with the ground state the capture says, across jump transitions |
| `planpath/`       | realizes the planned path when the engine runs on the seed boxes (the plan matches the recorded positions tick by tick) |
| `legal/`          | behaves in legal mode: lands at the goal wall with a non-positive shortfall, or reports the shortfall of the dropped goal wall with every hard wall held |
| `optimize/`       | is never made worse by Optimize: cold THOROUGH vs FAST, or THOROUGH adopting the FAST incumbent |
| `multistart/`     | runs the seed sweep of the multi-start preset where the start is free, and skips it where it is pinned |
| `smooth/`         | smooths after a solve: the Smooth button yields a smoothed, still feasible result that starts where the solve started (`free-start-synth` is a solve that moved a free start); with `resmooth` every press starts again from the unsmoothed solve; with `smoothInputs` the Turn and inputs mode changes the keys of at least `minChanged` ticks (default 1; 0 on `j021-rinav1-01`, a Force 45 jump where a single key move only sometimes beats Turn only on the watch score), stays feasible within the give-back, never scores worse on the watch score than Turn only on the same solve (`maxWatchShare` asks for a share of it), adds at most `maxForwardKeyChanges` forward key changes (W release, sprint reset, S tap) over Turn only when the sidecar sets it (`gh398-optimize-2jump` is a course whose turn is already smooth, where a release must not win; `stair-key-release` caps the stair plan after its tidy pass; `brake-key-swap` is a 1.8.9 jump that brakes with W held and the camera turned backward, where the key swap with a helper tick has to bring the watch score under 0.85 of Turn only; `force45-release-plan` is a Force 45 solve of the same course smoothed at 50 % give-back, where the release plan has to pass the jump tick between two quick jumps, change at least 20 ticks and bring the watch score under 0.6 of Turn only), and Apply writes the keys and switches those ticks to Keep / Derive |
| `freestart/`      | solves with a free start box: the adopted start lies in the box, the seed may be displaced, the box may be pinned afterwards |
| `levelset/`       | is lifted by the level-set ascent on the sidecar's `axis` and `goal` by at least `minGain` over the SLP hug, staying under the dual bound |
| `ils/`            | is polished by the ILS from the closed form's byte-exact feasible seed without losing feasibility or objective, and an infeasible seed comes back untouched |
| `translation/`    | scores translation-aware: the zero-width translated score byte-equals the pinned score, and with `cap` the best translation over the [-cap, cap] box matches a brute-force grid |
| `gapattribution/` | reaches `refObjective` in THOROUGH mode and attributes the gap between the dual bound and the achieved objective |
| `strafeside/`     | solves FAST with the strafe side pass flipping at least `minFlipped` Force 45 ticks onto the other A/D side (gh-338): less anchored yaw jerk, realized path feasible byte-exact at the shipped objective, Apply writes the key and the shifted yaw per tick; the check sets the strafe side to Both, then solves on A only and on D only and checks no pass ran and Apply writes that key on every strafe tick, and Smooth on Both keeps the A/D keys of the solve |
| `polish/`         | is improved by the lattice polish from a deterministic incumbent (the closed form seed, with `mode` `engine` a live solve, FAST unless the sidecar sets `effort`; a FAST solve never carries the stage, so it is the kind of landing the stage starts from in Optimize): the result replays byte-exact feasible with the start inside the box, is strictly better by at least `minGain` (no `minGain`: the stage may keep the incumbent), a second run returns the same facings and the same start facing, a spec whose start turn is not free keeps its start facing, an infeasible incumbent comes back without a result, and with `unsupported` the stage declines the spec for that reason; with `roundsGain` the result must beat the stage run with a single grid search round by that much; with `fullSearch` the stage runs with the Optimize graph's parameters as well and must not come back short of the default parameters' result; with `turns` the check switches the solver option `Full turn start facing` on (every other case runs with it off, the default), the result must sit one full turn away from the incumbent and beat the stage run without the turn, and in `engine` mode the solve runs the Fast graph with the stage added between `cap2` and `wrap` (a custom graph, Fast itself does not carry the stage) and must pick another turn itself, Apply must publish that start facing, the applied rows must read back to the solved objective bit for bit and a second solve on them must give the same objective and start facing |

## Adding a capture

A capture is named by its stem (e.g. `j154`). Resolved from a co-located `<name>.json` in the check folder if
present, else from the shared `../captures/` library; a sidecar's `capture` field points at a nested library
path (e.g. `hpk/d10/j343_...`), and a leading `/` names a resource root outside the library
(e.g. `/hpk_human/d11/j1150-2x2bm_Nix_Neo`). The optional `<name>.expect.json` holds the constraints; with no
sidecar, defaults come from the capture's own result. A capture may appear in several folders (e.g. `j154` is
in `solve/` and `closedform/`), which is why captures live in one shared library.

Every fixture JSON is one line, no BOM, no trailing newline.

### Sidecar fields

```jsonc
{
  // any check
  "check":           "solve",   // run another folder's check on this file; default: the folder name
  "capture":         "hpk/d10/j343_...", // library path when the stem alone does not resolve it
  "effort":          "FAST",    // FAST | THOROUGH; default FAST
  "optimizeSeconds": 10,        // THOROUGH only: override the save's Optimize time budget
  "timeoutMs":       60000,     // polling timeout of one live solve; each check has its own default

  // solve/ (also read by legal/)
  "shouldSolve":     true,      // default: the capture's recorded angleSolver.result.success
  "maxSolveMs":      4000,      // wall-clock budget; omit for no timing assertion
  "minMet":          12,        // require >= this many constraints met; omit to require full success
  "allDirections":   false,     // require every Solve-For (axis x goal) to solve, not just the saved one
  "verdict":         "INFEASIBLE", // the three-valued verdict: FEASIBLE, UNDETERMINED (not found, nothing ruled
                                // out) or INFEASIBLE (certified: a constraint conflict or an exhausted certified search)
  "noticeLabel":     "Overconstrained",     // the result's notice label (a failing solve)
  "noticeContains":  ["one heading"],       // substrings the notice text must carry
  "maxRecomputeDivergence": 1e-3,           // replay the result's yaws through the model, max position gap
  "reproducibleWithin": 0.01,               // solve twice, objectives agree within this
  "boxSeeds":        true,      // repeat the solve from six seeds across the start box (corners, centre, off-centre)

  // solve/, dualrecovery/, closedform/, multistart/, gapattribution/, replay/
  "refObjective":    -279.3,    // objective to reach (within maxObjectiveGap, objectiveTol, or 1e-6)
  "maxObjectiveGap": 0.01,      // max objective shortfall vs refObjective (default 0.01)

  // dualrecovery/
  "bnbSeconds":      10,        // on chain miss (or target shortfall), run the blind pattern-B&B with this budget;
                                // with refObjective set the B&B stops at that target and the check asserts it is
                                // reached (within maxObjectiveGap), else it stops at the first feasible point

  // closedform/
  "maxMicros":       2000,      // max us per single solve (default 2000)

  // replay/
  "numTicks":        49,        // the replayed path has this many ticks
  "debugPositions":  true,      // every replayed position matches the capture's debug positions to 1e-12
  "feasible":        true,      // the path meets every constraint (default true); false asserts a violation
  "refViolation":    2.27e-3,   // the max violation (within violationTol, default 1e-6)
  "violationTol":    1e-9,
  "objectiveTol":    1e-6,
  "legal":           true,      // judge against the legal goal wall: hard walls hold, shortfall = refShortfall
  "goalWall":        "X@49lo",  // the selected legal goal wall's name
  "goalWallRhs":     212.7,     // the selected legal goal wall's rhs
  "refShortfall":    9.68e-5,   // the shortfall to the goal wall (within shortfallTol, default 1e-9)
  "shortfallTol":    1e-9,
  "bindingTick":     12,        // the tick that carries the max violation
  "feasibleTicks":   [48, 49],  // ticks whose own constraints hold although the path is infeasible

  // transitions/ and planpath/
  "minCompared":     100,       // at least this many ticks compared
  "minJumps":        2,         // at least this many jump transitions (transitions/ only)
  "groundSlip":      0.6,       // the ground friction the replay assumes (transitions/ only)

  // legal/ (goalWall and goalWallRhs as in replay/)
  "savedLegalMode":  true,      // the save round-trips legal mode on
  "starveSeedMs":    1,         // repeat the solves with the Fast graph's seed stage starved to this budget
  "minShortfall":    95.0,      // an unlandable capture's shortfall lies in (minShortfall, maxShortfall)
  "maxShortfall":    100.0,
  "deterministic":   true,      // two legal solves give the same shortfall bit for bit

  // optimize/
  "mode":            "cold",    // cold: fresh FAST vs fresh THOROUGH; incumbent: THOROUGH adopts the FAST result
  "minIncumbents":   2,         // cold: the THOROUGH trajectory records at least this many incumbents

  // multistart/
  "seedsTaken":      "FOUND",   // FOUND: the seed sweep ran and reached refObjective; NONE: it was skipped fast

  // freestart/
  "displaceSeedX":   3.0,       // move the seed this far in X before solving
  "startXLo":        0.699999,  // the adopted start lies in [startXLo, startXHi] x [startZLo, startZHi]
  "startXHi":        0.7,
  "startZLo":        0.4999,
  "startZHi":        0.5001,
  "pinnedAfterAdoption": true,  // the start box is pinned after the solve adopted the start

  // levelset/
  "axis":            "Z",       // X | Z
  "goal":            "MAX",     // MAX | MIN
  "minGain":         0.5,       // the ladder beats the SLP hug by at least this much (required)

  // ils/
  "seedMayMiss":     true,      // the closed form may fail to seed this capture; then only the infeasible-seed half runs

  // polish/
  "mode":            "engine",  // take the incumbent from a live solve instead of the closed form seed (FAST unless "effort" says otherwise; a FAST solve never carries the stage)
  "minGain":         1e-4,      // the stage beats the incumbent by at least this much; absent: it may keep the incumbent
  "unsupported":     "damage tick", // the stage declines the spec and its reason contains this text
  "turns":           true,      // switches the Full turn start facing option on; the result must start one full turn away (315 instead of -45); with "mode": "engine" the solve uses the Fast graph plus the stage and the check also runs Apply and solves again
  "roundsGain":      5e-5,      // the stage beats its own run with one grid search round ("rounds" 1) by at least this much
  "fullSearch":      true,      // the stage also runs with the Optimize graph's parameters and must not come back short of the run with the default parameters

  // translation/
  "cap":             0.05,      // also brute-force the best translation over the [-cap, cap] box and compare

  // smooth/
  "resmooth":        true,      // also smooth at 50 % twice and then at 0 % on a fresh solve: each press starts from the unsmoothed solve
  "smoothInputs":    true,      // also smooth in the Turn and inputs mode (gh-606): the keys of at least minChanged ticks change, and the applied rows, replayed through the game's sprint rules, sprint exactly where the plan assumed
  "minChanged":      1,         // smoothInputs: the least number of ticks whose keys must change (default 1)
  "maxWatchShare":   0.5,       // smoothInputs: the watch score of the result is at most this share of the Turn only score
  "maxForwardKeyChanges": 2,    // smoothInputs: the result has at most this many more forward key changes (W release, sprint reset, S tap) than Turn only
  "strafeSide":      "BOTH"     // any check: the Force 45 strafe side the rig solves with (A, D or BOTH; the save's own when absent)
}
```

`maxSolveMs` / `maxMicros` are the "must not regress timewise" guards. Keep them generous: they exist to catch
gross regressions, not CI jitter. An empty `{}` sidecar just runs the folder's check with defaults.
