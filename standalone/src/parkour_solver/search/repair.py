from parkour_solver.validation.vertical import validate_vertical_schedule
from parkour_solver.constraints.contacts import contact_conflicts, avoid_contact_rows
import copy
from collections import Counter
import heapq
import json
import time

from parkour_solver.search.ranking import candidate_rank
from parkour_solver.search.ordered_route import find_route
from parkour_solver.constraints.avoidance import blockage_usage, gate_timeline, passage_y_valid
from parkour_solver.validation.scene import check_geometry
from parkour_solver.constraints.hit_events import wall_hit_options
from parkour_solver.search.repair_state import branch_key, compatible, context as repair_context

# Close losers are kept only to recover when the route fails later; objectives are positions in blocks.
FALLBACK_LIMIT = 2
FALLBACK_GAP = 0.3
# The confirmation walk stops after this many examined candidates once one is confirmed.
WALK_LIMIT = 3


def usable(accepted):
    """Accepted variants a walk has not rejected, each with its confirmed point when it has one."""
    return [dict(v, best=v['confirmation']) if isinstance(v.get('confirmation'), dict) else v
            for v in accepted if v.get('confirmation') != 'rejected']


def node_alternatives(accepted, incumbent, objective):
    """A decision check's held alternatives, best first: each branch choice's best accepted point plus
    up to FALLBACK_LIMIT close switch timings of it, without the incumbent.

    The branch choice (closed stretches) is what the node decides; open stretches are re-decided later.
    """
    chosen = branch_key(incumbent['rows'], incumbent['passages'], incumbent['hits']) if incumbent else None
    classes = {}
    for variant in usable(accepted):
        classes.setdefault(variant['best'].get('alternativeBranchKey'), []).append(
            (candidate_rank(variant['best'], objective), variant))
    kept = []
    for members in classes.values():
        members.sort(key=lambda entry: entry[0])
        top = members[0][0]
        kept += members[:1]+[entry for entry in members[1:]
                             if entry[0][0] == top[0] and entry[0][1]-top[1] <= FALLBACK_GAP][:FALLBACK_LIMIT]
    kept.sort(key=lambda entry: entry[0])
    return [copy.deepcopy(variant) for _, variant in kept if variant['branch'] != chosen]


def alarmed(result):
    """The alarm ticks of a branch result's relaxed point (numerics/disk.py alarm_ticks); an IPOPT point has none."""
    if result['best']['candidate'].get('model') != 'relaxed_disk':
        return []
    for attempt in reversed(result['attempts']):
        for d in attempt.get('diagnostics', []):
            if d.get('problemHash') == result['best'].get('problemHash') and d.get('status') == 'feasible':
                return list((d.get('disk') or {}).get('alarmTicks', []))
    return []


def branch_meta(variant):
    """The intermediate choice a decision check that accepts this variant makes for its search branch."""
    return dict(branchChoices=variant['branchChoices'], branchKey=variant['branchKey'])


