import copy
import time

from .case_profiles import entry_possible
from .movement_composition import append_choice, boundary_state, empty_program, program_physics_key, program_search_key, truncate_program
from .movement_generation import generate_choices
from .solve_spec import digest, processing_key


def persistent_programs(scene, *, settings, report, continuation=None, deadline=None, cancel_event=None, work=None,
                        initial_programs=None, checkpoint_tick=None, threshold=.005, screen=None,
                        context_adapter=None, program_filter=None, stop_on_goal=True, terminal_goal=None, **kwargs):
    initial = empty_program(scene['initial'], threshold=threshold)
    initial['terminalSupport'] = scene['startBlock']
    seeds = [initial] if initial_programs is None else initial_programs
    context_value = dict(version=2, scene=scene, settings={k: v for k, v in settings.items() if k not in
        ('maxPrefixes', 'maxQueuedPrefixes', 'maxChoicesPerType', 'expansionsPerProposal')},
        roots=[program_search_key(p) for p in seeds], checkpoint=checkpoint_tick, threshold=threshold,
        stopOnGoal=stop_on_goal)
    if terminal_goal is not None:
        context_value['terminalGoal']=terminal_goal
    context=digest(context_value)
    if continuation is not None and continuation.get('context') != context:
        raise ValueError('Incompatible movement generation checkpoint')
    state = copy.deepcopy(continuation) if continuation is not None else dict(version=2, context=context,
        pending=copy.deepcopy(seeds), ready=[], expansion=None, seen=[], generated=0, expanded=0, emitted=0,
        screened=0, policyRejections=0, exhausted=False)
    state.setdefault('exclusions', [])
    seen = set(state['seen'])
    expanded = 0
    iterator, iterator_key = None, None
    initial_bounds = [scene['initial']['position'][0]]*2+[scene['initial']['position'][2]]*2

    def budget():
        if cancel_event is not None and cancel_event.is_set():
            raise TimeoutError('cancelled')
        if work is not None:
            work.check()
        if deadline is not None and time.monotonic() >= deadline:
            raise TimeoutError('deadline')

    def complete(p):
        if terminal_goal is not None:
            return p.get('terminalGoal')==terminal_goal
        return (checkpoint_tick is not None and p['profile']['airtime'] == checkpoint_tick or
            stop_on_goal and p['profile']['grounded'][-1] and p['terminalSupport'] == scene['goalBlock']
            and p['profile']['airtime'] > 0)

    def key(p):
        return processing_key(p, digest(program_physics_key(p)))

    def take(items):
        p = min(items, key=key)
        items.remove(p)
        return p

    def update(reason='running'):
        report.update(termination=reason, expandedPrefixes=expanded, totalExpandedPrefixes=state['expanded'],
            generated=state['generated'], emittedPrograms=state['emitted'], screenedPrefixes=state['screened'],
            remainingPrefixes=len(state['pending'])+len(state['ready'])+int(state['expansion'] is not None),
            queuedCompletePrograms=len(state['ready']), queueDrops=0, partialQueueDrops=0, completeQueueDrops=0,
            policyRejections=state['policyRejections'], queuePolicy='persistent', types=settings['types'],
            explicitProgramPriority=False, deferredPrefixes=max(0, len(state['pending'])-settings['maxQueuedPrefixes']),
            continuation=state, minimumTicksProven=False)

    try:
        while state['pending'] or state['ready'] or state['expansion'] is not None:
            budget()
            e = state['expansion']
            if state['ready'] and (e is None or e.get('cursor', 0) % settings['maxChoicesPerType'] == 0):
                program = take(state['ready'])
                if program_filter is not None and not program_filter(program):
                    state['policyRejections'] += 1
                    continue
                state['emitted'] += 1
                program['terminal'] = 'open' if checkpoint_tick is not None else 'landed'
                update()
                yield program
                continue
            if e is None:
                if expanded >= settings['maxPrefixes']:
                    update('prefix_limit')
                    return
                program = take(state['pending'])
                if program_filter is not None and not program_filter(program):
                    state['policyRejections'] += 1
                    continue
                if complete(program):
                    state['ready'].append(program)
                    continue
                if program['profile']['airtime'] >= settings['maxInputs'] or program['interactions'] >= settings['maxInteractions']:
                    continue
                e = dict(program=program, kind=0, cursor=0)
                state['expansion'] = e
                expanded += 1
                state['expanded'] += 1
            if e['kind'] == len(settings['types']):
                state['expansion'] = None
                iterator, iterator_key = None, None
                continue
            ident = (digest(program_physics_key(e['program'])), e['kind'])
            if iterator_key != ident:
                p = e['program']
                remaining = settings['maxInputs']-p['profile']['airtime']
                context_data = dict(mode='open', scene=scene, program=p, state=boundary_state(p), maxInputs=remaining,
                    maxEntryInputs=settings['maxEntryInputs'], checkBudget=budget, deadline=deadline,
                    cancelEvent=cancel_event, normalGap=checkpoint_tick is not None, generationRecords=[])
                if terminal_goal is not None:
                    context_data['terminalGoal']=terminal_goal
                if threshold != .005:
                    context_data['threshold'] = threshold
                if checkpoint_tick is not None:
                    context_data['normalGapInputs'] = checkpoint_tick-p['profile']['airtime']
                if context_adapter is not None:
                    context_adapter(context_data)
                iterator = iter(generate_choices(settings['types'][e['kind']], context_data))
                iterator_key = ident
                for _ in range(e['cursor']):
                    budget()
                    try:
                        next(iterator)
                    except StopIteration:
                        raise ValueError('Movement generator changed across checkpoint restoration')
            if 'choice' not in e:
                try:
                    choice = next(iterator)
                except StopIteration:
                    e.update(kind=e['kind']+1, cursor=0)
                    iterator, iterator_key = None, None
                    continue
                e['choice'] = copy.deepcopy(choice)
                e['cursor'] += 1
                state['generated'] += 1
            child = append_choice(e['program'], e['choice'], max_inputs=settings['maxInputs'])
            if child is not None and terminal_goal is not None:
                from .terminal_goals import bind_goal
                child=bind_goal(child,terminal_goal,scene.get('supportClearance',1e-6))
            if child is not None and screen is not None:
                proof = screen(child)
                if proof is not None:
                    state['screened'] += 1
                    state['exclusions'].append(dict(id=digest(program_physics_key(child)),
                        ticks=child['profile']['airtime'], proof=proof, scope='full_proposal_before_projection'))
                    e.pop('choice', None)
                    update()
                    continue
            if child is not None and checkpoint_tick is not None and child['profile']['airtime'] > checkpoint_tick:
                child = truncate_program(child, checkpoint_tick)
            accepted = child is not None and entry_possible(child['profile'], initial_bounds)
            if accepted and program_filter is not None and not program_filter(child):
                state['policyRejections'] += 1
                accepted = False
            if accepted and context_data.get('prefixFilter') is not None and not context_data['prefixFilter'](child):
                accepted = False
            identity = program_search_key(child) if accepted else None
            if identity in seen:
                accepted = False
            if accepted and screen is not None:
                proof = screen(child)
                if proof is not None:
                    state['screened'] += 1
                    state['exclusions'].append(dict(id=digest(identity), ticks=child['profile']['airtime'], proof=proof))
                    accepted = False
            if accepted:
                seen.add(identity)
                state['seen'].append(identity)
                (state['ready'] if complete(child) else state['pending']).append(child)
            e.pop('choice', None)
            update()
        state['exhausted'] = True
        update('exhausted')
    except TimeoutError as error:
        update(work.termination if work is not None and work.termination else str(error))
    finally:
        if not state['exhausted'] and report.get('termination') == 'running':
            update('consumer_closed')
