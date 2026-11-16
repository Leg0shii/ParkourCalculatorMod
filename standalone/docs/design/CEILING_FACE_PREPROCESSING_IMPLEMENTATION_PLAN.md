# Ceiling-face preprocessing and finite-contact coverage: implementation plan

2026-11-15. Status: concrete proposal requested by the user; **implementation has
not begun**. Baseline `65ed610a` in the starting checkout. This is the single
implementation plan for the [Real11 investigation](../REAL11_EXTENDED_GEOMETRY.md#preprocessing-investigation-shared-ceilings-distinct-landings).
It covers the correctness fix before the optimization, includes scoped avoidance
reuse, and preserves original collision solids and owner-specific landings.
The deferred scheduler and separately assigned bookkeeping task remain separate.

## 1. Intended result and scope

B9a/B9b have a common underside at Y9, but tops10.5/9.5. Their adjacent underside
patches should produce one ordinary ceiling contact domain rather than two
block-specific decisions. B10 also has underside Y9 and can join that surface.
Their original boxes, top heights, support identities and mandatory-anchor
semantics remain distinct. No longer-course anchor order/finish is selected here.

Implement:

1. Correct every finite-contact identity/dedup path that can discard geometric
   coverage, including the second profile-level dedup.
2. Precompute immutable, provenance-preserving ordinary ceiling-face groups.
3. Generate and compile exact finite contact domains from those groups across
   open, support/profile, timeline and mixed-ladder consumers.
4. Reuse grouped work for obstacle avoidance only under an explicit scoped
   equivalence proof; retain original alternatives whenever that proof or
   per-member resource equivalence is unavailable.
5. Preserve replay, phase order, bounds, original-state re-solving, identity,
   exact continuation and auditability.

Initial grouping is for **ordinary upward Y contacts**. Wall-contact dedup is
fixed too, but grouping arbitrary walls is outside this feature. Named grind,
blip, step and ladder transition behavior cannot be coalesced merely because a
plane matches. Existing distinct named generators remain legitimate consumers.
There is no scheduler, new pruning policy, tolerance change or solver-objective
change in this work.

## 2. Confirmed current paths and bugs

| Owner | Current behavior / required change |
| --- | --- |
| [`movement/contact_combinations.py`](../../src/parkour_solver/movement/contact_combinations.py) | `contact_combinations` drops all but the first opportunity with equal leg/offset/axis/plane/direction/role, without including finite geometry. Fix first. `merge_contacts` must not silently lose finite requirements when used as a scalar merger. |
| [`movement/contact_profiles.py`](../../src/parkour_solver/movement/contact_profiles.py) | `generate_contact_profiles` dedups accepted profiles by offset/axis/direction/plane only. It can discard the second finite domain even after the first bug is fixed. |
| [`movement/contacts.py`](../../src/parkour_solver/movement/contacts.py) | `contact_signature` includes box/role/step, but omits other contact semantics. Make it delegate to the shared identity. Predictions, singletons, combinations and existing-contact checks all use it. |
| [`movement/ceiling_generation.py`](../../src/parkour_solver/movement/ceiling_generation.py), [`ceiling_profiles.py`](../../src/parkour_solver/movement/ceiling_profiles.py) | Ordinary ceiling generation is per block/solid. Share face groups before contact/profile products; keep named edge-dependent cases separate. |
| [`movement/ladder/ceiling.py`](../../src/parkour_solver/movement/ladder/ceiling.py) | Ownership/effect construction reads a single `box`. Preserve ladder owner/successor/refresh behavior and support the shared finite-domain descriptor. |
| [`spatial/cells.py`](../../src/parkour_solver/spatial/cells.py), [`regions.py`](../../src/parkour_solver/spatial/regions.py), [`event_routes.py`](../../src/parkour_solver/spatial/event_routes.py) | Exact free-volume union, planar `canonical_cover`, contact patches and coplanar **top** surface grouping exist. Reuse them; underside grouping is missing. |
| [`constraints/variants.py`](../../src/parkour_solver/constraints/variants.py) | Avoidance preparation/products are per active solid, with per-owner blockage counts and resumable cursors. This is where scoped group reuse belongs. |
| [`constraints/hit_events.py`](../../src/parkour_solver/constraints/hit_events.py), `compile_contact_events` | Compile finite constraints from a single actual box. Add the shared face-patch lowering, rather than constructing a fictitious merged solid. |
| [`validation/scene.py`](../../src/parkour_solver/validation/scene.py), [`native bridge`](../../native/java/autonomy/CompileRunTicksPhysics.java) | Original-scene checking already accepts a planned contact's `boxes` alternatives. `matchesFace` checks an actual scene member. Reuse this existing contract. |
| [`search/identity.py`](../../src/parkour_solver/search/identity.py), [`movement/program.py`](../../src/parkour_solver/movement/program.py) | Scope validation and several identity/composition paths assume event `box`. Audit/update all readers before emitting grouped events. |

[Direct evidence](../../research/real11-extension/ceiling-preprocessing-audit.json)
shows two ceiling opportunities becoming one singleton; reversing input reverses
the retained box. Center X730.5/Z−1091.5 belongs only to B9b's ceiling footprint.
The [isolated profile-collector probe](../../research/real11-extension/profile-dedup-audit.json)
feeds two independently valid singleton choices past the first dedup. Both have
airtime11, both are attempted, and only one is retained. Turn this into a focused
regression rather than allowing the first fix to mask the second bug.
This is not a claim that a longer Real11 cold solve has already failed.

## 3. Invariants and terminology

Distinguish three things:

- **Physical member:** an original scene solid with original owner/solid index.
- **Face group:** exact coplanar underside geometry, with all physical members.
- **Contact patch choice:** one exact finite center-domain rectangle at a
  specified input/phase/plane, associated with compatible member alternatives.

A single grouped contact means **hit any permitted member within its selected
patch**. It is an OR. Two separately required finite contacts are an AND and
must never become an OR as an incidental effect of scalar-contact merging.
The vertical impulse/clip is still one effect on that input axis.

Hard requirements:

- Never replace original solids with their 3D bounding box. That would invent
  material above the shorter block and change landings/side collisions.
- Never turn a nonrectangular/disconnected union into its bounding rectangle.
- Equal plane or equal scalar vertical trace does not establish equal finite
  domains, ownership, named effects, resources or continuation consequences.
- Phase contracts stay unchanged: upward Y contact checks X/Z at the existing
  Y-phase references; horizontal contacts retain their existing asymmetric X/Z
  phase references. Do not substitute end-of-tick positions.
- The cover must preserve the **current finite-clearance semantics**. Expand
  and apply contact clearance to each original member domain before canonical
  union. Eroding the final union instead is not generally equivalent.
- Full supplied-scene validation continues to use the original boxes.
- Unknown reachability/shortening remains unknown; no grouping from one solved
  pose or a guide's direction.

## 4. Shared ownership and data contracts

Preprocessing has three lifetimes: scene geometry, anchor-span eligibility, and
exact proposed-movement specialization. A physical block never receives one
permanent exclusive label such as ceiling/obstacle/landing.

### 4.1 Finite-contact identity and scalar/finite separation

Extend `movement/contact_combinations.py`, the existing contact merge/validation
owner, with one canonical finite-contact identity function. Use it from
`contact_combinations`, `generate_contact_profiles`, `contacts.contact_signature`
and every dedup/cache discovered in the contact-call-site audit.

The identity includes normalized event time, axis/plane/direction/role, exact
finite domain/member geometry and provenance, step/case/clearance metadata,
before/after avoidance requirements, and effect/owner/successor fields. Unknown
semantic fields are retained in canonical serialization rather than silently
discarded. Normalize redundant `state=offset+1` when both forms are present and
reject inconsistent times. Only established display fields may be omitted.
No coordinate rounding or point-sample equivalence.

Exact duplicate opportunities collapse. Different finite domains remain separate
until an explicit union descriptor exists. The opportunity combinator treats
different ordinary finite contacts on the same leg/input/axis as alternatives,
not a useful product of two independent impulses. A group can represent their
union. Explicit independently required constraints must remain separately
represented/validated; do not silently drop them through `merge_contacts`.
Scalar projection merges compatible vertical effects once, after separating the
finite requirements. Preserve the existing scene-member alternative contract.

### 4.2 Geometric catalog

Add **one new geometry owner**, `spatial/contact_faces.py`, beside current
surface grouping. It reuses `canonical_cover`, intersections and player
dimensions. It must not contain Real11 IDs or solver traversal policy.

Provide these responsibilities in that module (names may follow conventions):

```text
build_ceiling_faces(solids, owners) -> immutable catalog
contact_patches(face, clearance, active_members) -> canonical finite rectangles
contact_members(event) -> validated original member references
contact_region(event, phase) -> selected finite domain, with explicit semantics
```

Build the catalog from actual ordinary upward-contact undersides. Group by exact
axis/normal/underside plane and compatible geometric contact behavior, then
canonical connected covers. Keep disconnected components explicit. Store:

```text
policyVersion / body dimensions / original-scene fingerprint
faceId: exact plane + direction + canonical geometric cover
members: stable original owner, original solid index, full original box
member domains and canonical cover, with boundary/clearance convention
```

`faceId` is geometric, but cache/solve identities also include original scene,
member provenance and active scope. A matching faceId cannot reuse a proof after
the taller cap, owners, scope or behavior changed. Sort canonical geometry;
store original indices as provenance, not the sole geometric key.

Do not require a new full cell decomposition in every generator. Existing
decomposition/contact patches may supply proven exposure diagnostics; a raw
member face retained because exposure is unresolved remains subject to the
existing gate and full scene validation. No new pose-based exposure filter.

### 4.3 Selected event and exact rectangular lowering

Keep historical single-`box` input supported through the shared reader. A new
grouped ceiling event carries:

```text
leg, offset, axis=Y, direction=+1, role=ceiling, plane
faceId, patchId
contactRegion: exact clearanced center rectangle and phase contract
members: original boxes + owners/indices compatible with that patch
```

Do **not** put an arbitrary representative into `box` and hope other consumers
ignore it. New grouped events cannot masquerade as one original box. All readers
on their path must use the shared geometry accessor.

For one canonical rectangle, emit one contact choice and four finite X/Z rows
at the existing contact phase, plus the existing scalar Y clip. Forward original
member boxes as `boxes` for scene validation. A connected L-shape needs several
canonical rectangles: generate their choices lazily before expensive profiles
or NLP calls, retaining exact union coverage. Do not impose every rectangle's
rows at once. This uses the current fixed-choice solve model and avoids adding
a new mixed/disjunctive numerical solver.

If patches overlap, exact duplicate effective models may share work only while
preserving distinct required ownership/resource observations. Geometry-only
aliasing is insufficient for a general program merge.

### 4.4 Anchor-span views and exact decision specialization

**Scene lifetime:** compute potential underside groups once. Original top
surfaces and full collision solids already supply landing/obstacle geometry.
For B9a/B9b, the catalog contains a shared underside at9 and separate tops at
10.5/9.5 simultaneously. This geometric fact does not depend on anchor order.
The catalog does not assert that all those interactions are possible in every
span, or that an underside group replaces the full blocks.

**Anchor-span lifetime:** prepare a conservative view for ordered S→E:

- E is the required arrival; S retains the existing legal setup hops.
- Optional landing eligibility follows current `AnchorAdapter.context` and
  physical domain/latch/visit rules. Existing future-anchor restrictions and
  departed-owner rules remain authoritative; no new landing whitelist.
- Each other member retains a set of possible interaction roles: landing,
  upward ceiling contact, side/full-solid obstruction, or proved irrelevant.
  These roles can overlap. In particular a landing target is still an obstacle
  during an incorrect approach and can have a reachable underside.
- Index applicable face groups and retain original member/top references. A
  span-wide ceiling-only simplification is allowed only if a conservative bound
  covers **all admitted preparations/optional contacts/decisions and incoming
  states** in that span, including its remaining horizon and mechanics. An
  ordinary single-jump arc, straight guide or one incoming witness is not such
  a bound. If that enclosure is unavailable, keep multiple potential roles.

Attach this view at `AnchorAdapter.for_goal/context`, using its actual
`anchorIndex`, policy/domain, remaining resources and permissions. Non-anchor
event search uses the corresponding request/context; shared geometry must not
require anchors. The span view is an index/eligibility view, not a new hard
corridor, artificial future clock or future-anchor row in an internal query.

**Exact proposed-movement lifetime:** after fixing a proposed scalar schedule
and its actual query window, use the existing `InteractionGate`/phase envelopes
to select applicable roles and prove any scoped avoidance equivalence in P4.
Decisions within the same anchor span may therefore have different views. Known
endpoint Y alone does not bound intended/probe Y; keep the existing unknowns for
stepping/ceiling outcomes. Unknown role relevance retains the original choices.

For a lower movement under B9a/B9b, the chosen/proven context may need only their
shared underside. In a later movement landing on B9a, its10.5 top and B9b's9.5
top remain separate choices and original side collision geometry remains active.
No destruction/reconstruction of physical blocks is required when advancing
anchors; only the view over the persistent geometry catalog changes.

Cache keys follow lifetime: geometry keys include the exact scene/body/face
policy; span indexes also include source/target, visit/latch/landing permissions,
mechanics, resources/horizon and the enclosure they actually cover; exact proofs
add the program/model, constraints, phase/window and active scope. Do not cache
an exact-model proof solely under the anchor pair. Only a proved span-wide
enclosure can authorize reuse across all its covered candidate models.

## 5. Implementation sequence and recoverable commits

### P1 — Correct finite-contact coverage before optimization

1. Add minimal generic regressions for same-plane, different-footprint ceiling
   and wall contacts. Include input reversal, disjoint domains and a point unique
   to each footprint. Confirm the existing combination bug fails them.
2. Add the profile-level regression independently, so fixing the first key cannot
   hide the second. Check contact semantics/avoidance metadata as well as boxes.
3. Implement the shared identity, delegate all relevant signatures, and separate
   scalar merging from finite requirements. Exact duplicates still collapse;
   compatible same-axis alternatives stay available without pair fan-out.
4. Audit generated-program, legacy support-profile and mixed-ladder dedup paths.
   Search all contact keys/signatures and all direct event `box` reads. Preserve
   current caps and report bounded enumeration honestly if restored alternatives
   consume more of a finite cap.
5. Bump generation/repair compatibility policy for the corrected enumeration;
   old pending/seen cursors cannot claim exact resume under the new generator.

Commit the standalone correctness fix with focused tests and documentation.
This stage deliberately may generate more legal alternatives than the buggy
baseline. Do not judge the optimization against accidentally lost coverage.

### P2 — Build and test the immutable ceiling catalog

1. Add `spatial/contact_faces.py` with exact member-domain canonicalization and
   the explicit event readers above. Reuse `canonical_cover`; no duplicate union
   algorithm and no dependency from scene/physics primitives into search.
2. Add catalog ownership to `RouteDomain`/adapter context. Build once for a scene;
   keep the JSON catalog immutable and refer to stable face/patch IDs. Reuse it
   across internally owned slices rather than copying it per frontier entry.
   Build the anchor-span index described in §4.4 over this same catalog; preserve
   multiple potential roles until their actual relevance is established.
3. Give support-profile/timeline/mixed-ladder entrypoints one equivalent catalog
   built at their existing scene/pool preparation boundary. Add optional prepared
   catalog parameters where their public API currently takes raw solids; absence
   derives the same catalog, not another implementation.
4. Keep body/scene/clearance/scope identities explicit. Preserve unknown exposure
   and original supports. Catalog build interruption cannot publish a partial
   catalog as complete or silently truncate geometry.

Tests: B9a/B9b/B10 share a ceiling but have separate tops; split-box/permutation
invariance of geometric covers; disjoint, L-shaped, holed, contained and touching
patches; distinct underside heights; exact boundary/clearance cases; different
owner/task requirements. Preserve provenance after geometric simplification.

### P3 — Wire ordinary ceiling generation and fixed compilation end to end

Update, in dependency order:

1. `ceiling_opportunities`, the ordinary branch of `ceiling_profiles`, and
   `open_ceiling_choices`: iterate face patch choices instead of raw box aliases.
   Compute the scalar approach/hit once per compatible plane/incoming scalar
   context; do not rerun `approaches` independently for every member.
2. Timeline `CeilingGenerator`, `ceiling_sequence` and ladder sequence prediction:
   accept the same finite descriptor and reuse scalar results at equal planes.
   Preserve ladder activation/exit filtering and repeated-ceiling semantics.
3. `contact_profile`, composition, `own_ceiling_contacts`, generated movement
   units and truncation: retain face/member/patch data and the existing effect
   ownership. No extra requested jump impulses, contacts or decision ticks.
4. `validate_contact_events`, `compile_contact_events`, legacy `ceilingEvents`
   validation/lowering and `compile_hits`: use one finite-domain reader/lowering
   helper for grouped upward contacts. Existing wall/landing/named-case phase
   rules remain unchanged. Avoid a second ceiling-only constraint formula.
5. Program/search scope membership: every referenced selected member must belong
   to the modeled scope. Build scoped catalogs from active members when proposing
   scoped work; reject incompatible already-selected events rather than silently
   widening numerical coverage or changing their requirements at dispatch.
6. `check_geometry`: pass the actual allowed original members through the bridge's
   existing `boxes` contact contract. The contactRegion rows and member clip check
   must agree. No Java/model equation changes are expected; if a bridge change is
   actually needed, read the affected guidance and expand validation then.
7. Hard-flight ceiling bounds (`search/bounds/flight.py`): reuse the same group
   membership/plane queries, preserve the first-upward-contact logic and unknown
   result on any possible shortening. A group query is absent only if its full
   permitted member/patch union is absent. No larger flight bound from a missed
   member; no change to the hard-transfer no-stepping assumption.

Commit once grouped contacts work from proposal through original-scene replay,
with coverage equivalent to the corrected P1 baseline. Do not ship a catalog
that only the diagram/reporter uses.

### P4 — Scope-safe avoidance reuse before obstacle products

This stage addresses unintended obstacle interaction, not just selected head
hits. Unconditional full-volume merging is invalid because the tops differ.

1. Add a group preparation path to `ConstraintVariants` and shared interaction
   preparation. Use existing original-state envelopes and actual Y/X/Z probe
   windows. Do not classify a block as ceiling-only from the route label.
   A valid span-wide enclosure can serve all candidate models it covers;
   otherwise establish the equivalence for the exact proposed model/window.
2. A sufficient initial proof for this specific reduction is: throughout every
   relevant probe window, the conservative feet-Y upper bound is **strictly
   below the minimum physical top of every grouped member**. All members then
   have the same relevant lower Y boundary and their differing upper caps cannot
   affect that window. Unknown/unbounded Y, stepping probes or equality at the
   shortest top fails this test and uses original per-solid preparation.
3. Under that proof, represent the union's forbidden interior as the exact
   horizontal member cover above the common underside-minus-body-height plane,
   within the certified window. For a rectangular union this shares one exterior
   face set. For nonrectangular cover, avoidance is a **conjunction** of avoiding
   every component, each with its own exterior alternatives; it is not the OR
   used by a required contact. Retain original items if that exact product is
   not representable with unchanged member accounting. These
   are scoped constraint domains, **not replacement collisionScene solids**.
4. Share plane/probe work and compatible rows before constructing Cartesian
   products. Keep proof IDs, original member masks and each member's original
   blockage budget/witness. Use the existing masks/tables to establish which
   per-member legal avoidance chains witness a shared option. Retain distinct
   resource states whenever future choices can distinguish them; group counts
   do not replace original per-owner caps.
5. Merge an option only when both its geometric domain and its legal per-member
   accounting are represented exactly. If that proof is unavailable, share the
   inexpensive geometry/plane calculations but retain the original item/options.
   Do not force a single arbitrary member chain or silently loosen/tighten caps.
6. If a member is already proved irrelevant by the shared gate, normalize it out
   in that exact role/window and recompute the active cover. Unknown retains it.
   Required landing/contact obligations are not removed with unwanted-contact
   proofs. A grouped broad-phase miss must cover all member alternatives.
7. Full scene checking and diagnostics still report actual original solid indices.
   Resumable group preparation records the exact unprocessed phase/item/option,
   and interrupted proof construction commits no partial equivalence assertion.

Acceptance: exhaustive tiny fixed-horizon controls compare corrected ungrouped
versus grouped permitted models/coverage under original caps, including paths
along different exterior faces, phase changes, finite gaps, different member
caps and unknown probes. Equal sampled trajectories alone are insufficient.
If a case cannot be represented with existing accounting, retain its per-member
options and record the fallback reason. This fallback is a precise semantic
condition, not a user-facing old/new feature flag.

Commit scoped reuse separately so its coverage and cost effects are reviewable.

### P5 — Resume, observability, docs and final validation

**Compatibility:** add shared contact-identity/face-preprocessing policy constants
to generator, domain, DFS, exact solve and repair/variant context identities.
Update existing hardcoded generation3/constraint7/assembly9 references together
when their behavior changes; do not leave different callers accepting different
policies. Add schema versions where serialized field/cursor shape changes.
Storage manifest/SQLite format need not change. Old report decoding remains
supported; old-policy pending cursors must be rejected explicitly for exact
resume, not quietly reused. A legacy witness may be independently revalidated
as a fresh control rather than treated as an exact continuation.

Audit `program_physics_key`, contact signatures, `solve_scope`, `solve_identity`,
`repair_state.compatible`, `validation_prefix`, projection/truncation, outer/inner
generator cursors and checkpoint callback ownership. Store immutable shared face
data once; external resumed mutable values remain isolated. No changes to
bookkeeping cache semantics without reviewing the separately assigned work.

**Observability:** add counts/times to existing diagnostics, not a new log system:

- raw member faces, groups, clearanced patches and ordinary proposals emitted;
- exact duplicate contacts removed versus different domains retained;
- scalar plane/hit calculations reused, member coverage in each selected event;
- scoped avoidance groups attempted/admitted/fallbacks, proof IDs and original
  member resource contexts;
- prefix/proposal/profile/constraint-variant counts, NLP calls and worker time;
- geometry preparation and identity overhead, separately from numerical time.

Extend `tools.geometry_bounds` to optionally include shared ceiling groups and
their members/patches in its report, calling the production catalog. Update the
existing report/runbook; do not add another Real11-only ceiling calculator.

**Validation sequence:**

1. New `tests/unit/spatial/test_contact_faces.py` for catalog geometry; extend
   `tests/unit/movement/test_contact_combinations.py` for both identity losses
   and `tests/unit/movement/test_ceiling_contacts.py` for finite-row lowering
   and wrong-member negatives. Extend `tests/integration/test_geometry_bounds.py`
   for report/catalog agreement. Reuse existing test builders rather than putting
   Real11 coordinates in production or importing another test case.
2. Existing contact-combination, ceiling-contact, stepping, ladder-ceiling,
   mixed-contact, scope, hard-flight-bound and projection suites, native enabled
   wherever needed. Dedicated ladder suites are outside the old selected gate,
   so name/run them explicitly and distinguish pre-existing baselines.
3. Tiny exhaustive finite-domain comparison with the corrected P1 behavior:
   no loss of member-only contacts or legal resource contexts; duplicate profiles
   actually collapse for a rectangular union.
4. JSON and shared-store interruption/resume at catalog/group preparation,
   proposal, constraint-alternative and in-flight repair boundaries. No repeated
   completed query, lost alternative, cursor renumbering or alias leakage.
   Extend `tests/integration/test_repair_resume.py`,
   `tests/integration/test_orchestrator_continuation.py` and the existing
   checkpoint-store tests at their respective ownership boundaries.
5. A small floor+adjacent-ceilings solve with distinct landing tops and an actual
   trajectory contacting each member; validate against original boxes. Include
   a group-rectangle point in a concave hole that must be rejected.
6. Real11 geometry audit: B9a/B9b/B10 -> one compatible underside group, tops
   remain10.5/9.5/10; B8/B22's equal underside must preserve their irregular union;
   B13 stair/owner semantics and unit B6/B7 remain correct.
7. Fixed-work matched grouped/ungrouped controls with the same legal domain and
   generous caps. Measure generation, variants, numerical calls and preparation
   separately. A shorter wall-budget trace or the buggy pre-P1 domain is not the
   reference for coverage. Longer-course cold timing waits for its task inputs;
   do not invent a B22 goal or claim a new full-course result.
8. Selected native gate, package/layout checks, protected902 hashes, documentation
   check, then a coherent implementation commit with updated current status.

## 6. Completion criteria and remaining caveats

Done means the same-plane finite-contact loss is fixed at both known dedup stages
and audited consumers; rectangular ceiling unions generate one ordinary physical
choice per incoming/timing context; all accepted contacts validate against actual
members; top landings remain distinct; nonrectangular coverage, scope/caps and
exact resume are preserved; and measured diagnostics distinguish saved work from
restored coverage.

No user decision is required to choose the implementation mechanics above. The
following are limits, not hidden assumptions:

- Named edge cases are not interchangeable with ordinary ceiling hits.
- Arbitrary avoidance products may remain per-member when scoped equivalence or
  original resource accounting cannot be certified. The report must say why.
- Correctness fixes can increase legal work before grouping reduces duplication.
- Performance benefit is unmeasured until fixed-work controls run.
- The longer Real11 task still needs anchors/finish/budgets; that does not block
  this generic feature's implementation or small end-to-end acceptance.

Update this plan's milestone/status table in place during implementation. The
investigation, current state and deferred-work register link here; do not append
a conflicting second instruction set.

| Milestone | Status |
| --- | --- |
| P1: complete finite-contact identity fix | Planned |
| P2: shared immutable ceiling catalog | Planned |
| P3: generators, constraints, validation and bounds | Planned |
| P4: scope-safe avoidance reuse | Planned |
| P5: resume, observability and acceptance | Planned |
