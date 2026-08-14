# Design record: paired client-server simulation (integrated server rework)

Written 2026-08-14. Status: accepted design, pre-implementation. Origin: the slime damage boost investigation (branch feature/allow-damage), the lagback problem, and the block interaction ticket (mid-run trapdoor flips, today done by hand as flip-then-resimulate-from-checkpoint). Decision driver: the simulation entity must behave like the real player, server included, so TASers can stumble on tech they did not know existed because the entity behaves "strangely" while building a TAS. Nothing in this design requires a glitch to be understood before it reproduces.

Verification status: the vanilla mechanics claims in section 0 agree with the in-repo mcpk mirror where it covers them (docs/reference/mcpk/06-timings-momentum-glitches.md quotes the BlockSlime collision behavior) but were NOT source-verified against decompiled MC in the environment this record was written in (the MC mavens were unreachable there, so genSources could not run). Every claim marked [verify] must be re-checked against the target loader's genSources output before the corresponding code is written. If a marked claim turns out wrong, the architecture survives, reusing real code paths is the point; only the test expectations in section 11 would move.

## 0. Problem: the trick class is unrepresentable in a single-entity sim

In vanilla, one block sample decides both halves of a landing. After collision resolution, Entity movement samples the single block roughly 0.2m below the player's center [verify: exact offset and fence special case per version], and that one block drives both the fall damage decision (onFallenUpon, which slime negates unless sneaking) and the bounce (onLanded, motionY inversion). One entity in one world therefore gets damage XOR bounce, never both. No amount of letting damage through to SimulatorEntity (the feature/allow-damage approach) can express "took fall damage AND bounced".

The real trick lives in the client-server split. The client decides its landing mid-tick at the collision position. The server does independent fall bookkeeping in its move-packet handler, fed by the client's end-of-tick reported positions and onGround flags, sampling below the reported center [verify: handler fall path per version]. Edge landings can make the two samples straddle the slime edge: the client bounces, the server rules fall damage, damage marks the server entity velocityChanged, and the server flushes a velocity packet whose payload is the server entity's motion at that moment [verify: flush site and payload per version]. The client overwrites its motion with that payload when the packet queue drains at a tick boundary [verify]. The observed "very high bounce" is the interaction of the preserved client bounce and that packet; this record deliberately does not claim which part dominates, because the design reproduces it without needing to know.

The same handler contains the vanilla movement checks (moved too quickly, moved wrongly) whose correction teleport is the singleplayer flavor of the lagback. And a mid-run trapdoor flip is a server-side world mutation triggered by a click the input rows already record. All three features are the same shape: server-originating effects landing on the client at tick boundaries.

## 1. Decision

Build a lockstep client-server pair under the simulator's control, singleplayer only. The current client-style SimulatorEntity keeps doing client physics; a persistent fake server player in the WorldServer, wired to a real server-side network handler over a local channel, does the server's half; the sim loop carries synthesized packets between them at fixed, deterministic boundaries. The simulation remains the single source of truth; it now computes the truth of the pair, not of one entity.

Rejected alternatives, with dispositions:

