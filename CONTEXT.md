# Parkour Calculator Mod

Domain glossary for the Minecraft parkour and movement TASing niche this tool operates in. These terms come from the speedrun community and from Minecraft's movement internals; almost none are general programming concepts and almost none appear in an LLM's training data. For where code lives and the rules that must not break, see `AGENTS.md`. For the project's north star, see `docs/VISION.md`.

This file is a glossary only: what terms mean, not how anything is implemented.

The canonical community source is the Minecraft Parkour Wiki, `https://www.mcpk.wiki/wiki/Parkour_Nomenclature`. Its terms are mirrored here in-repo deliberately: that wiki sits behind a Cloudflare challenge and cannot be fetched by an agent, so a bare link would be useless. Aliases (the community shorthand, e.g. `bm`, `hh`, `cp`) are given in parentheses. The exact physics this tool replicates (movement formulas, constants, the sine table, collision order, block friction, status effects, tiers) is mirrored as reference under `docs/reference/mcpk/`.

## Roles and workflow

**TAS**:
Tool Assisted Speedrun. A frame-perfect (tick-perfect) sequence of inputs crafted with tool help rather than executed live by a human. This tool produces TASes for parkour and movement.

**Strat**:
A specific method or setup to complete a jump: where to stand, what to press, how to turn. Stratfinding is discovering whether a jump is possible and exactly how to land it; a stratfinder is a person who does this. One of the tool's two audiences (the other is TAS creators).

**Route**:
A multi-jump sequence from a start block to a goal block, as opposed to a single jump.

## Ticks and controls

**Tick** (t):
One Minecraft simulation step, 1/20 of a second (50 ms; 20 ticks per second). The atomic unit of the whole tool: inputs are per tick, the path is one position per tick, and the solver reasons tick by tick.

**WASD**:
The default movement controls: W forward, A left, S backward, D right.

**Sprint** (ctrl):
An action that makes the player move ~30% faster. Activated by the sprint key or by double-tapping W. A jump performed while sprinting (a sprint-jump) is the standard long jump and gets an extra forward velocity boost on the takeoff tick.

**Sneak** (shift):
An action that makes the player move ~70% slower and prevents them from walking off a block edge.

**Strafe** (A or D):
Moving sideways with A or D, optionally combined with W or S. Changes the player's direction of travel without turning the camera.

**45 strafe**:
Turning the camera 45 degrees while strafing accordingly, so the raw movement input points diagonally. Lets the player move up to ~2% further than W alone, but is hard to do consistently.

**Timing**:
A simple sequence of inputs used to gain momentum. Basic timings include jam (0t) and headhitter (1t).

**Tap**:
Moving in small intervals (1 or 2 ticks, usually while sneaking) to set the player in an optimal position.

## Orientation and measurement

Note: community canon and Minecraft's code use different words for the same things. The glossary uses the community word as canonical and notes the code field name.

**Facing** (F_t):
The player's look angle in degrees, the value shown in F3. It is the yaw wrapped to the range [-180, 180], and depends on rotation only. In the movement formulas it is `F_t`, and it sets the direction of the sprint-jump boost.

**Yaw**:
The raw float backing facing: horizontal rotation in degrees, unbounded. It keeps growing the longer you turn one way, losing float precision (jittery past 2^22, movement breaks at 360 * 2^15, the game crashes past ~8.59e9). Wrapped to [-180, 180] it becomes the facing.
_Avoid_: rotationYaw (the code field name)

**Pitch**:
The vertical look angle. Does not affect horizontal parkour movement at all (it only matters for elytra and swimming, both out of scope), but it is still a per-tick input the tool records.

**Direction** (D_t):
The horizontal direction the player actually moves, `atan2(-vx, vz)`, determined by inputs and rotation together. In the movement formulas it is `D_t`, and it sets the direction of ground and air acceleration. Distinct from facing: facing is where you look (rotation only), direction is where you go (inputs plus rotation). The two coincide only when moving straight forward.
_Avoid_: xz angle (internal name for the same quantity), heading

**Significant angle**:
The integer index, 0 to 65535, that Minecraft actually uses for trigonometry. `sin`/`cos` are a 65536-entry lookup table indexed by `(int)(radians * 10430.378) & 65535`, so any facing is effectively snapped to one of 65536 significant angles (~0.0055 degrees each) before the movement math runs. This snapping is why a tiny facing change can produce no movement change, and is the reason the angle solver's objective is a step function.

