"""Declared milestones and acyclic physical-owner visits, including local hops."""

import copy

from parkour_solver.search.identity import digest
from parkour_solver.search.comparison import comparison_label, priority_key, POLICY_VERSION as COMPARISON_POLICY_VERSION


HARD_JUMP_POLICY_VERSION = 4


def settings(value, scene):
    result = dict(version=1, anchors=[], turnaround=dict(degrees=110., spatialMargin=.3),
                  solveEveryDecisionLevels=1, feasibilityPolicy='numerical_oracle', observability=True)
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
    if result['version'] != 1 or result['feasibilityPolicy'] != 'numerical_oracle':
        raise ValueError("Unsupported anchor policy: version must be 1 and feasibilityPolicy 'numerical_oracle'")
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
                hardJumpPolicyVersion=HARD_JUMP_POLICY_VERSION,searchPolicyVersion=12,comparisonPolicyVersion=COMPARISON_POLICY_VERSION)


def owner(scene, block):
    b = next((b for b in scene['blocks'] if b['id'] == block), None)
    return b.get('sourceBlock', b['id']) if b else block


def box_owners(scene):
    return {tuple(scene['collisionScene']['solids'][b['solidIndex']]): owner(scene,b['id']) for b in scene['blocks']}


def visits(scene, program):
    """Read actual selected contacts, never profile height or transient air IDs."""
    by_box = box_owners(scene)
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


def launch(scene, program, begin=None, end=None):
    tick, name = [v for v in visits(scene, program) if begin is None or v[0] <= begin][-1]
    ladder = program['profile']['ladderTicks'][tick:None if end is None else end+1]
    return None if any(ladder) else (tick, name)


FEET_CASES = frozenset(('normal_step', 'slab_boost', 'blip', 'blip_up', 'corner_jump_cancel', 'corner_blip_up'))


def feet_touches(scene, program):
    by_box = box_owners(scene)
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
    """Required anchors reached in order (and their arrival ticks), even for a rejected program."""
    state = visit_state(scene,program)
    required = [owner(scene,a) for a in anchors]
    index = 0
    times = []
    reason = None
    for tick, name in visits(scene,program):
        if name in required[index:]:
            if name != required[index]:
                reason = 'required_anchor_order'
                break
            times.append(tick)
            index += 1
    if not state['valid']:
        reason = 'departed_owner_return'
    elif reason is None and reached is not None and index < reached:
        reason = 'missing_prior_anchor'
    return dict(accepted=reason is None, reason=reason, visits=state, reached=index, ticks=times)


def next_anchor(scene, program, anchors, task):
    """Owner of the next required anchor (anchors are visited in order), or of the task block after the last."""
    reached = audit(scene, program, anchors)['reached']
    return owner(scene, anchors[reached] if reached < len(anchors) else task)


ADVANTAGE_CASES = frozenset(('grind', 'slab_boost', 'corner_jump_cancel'))


def advantageous_targets(scene, base, child, next_owner):
    """Keys of the advantageous jumps made by the choice that turns base into child.

    A jump (or a run-off/step-up, itself a run tick) is advantageous when it lands on the
    next anchor; a jump also when it hits a ceiling or is a grind, slab boost or corner jump
    cancel. Run ticks are generated only as the minimum that makes one of these possible.
    """
    p, start = child['profile'], base['profile']['airtime']
    by_box = box_owners(scene)
    def owners(boxes):
        return '+'.join(sorted({by_box[tuple(b)] for b in boxes if tuple(b) in by_box}))
    keys = set()
    if p['grounded'][-1] and owner(scene, child.get('terminalSupport')) == next_owner:
        keys.add(('anchor', next_owner))
    if not p['jump'][start]:
        return keys
    keys |= {('ceiling', owners(e['boxes'])) for e in p.get('contactEvents', [])
             if e.get('role') == 'ceiling' and e['offset'] >= start}
    keys |= {(e['case'], owners(e['boxes'])) for e in p.get('caseEvents', [])
             if e.get('case') in ADVANTAGE_CASES and e['input'] >= start}
    return keys


