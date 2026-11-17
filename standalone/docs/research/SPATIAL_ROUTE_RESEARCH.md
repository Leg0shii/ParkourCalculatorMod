# From collision geometry to spatial route families

2026-10-31. Research response to the user's question: given relevant blocks as AABBs or another reasonable geometric representation, how can we automatically derive the broad path choices that precede local movement/timing optimization? Input serialization is deliberately outside this design. This is a proposed abstraction and source review, not an implementation or measured performance result.

Latest user clarification, 2026-11-03: support progression and 3D passage choices distinguish spatial routes. In particular overflight and landing then running off a block are different routes even with identical X/Z projections. This supersedes the older optional-contact grouping in this research note. Timing, run duration and facings within the same spatial/contact itinerary remain local. See the requested [concrete refactor plan](../design/SPATIAL_CORRIDOR_REFACTOR_PLAN.md) for current implementation direction; historical research and measurements below remain preserved.

## General cell-decomposition context, researched 2026-10-31

The user requested that the general method be studied before committing to its application. The earlier height-band sketch was one construction, not a definition of cell decomposition or an adopted implementation.

### What is decomposed and why

In general robot planning, the space being divided is configuration space: each point denotes a complete geometric placement of the robot, not an occupied world voxel. A free-space cell is a simple region of valid placements. Convex cells are common because any two points in one cell admit a straight segment within that cell. Other decompositions use nonconvex cells with a different simple local planner.

Cells need not be rectangular prisms. Examples include trapezoids/triangles in 2D, convex polyhedra/tetrahedra in 3D, and boxes in axis-aligned or adaptive constructions. A decomposition also needs boundary/incidence information. Volumes alone without their interfaces are insufficient to build the roadmap correctly.

The standard planning recipe is to construct simple free cells, identify the start/goal cells, search cell adjacency, and connect the selected interfaces using a local planner inside each cell. A representative point such as a centroid is a convenient geometric graph construction, not a requirement that a robot pass through that exact point. Our solver should retain interface regions.

