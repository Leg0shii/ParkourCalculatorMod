"""Generate `parkour_solver/physics/_coefficient_tables.py` from the real Java bridge.

Run from `standalone/` with an explicit compiler descriptor (see the runbook).
`--check` regenerates in memory and fails if the checked-in module differs.
"""
import argparse
import hashlib
import itertools
import json
from pathlib import Path
import struct
import sys

from parkour_solver.bridge.compiler import JavaCompiler
from parkour_solver.physics.vertical import f32
from tools.paths import REPOSITORY_ROOT, STANDALONE_ROOT

OUTPUT = STANDALONE_ROOT / 'src/parkour_solver/physics/_coefficient_tables.py'
LEGACY_FORWARD = f32(0.98)
LEGACY_FORWARD_TOLERANCE = 1e-7
# Java sources that decide generated rows: the bridge and its coefficient call graph.
SOURCES = (
    'standalone/native/java/autonomy/CompileRunTicksPhysics.java',
    'forge-core/src/main/java/de/legoshi/parkourcalc/forge/core/replay/FixedMovementCompiler.java',
    'forge-core/src/main/java/de/legoshi/parkourcalc/forge/core/replay/ReplayInputState.java',
    'forge-core/src/main/java/de/legoshi/parkourcalc/forge/core/sim/PlayerSprintMachine.java',
    'core/src/main/java/de/legoshi/parkourcalc/core/replay/ReplayRows.java',
    'core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/ExactJumpModel.java',
    'core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/JumpLinearModel.java',
    'core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/JumpPhysicsInputs.java',
    'core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/Constants.java',
    'core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/McSineTable.java',
    'core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/Slipperiness.java',
    'core/src/main/java/de/legoshi/parkourcalc/core/sim/StartResumeState.java',
    'core/src/main/java/de/legoshi/parkourcalc/core/ui/InputRow.java',
    'core/src/main/java/de/legoshi/parkourcalc/core/ui/MovementEffect.java',
    'core/src/main/java/de/legoshi/parkourcalc/core/save/SaveIO.java',
    'core/src/main/java/de/legoshi/parkourcalc/core/save/SaveFile.java',
)


def shared_probe(force45, ground, jump, sneak, previous_sprint, previous_wall, fires):
    """One request whose last input has the given state (from the research generator)."""
    # A grounded held jump that does not fire needs a preceding grounded jump for the
    # cooldown. That input's sneak sets the carried sprint; a wall contact sets the hit.
    blocked = ground and jump and not fires
    route = dict(sharedMovement=True, inputs=dict(sprint=True, force45=force45, incomingSprint=previous_sprint))
    plan = dict(grounded=[True, ground] if blocked else [ground], jump=[True, jump] if blocked else [jump],
                sneak=[not previous_sprint, sneak] if blocked else [sneak],
                sceneContacts=[{'axis': 0, 'state': 1}] if blocked and previous_wall else [],
                vertical={'profile': 'minecraft-1.8.9', 'onGround': True if blocked else ground,
                          'previousHorizontalCollision': previous_wall and not blocked})
    return route, plan


def shared_rows(compiler):
    rows, labels = {}, None
    for force45, ground, jump, sneak, previous_sprint, previous_wall, fires in itertools.product((False, True), repeat=7):
        if fires and not (ground and jump):
            continue
        route, plan = shared_probe(force45, ground, jump, sneak, previous_sprint, previous_wall, fires)
        result = compiler.compile(route, plan, 30)
        movement, vertical = result['movement'], result['vertical']
        last = len(plan['jump']) - 1
        if (movement['airSprinting'][last] != previous_sprint or movement['horizontalCollision'][last] != previous_wall
                or (vertical['jumpVelocity'][last] is not None) != fires):
            raise RuntimeError('Probe did not reach its intended input state')
        key = (ground, force45 and not (ground and jump), sneak, movement['sprinting'][last], previous_sprint, fires)
        row = (result['magnitude'][last], result['friction'][last], result['base'][last],
               movement['moveForward'][last], movement['moveStrafe'][last], vertical['jumpVelocity'][last])
        if key in rows and literal(rows[key]) != literal(row):
            raise RuntimeError(f'Shared row {key} differs between probes')
        rows[key] = row
        label = dict(model=result['model'], profile=movement['profile'], compiler=movement['compiler'])
        if labels not in (None, label):
            raise RuntimeError('Shared probes disagree on model labels')
        labels = label
    return labels, rows


