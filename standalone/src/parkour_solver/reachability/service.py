import collections

from parkour_solver.scene.geometry import expanded_box
from parkour_solver.reachability.intervals import interaction_reachability, reachable_envelopes


PROOF_POLICY = 3


def interaction_context_key(context):
    """Fingerprint all bounds/rows/probe uncertainty, including unknown Y."""
    import math
    from parkour_solver.search.identity import digest
    def finite(value):
        if isinstance(value, float) and not math.isfinite(value):
            return {'nonfiniteBound': str(value)}
        if isinstance(value, dict):
            return {k: finite(v) for k, v in value.items()}
        if isinstance(value, (list, tuple)):
            return [finite(v) for v in value]
        return value
    return digest(dict(policy=PROOF_POLICY, context=finite(context)))


def query_interaction(request, context, *, session=None, budget=None, context_key=None):
    """Conservative role/window query shared by routing and fixed generation.

    Unknown family contexts cannot borrow a fixed-program proof. The caller
    may supply a precomputed key only for this exact immutable context.
    """
    import copy
    from parkour_solver.search.identity import digest
    role = request['role']
    if role not in ('required_contact', 'unwanted_contact', 'approach_prerequisite'):
        raise ValueError('Unknown interaction role')
    windows = request['windows']
    if any(type(w['tick']) is not int or w['tick'] < 0 or w['phase'] not in ('Y', 'X', 'Z') for w in windows):
        raise ValueError('Interaction windows require nonnegative ticks and Y/X/Z phases')
    context_key = context_key or interaction_context_key(context)
    key = digest(dict(policy=PROOF_POLICY, context=context_key, request=request))
    if session is not None and key in session:
        return copy.deepcopy(session[key])
    envelope = context.get('envelopes')
    phases = []
    status, reason = 'unknown', 'unknown_motion_family'
    if envelope is not None and windows:
        for window in windows:
            if budget is not None:
                budget()
            tick, phase = window['tick'], window['phase']
            if tick >= len(envelope['phases']):
                phases.append(dict(window, ruledOut=False, reason='window_outside_context'))
            else:
                checked = block_interaction(envelope, request['box'], tick, (phase,),
                    interior=request.get('interior', role == 'unwanted_contact'))
                phases.append(dict(checked, **window))
        status = 'absent' if all(p['ruledOut'] for p in phases) else 'not_ruled_out'
        reason = 'all_scoped_phases_excluded' if status == 'absent' else 'interaction_retained'
    proof = dict(policy=PROOF_POLICY, id=key, context=context_key, role=role, ruledOut=status == 'absent',
        status=status, reason=reason, phases=phases, owner=request.get('owner'),
        interior=request.get('interior', role == 'unwanted_contact'), invalidation='exact_context_only')
    if session is not None:
        session[key] = copy.deepcopy(proof)
    return proof


def normalize_alternatives(alternatives, proofs):
    """Remove absent unwanted items; absent prerequisites reject their owner."""
    kept, audit = [], []
    rejected = {item.get('owner') for item in alternatives if
                proofs[item['requestId']]['status']=='absent' and
                proofs[item['requestId']]['role']!='unwanted_contact'}
    for item in alternatives:
        proof = proofs[item['requestId']]
        action = ('omit' if proof['role'] == 'unwanted_contact' else 'reject_owner') if proof['status'] == 'absent' else 'retain'
        if item.get('owner') in rejected and action=='retain':
            action='reject_owner'
        audit.append(dict(requestId=item['requestId'], proof=proof['id'], action=action))
        if action == 'retain':
            kept.append(item)
    return kept, audit


def block_interaction(envelopes, box, tick, phases=('Y', 'X', 'Z'), *, interior=False):
    return interaction_reachability(envelopes, dict(alternatives=[dict(regions=[
        dict(tick=tick, phase=phase, bounds=expanded_box(box), interior=interior)]) for phase in phases]))