**Half angle**:
A yaw that lands between two significant angles, where floating-point error makes the sin/cos pair have a norm slightly off from 1: an "increasing" half angle (norm > 1) gains a little speed, a "decreasing" one (norm < 1) loses some. The gains are tiny (135.0055 degrees gives ~1.00005) and only practical in TAS; "large half angles" at extreme yaws are far stronger (up to ~1.003 in vanilla, more with Optifine Fast Math). Detail in `docs/reference/mcpk/`.

**Coordinates** (coords):
The player's position, readable via the F3 screen. Usually refers to the set of coordinates used to set up for a jump.

**X facing** (x) / **Z facing** (z):
The axis an obstacle is oriented along. An X-facing obstacle faces East/West and is harder to avoid; a Z-facing obstacle faces North/South and is easier. Visible via F3. (Distinct from the player's facing above.)

**Block** (b):
The standard unit of distance in Minecraft (visual block count). 1 block = 16 pixels.

**Pixel** (px):
1/16 of a block, 0.0625 blocks. A sub-block unit of distance and position.

**Meter** (m):
A distance unit that accounts for the player's 0.6-wide bounding box, so it reflects the physical gap rather than the visual block count. A "4 block" jump is ~3.4 meters of actual air travel.

## Velocity and speed

The project's own precise taxonomy. The one true quantity is velocity; speed and direction are its polar components.

**Velocity**:
The player's stored per-tick movement vector `(vx, vy, vz)`: the movement the player intends to apply next tick. Named instances appear throughout, e.g. entry velocity (the velocity a jump starts with) and rest velocity (standing still on the ground).
_Avoid_: motion (a Minecraft-ism; being renamed to velocity everywhere)

**Speed**:
The magnitude of velocity, `||(vx, vy, vz)||`. A scalar.

**XZ velocity**:
The horizontal projection of velocity, `(vx, vz)`. The only velocity component that matters for parkour.

**XZ speed**:
The magnitude of the XZ velocity, `||(vx, vz)||`. With direction it gives the polar identity `(vx, vz) = xz_speed at angle direction`: the same vector in polar form.

**Displacement**:
The realized movement over one tick, `pos - lastPos`. Equals velocity when nothing is in the way, but diverges the moment the player collides: velocity is what the player intended, displacement is what actually happened. Shown in the MPK movement readout.
_Avoid_: speed (speed is the magnitude of velocity, before collision; displacement is after)

## Momentum

**Momentum** (mm):
1) The speed gained and conserved by moving. 2) The run-up space given to build enough speed for a jump.

