import copy
import json
import time

from parkour_solver.movement.cases import entry_possible
from parkour_solver.movement.program import append_choice, boundary_state, empty_program, program_physics_key, program_search_key, truncate_program
from parkour_solver.movement.generation import generate_choices, propose_choices, assess_proposal
from parkour_solver.movement.normal import MAX_ADVANTAGEOUS_RUN_TICKS
from parkour_solver.search.identity import digest, processing_key
from parkour_solver.search.comparison import POLICY_VERSION as COMPARISON_POLICY_VERSION


def take_first(items, key, identity):
    """Pop min(items, key=lambda p: key(p, identity(p))), list order breaking ties.

    The identity hashes the whole program and is only the key's last field, so it is
    computed only for the programs tied on every other field.
    """
    heads = [key(p, '')[:-1] for p in items]
    low = min(heads)
    tied = [i for i, head in enumerate(heads) if head == low]
    index = tied[0] if len(tied) == 1 else min(tied, key=lambda i: identity(items[i]))
    return items.pop(index)


def restore_alternatives(state, extra_live=()):
    """Queue a decision node's next held alternative once its branch died: no live program descends
    from the node (or from every node the entry watches) or from an alternative already restored for
    it. A branch with a validated group-end arrival below it never restores. Returns whether anything
    was queued."""
    succeeded = set(state.get('succeededNodes', []))
    waiting = [(node, held) for node, held in state.get('alternatives', {}).items()
               if held['next'] < len(held['programs']) and node not in succeeded]
    if not waiting:
        return False
    parents = state['decisionParents']
    expansion = state['expansion'] or {}
    alive = set()
    for program in [*state['pending'], *state['ready'], *state.get('deferredNodes', []), state.get('nodeInFlight'),
                    expansion.get('program'), *extra_live]:
        node = program.get('decisionNode') if program else None
        while node and node not in alive:
            alive.add(node)
            node = parents.get(node)
    restored = False
    for node, held in waiting:
        if (any(n in alive or n in succeeded for n in held.get('watch', [node])) or
                any(r in alive for r in held['restored'])):
            continue
        program = copy.deepcopy(held['programs'][held['next']])
        held['next'] += 1
        held['restored'].append(program['decisionNode'])
        state['pending'].append(program)
        restored = True
    return restored


