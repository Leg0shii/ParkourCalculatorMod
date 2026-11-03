from .obstacle_geometry import expanded_box
from .reachability import interaction_reachability, reachable_envelopes


def block_interaction(envelopes, box, tick, phases=('Y', 'X', 'Z'), *, interior=False):
    return interaction_reachability(envelopes, dict(alternatives=[dict(regions=[
        dict(tick=tick, phase=phase, bounds=expanded_box(box), interior=interior)]) for phase in phases]))


def interaction_allowed(context, box, tick, *, jumping=False, phases=('Y', 'X', 'Z')):
    gate = context.get('interactionGate')
    return gate is None or not gate.query(context, box, tick, jumping=jumping, phases=phases)['ruledOut']


class InteractionGate:
    def __init__(self, scene, compiler, *, seconds=1):
        self.scene, self.compiler, self.seconds = scene, compiler, seconds
        self.cache = {}
        self.checked = 0
        self.exclusions = []

    def envelopes(self, program):
        from .movement_composition import compile_program, program_physics_key
        from .route import prepare_route, schedule
        from .block_contacts import validate_vertical_schedule
        from .solve_spec import digest
        key = digest(program_physics_key(program))
        if key not in self.cache:
            compiled = compile_program(self.scene, program, require_complete=False)
            route = prepare_route(compiled['route'])
            plan = schedule(route, [0])
            vertical = validate_vertical_schedule(route, compiled['profiles'], plan)
            if not vertical['feasible']:
                return None
            plan['vertical'] = dict(vertical['vertical'], referencePosition=vertical['y'], referenceVelocity=vertical['vy'])
            coefficient = self.compiler.compile(route, plan, self.seconds)
            # Artificial terminal support rows do not restrict an open proposal.
            rows = [r for r in plan['walls'] if not r.get('name', '').startswith('landing:1:')]
            self.cache[key] = reachable_envelopes(dict(coefficient, ticks=plan['ticks'], walls=rows,
                position=self.scene['initial']['position'][::2], velocity=self.scene['initial']['velocity'][::2],
                threshold=program.get('threshold', .005), contacts=plan['contacts'], vertical=vertical['vertical'],
                ladderTicks=plan.get('ladderTicks', [False]*(plan['ticks']+1))), vertical['y'])
        return self.cache[key]

    def query(self, context, box, tick, *, jumping=False, phases=('Y', 'X', 'Z')):
        from .movement_composition import append_choice, program_physics_key
        from .open_movement import event_choice
        from .solve_spec import digest
        # Prefix inputs and free approach inputs are known; the proposed interaction
        # has not changed the post-movement height/ground state yet. Use its horizontal
        # envelope, keeping Y unconstrained for stepping and ceiling outcomes.
        prefix = context['program']
        key = (digest(program_physics_key(prefix)), jumping, tick)
        cache = context.setdefault('_interactionApproaches', {})
        if key not in cache:
            choice = event_choice(context, 'normal', context['state'], tick+1, jumping=jumping)
            p = append_choice(prefix, choice) if choice is not None else None
            cache[key] = self.envelopes(p) if p is not None else None
        envelope = cache[key]
        if envelope is None:
            return dict(ruledOut=False, reason='unknown_approach')
        at = prefix['profile']['airtime']+tick
        bounds = expanded_box(box)
        bounds[1], bounds[4] = -float('inf'), float('inf')
        proof = interaction_reachability(envelope, dict(alternatives=[dict(regions=[
            dict(tick=at, phase=phase, bounds=bounds)]) for phase in phases]))
        self.checked += 1
        if proof['ruledOut']:
            self.exclusions.append(dict(box=list(box), input=at, proof=proof))
        return proof

    def required(self, program):
        envelope = self.envelopes(program)
        if envelope is None:
            return dict(reason='vertical_schedule')
        profile = program['profile']
        for event in profile.get('contactEvents', [])+profile.get('caseEvents', []):
            if 'box' not in event:
                continue
            tick = event.get('offset', event.get('input'))
            proof = block_interaction(envelope, event['box'], tick)
            if proof['ruledOut']:
                return dict(reason='required_interaction_unreachable', input=tick, box=event['box'], proof=proof)
        return None

    def context(self, context):
        context['interactionGate'] = self

    def summary(self):
        return dict(checked=self.checked, excluded=len(self.exclusions), exclusions=self.exclusions)