**Block momentum** (#bm):
The distance given to gain momentum. 1bm is one block (1.6 m of momentum); 2bm is two consecutive blocks (2.6 m).

**Flat momentum** (flat mm):
The standard momentum setup, where the run-up space is flat ground (12 tick cycle).

**Elevation momentum**:
A more efficient setup where the run-up is elevated at each step: +0.125 is the lowest (11 tick cycle), +1 the default (9 tick cycle), +1.1875 the maximum (7 tick cycle).

**Headhitter momentum** (hh mm):
A very efficient momentum setup using a 2-block ceiling (3 tick cycle).

**Trapdoor-headhitter momentum** (tdhh mm):
The most efficient momentum setup (2 tick cycle).

**Backwalled**:
Describes a momentum that has a wall at its back, which reduces the run-up space.

**Sidestep**:
Jumping sideways while gaining momentum. Utilises 45 strafe and makes turning more consistent; common for 1bm butterfly neos.

**Backward momentum** (bwmm):
Moving backward to increase the run-up space before jumping. Useful on a short momentum, necessary for some jumps.

**Loop momentum** (loop mm):
Repeated backward momentum, alternating back and forth across the momentum. One backward momentum already gains distance because the player can stay airborne for a tick if the prior tick was still on the ground, and the more velocity they carry the further they get from the edge, which in turn gives more room to accelerate. Doing this at the next edge and jumping the other way extends it a little more, and looping accumulates slightly more momentum each pass.

## Jumps: distance, ceilings, names

**Distance**:
The size of a jump, given in blocks (visual) or meters (accurate, accounting for the 0.6 m player width).

**Length** (#b) / **Width** (x#) / **Height** (+#):
A jump's dimensions. Length is the longest horizontal side, width the shortest horizontal side, height the vertical change. Max jump-up height is 1.249 blocks in 1.8, 1.252 in 1.9+.

**Jump format** (#x#+#):
The conventional notation for a simple jump, `length x width + height`. Width and height are dropped when zero; a bare length gets a "b". Examples: `3b`, `4x1`, `5-1`, `4+0.5`, `3x3+0.4375`.

**Duration**:
The number of ticks between jumping and landing. Depends on height: a flat jump is 12 ticks, a +1 jump is 9 ticks, a 2-block-ceiling jump is 3 ticks.

**Tier**:
An intuitive label for jump duration. Tier 0 is a flat jump by convention; positive tiers are jumps with positive height, negative tiers with negative height. (Reference: a Tier 0 sprint-jump maxes at ~4.3227043 m.)

**Block ceiling** (#bc):
The height of a ceiling, in blocks. 2bc and 3bc still affect movement; 4bc is the same as no ceiling. The player is 1.8 m tall.

**Headhitter** (hh):
A synonym for 2bc (a 2-block ceiling).

**Trapdoor headhitter** (tdhh):
A 2bc lowered further with a trapdoor (1.8125 bc), leaving 0.0125 b of room: the lowest ceiling a player can walk through in 1.8.

**Linear jump**:
A jump with no obstacles, completable without turning (apart from using 45 strafe).

**Neo** (#b neo):
A jump that goes around a wall; the number is the wall length (a 2b neo is a "double neo"). Variants: winged neo (wall extended outwards), nix neo (wall and landing extended), reverse nix neo (wall and momentum extended), butterfly neo (panes on its side).

**Cross neo**:
A jump that goes around a corner.

**Squeeze jump**:
A jump through a small gap.

## Mechanics, glitches, and geometry

**Stepping** (step-assist):
Minecraft's auto-step assist: the player rises over obstacles up to 0.6 blocks tall without jumping.

**Blip**:
A glitch from landing between two blocks of different height (caused by stepping). The player "lands mid-air" and can jump with initial height.

**Jump cancel**:
A glitch from jumping into a ceiling or step (caused by stepping). Cancels the player's upward momentum, letting them jump again sooner.

**Grinding**:
Chaining multiple jump cancels to gain momentum. Stair grinding (called slab boost) is the easier form; ceiling grinding is much harder.

**Bounding box**:
An axis-aligned cuboid given by min/max on each axis. The player's is 0.6 x 1.8 x 0.6.

**Collision box**:
The volume of a block the player physically collides with (one or more bounding boxes). The player's bounding box is not allowed to intersect a collision box.

## Map terminology

**Checkpoint** (cp):
A position the player can teleport back to.

**Failsafe** (fs):
A loose checkpoint that only allows partial recovery.

**Life or death** (l/d):
A section that is not failsafed; failing it loses some progress.

**Room** (r#) / **Transition** (t#-#):
A room is a subsection of a course; a transition connects two rooms and is sometimes life or death.

## Tool concepts

Specific to this tool, not on the wiki.

**Byte-exact**:
A prediction that reproduces Minecraft's movement bit-for-bit, with no approximation. Required to certify jumps whose success margin is 1e-4 of a block or tighter. Both the simulator and the solver's inner model are held byte-exact against real Minecraft.
_Avoid_: bit-exact (same thing; prefer byte-exact)

**Margin**:
How much room to spare a jump succeeds by. The tool's goal is to certify routes down to a 1e-4 block margin or tighter, the regime where human stratfinding breaks down.

**Run-up**:
The movement before takeoff that builds the entry velocity a jump needs. The tool can find the run-up that produces a required velocity.

**Takeoff**:
The tick and the spot on the start block where the player leaves the ground to begin a jump.

**Entry velocity**:
The velocity a jump begins with, at takeoff. What the velocity finder solves for.

**Rest velocity**:
The velocity of a player standing at rest on the ground: horizontally zero, with a small downward `vy` so the player still registers as on the ground. Used as the default start velocity; a saved value of exactly `(0,0,0)` is treated as a legacy unset sentinel and replaced with this.

**Constraint**:
A positional requirement the solved path must satisfy at a given tick: land within these X/Z bounds, stay clear of this collision box, pass through this point. Constraints can be hand-entered or derived from picked blocks. A constraint on tick n affects the position at the start of tick n; to constrain what tick n's input produces, place it on tick n+1.

**Mothball-string**:
The save/load notation, shared with the Mothball and Stratfinder community tooling, that lets setups roundtrip between this tool and those.