def solve_contact_branches(route, profiles, checks, compiler, *, seconds, candidate_seconds, retries,
                           improve=False, rank_equal_cost=False, optimize_goal=False,
                           python_executable=None, cancel_event=None, geometry=None, motion_bound=None,
                           solid_blocks=None, max_wall_hits=0,
                           max_wall_candidates=24, candidate_filter=None, work=None, resume_state=None, repair_scope=None,
                           on_progress=None, validation_scope=None, initial_variants=None, retry_unresolved=True,
                           stop_on_feasible=None, oracle_rejection=False, climb_families=False, numerical=None,
                           confirm=None):
    """`confirm`: with `rank_equal_cost`, the numerical callable that confirms an alarmed relaxed point
    (RelaxedDispatch.confirmed); `numerical` solves relaxed only and the walk at the end confirms."""
    route = copy.deepcopy(route)
    if solid_blocks is not None:
        for passage in route.get('passages', []):
            passage['blockId'] = solid_blocks[passage['solidIndex']]
    deadline = time.monotonic() + seconds
    queue, seen, serial, unresolved = [], set(), 0, []
    variant_source = iter(initial_variants) if initial_variants is not None else None
    source_exhausted = variant_source is None or getattr(variant_source, 'state', {}).get('exhausted', False)
    repair_sources = []
    source_instances = {}
    completed_source_metrics = Counter()
    choices = {}
    choice_alternatives = {}
    climbing = climb_families and rank_equal_cost
    families = {}
    settling = None
    policy = dict(stopOnFeasible=stop_on_feasible, optimizeGoal=optimize_goal,
                  rankEqualCost=rank_equal_cost, oracleRejection=oracle_rejection)
    current_context = repair_context(route, profiles, geometry, dict(scope=repair_scope, solvePolicy=policy, avoidancePolicy=14))
    resuming = (isinstance(resume_state, dict) and resume_state.get('version') == 3
                and compatible(resume_state.get('context'), current_context))
    exact_resume = resuming and resume_state['context'] == current_context
    if exact_resume:
        completed_source_metrics.update(resume_state.get('sourcesCompletedMetrics', {}))
        repair_sources.extend(copy.deepcopy(resume_state.get('repairSources', [])))
        seen.update(resume_state.get('seen', []))
    resumed_branches = 0
    # Branches the current conflict-driven generator emitted; superseded when its obstacle set grows.
    generated = set(resume_state.get('generatedBranches', [])) if exact_resume else set()
    reference_yaw = copy.deepcopy(resume_state.get('referenceWarmYaw')) if resuming else None
    hit_pool = wall_hit_options(route, profiles, geometry, solid_blocks, motion_bound, max_wall_candidates) \
        if geometry is not None and max_wall_hits else {'options': [], 'generated': 0, 'candidateDrops': 0}

    def point(meta):
        family = families.get(meta['family']) if climbing and meta is not None else None
        return family['points'].get(json.dumps(meta['switches'])) if family is not None else None

    def offer(score, rows, passages, hits, warm_yaw=None, selected=None, meta=None):
        nonlocal serial
        key = branch_key(rows, passages, hits)
        if selected is not None:
            choices.setdefault(key, copy.deepcopy(selected))
            alternatives = choice_alternatives.setdefault(key, [])
            if selected not in alternatives:
                alternatives.append(copy.deepcopy(selected))
        if key in seen:
            return
        seen.add(key)
        serial += 1
        heapq.heappush(queue, (score, serial, copy.deepcopy(rows), copy.deepcopy(passages), copy.deepcopy(hits),
                              copy.deepcopy(warm_yaw), copy.deepcopy(meta)))
        member = point(meta)
        if member is not None:
            member.update(status='visited', pending=member['pending']+1)

    def exhausted(family):
        if family['source'] is None:
            return source_exhausted
        return not any(branch_key(r['rows'], r['passages'], r['hits']) == family['source'] for r in repair_sources)

    def visit(member):
        held, member['held'] = member['held'], []
        member['status'] = 'visited'
        for b in held:
            offer(b['score'], b['rows'], b['passages'], b['hits'], b['warmYaw'], b['selected'], b['meta'])
        if not member['pending']:
            member['status'] = 'settled'

    def advance(name):
        # The objective is unimodal in each switch tick: climb from the best point to its held +-1 neighbours.
        family = families[name]
        points = family['points']
        while True:
            family['done'] = False
            if any(p['pending'] for p in points.values()):
                return
            ranked = [p for p in points.values() if p['rank'] is not None]
            if not ranked:
                target = next((points[k] for k in family['order'] if points[k]['status'] == 'held'), None)
                if target is None:
                    family['done'] = exhausted(family)
                    return
                visit(target)
                continue
            best = min(ranked, key=lambda p: p['rank'])
            neighbours = [json.dumps(best['switches'][:i]+[t+step]+best['switches'][i+1:])
                          for i, t in enumerate(best['switches']) for step in (-1, 1)]
            held = [points[k] for k in neighbours if k in points and points[k]['status'] == 'held']
            if not held:
                family['done'] = all(k in points for k in neighbours) or exhausted(family)
                return
            for member in held:
                visit(member)

    def advance_all():
        for name in list(families):
            advance(name)

    def arrive(score, rows, passages, hits, warm_yaw, selected, meta, source=None):
        if not climbing or meta is None:
            offer(score, rows, passages, hits, warm_yaw, selected, meta)
            return
        family = families.get(meta['family'])
        if family is None:
            family = families[meta['family']] = dict(points={}, order=[], source=source, done=False)
        name = json.dumps(meta['switches'])
        member = family['points'].get(name)
        if member is None:
            member = family['points'][name] = dict(switches=meta['switches'], status='held', rank=None, objective=None,
                                                   held=[], pending=0, keys=[])
            family['order'].append(name)
        member['keys'].append(branch_key(rows, passages, hits))
        first = len(family['points']) == 1 and not member['held']
        if first or member['status'] != 'held':
            offer(score, rows, passages, hits, warm_yaw, selected, meta)
            return
        member['held'].append(dict(save_branch(score, rows, passages, hits, warm_yaw, meta), selected=copy.deepcopy(selected)))
        advance(meta['family'])

    def settle(done):
        member = point(done['meta'])
        if member is None:
            return
        member['pending'] -= 1
        if done['rank'] is not None and (member['rank'] is None or done['rank'] < member['rank']):
            member.update(rank=done['rank'], objective=done['objective'])
        if not member['pending']:
            member['status'] = 'settled'
            advance(done['meta']['family'])

    if resuming:
        choices.update(copy.deepcopy(resume_state.get('interactionChoices', {})))
        choice_alternatives.update(copy.deepcopy(resume_state.get('interactionAlternatives', {})))
        if climbing:
            families.update(copy.deepcopy(resume_state.get('families', {})))
            for family in families.values():
                for member in family['points'].values():
                    member['pending'] = 0
        pending = resume_state.get('pending', [])
        deferred = (resume_state.get('unresolved', []) if retry_unresolved else [])+([resume_state['inFlight']] if resume_state.get('inFlight') else [])
        if not retry_unresolved:
            unresolved.extend(copy.deepcopy(resume_state.get('unresolved', [])))
        floor = max([b['score'] for b in pending]+[0.])+1
        # An accepted branch left in flight is an unfinished walk's candidate: keep its result, re-walk.
        restored = {v['branch'] for v in resume_state.get('acceptedVariants', [])} if exact_resume else set()
        for index, saved in enumerate(pending+deferred):
            key = branch_key(saved['rows'], saved['passages'], saved['hits'])
            if key in restored:
                continue
            restored.add(key)
            seen.discard(key)
            offer(saved['score'] if index < len(pending) else floor+saved['score'],
                  saved['rows'], saved['passages'], saved['hits'], saved.get('warmYaw'), meta=saved.get('meta'))
        resumed_branches = len(queue)
        advance_all()
    if not exact_resume:
        if initial_variants is None:
            offer(min([b[0] for b in queue]+[0.])-1, [], route.get('passages', []), route.get('hitEvents', []), route.get('warmYaw'))

    def save_branch(score, rows, passages, hits, warm_yaw, meta=None):
        return copy.deepcopy(dict(score=score, rows=rows, passages=passages, hits=hits, warmYaw=warm_yaw,
                                  **(dict(meta=meta) if meta is not None else {})))

    def fallbacks():
        if best_result is None or best_branch is None:
            return []
        top = candidate_rank(best_result['best'], route['objective'])
        chosen = branch_key(best_branch['rows'], best_branch['passages'], best_branch['hits'])
        close = sorted(((candidate_rank(v['best'], route['objective']), v) for v in usable(accepted_variants)
                        if v['branch'] != chosen), key=lambda entry: entry[0])
        return [copy.deepcopy(v) for rank, v in close
                if rank[0] == top[0] and rank[1]-top[1] <= FALLBACK_GAP][:FALLBACK_LIMIT]

    def snapshot():
        saved_best = ({k: copy.deepcopy(v) for k, v in best_result.items()
                      if k not in ('repairState', 'repairResume', 'attempts', 'contactAttempts')}
                      if best_result is not None else None)
        return dict(version=3, context=current_context, repairSources=copy.deepcopy(repair_sources),
            sourcesCompletedMetrics=dict(completed_source_metrics),
            pending=[save_branch(score, rows, passages, hits, yaw, meta)
                     for score, _, rows, passages, hits, yaw, meta in sorted(queue)],
            unresolved=copy.deepcopy(unresolved), seen=sorted(seen), incumbent=copy.deepcopy(best_branch),
            acceptedVariants=copy.deepcopy(accepted_variants), families=copy.deepcopy(families),
            **(dict(fallbackVariants=fallbacks()) if rank_equal_cost else {}),
            acceptedResult=saved_best, inFlight=copy.deepcopy(in_flight), completed=copy.deepcopy(completed),
            referenceWarmYaw=copy.deepcopy(reference_yaw), interactionChoices=copy.deepcopy(choices),
            interactionAlternatives=copy.deepcopy(choice_alternatives),
            constraintGeneration=copy.deepcopy(getattr(variant_source, 'state', None)),
            **(dict(generatedBranches=sorted(generated)) if getattr(variant_source, 'conflict_driven', False) else {}),
            attemptLog=copy.deepcopy((resume_state.get('attemptLog', []) if exact_resume else [])+inner_attempts),
            contactLog=copy.deepcopy((resume_state.get('contactLog', []) if exact_resume else [])+attempts))

    def publish():
        if on_progress is not None:
            on_progress(snapshot())

    def finish(result):
        nonlocal in_flight
        # An unfinished walk's next candidate stays in flight: the check is unfinished and a resume re-walks.
        in_flight = walking
        result['repairState'] = snapshot()
        metrics = completed_source_metrics.copy()
        metrics.update(getattr(variant_source, 'state', {}).get('metrics', {}))
        for record in repair_sources:
            metrics.update(record.get('state', {}).get('metrics', {}))
        result['avoidanceMetrics'] = dict(metrics, generationIncomplete=bool(repair_sources or not source_exhausted),
            heldUnsolved=sum(len(p['held']) for f in families.values() for p in f['points'].values()))
        result['repairResume'] = dict(requested=resume_state is not None, compatible=resuming,
                                     exact=exact_resume, resumedBranches=resumed_branches)
        publish()
        return result
    attempts, inner_attempts = [], []
    best_result = copy.deepcopy(resume_state.get('acceptedResult')) if exact_resume else None
    best_branch = copy.deepcopy(resume_state.get('incumbent')) if exact_resume else None
    completed = copy.deepcopy(resume_state.get('completed', [])) if exact_resume else []
    accepted_variants = copy.deepcopy(resume_state.get('acceptedVariants', [])) if exact_resume else []
    in_flight = walking = None
    if best_result is not None:
        saved = best_result['best']
        valid = validate_vertical_schedule(route, profiles, saved['plan'])
        verdict = candidate_filter(saved['plan'], saved['candidate'], valid) if valid['feasible'] and candidate_filter else dict(accepted=valid['feasible'])
        accepted = valid['feasible'] and verdict['accepted']
        if accepted and geometry is not None:
            from parkour_solver.search.identity import scoped_geometry, validation_prefix
            if validation_scope is None:
                accepted = check_geometry(geometry, saved['plan'], saved['candidate'], valid, compiler, seconds)['feasible']
            else:
                selected, _ = scoped_geometry(geometry, validation_scope)
                p, c, v = validation_prefix(saved['plan'], saved['candidate'], valid, validation_scope['through'])
                accepted = check_geometry(selected, p, c, v, compiler, seconds)['feasible']
        if not accepted:
            best_result, best_branch = None, None
            key = branch_key([], route.get('passages', []), route.get('hitEvents', []))
            seen.discard(key)
            offer(-1, [], route.get('passages', []), route.get('hitEvents', []), route.get('warmYaw'))

    def build_branch(rows, passages, hits, warm_yaw):
        branch = copy.deepcopy(route)
        branch['constraints'].extend(rows)
        branch['passages'] = passages
        branch['hitEvents'] = hits
        if warm_yaw is not None:
            branch['warmYaw'] = warm_yaw
        return branch

    def audit_point(best, branch, passages):
        plan, candidate = best['plan'], best['candidate']
        checked = dict(conflicts=contact_conflicts(checks, plan, candidate), accepted=False)
        if not checked['conflicts']:
            vertical = validate_vertical_schedule(branch, profiles, plan)
            checked['vertical'] = vertical
            if not vertical['feasible']:
                checked['reason'] = 'vertical_schedule_mismatch'
            elif any(not passage_y_valid(p, plan['events'][p['leg']], profiles[p['leg']]) for p in passages):
                checked['reason'] = 'passage_y_mismatch'
            else:
                verdict = candidate_filter(plan, candidate, vertical) if candidate_filter is not None else dict(accepted=True)
                checked['verdict'] = verdict
                if not verdict['accepted']:
                    checked['reason'] = verdict.get('reason', 'candidate_filter')
                elif geometry is not None:
                    remaining = deadline-time.monotonic()
                    if remaining <= 0:
                        raise TimeoutError('Geometry validation exceeded the contact budget')
                    if validation_scope is None:
                        checked['geometryCheck'] = check_geometry(geometry, plan, candidate, vertical, compiler, remaining)
                    else:
                        from parkour_solver.search.identity import scoped_geometry, validation_prefix
                        selected, indices = scoped_geometry(geometry, validation_scope)
                        p, c, v = validation_prefix(plan, candidate, vertical, validation_scope['through'])
                        checked['geometryCheck'] = check_geometry(selected, p, c, v, compiler, remaining)
                        if 'solidIndex' in checked['geometryCheck']:
                            checked['geometryCheck']['solidIndex'] = indices[checked['geometryCheck']['solidIndex']]
                    checked['accepted'] = checked['geometryCheck']['feasible']
                else:
                    checked['accepted'] = True
        return checked

    def enrich_point(best, checked, branch, rows, passages, hits, meta):
        best = copy.deepcopy(best)
        selected = copy.deepcopy(choices.get(branch_key(rows, passages, hits), []))
        described = passages+[p for p in selected if p not in passages]
        best['alternativeRows'] = copy.deepcopy(rows)
        if meta is not None and 'branchChoices' in meta:
            best.update(alternativeBranchChoices=copy.deepcopy(meta['branchChoices']),
                        alternativeBranchKey=meta['branchKey'])
        best.update(route=best.get('route', branch), profiles=profiles, contactChecks=checks,
            passages=described, hitEvents=hits, ceilingEvents=branch.get('ceilingEvents', []),
            blockageTimeline=gate_timeline(described, best['plan']['events']),
            blockageUsage=[{'blockId': value if kind == 'block' else 'solid:' + str(value), **counts}
                for (kind, value), counts in blockage_usage(described).items()], vertical=checked['vertical']['vertical'])
        best['selectedInteractions'] = copy.deepcopy(choices.get(branch_key(rows, passages, hits), passages))
        best['selectedInteractionAlternatives'] = copy.deepcopy(choice_alternatives.get(
            branch_key(rows, passages, hits), [best['selectedInteractions']]))
        best['candidate'].update({key: checked['vertical'][key] for key in ('y', 'vy', 'onGround')})
        if candidate_filter is not None:
            best['corridorCheck'] = checked['verdict']
        if 'geometryCheck' in checked:
            best['geometryCheck'] = checked['geometryCheck']
        return best

    def walk_order():
        """Each family's best relaxed candidate and a close second (same ticks, within FALLBACK_GAP), best first."""
        groups = {}
        for variant in accepted_variants:
            groups.setdefault(variant.get('family') or variant['branch'], []).append(
                (candidate_rank(variant['best'], route['objective']), variant))
        kept = []
        for members in groups.values():
            members.sort(key=lambda entry: entry[0])
            top = members[0][0]
            kept += members[:1]+[entry for entry in members[1:2]
                                 if entry[0][0] == top[0] and entry[0][1]-top[1] <= FALLBACK_GAP]
        kept.sort(key=lambda entry: entry[0])
        return [variant for _, variant in kept]

    def examine(variant):
        """One IPOPT solve of an alarmed candidate's own branch; its point must pass the same audit.

        Returns the confirmed point, 'rejected' (IPOPT infeasible, timed out or unknown, or the point
        failed the audit), or None when there was no budget to run it."""
        saved = variant['state']
        if deadline-time.monotonic() <= 0 or cancel_event is not None and cancel_event.is_set():
            return None
        if work is not None:
            try:
                work.check()
            except TimeoutError:
                return None
        branch = build_branch(saved['rows'], saved['passages'], saved['hits'], saved.get('warmYaw'))
        result = find_route(branch, compiler, seconds=deadline-time.monotonic(), candidate_seconds=candidate_seconds,
                            retries=retries, improve=improve, rank_equal_cost=rank_equal_cost,
                            optimize_goal=optimize_goal, stop_on_feasible=stop_on_feasible,
                            python_executable=python_executable, cancel_event=cancel_event, work=work, numerical=confirm)
        inner_attempts.extend(result['attempts'])
        if result['status'] in ('cancelled', 'error'):
            return None
        if result['best'] is None:
            completion = next((c for c in completed if c['branch'] == variant['branch']), None)
            if completion is not None:
                completion['oracleRejected'] = True
            return 'rejected'
        try:
            checked = audit_point(result['best'], branch, saved['passages'])
        except (TimeoutError, ValueError, RuntimeError, OSError):
            return None
        if not checked['accepted']:
            return 'rejected'
        return enrich_point(result['best'], checked, branch, saved['rows'], saved['passages'], saved['hits'],
                            saved.get('meta'))

    def walk():
        """Confirm the loop's kept candidates once, in relaxed rank order (relaxed objectives are optimistic
        for their own branch): an unalarmed point is accepted as is, an alarmed one by its IPOPT re-solve.

        Stops when the best confirmed point ranks at least as well as the next relaxed candidate, or after
        WALK_LIMIT examined candidates once one is confirmed; with none confirmed it keeps walking."""
        nonlocal best_result, best_branch, walking
        incumbent, examined = None, 0
        for variant in walk_order():
            if incumbent is not None and (examined >= WALK_LIMIT or candidate_rank(incumbent[0], route['objective'])
                                          <= candidate_rank(variant['best'], route['objective'])):
                break
            point = variant['best']
            if variant['alarmTicks']:
                if variant.get('confirmation') is None:
                    outcome = examine(variant)
                    if outcome is None:
                        walking = variant['state']
                        break
                    variant['confirmation'] = outcome
                point = variant['confirmation']
            examined += 1
            if point != 'rejected' and (incumbent is None or candidate_rank(point, route['objective'])
                                        < candidate_rank(incumbent[0], route['objective'])):
                incumbent = (point, variant)
        if incumbent is None:
            best_result = best_branch = None
            return
        point, variant = incumbent
        saved = variant['state']
        best_branch = save_branch(saved['score'], saved['rows'], saved['passages'], saved['hits'],
                                  point['candidate']['yaw'], saved.get('meta'))
        best_result = dict(status='feasible', best=copy.deepcopy(point), termination='validated_incumbent',
                           minimumProven=False)

    def grow(collision):
        """Conflict-driven avoidance: a solve hit an obstacle; enumerate it from now on.

        The generator restarts over the larger obstacle set, so families and switch climbs stay joint over
        every obstacle hit so far. The old generator's unsolved branches lack the new obstacle's rows and
        are dropped (the new generator emits them refined); accepted points stay. A hit on an enumerated
        obstacle or a settled stretch has no repair."""
        nonlocal variant_source, source_exhausted, generated
        key = variant_source.obstacle_key(collision['solidIndex'], collision['tick'])
        if key is None or key in variant_source.obstacles:
            attempts[-1]['termination'] = 'settled_obstacle_hit' if key is None else 'enumerated_obstacle_hit'
            return
        attempts[-1]['termination'] = 'obstacle_added'
        variant_source = variant_source.with_obstacles(variant_source.obstacles+[key])
        source_exhausted = False
        stale = [entry for entry in queue if branch_key(entry[2], entry[3], entry[4]) in generated]
        for entry in stale:
            queue.remove(entry)
            seen.discard(branch_key(entry[2], entry[3], entry[4]))
        heapq.heapify(queue)
        generated = set()
        families.clear()

    termination = 'contact_search_exhausted'
    while queue or not source_exhausted or repair_sources:
        if settling is not None:
            done, settling = settling, None
            settle(done)
        in_flight = None
        publish()
        if cancel_event is not None and cancel_event.is_set():
            if best_result is not None:
                best_result.update(termination='cancelled', attempts=inner_attempts, contactAttempts=attempts)
                return finish(best_result)
            return finish({'status': 'cancelled', 'termination': 'cancelled', 'best': None,
                    'attempts': inner_attempts, 'contactAttempts': attempts})
        if work is not None:
            try:
                work.check()
            except TimeoutError as error:
                termination = str(error)
                break
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            termination = 'deadline'
            break
        if repair_sources and not queue:
            record = repair_sources[-1]
            from parkour_solver.constraints.variants import ConstraintVariants
            try:
                token = id(record)
                if token not in source_instances:
                    source_instances[token] = ConstraintVariants.for_plan(record['route'], profiles, record['plan'],
                        geometry, compiler, record['vertical'], record['candidate'], seconds=remaining,
                        state=record.get('state'), deadline=deadline, owners=solid_blocks,
                        budget=work.check if work is not None else None, scope=validation_scope, cancel_event=cancel_event)
                source = source_instances[token]
                try:
                    variant = next(source)
                finally:
                    record['state'] = source.state
            except StopIteration:
                completed_source_metrics.update(record.get('state', {}).get('metrics', {}))
                if record.get('state', {}).get('uncertainInteractions'):
                    unknown = save_branch(record['score'], record['rows'], record['passages'],
                                          record['hits'], record['candidate']['yaw'], record.get('meta'))
                    key = branch_key(unknown['rows'], unknown['passages'], unknown['hits'])
                    if not any(branch_key(b['rows'], b['passages'], b['hits']) == key for b in unresolved):
                        unresolved.append(unknown)
                repair_sources.pop()
                source_instances.pop(token, None)
                advance_all()
                continue
            except TimeoutError as error:
                termination = str(error)
                break
            else:
                def on_legs(rows):
                    converted = []
                    for row in rows:
                        tick = row['offset']
                        leg = max(i for i, e in enumerate(record['plan']['events']) if e['landed'] <= tick)
                        converted.append(dict(row, leg=leg, offset=tick-record['plan']['events'][leg]['jump']))
                    return converted
                converted = on_legs(variant['rows'])
                selected = copy.deepcopy(variant.get('selectedPassages', []))
                for passage in selected:
                    passage['phaseReferences'] = []
                    for at in passage['switchPhases']:
                        leg = max(i for i, e in enumerate(record['plan']['events']) if e['landed'] <= at//3)
                        passage['phaseReferences'].append(dict(leg=leg, offset=at//3-record['plan']['events'][leg]['jump'], phase=at%3))
                source = branch_key(record['rows'], record['passages'], record['hits'])
                from parkour_solver.search.identity import digest
                arrive(record['score']+variant['score']*1e-9, record['rows']+converted, record['passages'],
                       record['hits'], record['candidate']['yaw'], selected,
                       dict(family=digest([source, variant['family']]), switches=variant['switches'],
                            **branch_meta(variant))
                       if 'family' in variant else None, source)
            continue
        if variant_source is not None and not source_exhausted and not queue:
            try:
                variant = next(variant_source)
            except StopIteration:
                source_exhausted = True
                advance_all()
            except TimeoutError as error:
                termination = str(error)
                break
            else:
                arrive(min([b[0] for b in queue]+[0.])-1, variant['rows'], variant.get('passages', []),
                       route.get('hitEvents', []), reference_yaw or route.get('warmYaw'), variant.get('selectedPassages', []),
                       dict(family=variant['family'], switches=variant['switches'],
                            **branch_meta(variant)) if 'family' in variant else None)
                generated.add(branch_key(variant['rows'], variant.get('passages', []), route.get('hitEvents', [])))
            if not queue:
                continue
        # Generation can consume the remaining budget. Leave the next branch
        # queued instead of dispatching it into an unstarted unknown attempt.
        if work is not None:
            try:
                work.check()
            except TimeoutError as error:
                termination = str(error)
                break
        if time.monotonic() >= deadline:
            termination = 'deadline'
            break
        score, _, rows, passages, hits, proposal_yaw, meta = heapq.heappop(queue)
        in_flight = save_branch(score, rows, passages, hits, proposal_yaw, meta)
        settling = dict(meta=meta, rank=None, objective=None) if point(meta) is not None else None
        publish()
        if len(hits) < max_wall_hits:
            for index, hit in enumerate(hit_pool['options']):
                if any(e['leg'] == hit['leg'] and e['axis'] == hit['axis'] and e['offset'] == hit['offset']
                       for e in route.get('contactEvents', [])):
                    continue
                if any(h['leg'] == hit['leg'] and h['axis'] == hit['axis']
                       and h['offset'] == hit['offset'] for h in hits):
                    continue
                offer(score + 10 + index * .01, rows, passages, hits + [hit], proposal_yaw)
        branch = build_branch(rows, passages, hits, reference_yaw if reference_yaw is not None else proposal_yaw)
        if best_result is not None:
            branch['maxTotalRunTicks'] = min(branch['maxTotalRunTicks'], best_result['best']['cost'])
        audit_cache, audit_errors = {}, []

        def audit(best):
            from parkour_solver.search.identity import digest
            # Equal facings with different relaxed accelerations are different trajectories.
            key = (tuple(best['allocation']), best['plan']['ticks'], tuple(best['candidate']['yaw']),
                   digest(best['candidate']['controls']) if 'controls' in best['candidate'] else None)
            if key not in audit_cache:
                audit_cache[key] = audit_point(best, branch, passages)
            return audit_cache[key]

        def enrich(best, checked):
            return enrich_point(best, checked, branch, rows, passages, hits, meta)

        def retain(best):
            nonlocal best_result, best_branch, proposal_yaw, reference_yaw
            proposal_yaw = best['candidate']['yaw'][:]
            if best_result is not None and candidate_rank(best, route['objective']) >= candidate_rank(best_result['best'], route['objective']):
                return
            try:
                checked = audit(best)
            except (TimeoutError, ValueError, RuntimeError, OSError) as error:
                audit_errors.append(str(error))
                return
            if checked['accepted']:
                accepted = enrich(best, checked)
                reference_yaw = accepted['candidate']['yaw'][:]
                best_branch = save_branch(score, rows, passages, hits, accepted['candidate']['yaw'], meta)
                if work is not None:
                    work.accepted(accepted)
                    checked['reportedAcceptance'] = True
                best_result = dict(status='feasible', best=accepted, termination='validated_incumbent',
                    attempts=inner_attempts, contactAttempts=attempts, minimumProven=False)
                publish()

        result = find_route(branch, compiler, seconds=remaining, candidate_seconds=candidate_seconds,
                            retries=retries, improve=improve, rank_equal_cost=rank_equal_cost,
                            optimize_goal=optimize_goal, stop_on_feasible=stop_on_feasible, python_executable=python_executable,
                            cancel_event=cancel_event, work=work, on_candidate=retain, numerical=numerical)
        inner_attempts.extend(result['attempts'])
        completion = dict(branch=branch_key(rows, passages, hits), status=result['status'],
            validationStatus='not_run' if result['best'] else 'no_candidate',
            problemHashes=[a.get('problemHash') for a in result['attempts'] if a.get('problemHash')])
        completed.append(completion)
        if result['best'] is not None:
            proposal_yaw = result['best']['candidate']['yaw'][:]
        try:
            checked = audit(result['best']) if result['best'] else None
            if checked is not None:
                completion['validationStatus'] = 'accepted' if checked.get('accepted') else checked.get('reason','rejected')
        except TimeoutError:
            unresolved.append(save_branch(score, rows, passages, hits, proposal_yaw, meta))
            termination = 'geometry_deadline'
            break
        except (ValueError, RuntimeError, OSError) as error:
            unresolved.append(save_branch(score, rows, passages, hits, proposal_yaw, meta))
            if best_result is not None:
                best_result.update(termination='geometry_error', error=str(error),
                                   attempts=inner_attempts, contactAttempts=attempts)
                return finish(best_result)
            return finish(dict(status='error', termination='geometry_check', error=str(error), best=None,
                        attempts=inner_attempts, contactAttempts=attempts))
        conflicts = checked['conflicts'] if checked else []
        attempts.append({'branch': branch_key(rows, passages, hits), 'status': result['status'], 'constraintCount': len(rows),
                         'passageCount': len(passages),
                         'wallHitCount': len(hits), 'wallHitGeneration': {k: v for k, v in hit_pool.items() if k != 'options'},
                         'passageKinds': [p['kind'] for p in choices.get(branch_key(rows, passages, hits), passages)],
                         'incumbentAuditErrors': audit_errors,
                         'conflicts': [{'block': c['block']['id'], 'state': c['state'], 'reason': c['reason']}
                                       for c in conflicts], 'termination': result['termination']})
        if result['status'] in ('error', 'cancelled'):
            unresolved.append(save_branch(score, rows, passages, hits, proposal_yaw, meta))
            if best_result is not None:
                best_result.update(termination=result['status'], attempts=inner_attempts, contactAttempts=attempts)
                return finish(best_result)
            result.update(attempts=inner_attempts, contactAttempts=attempts)
            return finish(result)
        if result['best'] is None:
            tried = result.get('attempts', [])
            certificates = [a['certificate'] for a in tried if a.get('status') == 'blocked']
            proven = (result.get('termination') == 'search_exhausted' and bool(tried) and
                      len(certificates) == len(tried))
            diagnostics = [d for a in tried for d in a.get('diagnostics', [])]
            oracle_completed = oracle_rejection and diagnostics and all(
                d.get('status') == 'unknown' and (d.get('termination') == 'worker_finished' and
                d.get('workerStatus') in ('Infeasible_Problem_Detected','Search_Direction_Becomes_Too_Small',
                                         'Restoration_Failed','Solve_Succeeded','Solved_To_Acceptable_Level')
                or d.get('termination') == 'disk_finished' and
                d.get('workerStatus') in ('PrimalInfeasible', 'AlmostPrimalInfeasible'))
                for d in diagnostics)
            completion.update(oracleRejected=bool(oracle_completed), proven=proven, certificates=certificates)
            if result['status'] == 'unknown' and not oracle_completed and not proven:
                unresolved.append(save_branch(score, rows, passages, hits, proposal_yaw, meta))
            continue
        if not conflicts:
            if 'verdict' in checked and candidate_filter is not None:
                attempts[-1]['candidateFilter'] = {k: v for k, v in checked['verdict'].items() if k != 'alternatives'}
                if 'corridorAudit' in checked['verdict']:
                    logged = copy.deepcopy(checked['verdict']['corridorAudit'])
                    logged.update(selectedPassages=copy.deepcopy(choices.get(branch_key(rows, passages, hits), passages)), selectedHits=copy.deepcopy(hits),
                        extraRows=copy.deepcopy(rows),
                        numericalRequests=[{k: a[k] for k in ('problemHash', 'status', 'allocation', 'stage', 'leg') if k in a}
                                           for a in result['attempts']],
                        physicalReplay=dict(status='not_run' if geometry is not None else 'not_configured',
                            reason='corridor_rejected_before_scene_validation' if geometry is not None else 'no_scene_configured'))
                    attempts[-1]['candidateFilter']['corridorAudit'] = logged
            if 'reason' in checked:
                attempts[-1].update(status='unknown', termination=checked['reason'])
                for extra in checked.get('verdict', {}).get('alternatives', []):
                    offer(score + 1, rows + extra, passages, hits, proposal_yaw, meta=meta)
                continue
            audit_result = checked
            if geometry is not None:
                checked = audit_result['geometryCheck']
                attempts[-1]['geometryCheck'] = checked
                if not checked['feasible']:
                    attempts[-1]['status'] = 'unknown'
                    if 'solidIndex' not in checked:
                        attempts[-1]['termination'] = checked['reason']
                        continue
                    if getattr(variant_source, 'conflict_driven', False):
                        grow(checked)
                        continue
                    plan = result['best']['plan']
                    # Save construction inputs before any interruptible preparation.
                    # The shared builder assigns all applicable boxes jointly.
                    repair_sources.append(dict(route=branch, plan=plan, vertical=audit_result['vertical'],
                        candidate=result['best']['candidate'], score=score, rows=rows, passages=passages, hits=hits, meta=meta))
                    continue
                result['best']['geometryCheck'] = checked
            result['best'] = enrich(result['best'], audit_result)
            accepted_key = branch_key(rows, passages, hits)
            if not any(v['branch']==accepted_key for v in accepted_variants):
                accepted_variants.append(dict(branch=accepted_key, best=copy.deepcopy(result['best']),
                    family=meta['family'] if meta is not None and 'family' in meta else None,
                    alarmTicks=alarmed(result) if confirm is not None else [],
                    state=save_branch(score, rows, passages, hits, result['best']['candidate']['yaw'], meta)))
            if reference_yaw is None:
                reference_yaw = result['best']['candidate']['yaw'][:]
            if work is not None and not audit_result.get('reportedAcceptance'):
                work.accepted(result['best'])
            result.update(attempts=inner_attempts, contactAttempts=attempts)
            if settling is not None:
                settling.update(rank=list(candidate_rank(result['best'], route['objective'])),
                                objective=result['best']['candidate']['objective'])
            if not rank_equal_cost:
                if best_result is not None and candidate_rank(best_result['best'], route['objective']) < candidate_rank(result['best'], route['objective']):
                    return finish(best_result)
                best_branch = save_branch(score, rows, passages, hits, result['best']['candidate']['yaw'], meta)
                return finish(result)
            if best_result is None or candidate_rank(result['best'], route['objective']) <= candidate_rank(best_result['best'], route['objective']):
                best_result = result
                best_branch = save_branch(score, rows, passages, hits, result['best']['candidate']['yaw'], meta)
                reference_yaw = result['best']['candidate']['yaw'][:]
            continue
        conflict = conflicts[0]
        for row in avoid_contact_rows(conflict, max(route['supportClearance'], 2e-7)):
            new_rows = rows + [row]
            delta = abs(conflict['position'][0 if row['mode'] == 'X' else 1] - row['rhs'])
            offer(score + delta, new_rows, passages, hits, proposal_yaw, meta=meta)
    if settling is not None:
        settle(settling)
    if confirm is not None and rank_equal_cost:
        walk()
    if best_result is not None:
        best_result.update(termination=termination, attempts=inner_attempts, contactAttempts=attempts)
        return finish(best_result)
    return finish({'status': 'cancelled' if termination == 'cancelled' else 'unknown', 'termination': termination, 'best': None,
            'attempts': inner_attempts, 'contactAttempts': attempts})
