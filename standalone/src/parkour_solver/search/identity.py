import copy
import hashlib
import json

from parkour_solver.movement.program import program_physics_key
from parkour_solver.search.comparison import comparison_label, priority_key


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, allow_nan=False).encode()).hexdigest()


def motion_record(program):
    p = program.get('profile', {})
    c = comparison_label(program)
    if c['J'] is None:
        return None
    phase = {k:p[k] for k in ('position','velocity','grounded','contacts','caseEvents') if k in p}
    return dict(ticks=c['T'], jumps=c['J'], runs=c['R'], schedule=c['schedule'], phase=digest(phase))


def processing_key(program, identity=''):
    return priority_key(comparison_label(program, identity=identity))


def solve_scope(scene, program, supplied=None):
    value = copy.deepcopy(supplied if supplied is not None else program.get('solveScope', {}))
    if set(value) - {'purpose', 'modeledBlocks', 'through'}:
        raise ValueError('Unknown solve scope field')
    purpose = value.get('purpose', 'complete')
    if purpose not in ('preliminary', 'complete'):
        raise ValueError('Solve purpose must be preliminary or complete')
    n = program['profile']['airtime']
    through = value.get('through', n)
    if type(through) is not int or not 0 < through <= n:
        raise ValueError('Solve scope must cover a nonempty prefix of the program')
    names = {b['id'] for b in scene['blocks']} | {b['sourceBlock'] for b in scene['blocks']}
    requested = value.get('modeledBlocks', sorted(names))
    if not isinstance(requested, list) or any(name not in names for name in requested):
        raise ValueError('Unknown modeled block')
    solids = [b['solidIndex'] for b in scene['blocks'] if b['id'] in requested or b['sourceBlock'] in requested]
    if purpose == 'complete' and (through != n or len(solids) != len(scene['collisionScene']['solids'])):
        raise ValueError('Complete solves must model the full declared scene and horizon')
    active = {tuple(scene['collisionScene']['solids'][i]) for i in solids}
    if any(e['offset'] < through and any(tuple(b) not in active for b in e['boxes'])
           for e in program['profile'].get('contactEvents', [])):
        raise ValueError('Selected contacts must belong to the modeled scene')
    return dict(version=1, purpose=purpose, through=through, solidIndices=solids,
                modeledBlocks=sorted(set(requested)))


def solve_identity(scene, program, objective, scope, route=None, coefficients=None):
    from parkour_solver.scene.route import HALF_WIDTH
    from parkour_solver.scene.geometry import BODY_HEIGHT
    return digest(dict(version=1, initial=scene['initial'], geometry=scene['collisionScene'],
        inputs=scene['inputs'], program=program_physics_key(program), objective=objective, scope=scope, route=route,
        body=[HALF_WIDTH,BODY_HEIGHT], coefficients=coefficients))


def scoped_geometry(geometry, scope):
    indices = scope['solidIndices']
    return dict(geometry, solids=[geometry['solids'][i] for i in indices]), indices


def full_witness(best):
    scope = best.get('validationScope', {})
    return scope.get('purpose', 'complete') == 'complete' and scope.get('through', best['plan']['ticks']) == best['plan']['ticks']


def validation_prefix(plan, candidate, vertical, through):
    if through == plan['ticks']:
        return plan, candidate, vertical
    p, c, v = copy.deepcopy(plan), copy.deepcopy(candidate), copy.deepcopy(vertical)
    p['ticks'] = through
    for key in ('jump', 'grounded', 'sneak'):
        if key in p:
            p[key] = p[key][:through]
    if 'ladderTicks' in p:
        p['ladderTicks'] = p['ladderTicks'][:through+1]
    p['sceneContacts'] = [e for e in p.get('sceneContacts', []) if e['offset'] < through]
    p['supportChecks'] = [e for e in p.get('supportChecks', []) if e[0] < through]
    for key in ('positions', 'velocity'):
        if key in c:
            c[key] = c[key][:through+1]
    for key in ('yaw', 'rawMovement', 'movement', 'friction'):
        if key in c:
            c[key] = c[key][:through]
    for key in ('y', 'vy', 'onGround'):
        v[key] = v[key][:through+1]
    if 'vertical' in v:
        for key in ('jumpVelocity', 'sneak'):
            if key in v['vertical']:
                v['vertical'][key] = v['vertical'][key][:through]
        v['vertical']['contacts'] = [e for e in v['vertical'].get('contacts', []) if e['state'] <= through]
    return p, c, v