def interaction_allowed(context, box, tick, *, label, jumping=False, phases=('Y', 'X', 'Z')):
    return bounds_allowed(context, expanded_box(box), tick, label=label, jumping=jumping, phases=phases)


def bounds_allowed(context, bounds, tick, *, label, jumping=False, phases=('Y', 'X', 'Z')):
    """`label` (owner, role) names the generator choice in the exclusion log."""
    if 'interactionGate' not in context:
        raise ValueError('Generation needs an interactionGate; set it to None to run without reachability')
    gate = context['interactionGate']
    return gate is None or not gate.query(context, bounds, tick, label=label, jumping=jumping, phases=phases)['ruledOut']


def candidate_blocks(context, kind='blocks'):
    blocks = context['scene']['blocks']
    owner = context.get('launchOwner')
    if owner is None or context['interactionGate'] is None:
        return blocks
    kept = context['interactionGate'].reachable_blocks(owner)[kind]
    return [b for b in blocks if b['id'] in kept]


def candidate_option(context, option, kind='solids'):
    owner = context.get('launchOwner')
    if owner is None or context['interactionGate'] is None:
        return True
    solids = context['interactionGate'].reachable_blocks(owner)[kind]
    return any(i in solids for i in option.members)


def block_table(scene, owner, traces):
    """Blocks one flight from `owner` can touch, by kind (generous: no route, as-given speed limits).

    Heights are relative to the launch piece's top (feet): any block whose raw
    bottom is within head reach can be hit; `landing` tops are within the game's
    jump rise; `ceiling` bottoms lie between head height and head reach.
    `stepping` adds blip secondaries, touched one (air) tick after a landing-block
    primary. `traces` caches threshold-0 jump traces by top height.
    """
    import math
    from parkour_solver.movement.normal import JUMP_RISE, first_descent, replay
    from parkour_solver.physics.vertical import REST_CARRY, STEP_HEIGHT
    from parkour_solver.scene.geometry import BODY_HEIGHT
    from parkour_solver.spatial.surfaces import scene_geometry
    blocks = scene['blocks']
    geometry = scene_geometry(scene['collisionScene'])
    lowest = min(b['top'] for b in blocks)
    by_solid = {}
    for b in blocks:
        by_solid.setdefault(b['solidIndex'], []).append(b['id'])
    tops = {b['id']: b['top'] for b in blocks}
    solid_of = {b['id']: b['solidIndex'] for b in blocks}
    kept, landing, ceiling, primaries, ticks = set(), set(), set(), [], None

    def near(box, radius):
        foot = [box[0], box[2], box[3], box[5]]
        for i in geometry.near([foot[0]-radius, foot[1]-radius, foot[2]+radius, foot[3]+radius]):
            other = geometry.boxes[i]
            dx = max(0., other[0]-foot[2], foot[0]-other[3])
            dz = max(0., other[2]-foot[3], foot[1]-other[5])
            if math.hypot(dx, dz) <= radius:
                yield i, other

    for block in blocks:
        if block.get('sourceBlock', block['id']) != owner:
            continue
        kept.add(block['id'])
        if block['top'] not in traces:
            state = dict(position=block['top'], velocity=REST_CARRY, onGround=True, previousHorizontalCollision=False)
            traces[block['top']] = replay(state, 200, jumping=True, threshold=0.)
        descent = first_descent(traces[block['top']], lowest)
        flight = 200 if descent is None else descent+1
        ticks = flight if ticks is None else min(ticks, flight)
        feet = block['top']
        for i, box in near(geometry.boxes[block['solidIndex']], .4+.7+.4*(flight-1)):
            bottom = box[1]+BODY_HEIGHT
            if bottom > feet+BODY_HEIGHT+JUMP_RISE:
                continue
            names = by_solid.get(i, ())
            kept.update(names)
            if feet+BODY_HEIGHT <= bottom:
                ceiling.update(names)
            for name in names:
                if tops[name] <= feet+JUMP_RISE:
                    landing.add(name)
                    primaries.append((name, feet+JUMP_RISE))
    stepping = set(landing)
    for name, peak in primaries:
        for i, _ in near(geometry.boxes[solid_of[name]], .4):
            stepping.update(n for n in by_solid.get(i, ()) if tops[n] <= peak+STEP_HEIGHT)
    solids = lambda names: frozenset(solid_of[n] for n in names)
    return dict(blocks=frozenset(kept), solids=solids(kept), ticks=ticks, landing=frozenset(landing),
                stepping=frozenset(stepping), ceiling=frozenset(ceiling), ceilingSolids=solids(ceiling))