def legacy_forwards():
    """Every float32 the bridge can receive for an accepted legacy forward input."""
    def bits(value):
        return struct.unpack('<I', struct.pack('<f', value))[0]
    low = bits(LEGACY_FORWARD - LEGACY_FORWARD_TOLERANCE)
    high = bits(LEGACY_FORWARD + LEGACY_FORWARD_TOLERANCE)
    return list(range(low, high + 1))


def legacy_rows(compiler):
    rows, model = {}, None
    for bits in legacy_forwards():
        forward = struct.unpack('<f', struct.pack('<I', bits))[0]
        for ground, jump, sprint, previous_sprint, force45 in itertools.product((False, True), repeat=5):
            route = dict(inputs=dict(forward=forward, strafe=0., sprint=sprint, incomingSprint=previous_sprint,
                                     force45=force45))
            result = compiler.compile(route, dict(grounded=[ground], jump=[jump]), 30)
            rows[bits, ground, jump, sprint, previous_sprint, force45] = (
                result['magnitude'][0], result['friction'][0], result['base'][0])
            if model not in (None, result['model']):
                raise RuntimeError('Legacy probes disagree on the model label')
            model = result['model']
    return model, rows


def literal(value):
    if isinstance(value, float):
        if value != value or value in (float('inf'), float('-inf')):
            raise ValueError('Generated coefficients must be finite')
        return repr(value)
    if isinstance(value, tuple):
        return '(' + ', '.join(map(literal, value)) + (',)' if len(value) == 1 else ')')
    if value is None or isinstance(value, (bool, int, str)):
        return repr(value)
    raise TypeError(f'Unsupported generated value {value!r}')


def render(compiler):
    labels, shared = shared_rows(compiler)
    model, legacy = legacy_rows(compiler)
    sources = {path: hashlib.sha256((REPOSITORY_ROOT / path).read_bytes()).hexdigest() for path in SOURCES}
    lines = ['"""Generated by tools/generate_coefficient_tables.py from the Java bridge; do not edit."""', '',
             '# Java sources that define these rows, relative to the repository root.', 'SOURCES = {']
    lines += [f'    {path!r}: {digest!r},' for path, digest in sources.items()]
    lines += ['}', '', 'SHARED_LABELS = {']
    lines += [f'    {key!r}: {labels[key]!r},' for key in sorted(labels)]
    lines += ['}', '# (grounded, authoredDiagonal, sneak, sprinting, previousSprint, jumpFires):',
              '#     (magnitude, friction, base, moveForward, moveStrafe, jumpVelocity)', 'SHARED_ROWS = {']
    lines += [f'    {literal(key)}: {literal(shared[key])},' for key in sorted(shared)]
    lines += ['}', '', f'LEGACY_MODEL = {model!r}', f'LEGACY_FORWARD = {literal(LEGACY_FORWARD)}',
              f'LEGACY_FORWARD_TOLERANCE = {literal(LEGACY_FORWARD_TOLERANCE)}',
              '# (forward float32 bits, grounded, jump, sprint, previousSprint, force45): (magnitude, friction, base)',
              'LEGACY_ROWS = {']
    lines += [f'    ({key[0]:#010x}, {", ".join(map(literal, key[1:]))}): {literal(legacy[key])},' for key in sorted(legacy)]
    lines += ['}', '']
    return '\n'.join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--compiler', type=Path, required=True, help='Java compiler descriptor (compiler.json)')
    parser.add_argument('--output', type=Path, default=OUTPUT)
    parser.add_argument('--check', action='store_true', help='fail if the output differs from a fresh generation')
    args = parser.parse_args()
    with JavaCompiler(json.loads(args.compiler.read_text())) as compiler:
        text = render(compiler)
    if args.check:
        if not args.output.exists() or args.output.read_text() != text:
            print(f'{args.output} is stale; regenerate it with tools/generate_coefficient_tables.py', file=sys.stderr)
            return 1
        print(f'{args.output} matches the Java bridge')
        return 0
    args.output.write_text(text)
    print(f'Wrote {args.output}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
