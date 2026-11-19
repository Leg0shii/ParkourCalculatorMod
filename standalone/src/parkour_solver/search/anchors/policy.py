"""Declared milestones and acyclic physical-owner visits, including local hops."""

import copy

from parkour_solver.search.identity import digest
from parkour_solver.search.comparison import comparison_label, priority_key, POLICY_VERSION as COMPARISON_POLICY_VERSION


HARD_JUMP_POLICY_VERSION = 3


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
    return dict({k:v for k,v in policy.items() if k!='observability'},
                hardJumpPolicyVersion=HARD_JUMP_POLICY_VERSION,searchPolicyVersion=5,comparisonPolicyVersion=COMPARISON_POLICY_VERSION)


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
            events.append((e['input'],1,dict(boxes=[e['lowerBox']])))
        boxes=e.get('boxes')
        if boxes and profile['grounded'][t] and abs(profile['position'][t]-max(b[4] for b in boxes))<=1e-7:
            events.append((e['input'],2,e))
    result = [(0, owner(scene,scene['startBlock']))]
    for tick, _, e in sorted(events, key=lambda x:(x[0],x[1],digest(x[2]))):
        name = next((by_box[tuple(b)] for b in e.get('boxes', ()) if tuple(b) in by_box), None)
        if name is not None:
            result.append((tick+1, name))
    # Some named profiles report only terminalSupport. Include its physical
    # owner without collapsing the input/jump history used by the solver.
    if program.get('terminalSupport') is not None:
        result.append((program['profile']['airtime'], owner(scene,program['terminalSupport'])))
    return result


def launch_owner(scene, program):
    tick, name = visits(scene, program)[-1]
    return None if any(program['profile']['ladderTicks'][tick:]) else name


FEET_CASES = frozenset(('normal_step', 'slab_boost', 'blip', 'blip_up', 'corner_jump_cancel', 'corner_blip_up'))


def feet_touches(scene, program):
    by_box = {tuple(scene['collisionScene']['solids'][b['solidIndex']]): owner(scene,b['id'])
              for b in scene['blocks']}
    result = []
    for e in program['profile'].get('caseEvents', []):
        if e.get('case') not in FEET_CASES:
            continue
        boxes = list(e.get('boxes') or ()) + ([e['lowerBox']] if e.get('lowerBox') is not None else [])
        result.extend((e['input']+1, by_box[tuple(b)]) for b in boxes if tuple(b) in by_box)
    return sorted(result)


def visit_state(scene, program):
    current = None
    departed = set()
    valid = True
    touches = feet_touches(scene,program)
    def touch_until(tick):
        nonlocal valid
        while touches and (tick is None or touches[0][0] < tick):
            if touches.pop(0)[1] in departed:
                valid = False
    for tick, name in visits(scene,program):
        touch_until(tick)
        if name == current:
            continue
        if name in departed:
            valid = False
        if current is not None:
            departed.add(current)
        current = name
    touch_until(None)
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
    c=comparison_label(program,start=start)
    return {k:c[k] for k in ('T','J','R','A','start')}


def priority(program, start=0, turnaround=False, identity=''):
    return priority_key(comparison_label(program,start=start,mode=turnaround,identity=identity))


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


def transfer_structure(scene, program, start, source, target, *, complete=False):
    """Separate source-local momentum preparation from the transfer flight.

    Each actual return to the source closes a setup hop. The pending/complete
    flight after the last source contact has at most/exactly one requested
    jump. A source contact during the target's arrival input belongs to that
    transfer's physical case, not an earlier setup boundary.
    """
    p=program['profile'];n=p['airtime']
    launches=[t for t in range(start,n) if p['grounded'][t] and p['jump'][t]]
    source,target=owner(scene,source),owner(scene,target)
    contacts=[(t,name) for t,name in visits(scene,program) if start<t<=n]
    arrival=min((t for t,name in contacts if name==target),default=None)
    boundary=max([start]+[t for t,name in contacts if name==source and (arrival is None or t<arrival)])
    preparation=[t for t in launches if t<boundary]
    transfer=[t for t in launches if t>=boundary and (arrival is None or t<arrival)]
    from parkour_solver.movement.cases import CASES
    stepping=[e['input'] for e in p.get('caseEvents', [])
              if (e.get('type')=='stepping' or e.get('case') in CASES-{'grind'})
              and boundary<=e['input'] and (arrival is None or e['input']<arrival)]
    reason=None
    if any(name not in (source,target) for _,name in contacts):
        reason='intermediate_support_between_anchors'
    elif arrival is not None and any(t>arrival and name==source for t,name in contacts):
        reason='return_after_target_arrival'
    elif arrival is not None and any(t>=arrival for t in launches):
        reason='jump_after_target_arrival'
    elif len(transfer)>1:
        reason='multiple_transfer_impulses'
    elif arrival is not None and stepping:
        reason='stepping_during_hard_transfer'
    elif complete and (arrival is None or len(transfer)!=1):
        reason='missing_transfer_jump'
    return dict(version=HARD_JUMP_POLICY_VERSION,valid=reason is None,reason=reason,
                source=source,target=target,spanStart=start,preparationThrough=boundary,
                preparationJumps=preparation,transferJumps=transfer,arrival=arrival,
                transferSteppingInputs=stepping,assumption='hard_transfer_without_stepping')


def single_jump_structure(scene, program, start, source, target, *, complete=False):
    return transfer_structure(scene,program,start,source,target,complete=complete)['valid']