1. Declared per-tick event track (velocity set, position set, block set as user-declared inputs). Rejected as the primary mechanism: declared events can only express tech somebody already understands, which is backwards from the emergence goal. The idea survives inverted, as the diagnostic event log the paired sim emits (section 10).
2. Shadow prediction (reimplementing the server's fall bookkeeping as a core pure function per version). Rejected: per-version physics reimplementation that drifts from MC with every update. The pair reuses each version's real server code instead.
3. Multiplayer support for desync tech. Rejected by decision: MP timing is not deterministic, and the project only wants these glitches supported in singleplayer.
4. Isolating the sim in a separate world. Rejected: the entity mutating the real world is desired, the player watching the run should see the trapdoor flip and every other consequence of the current TAS.
5. RNG pinning at checkpoints. Withdrawn: see the invariant in section 3; no supported mechanic reads RNG into an outcome, so there is nothing to pin.

## 2. Architecture: the lockstep pair

Components, per loader:

- Client entity: the existing SimulatorEntity, unchanged physics role.
- Server entity: a persistent fake server player (Fabric API FakePlayer on the fabric loaders, FakePlayerFactory-style construction on the forge loaders) in the same WorldServer the sim already binds to in singleplayer.
- Handler: a real server-side network handler (ServerGamePacketListenerImpl / NetHandlerPlayServer) for the fake player over a local in-memory channel, so processPlayer-era logic runs unmodified.
- Wire: two ordered queues owned by the sim loop, clientbound and serverbound, drained at fixed points below. No netty threads are involved during simulation.

Per-tick sequence for tick N:

1. Apply pending clientbound effects queued at the end of tick N-1 to the client entity (velocity sets, position corrections, world-visible block results are already in the world). This models packet-queue drain before the client tick [verify: client apply boundary per version].
2. If a position correction was applied and the version uses teleport IDs, synthesize the accept packet and the follow-up move packet the real client sends, before any other serverbound traffic for this tick [verify: modern ack flow; without this the handler freezes movement processing and the pair deadlocks after the first rubber-band].
3. Set inputs and tick the client entity (existing path: setInput, applyTickEffects, applyYaw, tickEntity).
4. Synthesize the serverbound traffic the real client would emit for this tick: the move packet with end-of-tick position, rotation, and onGround; the per-tick input-state packet on versions that send it (1.21.2+) [verify]; interaction packets on click ticks (use-item-on for a recorded right click), routed through the real interaction manager so the server performs the block activation itself.
5. Drain the serverbound queue through the handler synchronously, then run the server entity's per-tick update to the extent the handler does not already (damage bookkeeping, velocityChanged flush) [verify: which parts of the server player tick are handler-driven vs entity-tick-driven per version].
6. Collect everything the handler and server entity emitted (velocity packet, position correction, block changes, damage events) into the clientbound queue for step 1 of tick N+1, and append each to the run's event log.

The canonical interleave is thus: the server processes tick N's packets immediately after tick N; its effects land on the client at the start of tick N+1. Real singleplayer runs client and server on separate threads where this round trip is the common case but can jitter under load; the pair fixes the common case as the deterministic canonical timing. Playback against the real integrated server remains the place where real-timing behavior is observed (section 9).

## 3. Determinism

One loop drives both sides; there are no thread races and no wall-clock dependence inside a sim pass. The interleave is a fixed rule, not a measurement.

RNG invariant, replacing the withdrawn pinning requirement: the pair is deterministic because no supported mechanic reads RNG into an outcome. Movement integration is RNG-free. No-attacker damage consumes a random only for the hurt animation direction. The knockback gate draws a random and compares it against knockback resistance, which is 0.0 for players, so the branch is unconditionally taken and the draw is discarded. Sound and particle randoms sit in paths the sim entities already no-op. Consumption-only draws cannot desync anything; stream position is irrelevant when nothing reads it into an outcome. The invariant must be re-checked when a new mechanic enters scope (arrow spread is the classic counterexample if bow use is ever supported); until then, checkpoints capture no RNG state.

## 4. Checkpoints and the world journal

A checkpoint grows from one part to three:

1. Client entity scalars: the existing Checkpoint fields, unchanged.
2. Server-side scalars: position, motion, fallDistance, onGround, damage bookkeeping (hurtResistantTime and hurtTime gate repeat damage), health (pinned, see section 9), plus the handler's own fields: pending teleport state and ID, last-good position, floating-tick counters on versions that kick for flying [verify: enumerate per version]. Order of thirty scalars, same save/restore shape as today. Restore follows the existing pattern: reset both sides to the clean baseline, then overlay the captured scalars.
3. The world journal: every block mutation the sim causes, recorded as (tick, pos, stateBefore, stateAfter). Restore-to-tick-N means revert all journaled mutations and replay the journal prefix up to N. The journal is what reconciles observability with resimulation: the world permanently shows the net effect of the most recent pass, and any resim from any checkpoint first rewinds it.

Flicker-free application, so per-keystroke resims do not strobe the world for the player: during a pass, mutations are applied to the WorldServer with update-suppressed, no-notify flags (silent, invisible to the real client, but live for both sim entities, which read the same world). At pass end, the loop diffs the pass's net journal against the previous pass's net journal and sends client notifications only for positions whose final state changed. The player sees the trapdoor open exactly when the TAS's net outcome changes, not on every intermediate resim. Mutations and the end-of-pass notify run inside a short window synchronized with the server thread; the sim already reads the WorldServer cross-thread, but writes get the synchronization reads never had.

The checkpoint remains expressible for one structural reason worth stating: the pair has no hidden inputs. Every state change flows through the loop (inputs, synthesized packets, journaled mutations), so the state that must be captured is enumerable, and the resume-equals-full-run property stays testable (section 11).

## 5. World interaction scope

v1 supports instant-state interactions: trapdoors, doors, fence gates, levers-as-state (any activation whose entire effect is a block state swap at click time). This covers the block interaction ticket completely: a recorded right click on tick N flips the block inside the pass, the path after N sees the new collision shape, and the manual flip-and-resimulate workflow disappears.

Explicitly deferred: scheduled-tick blocks (buttons popping back, repeaters, any redstone cascade). Their timers live in the world's scheduled-tick queue, which advances with the real server clock, not sim ticks, and cannot be stepped in sim time inside the shared world without affecting reality. Supporting them faithfully needs virtualized scheduled ticks and is out of scope until a concrete need exists.

Interaction fidelity: clicks route through the real interaction manager, so reach and face validation are whatever the server actually enforces. The sim does not second-guess whether a click "should" work; if the server rejects it, the rejection is the result, and it appears in the event log.

## 6. Performance

Budget: the addition per tick is the handler's move processing, whose dominant cost is one extra collision pass (the handler replays the reported move for its checks [verify]), plus interaction handling on click ticks only. Roughly 2x today's collision work per tick; current full-run resims cost low single-digit milliseconds, so the pair stays well inside per-frame budget for the resimulate-on-every-edit workflow. Chunk paging is unchanged, both entities read the already-preloaded WorldServer.

The real performance risks are structural, and they are rules, not options:

1. The pair is persistent across resims. Never spawn or despawn the fake server player per pass; construct once, reposition via checkpoint restore, exactly as the client entity works today.
2. The fake player must never register with the entity tracker or the player list, or the integrated server streams packets about it to the real client every pass.
3. No collisions, no AI targeting, no stat or advancement triggers, no sleep counting: the server-side mirror of the isolation SimulatorEntity and GhostPlayerEntity already implement client-side.

## 7. Rollout order and per-version notes

Order: loader-fabric (26.2) first, by decision; then the forge pair with its shape shared through forge-core (1.8.9 before 1.12.2, since 1.8.9 is where the community's desync folklore lives and gives the richest comparison material); loader-fabric-1.21.3 last, picking up the modern wiring with minor packet deltas.

26.2 notes: Fabric API ships a FakePlayer, Loom gives mapped sources for [verify] passes, and mixins cover the local-channel wiring. Two modern-specific requirements: the teleport-ack handshake (section 2 step 2) and the per-tick input-state packets since 1.21.2. One honest expectation to set: emergent behavior on 26.2 is modern behavior. Modern fall bookkeeping, movement validation, and slime handling differ from 1.8.9, so the pair will surface whatever desync tech exists on 26.2, which is not necessarily the 1.8.9 folklore from the wiki and community spreadsheets. "Does not reproduce the 1.8.9 slime boost on 26.2" is an expected outcome, not a bug.

Maintenance note: the pair touches more MC surface than the movement sim (handler internals, packet shapes), and loader-fabric tracks latest MC. The thin-loader rule below is what keeps each MC bump cheap.

## 8. Module placement and ports

Core stays Minecraft-free. Core gains: the event log data model (section 10), the extended checkpoint carrier shape (opaque server-side payload alongside the existing one), and whatever LazyEntitySimulator needs to sequence steps 1-6 abstractly. Loaders implement the pair behind the existing port pattern: entity construction, handler wiring, packet synthesis, journal application. The journal's block states are MC types and stay loader-side; core sees only counts and positions for UI. The paired path ships behind a settings toggle (default off) until the section 11 parity gate holds on a loader, then becomes that loader's default simulator.

## 9. Interplay

Solver: ExactJumpModel remains a byte-exact X/Z replica of client movement and knows nothing of server events. Solver work treats server-event ticks as segment boundaries, taking resume states from the paired sim, consistent with existing segmentation. Solving across an event (choosing inputs so that a damage boost lands) is future work and out of scope here.

Playback: unchanged in role, strengthened in meaning. Playback drives the real client and real integrated server, so it is the ground-truth check on the canonical interleave: where playback diverges from the pair, either a [verify] claim was wrong (fix the pair) or real thread jitter picked a non-canonical timing (expected, rare, and worth logging). The feature/allow-damage branch's playback-side pieces remain relevant: the real player's damage gating during playback, and health pinning. The branch's single-entity sim approach (letting SimulatorEntity take damage) is superseded by this design and should not merge as a sim feature.

Health pinning: the fake server player takes damage for real (that is the mechanism under study) but must never die or accumulate hunger effects; the setHealth-clamps-to-max override from feature/allow-damage carries over to the server entity.

## 10. Diagnostic event log

The pair emits a per-run log of server-originating events: (tick, kind, payload), where kind is one of velocity-set, position-correction, damage-ruled, block-changed, interaction-rejected. Surfaced in the UI alongside the tick list, this is how a TASer goes from "the entity behaved strangely at tick 14" to understanding the tech they just stumbled into: the log says the server ruled fall damage on tick 14 and set velocity on tick 15, without anyone having predicted it. The log is output only; nothing in the sim consumes it. It doubles later as the robustness tool (re-run with a shifted interleave and diff the logs), but nothing in v1 depends on that.

## 11. Verification gates

1. Baseline parity (the hard gate): on runs where the event log is empty, the paired sim's client trajectory is byte-identical to the current single-entity sim, on every loader, before the toggle defaults on. The pair must not perturb the no-event baseline.
2. Liveness: a run containing a position correction continues past it (proves the teleport-ack synthesis); a run containing damage continues with pinned health.
3. Slime edge case: an edge landing constructed so client and server samples straddle the slime edge produces bounce plus damage plus a velocity-set event in the log, and the resulting trajectory matches an instrumented singleplayer playback of the same inputs under canonical timing.
4. Trapdoor case: a recorded right click flips the trapdoor mid-pass, the post-flip path uses the new collision shape, the world shows the flip after the pass, and a later resim with the click removed restores the world (journal revert).
5. Checkpoint equivalence: resuming from a checkpoint taken after any event (damage, correction, block change) reproduces the full run's suffix byte-exactly, extending the existing resume test pattern across all three checkpoint parts.
6. Performance: full-run resim wall time under the paired sim stays within 3x the single-entity sim on the same run (budget says ~2x; the gate leaves headroom, and a breach means a structural rule in section 6 broke).

## 12. Open questions

1. Local-channel wiring per version: constructing a handler without a real connection differs per loader family; the fabric FakePlayer helps, the forge loaders need the FG-era equivalent spelled out during [verify].
2. Sound and particle suppression for server-side interactions: routing clicks through the real interaction manager plays activation sounds to nearby players; per-resim replay would spam them. Proposed: suppress during passes, emit once at end-of-pass when the net journal changes, mirroring the block-notify diffing.
3. Ghost-block tech: some desync glitches require the client and server worlds to disagree about block state. Both pair entities read the same WorldServer in v1, so that class is out of scope; supporting it would need a diverged client world view and is a separate design.
4. Scheduled-tick virtualization (section 5 deferral): whether a future version can step a private scheduled-tick queue for sim-owned mutations without touching the world's real queue.
5. Whether the client entity should tick against the WorldClient instead of the WorldServer for maximal client fidelity: today's WorldServer binding exists for reliable chunk paging and is kept as an accepted approximation; revisit only if a [verify] pass shows a client-world-dependent behavior that matters.