def advantageous_candidates(scene, table, launch, top, next_owner, types, threshold=.005):
    """Advantageous moves from a grounded decision on owner `launch` (feet at `top`), from launch tables
    (`table(owner)`, see reachability.service.block_table) and vertical replays only.

    Each entry is dict(key, A, via). A jump-now move (via None) has the key advantageous_targets
    gives as a JSON list and its airtime A; ceiling and grind airtimes are inferred (the built choice stops
    at the hit), the rest equal the generators' replays. A run-off onto another owner (via) is a
    ["landing", owner] entry: A counts its airborne inputs only, since the walk-off input moves as a run
    tick (0 when it lands within that input), and `limit` is the airtime of a jump from here onto that
    owner, which its run ticks plus A compete with whatever follows it. A walking step-up onto another
    owner is a ["step", owner] entry with A = 0: its step input is a run tick too.
    """
    import json
    import math
    from parkour_solver.movement.cases import case_profile
    from parkour_solver.movement.contact_combinations import scene_contact_options
    from parkour_solver.movement.normal import first_descent, replay
    from parkour_solver.physics.vertical import REST_CARRY, f32
    blocks = {b['id']: b for b in scene['blocks']}
    by_box = box_owners(scene)
    undersides = scene_contact_options(scene, 1, -1) if 'ceiling' in types and scene.get('ceilingHits', True) else []

    def rest(y):
        return dict(position=y, velocity=REST_CARRY, onGround=True, previousHorizontalCollision=False)

    def landing(trace, tops):
        return min((t+1 for u in tops for t in [first_descent(trace, u)] if t is not None), default=None)

    def jumps(name, t):
        """(kind, owner) -> A for the jumps from a block of owner `name` with feet at t."""
        kinds = table(name)
        tops = {blocks[b]['top'] for b in kinds['landing']}
        trace = replay(rest(t), 200, jumping=True, threshold=threshold)
        found = {}
        def add(key, airtime):
            if airtime is not None and airtime < found.get(key, math.inf):
                found[key] = airtime
        if 'normal' in types:
            add(('anchor', next_owner), landing(trace, {blocks[b]['top'] for b in kinds['landing']
                                                        if owner(scene, b) == next_owner}))
        for option in undersides:
            if not any(i in kinds['ceilingSolids'] for i in option.members):
                continue
            names, plane = '+'.join(sorted({by_box[tuple(b)] for b in option.boxes if tuple(b) in by_box})), option.plane
            hit = next((h for h, move in enumerate(trace['movement'])
                        if move > 0 and trace['position'][h] <= plane < trace['position'][h+1]), None)
            if hit is not None:
                add(('ceiling', names), landing(replay(rest(t), 200, jumping=True, threshold=threshold,
                                                       contacts=[dict(state=hit+1, plane=plane, direction=1)]), tops))
            if 0 <= plane-t < f32(.42):
                event = dict(case='grind', input=0, plane=t, clearance=plane-t)
                add(('grind', names), min((p['airtime'] for u in tops
                                           for p in [case_profile(t, u, event, threshold=threshold)] if p), default=None))
        if 'stepping' in types:
            for b in kinds['stepping']:
                if f32(.42) < blocks[b]['top']-t <= f32(.6):
                    add(('slab_boost', owner(scene, b)), 2)
                    add(('corner_jump_cancel', owner(scene, b)), 2)
        return found

    entries = {}
    def add(key, airtime, via, **bound):
        key = json.dumps(key)
        if key not in entries or airtime < entries[key]['A']:
            entries[key] = dict(key=key, A=airtime, via=via, **bound)
    for (kind, name), airtime in jumps(launch, top).items():
        add([kind, name], airtime, None)
    kinds, fall = table(launch), replay(rest(top), 200, threshold=threshold)
    rise = replay(rest(top), 200, jumping=True, threshold=threshold)
    for b in sorted(kinds['landing'] | kinds['stepping']):
        name, u = owner(scene, b), blocks[b]['top']
        if name == launch:
            continue
        if 'normal' in types and b in kinds['landing'] and (u < top or u == top and name == next_owner):
            t = first_descent(fall, u)
            limit = landing(rise, {blocks[c]['top'] for c in kinds['landing'] if owner(scene, c) == name})
            if t is None or limit is None:
                continue
            add(['landing', name], t, name, limit=limit)
        elif 'stepping' in types and b in kinds['stepping'] and 0 < u-top <= f32(.6):
            add(['step', name], 0, name)
    return sorted(entries.values(), key=lambda e: e['key'])


def encounter_groups(scene, names):
    """Partition the pairs names[k] -> names[k+1] into groups searched as one frame.

    From a group's first end block, follow touching solids (the obstacle-cluster
    test) that some landing block of the chain can touch in one flight. Every such
    solid joins and extends the chain; the block-table kinds only label members as
    landings (blocks a landing block can land on) or hit/ceiling obstacles. The
    group runs through the last later anchor among the landing blocks.
    """
    from parkour_solver.reachability.service import block_table
    from parkour_solver.spatial.surfaces import scene_geometry
    geometry = scene_geometry(scene['collisionScene'])
    solid = {b['id']: b['solidIndex'] for b in scene['blocks']}
    by_solid = {}
    for b in scene['blocks']:
        by_solid.setdefault(b['solidIndex'], []).append(b['id'])
    traces, tables = {}, {}

    def table(name):
        key = owner(scene, name)
        if key not in tables:
            tables[key] = block_table(scene, key, traces)
        return tables[key]

    def neighbours(name):
        return {n for i in geometry.touching(solid[name], by_solid) for n in by_solid[i]}

    groups, first = [], 0
    while first < len(names)-1:
        members, landings = {names[first+1]}, {names[first+1]}
        while True:
            frontier = {n for m in members for n in neighbours(m)}-members
            frontier = {c for c in frontier if any(c in table(l)['blocks'] for l in landings)}
            if not frontier:
                break
            members |= frontier
            grown = True
            while grown:
                added = {c for c in members-landings if any(c in table(l)['landing'] for l in landings)}
                landings |= added
                grown = bool(added)
        owners = {owner(scene, l) for l in landings}
        last = max(m for m in range(first+1, len(names)) if owner(scene, names[m]) in owners)-1
        groups.append(dict(first=first, last=last, anchors=names[first+1:last+2], members=sorted(members),
                           landings=sorted(landings)))
        first = last+1
    return groups


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


def transfer_structure(scene, program, start, source, target, *, complete=False, until_arrival=False):
    """Separate source-local momentum preparation from the transfer flight.

    Each actual return to the source closes a setup hop. The pending/complete
    flight after the last source contact has at most/exactly one requested
    jump. A source contact during the target's arrival input belongs to that
    transfer's physical case, not an earlier setup boundary. With
    `until_arrival`, history after the first target arrival belongs to later
    pairs of the same encounter group and is ignored.
    """
    p=program['profile'];n=p['airtime']
    launches=[t for t in range(start,n) if p['grounded'][t] and p['jump'][t]]
    source,target=owner(scene,source),owner(scene,target)
    contacts=[(t,name) for t,name in visits(scene,program) if start<t<=n]
    arrival=min((t for t,name in contacts if name==target),default=None)
    if until_arrival and arrival is not None:
        contacts=[(t,name) for t,name in contacts if t<=arrival]
        launches=[t for t in launches if t<arrival]
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