class InteractionGate:
    """Session-shared preparation, coefficient and exact-model proof cache."""
    def __init__(self, scene, compiler, *, seconds=1):
        self.scene, self.compiler, self.seconds = scene, compiler, seconds
        self.cache = {}
        self.metrics = {}
        self.checked = 0
        self.exclusions = []
        from parkour_solver.numerics.disk import STATS
        self.relaxation_start = {kind: collections.Counter(counts) for kind, counts in STATS.items()}
        self.block_table, self.jump_traces = {}, {}
        self.approaches = {}

    def reachable_blocks(self, owner):
        if owner in self.block_table:
            return self.block_table[owner]
        import time
        started = time.process_time()
        self.block_table[owner] = block_table(self.scene, owner, self.jump_traces)
        self.metrics['blockTableBuilds'] = self.metrics.get('blockTableBuilds', 0)+1
        self.metrics['blockTableSeconds'] = self.metrics.get('blockTableSeconds', 0.)+time.process_time()-started
        return self.block_table[owner]

    def _cached(self, key, build, kind):
        hit = key in self.cache
        name=kind+('Hits' if hit else 'Misses')
        self.metrics[name]=self.metrics.get(name,0)+1
        if hit:
            value=self.cache.pop(key)
        else:
            value=build()  # A timeout never commits a result or advances a cursor.
        self.cache[key]=value
        while len(self.cache)>512:
            self.cache.pop(next(iter(self.cache)))
        return value

    def prepare(self, program, *, scene=None, require_complete=False):
        from parkour_solver.movement.program import compile_program, program_physics_key
        from parkour_solver.scene.route import prepare_route, schedule
        from parkour_solver.validation.vertical import validate_vertical_schedule
        from parkour_solver.search.identity import digest
        scene=self.scene if scene is None else scene
        key=('prepared',digest(dict(scene={k:v for k,v in scene.items() if k!='movementSearch'},
            program=program_physics_key(program),complete=require_complete)))
        def build():
            compiled=compile_program(scene,program,require_complete=require_complete)
            route=prepare_route(compiled['route']);plan=schedule(route,[0])
            vertical=validate_vertical_schedule(route,compiled['profiles'],plan)
            if not vertical['feasible']:return None
            plan['vertical']=dict(vertical['vertical'],referencePosition=vertical['y'],referenceVelocity=vertical['vy'])
            return dict(route=route,profiles=compiled['profiles'],plan=plan,vertical=vertical)
        return self._cached(key,build,'preparation')

    def analyze_model(self, route, plan, vertical, *, open_terminal=False, scope=None, initial_intervals=None):
        from parkour_solver.bridge.compiler import coefficient_request
        from parkour_solver.search.identity import digest
        request=coefficient_request(route,plan)
        coefficient=self._cached(('coefficients',digest(request)),
            lambda:self.compiler.compile(route,plan,self.seconds),'coefficient')
        rows=[r for r in plan['walls'] if not open_terminal or not r.get('name','').startswith('landing:1:')]
        model=dict(coefficient,ticks=plan['ticks'],walls=rows,contacts=plan['contacts'],
            position=route['initial']['position'][::2],velocity=route['initial']['velocity'][::2],
            threshold=route.get('physicsThreshold',.005),vertical=vertical['vertical'],
            ladderTicks=plan.get('ladderTicks',[False]*(plan['ticks']+1)))
        if initial_intervals is not None:model['initialIntervals']=initial_intervals
        key=interaction_context_key(dict(model=model,y=vertical['y'],scope=scope))
        def build():
            envelope=reachable_envelopes(model,vertical['y']);envelope['modelKey']=digest(coefficient)
            return dict(key=key,model=model,envelopes=envelope,coefficient=coefficient,y=vertical['y'])
        return self._cached(('analysis',key),build,'analysis')

    def analyze(self, program, *, scene=None, require_complete=False, open_terminal=True, scope=None):
        prepared=self.prepare(program,scene=scene,require_complete=require_complete)
        if prepared is None:return None
        return self.analyze_model(prepared['route'],prepared['plan'],prepared['vertical'],
                                  open_terminal=open_terminal,scope=scope)

    def condition(self, analysis, rows):
        """Add X/Z prerequisites and repropagate, retaining the compiled physics."""
        model=dict(analysis['model'],walls=list(analysis['model']['walls'])+list(rows))
        key=interaction_context_key(dict(model=model,y=analysis['y']))
        from parkour_solver.reachability.screen import condition_envelopes
        return self._cached(('conditioned',key),lambda:dict(analysis,key=key,model=model,
            **condition_envelopes(analysis['envelopes'],rows,refine=False)),'conditioned')

    def envelopes(self, program):
        analysis=self.analyze(program)
        return analysis['envelopes'] if analysis else None

    def sequence(self, context, choice):
        """Screen a shared named outcome before multiplying its side variants."""
        from parkour_solver.movement.program import append_choice
        if context.get('checkBudget'):context['checkBudget']()
        program=append_choice(context['program'],choice)
        self.metrics['sequenceChecks']=self.metrics.get('sequenceChecks',0)+1
        proof=dict(reason='composition_rejected') if program is None else self.required(program)
        if proof:
            self.metrics['sequenceExclusions']=self.metrics.get('sequenceExclusions',0)+1
            self.exclusions.append(dict(reason='joint_prerequisites_unreachable',proof=proof,frame=context.get('frameId'),
                input=context['program']['profile']['airtime'],cases=choice['profile'].get('caseEvents',[])))
        return proof

    def check_program(self, program, objective=None):
        # This guard is a distinct physical requirement, not an interval bound.
        p=program['profile']
        if p['airtime'] and p['position'][1]<self.scene['initial']['position'][1]-1e-7:
            from parkour_solver.scene.route import supports
            if any(abs(b['top']-self.scene['initial']['position'][1])<1e-7 and
                   supports(b,*self.scene['initial']['position'][::2]) for b in self.scene['blocks']):
                return dict(reason='unselected_initial_floor')
        analysis=self.analyze(program)
        if analysis is None:return dict(reason='vertical_schedule')
        from parkour_solver.search.identity import digest
        objective=self.scene['objective'] if objective is None else objective
        key=('screen',analysis['key'],digest(objective))
        undecided=False
        def build():
            nonlocal undecided
            proof=self.required(program)
            if proof:return proof
            if self.scene.get('maxWallHits',0):return None
            self.metrics['relaxationChecks']=self.metrics.get('relaxationChecks',0)+1
            checked=interaction_reachability(analysis['envelopes'],{},refine=True,objective=objective)
            undecided=checked['reason']=='relaxation_undecided'
            return {k:v for k,v in checked.items() if k!='ruledOut'} if checked['ruledOut'] else None
        result=self._cached(key,build,'screen')
        if undecided:
            self.cache.pop(key)
        return result

    def query(self, context, bounds, tick, *, label, jumping=False, phases=('Y', 'X', 'Z')):
        from parkour_solver.movement.program import append_choice, program_physics_key
        from parkour_solver.movement.normal import approaches, event_choice, first_descent
        from parkour_solver.search.identity import digest
        # Prefix inputs and free approach inputs are known; the proposed interaction
        # has not changed the post-movement height/ground state yet. Use its horizontal
        # envelope, keeping Y unconstrained for stepping and ceiling outcomes.
        prefix = context['program']
        if '_prefixKey' not in context:
            context['_prefixKey'] = (digest(program_physics_key(prefix)), context.get('threshold', .005))
        prefix_key = context['_prefixKey']
        def remember(key, build):
            if key not in self.approaches:
                self.approaches[key] = build()
                while len(self.approaches) > 512:
                    self.approaches.pop(next(iter(self.approaches)))
            return self.approaches[key]
        def approach(inputs):
            def build():
                self.metrics['approachBuilds'] = self.metrics.get('approachBuilds', 0)+1
                choice = event_choice(context, 'normal', context['state'], inputs, jumping=jumping)
                p = append_choice(prefix, choice) if choice is not None else None
                return self.envelopes(p) if p is not None else None
            return remember((prefix_key, jumping, inputs), build)
        def horizon():
            lowest = min(b['top'] for b in context['scene']['blocks'])
            trace = next((t for _, j, t in approaches(context) if j == jumping), None)
            landing = first_descent(trace, lowest) if trace is not None else None
            return context.get('maxInputs', 0) if landing is None else min(landing+2, context.get('maxInputs', 0))
        horizon = remember((prefix_key, jumping, 'horizon', context.get('maxInputs', 0)), horizon)
        envelope = approach(horizon) if tick < horizon else None
        if envelope is None:
            self.metrics['approachFallbacks'] = self.metrics.get('approachFallbacks', 0)+1
            envelope = approach(tick+1)
        if envelope is None:
            return dict(ruledOut=False, reason='unknown_approach')
        at = prefix['profile']['airtime']+tick
        bounds = list(bounds)
        bounds[1], bounds[4] = -float('inf'), float('inf')
        proof = interaction_reachability(envelope, dict(alternatives=[dict(regions=[
            dict(tick=at, phase=phase, bounds=bounds)]) for phase in phases]))
        self.checked += 1
        if proof['ruledOut']:
            self.exclusions.append(dict(label, frame=context.get('frameId'), horizontal=[bounds[0], bounds[2], bounds[3], bounds[5]],
                                        input=at, proof=proof))
        return proof

    def required(self, program):
        envelope = self.envelopes(program)
        if envelope is None:
            return dict(reason='vertical_schedule')
        profile = program['profile']
        if envelope.get('contradiction'):
            return dict(reason='required_interaction_unreachable',input=envelope['contradiction'].get('state'),
                        proof=dict(ruledOut=True,**envelope['contradiction']))
        context = dict(envelopes=envelope)
        context_key = interaction_context_key(context)
        for event in profile.get('contactEvents', [])+profile.get('caseEvents', []):
            if 'boxes' not in event:
                continue
            tick = event.get('offset', event.get('input'))
            proofs = [query_interaction(dict(box=box, role='required_contact',
                windows=[dict(tick=tick, phase=p) for p in ('Y', 'X', 'Z')], interior=False),
                context, context_key=context_key) for box in event['boxes']]
            if all(proof['status'] == 'absent' for proof in proofs):
                return dict(reason='required_interaction_unreachable', input=tick, boxes=event['boxes'], proof=proofs[0])
        return None

    def context(self, context):
        context['interactionGate'] = self

    def summary(self):
        from parkour_solver.numerics.disk import STATS
        return dict(checked=self.checked,excluded=len(self.exclusions),exclusions=self.exclusions,metrics=dict(self.metrics),
                    relaxation={kind: dict(counts-self.relaxation_start[kind]) for kind, counts in STATS.items()})
