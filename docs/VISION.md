# Vision

Parkour Calculator Mod is a Tool Assisted Speedrun (TAS) tool for Minecraft parkour and movement. It serves two audiences: TAS creators who craft frame-perfect routes, and parkour stratfinders who need to know whether a jump is possible and exactly how to land it. The overarching goal is simple to state: be the most UX-friendly TASing tool for parkour and movement in Minecraft.


## The north star

Define a start block and a goal block. The tool figures out everything in between:

- the **constraints and collisions** the route must respect, derived from the blocks rather than hand-entered,
- the **path** from start to goal,
- the exact **inputs** the player presses on every tick, and
- **where on the start block to begin**: the run-up and the spot you take off from.

This must hold even for jumps whose success margin is 1e-4 of a block or tighter. Jumps that precise are exactly where human stratfinding breaks down, and where this tool earns its keep. Two blocks in, a complete and certified TAS out.


## How we get there

The arc runs from manual planning to full autonomy. Every level stands on one foundation: a byte-exact replica of Minecraft's movement, so each prediction is bit-for-bit what the game will do.

**The calculator (foundation).** You enter tick-by-tick inputs (movement keys, jump, sprint, sneak, yaw, pitch) in an in-game overlay; the tool simulates the exact resulting path and draws it in the world. Drag the start, edit a tick, watch the trajectory update live. This is the reliable, ergonomic manual workflow everything else builds on.

**Assisted solving (emerging).** Instead of hand-searching for inputs, you state intent and the tool solves the rest:
- the **angle solver** turns positional constraints into the yaws that satisfy them, certified byte-exact;
- the **velocity finder** answers what entry velocity lands a jump, and what run-up produces that velocity;
- **block-derived constraints** turn picked blocks into the collision and landing constraints a solve needs;
- **byte-exact playback** replays a solved sequence in-game to confirm it matches the simulation.

**Full autonomy (the north star).** Unify the above behind the two-block workflow: derive the constraints, find the path, solve the inputs, and locate the takeoff spot, for single jumps and for multi-jump routes across real loaded terrain, all at 1e-4 margins or tighter.


## Predecessor

This mod is the successor to the standalone desktop [ParkourCalculator](https://github.com/Leg0shii/ParkourCalculator) (JavaFX). The desktop app proved the concept across several Minecraft versions and even did AI pathfinding, but required rebuilding worlds in an external 3D editor. The mod removes that friction by operating directly on the loaded Minecraft world: no alt-tabbing, no reconstructed geometry.

It ships for Fabric 26.2, Forge 1.12.2, and Forge 1.8.9 in parallel. The Fabric loader tracks the newest Minecraft version; the two Forge loaders exist because the speedrun community is still anchored on those eras.


## Design principles

- **Simulation accuracy is non-negotiable.** Movement is computed by a real `PlayerEntity` subclass (or its 1.8.9 / 1.12.2 equivalent), byte-exact against Minecraft. Any divergence from the game's physics is a bug, never an optimization. The 1e-4 goal is only reachable because the model is bit-exact; approximate physics cannot certify a frame-perfect jump.
- **UX first.** The ideal interaction is two blocks in, a route out. Everything short of that ideal should still be fast, legible, and entirely in-game.
- **Client-side only.** The mod reads world state but never modifies it and never touches the server.
- **Minimal dependencies.** Fabric API on Fabric, nothing extra on Forge, imgui-java for the overlay. No heavyweight frameworks.
- **Community interop.** Save/load uses mothball-string notation so setups roundtrip with Stratfinder and other Mothball tooling.