References: [LaValle, general definitions](https://lavalle.pl/planning/node273.html), [vertical decomposition conditions](https://lavalle.pl/planning/node262.html), and [roadmap construction](https://lavalle.pl/planning/node265.html).

### Main forms and general uses

| Form | General construction/use | Main tradeoff |
| --- | --- | --- |
| Exact geometric decomposition | Place boundaries using obstacle geometry; partition the modeled free space with explicit interfaces. Used for geometric motion planning. | Can preserve geometric connectivity with a suitable local planner, but construction cost depends on geometric complexity. Cells are not necessarily boxes. |
| Uniform grid | Use cells of one size and classify occupancy. Common simple discretization. | Resolution governs cell count and which narrow features are represented. |
| Adaptive approximate decomposition | Recursively subdivide mixed obstacle/free boxes, for example a quadtree in 2D or octree in 3D, keeping larger cells in open space. | At a fixed cutoff some free space remains unresolved. Conservatively omitting it can hide a real passage. |
| Sweep/Morse/boustrophedon decomposition | Split when the structure/connectivity of a sweeping slice changes. Used particularly for covering all of an environment with simple within-cell motions. | Cells can be larger and nonconvex; the applicable local task is not necessarily straight-line point-to-point travel. |
| Algebraic decomposition | Decompose more general spaces defined by polynomial constraints, including configurations with rotation. | Powerful but unnecessary complexity for pure translational AABB geometry. |

Primary sources read include [Hauser, geometric motion planning, cell-decomposition section](https://motion.cs.illinois.edu/RoboticSystems/GeometricMotionPlanning.html), [LaValle, 3D vertical decomposition](https://lavalle.pl/planning/node279.html), [Choset's CMU cell-decomposition lectures](https://www.cs.cmu.edu/~motionplanning/lecture/lec15.pdf), and [Acar et al., Morse Decompositions for Coverage Tasks](https://journals.sagepub.com/doi/10.1177/027836402320556359). The coverage work illustrates that the task determines the useful cell structure; its coverage algorithm is not being proposed as a parkour route planner.

### Guarantees and limits

An exact decomposition with correct traversable interfaces and suitable local planners can preserve the connectivity of the modeled geometric free space. That supports a complete geometric existence query under the model's boundary assumptions. It does not establish shortest geometric distance from a cell-hop count or center-point roadmap, and it does not establish minimum movement time.

An approximate free-cell graph can certify a geometric connection when its cells/interfaces/local paths are valid, but disconnection at a finite resolution can mean an unresolved narrow passage. Under the relevant positive-clearance conditions, arbitrary refinement supports resolution completeness; a finite grid is not an exact map. See [LaValle on resolution](https://lavalle.pl/planning/node202.html).

Convexity answers whether a straight geometric segment fits. It does not answer whether a car can turn that sharply or a Minecraft player can produce the segment under momentum and gravity. Configuration-space planning and state-space/kinodynamic planning are different levels of the problem. In our application, yaw does not rotate the upright collision AABB, so it need not increase the dimension of the geometric position decomposition. Velocity and input state remain necessary for dynamics.

### There is no unique decomposition or automatic route-class definition

One box inside a larger empty box illustrates the choice. Splitting the larger box globally at both obstacle-face coordinates on all three axes creates 27 elementary boxes, of which 26 are free. Alternatively, subtracting the inner box in a fixed axis order can cover its complement with six disjoint-interior rectangular prisms. Both describe the same geometric free volume; the cell graphs differ in subdivision detail.

For an analytic example, an outer cube of side 10 and an inner cube of side 2 have free volume 1000-8=992. The six-piece construction can use piece volumes 400, 400, 80, 80, 16 and 16, which sum to 992. This is an arithmetic illustration, not an implemented algorithm or performance benchmark, and six is not claimed as a general minimum.

Thus fewer graph nodes can result from a better partition without changing the obstacle geometry. A graph path is nevertheless a cell itinerary, not automatically a unique broad spatial strategy: many itineraries can represent nearby trajectories. Corridor compression/grouping and diversity remain separate decisions.

### What the graph provides in general

- A finite connectivity representation of an infinite collection of geometric placements.
- A point-location structure for start, goal and later trajectory states.
- Explicit openings, branch alternatives and disconnected regions.
- A way to reuse local geometry and run graph searches instead of repeatedly reasoning about every obstacle.
- A selected corridor in which a more detailed path or trajectory can be optimized.

These benefits depend on retaining the relevant boundary geometry and using a local planner suited to the robot. They do not require treating all positions inside a cell as equal in travel cost or dynamically interchangeable.

## Conclusion and scope

After the general-method review, the recommended starting point is an exact decomposition adapted to axis-aligned collision geometry, with a graph of free cells and finite passages, annotated with contact opportunities and coarse movement possibilities. Group spatially similar graph routes into route families and retain a bounded diverse shortlist. Each family is then realized by the timed interaction graph and whole-history continuous solver.

The central separation is geometry versus movement: geometry identifies where alternatives exist; cheap movement models estimate which are worth trying; the local solver chooses a valid timed realization. Neither an ordinary floor-only navmesh nor an unrestricted 3D flying roadmap covers our task by itself.

AABBs alone do not identify all block behavior. Assume upright player dimensions, initial/goal conditions and movement rules are known, with semantic region labels where geometry does not suffice (for example climbable membership). Source physical block identity is retained even when its collision geometry has several boxes. This is information content, not a required file format.

## Research findings relevant to this choice

| Approach | Useful idea | Limitation for this project |
| --- | --- | --- |
| Configuration-space cell decomposition | Account for player size, partition collision-free space into simple connected cells, and extract adjacency as a roadmap. | Geometric connectivity does not imply reachability under gravity, momentum or Minecraft collision rules. |
| Navigation meshes and special links | Compress walkable geometry into regions, with explicit links for actions outside ordinary surface traversal. | Surface-only traversal omits continuous airborne wall/ceiling/ladder interactions and contact alternatives inside a transfer. |
| Hierarchical portal graphs such as HPA* | Share region entrances and replace internal traversal with cached abstract connections. | One cached distance or fixed portal midpoint is insufficient for our position/velocity-sensitive trajectories. |
| Homotopy/homology path classes | Group paths by topological obstacle relationships. | Pure 3D topology can merge different useful sides of finite boxes; their paths can deform over/around the obstacle. |
| Topo-geometric/neighborhood-augmented planning | Preserve different incoming spatial branches even at the same location. | Extra neighborhood/state work and scale parameters are required; this is not a ready-made Minecraft movement planner. |
| Diverse top-K routes | Select genuinely different alternatives instead of many almost-identical shortest paths. | Diversity is an explicit selection criterion, and standard simple-path assumptions must not forbid useful physical revisits. |

Primary sources inspected:

- [LaValle, Vertical Cell Decomposition](https://lavalle.pl/planning/node262.html) and [Defining the roadmap](https://lavalle.pl/planning/node265.html). These give the free-cell adjacency construction; our dynamics annotations below are a separate design inference.
- [LaValle, Planning Algorithms](https://lavalle.pl/planning/), section 4.3.2 on translational configuration-space obstacles. Obstacle expansion by the reflected robot shape supplies the geometry calculation.
- [Recast official construction overview](https://recastnav.com/) and [Detour off-mesh connections](https://recastnav.com/structdtOffMeshConnection.html). Recast derives walkable polygons; Detour represents additional traversable links. We are borrowing the regional/link organization rather than adopting its surface assumptions.
- [Botea, Mueller and Schaeffer, Near Optimal Hierarchical Path-Finding](https://webdocs.cs.ualberta.ca/~mmueller/ps/2004/hpastar.pdf). The indexed primary-paper description of clusters/entrances and cached internal crossings was inspected; direct author PDF retrieval currently redirects unsuccessfully. No HPA* runtime or speed claim is transferred here.
- [Sahin and Bhattacharya, Topo-Geometrically Distinct Path Computation](https://arxiv.org/html/2306.01203v2). It separates spatial alternatives beyond pure homotopy by retaining path neighborhoods at shared locations. The original method also has low-curvature/cost cases requiring a cut-point extension; do not assume it universally produces the desired classes without tuning.
- [Liu et al., Finding Top-k Shortest Paths with Diversity](https://homes.cs.aau.dk/~byang/papers/TKDE2017.pdf). The paper motivates diversity because ordinary top-K shortest routes can share most of their edges. Its graph-cost results are not guarantees for our heuristic movement costs.

The Baritone source inspection remains in [HEURISTIC_SOURCE_REVIEW.md](HEURISTIC_SOURCE_REVIEW.md): spatial nodes, a small movement library and cheap estimates are useful architectural precedents. The present multi-level portal/contact design is our adaptation.

## A concrete construction from AABBs

### 1. Describe free positions for the player's reference point

Use the player's foot position as the reference point. For an upright player of half-width r and height h, and a solid box with ranges `[xmin,xmax]`, `[ymin,ymax]`, `[zmin,zmax]`, the forbidden interior for that reference point is the expanded box:

```text
[xmin-r, xmax+r] x [ymin-h, ymax] x [zmin-r, zmax+r]
```

This is a translational configuration-space construction, not a replacement for tick collision simulation. Retain boundary contact patches explicitly. A legal landing or wall hit can lie on a boundary; a blanket positive clearance or deleting all boundaries would remove tight valid interactions. The existing `obstacle_geometry.expanded_box` already encodes this geometric expansion.

Compute geometric unions where useful to remove irrelevant internal obstacle seams, while retaining provenance back to physical boxes/blocks for contact semantics and per-block caps. Do not replace a nonrectangular union by its bounding box or bridge real gaps.

### 2. Partition the bounded relevant free volume into simple regions

For AABBs, a direct reference construction is to split at expanded box-face coordinates. It produces axis-aligned cells whose interiors are entirely free or blocked. Keep free cells and mark which faces touch physical geometry.

One construction sweeps the critical Y heights. Its efficiency relative to local box splitting has not been measured. Inside an open height slab, the active expanded X/Z rectangles are constant. Decompose their planar complement into rectangles/simple convex regions and extrude those regions over that slab. Neighboring height slabs are linked wherever their free cross-sections overlap. Add the contact strata at critical heights where needed.

This accounts for stacked floors, low obstacles, ceilings and over/under passages. A single X/Z projection of all solids would incorrectly turn low boxes into infinitely tall walls. A single walkable floor layer would miss airborne transfers.

The global coordinate-plane arrangement is a correctness reference for geometric partitioning, not a recommended eager production data structure: it can create cubic numbers of elementary boxes. Use local/adaptive decomposition and merge adjacent free cells where the merged region remains simple and the relevant interfaces/opportunities are retained. Geometry processing itself needs a work budget for large scenes.

### Application refinement after the general-method review

Preferred first candidate: keep elementary cells as axis-aligned rectangular prisms and preserve their interfaces. This exploits the special input geometry while avoiding the resolution cutoff of a uniform grid or ordinary octree. It is a recommendation, not user approval or a measured superiority result.

A concrete alternative to global height bands is local axis-aligned box subtraction:

1. Start with the bounded region of interest as one free-candidate box.
2. For each expanded obstacle, find current boxes whose interiors overlap it.
3. For an overlapping box C, compute I = C intersect obstacle. Remove I from the free volume.
4. Partition C minus I into at most six nonoverlapping-interior boxes: the left/right slabs, then lower/upper slabs within the remaining X range, then front/back slabs within the remaining X/Y ranges. Omit empty pieces.
5. Repeat for the other obstacles, preserving original contact geometry/provenance; merge adjacent boxes when their union is again one box and their opportunity/interface metadata is retained.
6. Derive all partial shared-face openings between free boxes. Unequal-sized neighbors can share only part of a face; do not assume one neighbor per side.

This construction and the sweep construction are exact geometric partition candidates for AABBs in ideal arithmetic. They can still generate many cells; partition order and merging affect the graph. Do not claim a minimal partition or measured performance without testing. An adaptive spatial index can accelerate overlap/neighbor queries without quantizing the cells themselves.

For ordinary full-dimensional passages, two cells connect through a free shared-face rectangle. Pure corner/edge contact is not automatically an ordinary traversable opening. Our exact contact-dependent cases require separately retained lower-dimensional boundary strata and the shared scene checker, rather than assuming the strictly-open-free-space theorem covers them.

Record each cell's coordinate bounds; each interface's plane and finite ranges; and physical contact patches with their source block, face, support/climbable semantics and relevant movement opportunities. If merging introduces internally varying ladder membership or another physical regime, retain that internal region annotation or keep a split. A free cell is not necessarily a uniform physics regime.

### What the representation gives the parkour solver

The strongest new application consequence is reducing collision disjunctions. Avoiding one expanded box is an OR over its six outside half-spaces. Choosing a valid free cuboid corridor can replace many of those choices by simple box inequalities during the assigned parts of a trajectory. A cell is represented by lower/upper X/Y/Z bounds; a portal adds a shared-plane crossing region. Those constraints are linear in position even though the movement dynamics remain nonlinear/hybrid. This is a solver-specific design inference, not a general convexity claim for the full optimization problem.

The graph therefore provides spatial alternatives, reusable geometric tests, boundary/contact opportunities, and selected corridors that simplify local avoidance choices. It does not determine event ticks or turn each portal crossing into a tick. One input may cross several cell interfaces; physical elapsed time belongs to the movement, with Y/X/Z substep semantics retained. Requiring only sampled pre-tick positions to lie in free cells is not sufficient to validate the intervening movement.

A raw spatial adjacency graph is geometric and usually undirected. A planning overlay can add directed movement possibilities and time estimates, conditioned on useful arrival contexts. Use actual movement/continuation estimates rather than charging one tick per cell edge or declaring center-to-center distance an exact time. A portal graph, with estimated entry-to-exit transfers through a cell, can express this dependency more naturally than one state-independent cost per cell.

A broad route remains a family of cell/portal corridors. Exact landing/run-off versus overflight and tick shifts are handled by local realization. A support patch on a cell boundary is an available interaction, not a mandatory visit or a frozen endpoint. A selected corridor may need to be a union of nearby cell sequences when local method changes alter which elementary cells are crossed. Keep that grouping separate from the exact geometric partition.

Do not build a full exact position/velocity/time decomposition as the first version. Use the 3D position cells as the shared spatial representation, a small heuristic set of arrival contexts for selection, and the existing original-state solver for dynamics. This respects the user's goal of selecting tens/hundreds of useful candidates rather than first enumerating exact reachable regions.

### Height-band clarification with a one-block example

2026-10-31. The user requested a plain explanation of the height-slab construction. A band is a range of reference-point heights over which the same horizontal obstacle footprints apply. It is a geometric range, not a tick or a decision to stay at one fixed Y. Geometry is evaluated after accounting for the player's body size.

Consider one low box whose physical top is Y=1, with no overhead obstacles, and restrict attention to nonnegative foot heights. For foot heights strictly between 0 and 1, the box's expanded X/Z footprint is forbidden. For foot heights strictly above 1, the player can fit above that footprint. The contact plane Y=1 is retained separately as a possible support boundary. These ranges have different floorplans for foot-position placement.

Within the lower band, one simple partition of the free horizontal area around a rectangular footprint is four rectangles: north, south, west and east. Extruding each over the band's height range gives four free 3D cells. Above the box, the same bounded area may be a single free region because the footprint is no longer blocked.

Connect lower cells to upper cells only through their overlapping free X/Z area at the shared height boundary. A point beside the box can have free space on both sides of that boundary. A point inside its footprint has solid directly below, so there is no free-space portal passing upward through the box. Its top is instead a contact opportunity approached from above. The connection expresses geometric adjacency, not a claim that Minecraft permits arbitrary vertical movement.

Critical heights include both ends of each expanded obstacle's Y interval, not only physical block tops. A ceiling with underside H and an idealized player height h starts blocking that footprint when foot Y exceeds H-h. At the boundary the applicable contact/tangency rules matter. Body-size expansion therefore supplies the actual cut heights.

This partition is one possible geometric construction under consideration. It is not an assertion that the player should stop at band boundaries, move through rectangle centers, or that the full arrangement must be constructed eagerly.

### 3. Extract portals and interaction patches

A portal is a connected finite opening on the common interface of neighboring regions. It is a set of possible crossing positions, not a fixed center waypoint. A portal graph can have portal vertices and intra-region edges between them; equivalently, region vertices can be joined by edges carrying the portal geometry. Either convention contains the same essential information.

Attach physical opportunities to the adjacent regions/interfaces:

- exposed support-top patches and their departure edges;
- accessible wall and ceiling patches;
- ladder/vine membership regions and finite entry/exit patches;
- pairs of nearby faces/edges that make supported stepping cases possible;
- corridor alternatives around obstacle sides.

These are opportunities, not mandatory contacts. A region near a block can offer both overflight and landing/run-off. Hidden or covered face portions are excluded geometrically where this is established; actual contact feasibility still depends on phase and movement.

### 4. Compress subdivision artifacts without erasing route choices

Merge simple free interiors and contract nonbranching cell/portal chains that contain no required landmark. Preserve distinct openings around an obstacle and distinct internal corridors between the same pair of outer portals. The abstract graph is a multigraph: two connections with the same endpoint regions can encode different spatial passages.

Do not merge everything merely because it belongs to one connected component. The whole reachable scene can be connected while containing many relevant route alternatives. Conversely, do not make every rectangle boundary a meaningful route choice.

A practical first version keeps junctions, narrow openings, changes in coarse height level and important support/climbable neighborhoods. Macroedges retain references to their underlying corridor cells and interaction patches. If a cluster contains an internal left/right split, retain both internal corridor options rather than replacing the cluster by one arbitrary shortest path. This is the portal-graph idea adapted to preserve alternatives and continuous crossing regions.

### 5. Add coarse directed movement possibilities

A pure free-volume roadmap would permit hovering, arbitrary ascent and instant changes of direction. Annotate its connections with plausible movement families and estimated times before searching it as a parkour graph.

Use the existing normal jump/fall recurrence, support opportunities, climbing regions and named contact/step families to obtain optimistic or representative movement envelopes. Track coarse arrival mode and useful momentum context where necessary. Filter impossible height/gap relations where justified; rank unresolved connections using expected transit time and the next interaction's requirements. An overestimate of physical capabilities is useful for a safe bound, while a more selective approximate forecast can supply the user's heuristic shortlist.

A macroconnection may represent an internally composed family, such as crossing a local block neighborhood either entirely in air or via landing/run-off. Its opportunities allow a support or ladder contact to refresh movement where needed; simply applying one long ordinary-air bound across every connection would omit such routes. Conversely, attach unsupported-air duration/height plausibility so arbitrary sky paths do not dominate the proposal pool.

No single straight-line geometric distance or scalar edge time fully captures all incoming states. Use it as a score, preserve a few useful arrival contexts, and let local evaluation update the estimate. Geometric existence is never presented as a numerical witness.

## Spatial cell itinerary versus timed movement realization

2026-10-31. The user agreed with the cell-decomposition direction and described high-level paths as sequences of cells, with several ticks potentially spent in one cell and local choices of transition/contact timing. They noted that the same spatial path can have different time costs depending on the interaction schedule. This is agreement on the conceptual hierarchy, not selection of a concrete decomposition algorithm or search backend.

Use a nonuniform 3D partition of free space; boxes need not have equal size or align to Minecraft block boundaries. A cell graph records spatial adjacency through finite interfaces. A route itinerary is an ordered cell/interface sequence. Consecutive occupancy of the same cell is not represented as a new spatial route node on each tick. Nonconsecutive revisits remain meaningful occurrences, including momentum-building returns.

A timed realization adds the actual input sequence, positions/velocities, transition phases, contact modes and contact ticks. A route's realized cost is its total elapsed Minecraft input ticks. In abstract notation:

```text
R = selected spatial cell/interface itinerary
C(R; initial_state) = minimum total ticks of a valid timed realization of R
```

This is the ideal route cost, not a quantity that bounded IPOPT computes globally. The existing secondary X/Z objective compares accepted realizations with the same elapsed ticks. The outer graph initially uses heuristic cost estimates and/or separate valid lower bounds, while local evaluation supplies candidate actual costs and useful continuation information.

### Artificial interfaces versus physical contacts

A cell boundary may be only an artificial subdivision plane in open air. Crossing or touching that plane creates no game collision, support, velocity reset or other physical effect. A physical contact is possible only on a marked patch belonging to actual geometry, with the appropriate footprint and phase. A face can contain both portal openings and physically obstructed/contact portions, so retain their finite patch geometry. Top/bottom/side refers to a cell's geometric face, not automatically to floor/ceiling/wall gameplay.

Thus local choices include when/where to cross a portal and whether/when to interact with a real support, wall, ceiling, ladder or stepping feature. Contacts can occur while remaining within one cell or on its closure; they need not cause a spatial transition. Multiple ticks in a large cell may include several inputs and interactions without changing the outer route signature.

### Why a cell edge cannot have one exact time weight

Traversal duration depends on entry position/velocity, movement/input state, chosen exit opening/position and the contact/control plan. Different realizations of the same cell sequence can therefore have different total time and exit momentum. A quicker local departure can make the next segment slower or infeasible. More time spent gaining momentum can improve total route time.

A movement connection can be viewed as a relation among an entry state, an exit state, a duration and a local event/control plan, rather than one universal number. The boundary state must include a tick phase and remaining movement context if the interface is crossed within an input, not just position/velocity at an integer state. For practical candidate ranking we can approximate this relation with a few arrival contexts, time estimates and feasible numerical seeds, retaining their context rather than treating a best observed transfer as universally available.

The spatial graph and a movement graph can share the same geometry. Ordinary fixed-weight shortest path is an approximation at the spatial level. A planning overlay may use state-conditioned transfer estimates; the local solver evaluates a consistent whole route. Numerical failure does not settle the minimum cost of the spatial itinerary.

One input may cross several cells, just as many inputs may stay in one cell. Do not assign an integer tick of cost to every cell interface. Preserve the original discrete inputs and Y/X/Z collision order when evaluating the whole movement. Cell crossings can be bookkeeping events within a physical input, and consecutive transfers within that input cannot choose independent controls or reset time.

### Practical division of work

- Spatial decomposition: store cells, openings and physical interaction patches.
- Outer selection: shortlist different spatial itineraries using coarse time and momentum/continuation estimates.
- Inner optimization: choose controls, contact methods and event timing for the complete selected itinerary from the original state; allow earlier facings to adjust.
- Feedback: rerank promising itineraries with observed actual costs and constraint deficits, without treating one witness or local failure as an exact universal edge cost.

This keeps the user's conceptual separation: path selection is about spatial choices, while local optimization determines a timed movement through those choices. At a fine partition, local method replacement may change incidental cell crossings; grouping such nearby itineraries into one broad route family remains an optional normalization step. No times, weights or movement states are fixed merely by accepting this abstraction.

## Defining a broad route family operationally

Historical grouping below: its explicit landing/overflight example is superseded by the 2026-11-03 clarification above. The requirement to preserve meaningful passages and ignore incidental subdivision still applies.

A family signature is the ordered sequence of significant spatial passages, side choices and selected transit landmarks. It does not contain every elementary cell ID or every physical contact. A small number of semantically meaningful portal tokens makes the grouping stable against arbitrary tessellation.

Example after automatic decomposition and compression:

```text
family A: start -> west passage of wall W -> region near block B -> ladder L -> goal
family B: start -> east passage of wall W -> ladder L -> goal
family C: start -> west passage of wall W -> outer ledge C -> goal
```

Inside family A, passing W at tick 4 or 5, entering L on adjacent ticks, and flying over B versus landing/running off are local realizations. B's neighborhood is not encoded as a mandatory landing. Bypassing the neighborhood through another broad corridor is a different family. If a particular support visit is chosen as a necessary landmark, its contact remains an explicit requirement.

Height distinctions are retained at the scale that affects broad connectivity, such as genuinely separate floors. Small vertical/contact variations within the same local interaction neighborhood can remain in one family. This grouping scale is a deliberate planning choice, not an invariant uniquely determined by the AABBs.

A literal left/right tag for every obstacle in the scene would be excessive and ambiguous when the path changes direction. Name actual physical face passages/portal groups encountered by the route, with world-space geometry and directed traversal. Preserve branch choices even if their difference occupies only a short portion of an otherwise shared route; a global overlap threshold alone could hide a decisive final obstacle-side alternative.

Local loops that build momentum in one neighborhood can stay in its local movement grammar. Broad revisits retain their ordered occurrence when they cross meaningful passages again. Never erase all cycles or close a physical region globally merely because ordinary geometric shortest paths would be simple. Methods for K shortest simple graph paths require this adaptation before use in a momentum-building problem.

## Small route shortlist: individual optimization and lower bounds

Status update, 2026-10-31: the user has now deferred new bound work. The bounding discussion below is retained as research context, not the next implementation. Follow [CELL_ROUTE_EXPERIMENT_PLAN.md](../design/CELL_ROUTE_EXPERIMENT_PLAN.md) for the current geometry/routes/local-decisions benchmark plan.

2026-10-31. User clarification: spatial route enumeration should already yield a small enough pool that every selected route can be optimized individually, obtaining its best found time and equal-time objective before comparison. They question the value of a detailed estimated route cost and suggest an optimistic hit/avoid/constraint choice as a possible, potentially weak, lower bound. The main architecture should not require a strong learned or estimated cost merely to evaluate the chosen routes.

### Revised primary workflow

1. Enumerate the bounded diverse spatial route set using the geometric/cell abstraction and chosen heuristics.
2. Give every selected route an initial local search over movement methods and timings, with full original-state continuous optimization and independent acceptance.
3. Compare accepted candidates by actual elapsed input ticks and then the configured equal-time objective.
4. Use valid lower bounds to skip work that cannot improve the current accepted incumbent. Use extra numerical effort selectively within a shared budget, without requiring accurate estimated costs as a foundational representation.

Estimated costs can still provide a solve order or effort-allocation hint if cheap. They are not needed as exact weights of the cell graph and should not be the basis for eliminating a route. Earlier score/weighted-best-first proposals remain optional techniques for generating a small shortlist or scheduling work, rather than the central requirement.

### Three separate numerical quantities

For route R, let T*_R be the ideal minimum elapsed ticks over all valid local realizations allowed within that route. Maintain:

```text
L_R <= T*_R <= U_R
```

L_R is a sound lower bound. U_R is the duration of the fastest independently validated witness found for that route, or unknown/infinite if there is none. An estimated typical/best time is a third quantity without this bounding guarantee.

A local optimizer's result supplies U_R, not T*_R. It may settle on one continuous basin or one contact/timing schedule. Optimizing continuous facings does not itself optimize the discrete schedule. The equal-time objective found is likewise a best found result unless a global certificate exists. This terminology preserves the intended per-route comparison without claiming a guarantee the present backend does not supply.

Weakness and validity are different. A weak bound that is far below T*_R cannot incorrectly discard R under the normal bound test; it simply fails to save work. A heuristic estimate above the true minimum can incorrectly discard a useful route. The user wants lower bounds for this safety role; their usefulness should be assessed against their computation cost.

### Elimination with a validated global incumbent

If the best complete accepted route takes T_best ticks:

- L_R > T_best: skip R because it cannot improve or match the incumbent time.
- L_R = T_best: keep R for the secondary X/Z objective, unless a separate certified objective bound settles it.
- L_R < T_best: R may be faster. A poor local witness U_R > T_best does not prove that R cannot improve.

Unknown numerical attempts remain unknown. If L_R meets an accepted U_R (accounting correctly for integer ticks and numerical validity), it can certify that route's minimum tick count, but does not certify the equal-time continuous objective. Heuristically omitted spatial routes remain outside this selected pool, so comparing all selected routes does not establish a global certificate over all possible routes.

### The proposed optimistic hit/avoid bound

Allowing an unrealistically favorable combination of interactions can give a valid relaxation: if every physically valid route realization remains available in the relaxed model, its true optimum cannot be greater than the true physical optimum. Independent local choices may have mutually incompatible exit/entry velocities or contact histories; allowing that incompatibility makes a bound weak, not automatically invalid.

However, several details determine validity:

- Include every allowed physical method or an outer relaxation covering it. Taking the best of only sampled timing/contact candidates is not a lower bound on all realizations.
- Obtain a globally valid bound for the relaxation, or a valid dual/analytic bound, rather than treating one local numerical solution of it as its minimum. A feasible point of a relaxed minimization problem is not automatically a lower bound for the original problem.
- Cover inertia snaps, contact resets, ladder caps and stepping behavior whenever replacing the movement dynamics. Simply deleting these effects is not automatically a relaxation.
- Do not sum one tick or a separately rounded-up duration for every cell/interface. One input can cross several cells, and independently optimal segment times require a consistent accounting of physical time.

Primary reference: [Boyd and Vandenberghe, duality and lower-bound property](https://web.stanford.edu/class/ee364a/lectures/duality.pdf). Backend limitation: [IPOPT documentation](https://www.coin-or.org/Ipopt/) describes local solutions. These establish the general bounding distinction, not a completed bound for our movement model.

### How to tighten a weak bound without solving the full problem

A useful direction is to retain coupling while relaxing hard choices: keep common positions, portal order and some momentum propagation, but overapproximate the permitted controls/contact modes. Pairwise or short-window joint interaction bounds can be stronger than allowing each event to independently obtain its most favorable incoming state. Start with cheap broad reach/time bounds and tighten only where the result might actually exclude expensive work. No particular relaxation or solver has been selected.

For a fixed-horizon bound, a proof that no realization can finish within T ticks implies a time lower bound above T. A test only for finishing at exactly T ticks is not necessarily monotone in T: earlier completion may exist without a legal way to pad to T. Any cumulative-horizon test or horizon search must handle that distinction instead of assuming an unknown/failed exact-T solve rules out all shorter routes.

This direction is compatible with the isolated mixed-integer experiment but does not authorize changing its scope or assume that an experimental disk/circle result is a certified bound. No new implementation or numerical experiment accompanies this discussion.

## Selecting a small useful set of families

The latest user preference is to enumerate a small route pool and optimize every selected route, as described above. As optional shortlist-generation methods, use bounded best-first search or diverse route enumeration with cheap time/continuation scores where useful; a detailed expected-cost model is not required. Keep a route only when it contributes a different passage signature or a useful arrival mechanism. Place caps on the number of broad families, local variants and actual numerical proposals separately.

Ordinary K-shortest enumeration can fill its output with nearly identical cell paths. A basic starting policy is cost-ordered candidate generation plus signature grouping and a diversity reserve. A neighborhood-augmented search is a research alternative for discovering spatially separated branches automatically, particularly when hand-built side signatures become cumbersome. Its neighborhood size and graph resolution require evaluation; it is not required for the first implementation.

Use local probe results to rerank families and refine coarse connections. Different method/timing alternatives share regional geometry and compiled templates where valid. Final solves preserve the complete original-state history and can adjust earlier facings; crossing a portal does not freeze a position or velocity.

Geometry cannot guarantee that a scene has only tens/hundreds of useful families: sequences of obstacle choices can create exponentially many possibilities. The small retained set is a heuristic product of the cost model and diversity policy. This matches the user's priority rather than requiring an exhaustive classification of every path first.

## What not to choose as the whole abstraction

- Pure 3D homotopy labels: distinct sides of finite box obstacles can share a topological class. We need spatial/movement relevance, not only topological inequivalence.
- A graph of block centers or support IDs alone: it discards finite corridor geometry and airborne interaction opportunities, while committing too early to landings.
- A floor navmesh with one fixed jump per link: useful for ordinary navigation, but too restrictive for multi-contact airborne composition.
- An unrestricted 3D shortest polyline: it models a flying agent without our dynamics.
- The full uncompressed cell graph as the definition of route identity: it treats tessellation details as route differences.

## Relationship to existing code and smallest validation study

Current code already has the geometric ingredients: expanded boxes, physical-block provenance, support footprints, finite face chains/gates, ladder regions and movement generators. Missing pieces are a shared free-region/portal structure, meaningful corridor compression/signatures and a shortlist policy over those families. The existing support transition graph supplies useful reach/time ideas, but not the complete airborne geometry representation.

A first abstract study should use boxes with: a wall with west/east passages; a low intermediate block offering overflight or landing/run-off; a stacked-floor/ceiling case that defeats a flat projection; and an airborne ladder transfer without an intermediate support. Check that it creates distinct broad passages, groups local variants, preserves finite contact patches and exposes the right movement opportunities. Add an equivalent collision union represented by differently split AABBs to check that incidental tessellation does not multiply route families. Include a useful revisit/momentum loop.

Measure graph construction cost, raw versus compressed cell/portal count, distinct signature count, shortlist diversity and useful-route retention under the same proposal budget. These studies and movement solves have not been run. This research selects a concrete abstraction to test; it does not assert a proven node-count reduction or production implementation.

## Active experiment plan, 2026-10-31

The user has deferred bounds and requested a staged implementation/benchmark plan. [CELL_ROUTE_EXPERIMENT_PLAN.md](../design/CELL_ROUTE_EXPERIMENT_PLAN.md) is the current next-work specification: isolate code in a new branch/worktree; build the cell/portal/contact representation; generate spatial routes; condition existing local movement generation on those routes; compare current generic search with prescribed-spatial-route and automatic-cell-route arms. Measure residual contact/mask/timing branching and real numerical work before deciding whether another interaction abstraction or later bounding work is warranted. This planning turn adds no implementation or measurements.
