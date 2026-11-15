# User-supplied blocks and mod-derived landing bounds

2026-11-02. This supersedes the initial JSON-only footprint hypotheses in INVENTORY.md. The user supplied seven geometries and asked for bounds incorporating the player hitbox and neighboring obstacles, using the mod's existing derivation.

## Coordinate interpretation for this report

Horizontal integer coordinates identify unit block cells. B1's ranges include both endpoint block coordinates, so X742 through746 means physical X=[742,747]; Z-1104 through-1105 means physical Z=[-1105,-1103]. Explicit Y=[0,h] is a physical vertical extent. B7's shorthand7 is provisionally read as topY7 with Y=[0,7]. If the user intended a single block at block-coordinate Y7, its Y range would instead be[7,8], and its top8. These conventions are recorded in user-blocks-provisional.json and must remain explicit until verified. The literal geometric-X-endpoint interpretation of B1 would exclude the saved start X746.5 from support, which is why the inclusive-cell interpretation is used here.

| ID | Physical X | Physical Z | Physical Y |
| --- | --- | --- | --- |
| b1 | [742,747] | [-1105,-1103] | [0,5] |
| b2 | [741,742] | [-1104,-1103] | [0,6] |
| b3 | [738,739] | [-1101,-1100] | [0,7] |
| b4 | [734,735] | [-1100,-1099] | [0,7.5] |
| b5 | [730,731] | [-1096,-1095] | [0,6] |
| b6 | [730,731] | [-1095,-1094] | [0,20] |
| b7 | [729,730] | [-1095,-1094] | [0,7], provisional shorthand interpretation |

## Player-center landing regions, including the B1 corner union

Executed the existing Java ConstraintDeriver.deriveFootprint and SupportOverlap code with Minecraft1.8.9 collision mode and all seven supplied boxes as neighbors. The original top-center reference and yaw0/-90 returned one rectangle for B1, shown as b1-a. The user's correction identifies the additional b1-b region. A second actual Java probe at reference X742.1/Z-1104.8 with yaw-90 returns a rectangle covering that missing part; the shared scene checker accepts a grounded state there. The full B1 support region is the union of b1-a and b1-b. The helper uses HALF=0.30000001192092896 and body-height1.8; display rounded to0.1. Original exact doubles are in derived-player-bounds.json; additional reference outputs and scene/cell checks are in b1-corner-check.json.

| ID | Player-center X | Player-center Z | Feet Y on top |
| --- | --- | --- | ---: |
| b1-a | [742.3,747.3] | [-1105.3,-1102.7] | 5 |
| b1-b | [741.7,742.3] | [-1105.3,-1104.3] | 5 |
| b2 | [740.7,742.3] | [-1104.3,-1102.7] | 6 |
| b3 | [737.7,739.3] | [-1101.3,-1099.7] | 7 |
| b4 | [733.7,735.3] | [-1100.3,-1098.7] | 7.5 |
| b5 | [729.7,731.3] | [-1096.3,-1095.3] | 6 |
| b6 | [729.7,731.3] | [-1095.3,-1093.7] | 20 |
| b7 | [728.7,729.7] | [-1095.3,-1093.7] | 7 under the shorthand interpretation |

B2 removes only the corner where X<742.3 AND Z>-1104.3 from B1's raw overlap region. Equivalently, within X=[741.7,747.3], Z=[-1105.3,-1102.7] at feetY5, require X>=742.3 OR Z<=-1104.3. The original selected GUI rectangle globally imposed the first alternative and omitted the second. Tall B6 clips B5's upper Z to-1095.3 and B7's upper X to729.7. These reproduce the narrower B5 and B7 authored player-center constraints without inventing narrow physical blocks. The earlier hypothesis that B5's physical Z footprint might be0.4 wide is superseded by the explicit user-supplied full footprint and neighboring obstruction.

These are top-surface standing/landing rectangles. They do not describe a complete swept movement between surfaces or prove reachability of a top. The mod's corner derivation can select a rectangle based on click/reference and facing; a selected rectangle is not a proof that no alternative collision-free corner region exists. Future orchestration must preserve full collision boxes and existing passage/case/scene validation rather than freeze one GUI rectangle as the universal space for every movement regime.

## Source and reproduction

The UI path is ConstraintKeyController.onKey -> ConstraintDeriver.deriveFootprint, with selected support, nearby collision boxes, click point and facing. SupportOverlap handles precise legal support endpoints. No player-box or collision-constraint logic was reimplemented.

DeriveSelectedFootprints.java is a source-mode invocation adapter for those existing Java methods. Input, exact outputs, invocation and hashes are stored beside this document. The standalone convenience footprints action currently calls deriveFootprint with obstacles=null; this report intentionally calls the shared Java method with the actual neighboring boxes instead. Existing full-scene collision checks remain separate. No mod code, solver model, limits or runtime footprint endpoint was changed, and no route solve has run in this bounds-verification step.


## B1 corner handling in the existing code

ConstraintDeriver.clipByObstacles clips relative to a reference point. clipByCornerObstacles then chooses one rectangular along-facing/across-facing alternative according to the facing; it returns one four-number rectangle, not a union of both. At the original support-center reference744.5/-1104, yaw0 and-90 both produce b1-a. At742.1/-1104.8, yaw0 still gives b1-a while yaw-90 gives X=[741.7,747.3], Z=[-1105.3,-1104.3]. Thus the helper accounts for the corner as a selectable rectangular constraint, but does not enumerate the full nonrectangular feasible set.

For the autonomous solve, preserve B1's full geometry/cell union, or enumerate both existing fixed rectangular avoidance alternatives in the orchestrator. A single GUI reference/facing choice must not remove the other valid region from discovery. The continuous solver still receives fixed constraints for its selected branch; no new mixed model or physics derivation is needed.

Existing cell decomposition already retains sample[742.1,5,-1104.8] in cell29 for this supplied scene, while excluding[742.1,5,-1104.1] inside the expanded B2. A shared Java scene check accepts one stationary grounded input at the valid corner sample. Added a generic regression showing both arms of the support region are retained, an obstacle-interior sample is excluded, a turn around the corner is covered and an obstacle-crossing diagonal is rejected. All eight tests in test_cell_routes.py pass. No production geometry/model code or mod GUI behavior was changed.
