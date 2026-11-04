"""Declared milestones and acyclic physical-owner visits, including local hops."""

import copy

from ..solve_spec import digest


def settings(value, scene):
    result = dict(version=1, anchors=[], turnaround=dict(degrees=110., spatialMargin=.3),
                  solveEveryDecisionLevels=1, feasibilityPolicy='ipopt_oracle', observability=True)
    if not isinstance(value, dict) or set(value)-set(result):
        raise ValueError('Unsupported anchorSearch fields')
    result.update(copy.deepcopy(value))
    names = {b['id'] for b in scene['blocks']}
    anchors = result['anchors']
    if (not isinstance(anchors, list) or len(set(anchors)) != len(anchors) or
            any(a not in names for a in anchors)):
        raise ValueError('Anchors must be ordered unique scene blocks')
    if scene['startBlock'] in anchors or scene['goalBlock'] in anchors:
        raise ValueError('Initial and terminal supports are implicit endpoints')
    if result['version'] != 1 or result['feasibilityPolicy'] != 'ipopt_oracle':
        raise ValueError('Unsupported anchor policy')
    if type(result['solveEveryDecisionLevels']) is not int or result['solveEveryDecisionLevels'] < 1:
        raise ValueError('Decision solve interval must be positive')
    if type(result['observability']) is not bool:
        raise ValueError('observability must be boolean')
    turn = result['turnaround']
    if not isinstance(turn, dict) or set(turn)-{'degrees','spatialMargin'}:
        raise ValueError('Unsupported turnaround settings')
    result['turnaround'] = dict(degrees=110., spatialMargin=.3) | turn
    import math
    if any(isinstance(v, bool) or not isinstance(v, (int,float)) or not math.isfinite(v)
           for v in result['turnaround'].values()):
        raise ValueError('Turn settings must be finite numbers')
    if not 0 < result['turnaround']['degrees'] < 180 or result['turnaround']['spatialMargin'] < 0:
        raise ValueError('Invalid turn angle or margin')
    return result


def semantic_settings(policy):
    return {k:v for k,v in policy.items() if k!='observability'}


def owner(scene, block):
    b = next((b for b in scene['blocks'] if b['id'] == block), None)
    return b.get('sourceBlock', b['id']) if b else block


def visits(scene, program):
    """Read actual selected contacts, never profile height or transient air IDs."""
    by_box = {tuple(scene['collisionScene']['solids'][b['solidIndex']]): owner(scene,b['id'])
              for b in scene['blocks']}
    events = [(e['offset'], 0, e) for e in program['profile'].get('contactEvents', [])
              if e['axis'] == 'Y' and e.get('direction') == -1]
    profile=program['profile']
    for e in profile.get('caseEvents', []):
        t=e['input']+1
        # A cancel/grind touches geometry without landing on its owner. Keep
        # its requested impulse and physical case, but do not invent a visit.
        if e.get('lowerBox') is not None:
            events.append((e['input'],1,dict(box=e['lowerBox'])))
        box=e.get('box')
        if box and profile['grounded'][t] and abs(profile['position'][t]-box[4])<=1e-7:
            events.append((e['input'],2,e))
    result = [(0, owner(scene,scene['startBlock']))]
    for tick, _, e in sorted(events, key=lambda x:(x[0],x[1],digest(x[2]))):
        name = by_box.get(tuple(e.get('box', ())))
        if name is not None:
            result.append((tick+1, name))
    # Some named profiles report only terminalSupport. Include its physical
    # owner without collapsing the input/jump history used by the solver.
    if program.get('terminalSupport') is not None:
        result.append((program['profile']['airtime'], owner(scene,program['terminalSupport'])))
    return result


def visit_state(scene, program):
    current = None
    departed = set()
    valid = True
    for _, name in visits(scene,program):
        if name == current:
            continue
        if name in departed:
            valid = False
        if current is not None:
            departed.add(current)
        current = name
    return dict(version=1, current=current, departed=sorted(departed), valid=valid)


def audit(scene, program, anchors, *, reached=None):
    state = visit_state(scene,program)
    if not state['valid']:
        return dict(accepted=False, reason='departed_owner_return', visits=state)
    required = [owner(scene,a) for a in anchors]
    index = 0
    times = []
    for tick, name in visits(scene,program):
        if name in required[index:]:
            if name != required[index]:
                return dict(accepted=False, reason='required_anchor_order', visits=state)
            times.append(tick)
            index += 1
    accepted = reached is None or index >= reached
    return dict(accepted=accepted, reason=None if accepted else 'missing_prior_anchor',
                visits=state, reached=index, ticks=times)


def local_counts(program, start=0):
    p=program['profile']; n=p['airtime']
    if not 0 <= start <= n:
        raise ValueError('Anchor span starts outside the program')
    jumps=sum(bool(p['grounded'][t] and p['jump'][t]) for t in range(start,n))
    runs=sum(bool(p['grounded'][t] and not p['jump'][t]) for t in range(start,n))
    return dict(T=n, J=jumps, R=runs, A=n-start-jumps-runs, start=start)


def priority(program, start=0, turnaround=False, identity=''):
    c=local_counts(program,start)
    return (c['T'], -c['J'], c['A'] if turnaround else c['R'], identity)


def within_caps(request, program):
    """Honor declared limits without importing legacy scene defaults as caps."""
    c=local_counts(program)
    if any(name in request and c[key]>request[name] for name,key in
           (('maxJumps','J'),('maxTotalRunTicks','R'))):
        return False
    if 'maxRunTicksPerJump' in request:
        run=0;p=program['profile']
        for t in range(p['airtime']):
            run=run+1 if p['grounded'][t] and not p['jump'][t] else 0
            if run>request['maxRunTicksPerJump']:return False
    return True


def single_jump_structure(scene, program, start, source, target, *, complete=False):
    p=program['profile'];n=p['airtime']
    jumps=[t for t in range(start,n) if p['grounded'][t] and p['jump'][t]]
    if len(jumps)>1 or complete and len(jumps)!=1:
        return False
    source,target=owner(scene,source),owner(scene,target)
    for tick,name in visits(scene,program):
        if tick<=start:continue
        if name not in (source,target):return False
        if jumps and tick>jumps[0] and name==source:return False
    return True
