# Linkcraft Real11 opening support inventory

Superseded for setup by [the user-supplied blocks and mod-derived landing bounds](PLAYER_BOUNDS.md). The earlier table below remains provenance for the initial JSON-only investigation; its guessed support IDs and ambiguous extents are not the current user block list.

2026-11-02. Source: `/Users/arvindmurty/Downloads/LinkcraftII_Competition_01/real/11.json`, SHA256 `af5d2d1bc6e0ab466f1b6cf309f3a26e76e4a63765ac8b15ff02fb4ad55d28bf`. The requested scope is the opening few jumps, using the JSON alone, followed by user verification before modeling/solving. The table includes the start and first five recorded landing surfaces to provide lookahead. No new numerical solve has been run.

The source has `angleSolver.selectedBlocks: []`. There is no explicit collision-box or block-type list. It contains authored player-center constraints and recorded position/ground states. The inventory is therefore a set of unconfirmed candidate supports, not an exported collision scene. Exact source intervals/provenance are saved in `support-inventory.json`.

| Candidate | Opening role | Candidate physical X interval | Candidate physical Z interval | Top Y | Source/ambiguity |
| --- | --- | --- | --- | ---: | --- |
| B0 | start | [746,747] | [-1105,-1104] | 5 | Footprint is the old fixture's grid-block hypothesis from the start position; no authored support bounds. |
| B1 | landing after j0 | [743,744] | [-1104,-1103] | 5 | Footprint is the old fixture's grid-block hypothesis from the recorded first landing; no authored support bounds. |
| B2 | landing after j1 | [741,742] | [-1104,-1103] | 6 | Compatible with authored center ranges at state20, if they encode ordinary overlap alone. |
| B3 | landing after j2 | [738,739] | [-1101,-1100] | 7 | Compatible with authored center ranges at state29 under the same interpretation. |
| B4 | landing after j3 | [734,735] | [-1100,-1099] | 7.5 | Compatible with center ranges at state39; vertical thickness/type is unknown. |
| B5 | landing after j4 | [730,731], conditional | unresolved | 6 | State54 constrains center X=[729.7,731.3], Z=[-1096.3,-1095.3]. These may include a tactical restriction, not just support overlap. |

Top heights for B1-B5 come from recorded grounded states12,21,30,40,55, not explicit block definitions. Start top comes from the saved start at Y5 and onGround=true. Recorded state numbers identify provenance only; they must not become fixed event times in the future cold search.

For B2-B4, a unit footprint is compatible with the player-center ranges expanded by the existing player half-width, approximately0.3. That inversion is conditional: authored bounds can be tighter than physical support and do not uniquely establish geometry. For B5, applying the same inversion literally would give X=[730,731], Z approximately[-1096,-1095.6], width0.4 in Z. Do not silently turn that into an actual narrow block. The true footprint requires confirmation.

No vertical bottom is specified for any of these surfaces. In particular Y7.5 does not identify whether B4 is a slab or another shape, nor the collision-box bottom. There are no explicit opening ceiling or obstruction boxes. Absence of boxes in this JSON does not prove an empty world.

## Setup after verification

Use confirmed physical AABBs as the planner's candidate geometry, with explicit declared coverage/assumptions. The orchestrator should discover support order, allowed movement cases and timings from those blocks with the existing movement/contact/constraint builders. Select an opening goal block and preserve the full original start position/velocity. Earlier facings remain variable. Recorded facings, airtimes, jump order and tactical scalar rows are diagnostics/provenance, not mandatory cold-search decisions. The old linkcraft extractor fixes profiles and uses inferred geometry; it is a historical comparator, not an adequate substitute for this new setup.

A list of support tops alone is enough only for a declared support-plane experiment. The current cell-decomposition/checkpoint path expects collision boxes, so unresolved shapes must be confirmed rather than invented. The user explicitly limits this stage to blocks represented by the JSON; no world archive geometry is imported.
