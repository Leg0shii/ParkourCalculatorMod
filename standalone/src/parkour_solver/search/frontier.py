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
        """Minimum run ticks for advantageous jumps at an ordinary decision P (see run_policy).

        N is P's fastest jump-now landing (on the next anchor first, else on any other block). Candidates
        come from run_policy['candidates'] (tables and vertical replays, nothing built):
        - k same-block run ticks then an advantageous jump: built when k + airtime < N, cheapest first, unless
          a kept one beats it on both total and airtime. Each kept one reserves its next run count; beaten
          ones wait for their beaters; when N's block dies, N widens to the next landing.
        - a run-off onto another block B, whose walk-off input is a run tick: the fewest run ticks (1 to 11)
          landing before a jump onto B would (run ticks + airborne inputs < the entry's limit, which may
          exceed N), only when a jump-now proposal lands on B.
        - a walking step-up onto another block, whose step input is a run tick: on the first tick, else after
          one run tick (more run-up is a slab boost).
        Run-offs and step-ups take no part in beating and hold no reserve. Without N, run counts are scanned
        until a block is reached.
        Everything is computed before frontier state changes, so a deadline inside redoes the step.
        """
        from parkour_solver.search.feasibility import meaningful_choice
        program=e['program']
        search=program.get('minimumRunSearch')
        node=search['node'] if search else program.get('decisionNode')
        upcoming,here,owner=run_policy['next'](program),run_policy['owner'](program['terminalSupport']),run_policy['owner']
        effects,queued,queued_ids,log,reserves=[],[],set(),[],[]
        levels,bases,checks,keys=[],[[program]],{},{}

        def level(k):
            """Unscreened proposals of every type after k same-block run ticks (bases are never queued)."""
            while len(levels)<=k:
                if len(levels)==len(bases):
                    ids,found=set(),[]
                    for item in levels[-1]:
                        base,_,proposal=item
                        child=proposal['program']
                        if (proposal['choice']['profile']['jump'][0] or meaningful_choice(proposal['choice'])
                                or lands(child)==upcoming):
                            continue
                        child,_=check(item)
                        if child is not None and program_search_key(child) not in ids:
                            ids.add(program_search_key(child))
                            found.append(child)
                    bases.append(found)
                found=[]
                for base in bases[len(levels)]:
                    context_data=expansion_context(base)
                    found.extend((base,context_data,proposal) for kind in settings['types']
                                 for proposal in proposals(kind,context_data) if proposal['program'] is not None)
                levels.append(found)
            return levels[k]

        def check(item):
            base,context_data,proposal=item
            if id(proposal) not in checks:
                child,reason,found=check_child(proposal,context_data,queued_ids)
                effects.append(found)
                if child is not None:
                    link(base,proposal['choice'],child)
                checks[id(proposal)]=(child,reason)
            return checks[id(proposal)]

        def lands(child):
            return owner(child['terminalSupport']) if child['profile']['grounded'][-1] and child.get('terminalSupport') else None

        def ticks(child):
            return child['profile']['airtime']-program['profile']['airtime']

        def enqueue(item, child, reason, landed):
            if reason is None and program_search_key(child) not in queued_ids:
                queued.append((item[0],item[2]['choice'],child))
                queued_ids.add(program_search_key(child))
            if lands(child) is not None:
                landed.append(dict(owner=lands(child),node=child['decisionNode']))

        def realize(entry, k):
            """(item, child, reason) of the proposals after k run ticks that make the entry's move and pass the screens."""
            found=[]
            for item in level(k):
                base,_,proposal=item
                child,jump=proposal['program'],proposal['choice']['profile']['jump'][0]
                if entry['via'] is None:
                    if jump and id(proposal) not in keys:
                        keys[id(proposal)]=target_keys(base,child)
                    wanted=jump and entry['key'] in keys[id(proposal)]
                else:
                    wanted=(not jump and lands(child)==entry['via'] and
                            (entry['via']==upcoming or meaningful_choice(proposal['choice'])))
                if wanted:
                    child,reason=check(item)
                    if child is not None:
                        found.append((item,child,reason))
            return found

        def runs(entry, k):
            """Run ticks of an entry built after k run-tick choices: a walk-off or step input is one more."""
            return k+1 if json.loads(entry['key'])[0] in ('landing','step') else k

        def record(entry, k, total, N, lower, number, outcome, nodes=()):
            log.append(dict(node=node,key=entry['key'],k=k,total=total,A=entry['A'],N=N,lower=lower,round=number,
                            outcome=outcome,nodes=list(nodes)))

        def reserve(watch, **fields):
            reserves.append((dict(node=node,**fields),list(watch)))

        def source(landings, dead):
            """(N, owner) of the fastest landing on the next anchor, else on a live block; None without one."""
            for allowed in (lambda o:o==upcoming,lambda o:o not in dead):
                times=[(l['ticks'],l['owner']) for l in landings if allowed(l['owner'])]
                if times:
                    return min(times)
            return None

        def take_entry(entry, start, N, number, landed):
            """The first run count from `start` at which the entry's move passes the screens (next/dominated reserves)."""
            for k in range(start,MAX_ADVANTAGEOUS_RUN_TICKS+1):
                total=k+entry['A']
                if total>=N:
                    break
                found=realize(entry,k)
                if found:
                    nodes=[child['decisionNode'] for _,child,_ in found]
                    for item,child,reason in found:
                        enqueue(item,child,reason,landed)
                    record(entry,k,total,N,0,number,'kept',nodes)
                    if k+1<=MAX_ADVANTAGEOUS_RUN_TICKS and k+1+entry['A']<N:
                        reserve(nodes,kind='next',entry=entry,start=k+1,N=N,round=number)
                    return
            record(entry,None,None,N,0,number,'unreached')

        def jumped():
            """Owners a jump-now proposal lands on: a run-off reaches only blocks close enough to jump onto."""
            return {lands(proposal['program']) for _,_,proposal in level(0) if proposal['choice']['profile']['jump'][0]}

        def round_(dead, lower, N, kept, number, landed, landings, widen=True):
            pairs,reachable=[],None
            for entry in run_policy['candidates'](program):
                kind=json.loads(entry['key'])[0]
                if entry['via'] is None:
                    counts=[k for k in range(1,MAX_ADVANTAGEOUS_RUN_TICKS+1) if lower<=k+entry['A']<N]
                elif lower:
                    # Run-offs and step-ups are offered once per decision, in its first round.
                    continue
                elif kind=='step':
                    counts=[0,1]
                else:
                    # The walk-off input is a run tick too: k run ticks before it, at most 11 in all.
                    reachable=jumped() if reachable is None else reachable
                    counts=[k for k in range(MAX_ADVANTAGEOUS_RUN_TICKS) if k+1+entry['A']<entry['limit']] if entry['via'] in reachable else []
                pairs.extend((runs(entry,k)+entry['A'],entry['A'],entry['key'],k,entry) for k in counts)
            pairs.sort(key=lambda pair:pair[:4])
            resolved,beaten={},{}
            for total,A,key,k,entry in pairs:
                if key in resolved:
                    continue
                # A run-off or step-up is an earlier landing, not an advantageous move: no beating either way.
                beaters=[m for m in kept if entry['via'] is None and m['total']<=total and m['A']<=A and (m['total'],m['A'])!=(total,A)]
                if beaters:
                    resolved[key]=True
                    record(entry,runs(entry,k),total,N,lower,number,'dominated')
                    beaten.setdefault(tuple(sorted({n for m in beaters for n in m['nodes']})),[]).append(dict(entry=entry,start=k))
                    continue
                found=realize(entry,k)
                if not found:
                    continue
                resolved[key]=True
                nodes=[child['decisionNode'] for _,child,_ in found]
                for item,child,reason in found:
                    enqueue(item,child,reason,landed)
                record(entry,runs(entry,k),total,entry.get('limit',None if entry['via'] else N),lower,number,'kept',nodes)
                if entry['via'] is None:
                    kept.append(dict(key=key,total=total,A=A,nodes=nodes))
                    if k+1<=MAX_ADVANTAGEOUS_RUN_TICKS and k+1+A<N:
                        reserve(nodes,kind='next',entry=entry,start=k+1,N=N,round=number)
            for total,A,key,k,entry in pairs:
                if key not in resolved:
                    resolved[key]=True
                    record(entry,None,None,entry.get('limit',None if entry['via'] else N),lower,number,'unreached')
            for watch,entries in beaten.items():
                # Candidates beaten by the same kept ones are restored together, once all of those died.
                reserve(watch,kind='dominated',entries=entries,N=N,round=number)
            blocked=widen and source(landings,dead)[1]
            if widen and blocked!=upcoming:
                watch=[l['node'] for l in landings+landed if l['owner']==blocked]
                reserve(watch,kind='widen',dead=dead,landings=landings,lower=N,kept=kept,landed=landed,round=number+1)

        def scan(dead, start, lower, kept, number, landed, landings):
            """No N: the first run count whose screened jumps or run-offs reach the next anchor, else another block."""
            def landers(k, wanted):
                found=[]
                for item in level(k):
                    base,_,proposal=item
                    child,jump=proposal['program'],proposal['choice']['profile']['jump'][0]
                    target=lands(child)
                    # A no-jump lander walks off on its own run tick: k+1 run ticks, at most 11.
                    if ((jump and k==0) or target is None or not wanted(target) or
                            not jump and (k==MAX_ADVANTAGEOUS_RUN_TICKS or
                                          target!=upcoming and not meaningful_choice(proposal['choice']))):
                        continue
                    child,reason=check(item)
                    if child is not None:
                        found.append((item,child,reason))
                return found
            for target,wanted in (('next',lambda o:o==upcoming),('block',lambda o:o not in dead and o!=here and o!=upcoming)):
                for k in range(start,MAX_ADVANTAGEOUS_RUN_TICKS+1):
                    found=landers(k,wanted)
                    if found:
                        break
                else:
                    continue
                break
            else:
                record(dict(key=json.dumps(['scan']),A=None),None,None,None,lower,number,'unreached')
                return
            nodes=[child['decisionNode'] for _,child,_ in found]
            for item,child,reason in found:
                enqueue(item,child,reason,landed)
            N=min(ticks(child) for _,child,_ in found)
            kept.extend(dict(key=json.dumps(['scan',lands(child)]),total=ticks(child),A=ticks(child)-k,
                             nodes=[child['decisionNode']]) for _,child,_ in found)
            record(dict(key=json.dumps(['scan',target]),A=N-k),k,N,N,lower,number,'kept',nodes)
            if k+1<=MAX_ADVANTAGEOUS_RUN_TICKS:
                reached=sorted({lands(child) for _,child,_ in found})
                reserve(nodes,kind='scan',target=target,dead=dead if target=='next' else sorted(set(dead)|set(reached)),
                        start=k+1,lower=N,kept=kept,landed=landed,round=number+1)
            round_(dead,lower,N,kept,number,landed,landings,widen=False)

        kind=search['kind'] if search else None
        if kind=='next':
            take_entry(search['entry'],search['start'],search['N'],search['round'],[])
        elif kind=='dominated':
            for held in search['entries']:
                take_entry(held['entry'],held['start'],search['N'],search['round'],[])
        elif kind=='scan':
            scan(search['dead'],search['start'],search['lower'],search['kept'],search['round'],search['landed'],[])
        else:
            if kind=='widen':
                landings,lower,kept,landed,number=search['landings'],search['lower'],search['kept'],search['landed'],search['round']
                dead=sorted(set(search['dead'])|{source(landings,search['dead'])[1]})
            else:
                landings,lower,landed,number,dead=e['landings'],0,[],0,[]
                entries={entry['key']:entry for entry in run_policy['candidates'](program) if entry['via'] is None}
                arrival=min((l['ticks'] for l in landings if l['owner']==upcoming),default=None)
                kept=[dict(key=key,total=A,A=A,nodes=nodes) for key,nodes in e['reached'].items() if key in entries
                      for A in [arrival if key==json.dumps(['anchor',upcoming]) else entries[key]['A']]]
            found=source(landings,dead)
            if found is None:
                scan(dead,0,lower,kept,number,landed,landings)
            else:
                round_(dead,lower,found[0],kept,number,landed,landings)
        for found in effects:
            commit(found)
        for parent,choice,child in queued:
            queue_child(parent,choice,child)
        state.setdefault('minimumRunCandidates',[]).extend(log)
        for fields,watch in reserves:
            key=digest([node,'minimumRunTicks',fields])
            if key in state['alternatives']:
                continue
            held=copy.deepcopy(program)
            for field in ('needsNodeSolve','minimumRunSearch'):
                held.pop(field,None)
            held.update(minimumRunSearch=fields,decisionNode=key,decisionParent=node)
            state['alternatives'][key]=dict(programs=[held],restored=[],next=0,watch=watch)
            state['decisionParents'][key]=node

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
                    e.update(reached={}, landings=[])
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
                    landed=run_policy['owner'](child['terminalSupport']) if child['profile']['grounded'][-1] and child.get('terminalSupport') else None
                    if landed not in (None,run_policy['owner'](e['program']['terminalSupport'])):
                        # N for minimum_runs: this decision's jump-now landings on other blocks.
                        e['landings'].append(dict(ticks=child['profile']['airtime']-e['program']['profile']['airtime'],
                                                  owner=landed,node=child['decisionNode']))
            for field in ('choice','child','fullProgram','projected','proposalChecked'):e.pop(field,None)
            update()
        state['exhausted'] = True
        update('numerical_unknown' if state.get('deferredNodes') else 'exhausted')
    except TimeoutError as error:
        update(work.termination if work is not None and work.termination else str(error))
    finally:
        if not state['exhausted'] and report.get('termination') == 'running':
            update('consumer_closed')