def persistent_programs(scene, *, settings, report, continuation=None, deadline=None, cancel_event=None, work=None,
                        initial_programs=None, checkpoint_tick=None, threshold=.005, screen=None,
                        context_adapter=None, program_filter=None, stop_on_goal=True, terminal_goal=None, stop_supports=None,
                        node_evaluator=None, priority_key=None, identity_key=None, policy_context=None, live_programs=None,
                        run_policy=None, **kwargs):
    initial = empty_program(scene['initial'], threshold=threshold)
    initial['terminalSupport'] = scene['startBlock']
    seeds = [initial] if initial_programs is None else initial_programs
    # The scene also carries movementSearch. Hash its physical fields here;
    # settings below already owns the domain and deliberately excludes slices.
    context_value = dict(version=3, comparisonPolicy=COMPARISON_POLICY_VERSION, proposalPolicy=1, scene={k:v for k,v in scene.items() if k!='movementSearch'}, settings={k: v for k, v in settings.items() if k not in
        ('maxPrefixes', 'maxQueuedPrefixes', 'maxChoicesPerType', 'expansionsPerProposal')},
        roots=[program_search_key(p) for p in seeds], checkpoint=checkpoint_tick, threshold=threshold,
        stopOnGoal=stop_on_goal)
    if terminal_goal is not None:
        context_value['terminalGoal']=terminal_goal
    if stop_supports is not None:
        context_value['stopSupports']=stop_supports
    if policy_context is not None:
        context_value.update(version=4,decisionPolicy=policy_context)
    context=digest(context_value)
    if continuation is not None and continuation.get('context') != context:
        raise ValueError('Incompatible movement generation checkpoint')
    state = copy.deepcopy(continuation) if continuation is not None else dict(version=4 if policy_context is not None else 3, context=context,
        pending=copy.deepcopy(seeds), ready=[], expansion=None, seen=[], generated=0, expanded=0, emitted=0,
        screened=0, policyRejections=0, exhausted=False)
    state.setdefault('exclusions', [])
    if node_evaluator is not None:
        state.setdefault('nodes', {})
        state.setdefault('deferredNodes', [])
        state.setdefault('decisionParents', {})
        state.setdefault('alternatives', {})
        state.setdefault('succeededNodes', [])
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
            stop_on_goal and p['profile']['grounded'][-1] and p['terminalSupport'] in (stop_supports or [scene['goalBlock']])
            and p['profile']['airtime'] > 0)

    def key(p, identity):
        return priority_key(p, identity) if priority_key else processing_key(p, identity)

    def identity_of(p):
        return identity_key(p) if identity_key else digest(program_physics_key(p))

    def take(items):
        return take_first(items, key, identity_of)

    def restore():
        return node_evaluator is not None and restore_alternatives(state, live_programs() if live_programs else ())

    def project_owned(program):
        from parkour_solver.search.goals import _landing_prefixes
        arrivals=[p for block in (stop_supports or [scene['goalBlock']])
                  for p in _landing_prefixes(scene,program,block,reuse_full=True)]
        if not arrivals:return program
        owned=min(arrivals,key=lambda p:(p['profile']['airtime'],p['terminalSupport']))
        owned['ownedEndpoint']=owned['profile']['airtime']
        return owned

    def expansion_context(p, run_ticks=True):
        context_data = dict(mode='open', scene=scene, program=p, state=boundary_state(p),
            maxInputs=settings['maxInputs']-p['profile']['airtime'], maxEntryInputs=settings['maxEntryInputs'],
            checkBudget=budget, deadline=deadline, cancelEvent=cancel_event, normalGap=checkpoint_tick is not None,
            generationRecords=[])
        if terminal_goal is not None:
            context_data['terminalGoal']=terminal_goal
        if threshold != .005:
            context_data['threshold'] = threshold
        if checkpoint_tick is not None:
            context_data['normalGapInputs'] = checkpoint_tick-p['profile']['airtime']
        if not run_ticks:
            context_data['runTickChoices'] = False
        if context_adapter is not None:
            context_adapter(context_data)
        return context_data

    def proposals(kind, context_data, start=0):
        bind=None
        if terminal_goal is not None:
            from parkour_solver.search.virtual_goals import bind_goal
            bind=lambda p:bind_goal(p,terminal_goal,scene.get('supportClearance',1e-6))
        return iter(propose_choices(kind,context_data,source=generate_choices(kind,context_data),start=start,
            max_inputs=settings['maxInputs'],inherit_decisions=node_evaluator is not None,
            project=project_owned if node_evaluator is not None and stop_on_goal else None,bind=bind))

    def no_effects():
        return dict(exclusions=[], screened=0, policyRejections=0)

    def proposal_effects(result, child, full):
        effects = no_effects()
        if result['fullProof'] is not None:
            effects['exclusions'].append(dict(id=digest(program_physics_key(full)),
                ticks=full['profile']['airtime'],proof=result['fullProof'],scope='unused_extension'))
        if result['proof'] is not None:
            effects['screened'] += 1
            effects['exclusions'].append(dict(id=digest(program_physics_key(child)),
                ticks=child['profile']['airtime'],proof=result['proof'],scope='owned_proposal'))
        return effects

    def commit(effects):
        state['exclusions'].extend(effects['exclusions'])
        state['screened'] += effects['screened']
        state['policyRejections'] += effects['policyRejections']

    def accept(child, context_data, effects, queued=()):
        """(child, None) when accepted after the proposal screen, (child, 'seen') when already queued, else (None, why)."""
        before_projection=child
        if child is not None and checkpoint_tick is not None and child['profile']['airtime'] > checkpoint_tick:
            child = truncate_program(child, checkpoint_tick)
        if child is None or not entry_possible(child['profile'], initial_bounds):
            return None, 'rejected'
        if program_filter is not None and not program_filter(child):
            effects['policyRejections'] += 1
            return None, 'rejected'
        if context_data.get('prefixFilter') is not None and not context_data['prefixFilter'](child):
            return None, 'rejected'
        identity = program_search_key(child)
        if identity in seen or identity in queued:
            return child, 'seen'
        if screen is not None and child is not before_projection:
            proof = screen(child)
            if proof is not None:
                effects['screened'] += 1
                effects['exclusions'].append(dict(id=digest(identity), ticks=child['profile']['airtime'], proof=proof))
                return None, 'screened'
        return child, None

    def check_child(proposal, context_data, queued=()):
        """Both screens and acceptance of one proposal, without changing frontier state: (child, reason, effects)."""
        child = proposal['program']
        if child is None:
            return None, 'rejected', no_effects()
        effects = no_effects()
        if screen is not None:
            result=assess_proposal(proposal,screen,context_data.get('interactionGate'))
            effects=proposal_effects(result,child,proposal['fullProgram'])
            if result['proof'] is not None:
                return None, 'screened', effects
        child, reason = accept(child, context_data, effects, queued)
        return child, reason, effects

    def link(parent, choice, child):
        """Set the child's decision fields (frontier state unchanged); returns whether the choice is meaningful."""
        if node_evaluator is None:
            return False
        from parkour_solver.search.feasibility import meaningful_choice, node_identity
        meaningful=meaningful_choice(choice)
        child.setdefault('decisionDepth',parent.get('decisionDepth',0)+int(meaningful))
        child.setdefault('lastSolvedDepth',parent.get('lastSolvedDepth',0))
        child.setdefault('decisionParent',parent.get('decisionNode') if meaningful else parent.get('decisionParent'))
        child.setdefault('decisionNode',node_identity(child,policy_context) if meaningful else parent.get('decisionNode'))
        if meaningful and not complete(child):
            child['needsNodeSolve']=True
        return meaningful

    def queue_child(parent, choice, child):
        if link(parent, choice, child):
            state['decisionParents'][child['decisionNode']]=child['decisionParent']
        identity = program_search_key(child)
        seen.add(identity)
        state['seen'].append(identity)
        (state['ready'] if complete(child) else state['pending']).append(child)

    def target_keys(base, child):
        return {json.dumps(list(target)) for target in run_policy['targets'](base, child)}

    def minimum_runs(e):
        """Minimum run ticks for advantageous jumps at an ordinary decision (see run_policy).

        Level L holds the jumps after L same-block run ticks and the run-offs/step-ups after L-1 of
        them (a run-off is itself a run tick); level 0 is the expansion's own jump-now children. A
        run-off or step-up also carries the targets of the jumps right after it. For each target
        key, the first level with an accepted option queues those options and reserves the next
        level, restored once every queued option's branch died. Everything is computed before
        frontier state changes, so a deadline inside redoes the step from scratch.
        """
        from parkour_solver.search.feasibility import meaningful_choice
        program=e['program']
        search=program.get('minimumRunSearch')
        node=search['node'] if search else program.get('decisionNode')
        first=search['start'] if search else 1
        satisfied=set() if search else set(e['reached'])
        reserves=[] if search else [(target,1,nodes) for target,nodes in e['reached'].items()]
        effects,queued,queued_ids,levels=[],[],set(),[]

        def relevant(keys):
            return {target for target in keys if target not in satisfied and (search is None or target==search['target'])}

        def checked(proposal, context_data):
            child,reason,found=check_child(proposal,context_data,queued_ids)
            effects.append(found)
            return child,reason

        def onward(run, own):
            """Unscreened jump-now children of a run-off/step-up that carry keys it does not."""
            if complete(run) or not boundary_state(run)['onGround']:
                return []
            context_data=expansion_context(run,False)
            return [(keys,proposal,context_data) for kind in settings['types'] for proposal in proposals(kind,context_data)
                    if proposal['program'] is not None
                    for keys in [relevant(target_keys(run,proposal['program']))-own] if keys]

        bases,runs=[program],[]
        for level in range(MAX_ADVANTAGEOUS_RUN_TICKS+1):
            jumps,next_runs,steps=[],[],[]
            for base in bases:
                context_data=expansion_context(base)
                for kind in settings['types']:
                    for proposal in proposals(kind,context_data):
                        if proposal['program'] is None:
                            continue
                        if proposal['choice']['profile']['jump'][0]:
                            if level>=first:
                                jumps.append((base,context_data,proposal))
                        elif level<MAX_ADVANTAGEOUS_RUN_TICKS:
                            # A walk onto a flush next anchor is a run tick that is itself a target, not a base.
                            run=meaningful_choice(proposal['choice']) or target_keys(base,proposal['program'])
                            (next_runs if run else steps).append((base,context_data,proposal))
            if level>=first:
                found={}
                for base,context_data,proposal in jumps+runs:
                    own=relevant(target_keys(base,proposal['program']))
                    later=[] if proposal['choice']['profile']['jump'][0] else onward(proposal['program'],own)
                    if not own and not later:
                        continue
                    child,reason=checked(proposal,context_data)
                    if child is None:
                        continue
                    keys=set(own)
                    for extra,grand,grand_context in later:
                        if extra-keys and checked(grand,grand_context)[0] is not None:
                            keys|=extra
                    if not keys:
                        continue
                    link(base,proposal['choice'],child)
                    if reason is None:
                        queued.append((base,proposal['choice'],child))
                        queued_ids.add(program_search_key(child))
                    for target in keys:
                        found.setdefault(target,[]).append(child['decisionNode'])
                for target,nodes in found.items():
                    satisfied.add(target)
                    levels.append(dict(node=node,target=target,level=level,options=len(nodes)))
                    if level<MAX_ADVANTAGEOUS_RUN_TICKS:
                        reserves.append((target,level+1,nodes))
                if search and search['target'] in found:
                    break
            runs,bases,ids=next_runs,[],set()
            for base,context_data,proposal in steps:
                child,_=checked(proposal,context_data)
                if child is None or program_search_key(child) in ids:
                    continue
                ids.add(program_search_key(child))
                link(base,proposal['choice'],child)
                bases.append(child)
            if not bases and not runs:
                break
        for found in effects:
            commit(found)
        for parent,choice,child in queued:
            queue_child(parent,choice,child)
        state.setdefault('minimumRunLevels',[]).extend(levels)
        for target,start,nodes in reserves:
            reserve=digest([node,'minimumRunTicks',target,start])
            if reserve in state['alternatives']:
                continue
            held=copy.deepcopy(program)
            held.pop('needsNodeSolve',None)
            held.update(minimumRunSearch=dict(node=node,target=target,start=start),decisionNode=reserve,decisionParent=node)
            state['alternatives'][reserve]=dict(programs=[held],restored=[],next=0,watch=list(nodes))
            state['decisionParents'][reserve]=node

    def update(reason='running'):
        report.update(termination=reason, expandedPrefixes=expanded, totalExpandedPrefixes=state['expanded'],
            generated=state['generated'], emittedPrograms=state['emitted'], screenedPrefixes=state['screened'],
            remainingPrefixes=len(state['pending'])+len(state['ready'])+len(state.get('deferredNodes',[]))+int(state['expansion'] is not None)+int(state.get('nodeInFlight') is not None),
            deferredNodes=len(state.get('deferredNodes',[])),
            queuedCompletePrograms=len(state['ready']), queueDrops=0, partialQueueDrops=0, completeQueueDrops=0,
            policyRejections=state['policyRejections'], queuePolicy='persistent', types=settings['types'],
            explicitProgramPriority=False, deferredPrefixes=max(0, len(state['pending'])-settings['maxQueuedPrefixes']),
            continuation=state, minimumTicksProven=False)

    try:
        while (state['pending'] or state['ready'] or state['expansion'] is not None or state.get('nodeInFlight') is not None
               or restore()):
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
                if state.get('nodeInFlight') is None:
                    restore()
                program = state.get('nodeInFlight') or take(state['pending'])
                if program_filter is not None and not program_filter(program):
                    state['policyRejections'] += 1
                    state.pop('nodeInFlight',None)
                    continue
                if complete(program):
                    state['ready'].append(program)
                    continue
                if program['profile']['airtime'] >= settings['maxInputs'] or program['interactions'] >= settings['maxInteractions']:
                    state.pop('nodeInFlight',None)
                    continue
                if node_evaluator is not None and program.get('needsNodeSolve'):
                    # Query a decision when it is selected for descent. Merely
                    # constructing an unselected sibling must not spend NLPs.
                    state['nodeInFlight']=program
                    update()
                    result=node_evaluator(program,state['nodes'],False)
                    state['nodeInFlight']=program=result['program']
                    if result.get('deferUnknown'):
                        state['deferredNodes'].append(program)
                        state.pop('nodeInFlight',None)
                        update()
                        continue
                    if result['status'] in ('suspended','error'):
                        update(result['status'])
                        return
                    state.pop('nodeInFlight',None)
                    if result['status'] in ('oracle_rejected','exhausted'):
                        update()
                        continue
                    # Other branch choices at this node wait until this branch dies (restore_alternatives).
                    held=result.get('alternatives',[])
                    if held and program['decisionNode'] not in state['alternatives']:
                        state['alternatives'][program['decisionNode']]=dict(programs=held,restored=[],next=0)
                        for p in held:
                            state['decisionParents'][p['decisionNode']]=p.get('decisionParent')
                    program.pop('needsNodeSolve',None)
                search = program.get('minimumRunSearch')
                e = dict(program=program, kind=len(settings['types']) if search else 0, cursor=0)
                if search is None and run_policy is not None and run_policy['ordinary'](program):
                    # Ordinary decision: the types yield jump-now children; minimum_runs adds run ticks.
                    e['reached'] = {}
                state['expansion'] = e
                expanded += 1
                state['expanded'] += 1
            if e['kind'] == len(settings['types']):
                if 'reached' in e or e['program'].get('minimumRunSearch'):
                    minimum_runs(e)
                state['expansion'] = None
                iterator, iterator_key = None, None
                continue
            ident = (digest(program_physics_key(e['program'])), e['kind'])
            if iterator_key != ident:
                context_data = expansion_context(e['program'], 'reached' not in e)
                iterator = proposals(settings['types'][e['kind']], context_data, e['cursor'])
                iterator_key = ident
            if 'choice' not in e:
                try:
                    proposal = next(iterator)
                except StopIteration:
                    e.update(kind=e['kind']+1, cursor=0)
                    iterator, iterator_key = None, None
                    continue
                e['choice'] = proposal['choice']
                e['child'] = proposal['program']
                e['fullProgram'] = proposal['fullProgram']
                e['projected'] = proposal['projected']
                e['cursor'] = proposal['ordinal']+1
                state['generated'] += 1
            child=e['child']
            if child is not None and screen is not None and not e.get('proposalChecked'):
                # Commit the raw ordinal/owned view before the budgeted query.
                update()
                result=assess_proposal(dict(program=child,fullProgram=e['fullProgram'],projected=e['projected']),screen,
                                       context_data.get('interactionGate'))
                e['proposalChecked']=True
                commit(proposal_effects(result,child,e['fullProgram']))
                if result['proof'] is not None:
                    for field in ('choice','child','fullProgram','projected','proposalChecked'):e.pop(field,None)
                    update()
                    continue
            effects=no_effects()
            child,reason=accept(child,context_data,effects)
            commit(effects)
            if reason is None:
                queue_child(e['program'],e['choice'],child)
                if 'reached' in e:
                    for target in target_keys(e['program'],child):
                        e['reached'].setdefault(target,[]).append(child['decisionNode'])
            for field in ('choice','child','fullProgram','projected','proposalChecked'):e.pop(field,None)
            update()
        state['exhausted'] = True
        update('numerical_unknown' if state.get('deferredNodes') else 'exhausted')
    except TimeoutError as error:
        update(work.termination if work is not None and work.termination else str(error))
    finally:
        if not state['exhausted'] and report.get('termination') == 'running':
            update('consumer_closed')
