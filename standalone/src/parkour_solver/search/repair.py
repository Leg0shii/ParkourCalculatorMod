from parkour_solver.validation.vertical import validate_vertical_schedule
from parkour_solver.constraints.contacts import floor_checks, contact_conflicts, avoid_contact_rows
import copy
from collections import Counter
import heapq
import time

from parkour_solver.numerics import prepare, validate
from parkour_solver.physics.vertical import f32, vertical_move, vertical_step
from parkour_solver.scene.route import center_bounds, supports
from parkour_solver.search.ranking import candidate_rank
from parkour_solver.search.ordered_route import find_route
from parkour_solver.constraints.avoidance import blockage_usage, gate_timeline, passage_y_valid
from parkour_solver.validation.scene import check_geometry
from parkour_solver.constraints.hit_events import wall_hit_options
from parkour_solver.search.repair_state import branch_key, compatible, context as repair_context


def solve_contact_branches(route, profiles, checks, compiler, *, seconds, candidate_seconds, retries,
                           improve=False, rank_equal_cost=False, optimize_goal=False,
                           python_executable=None, cancel_event=None, geometry=None, motion_bound=None,
                           solid_blocks=None, max_wall_hits=0,
                           max_wall_candidates=24, candidate_filter=None, work=None, resume_state=None, repair_scope=None,
                           on_progress=None, validation_scope=None, initial_variants=None, retry_unresolved=True,
                           stop_on_feasible=None, oracle_rejection=False):
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
    policy = dict(stopOnFeasible=stop_on_feasible, optimizeGoal=optimize_goal,
                  rankEqualCost=rank_equal_cost, oracleRejection=oracle_rejection)
    current_context = repair_context(route, profiles, geometry, dict(scope=repair_scope, solvePolicy=policy, avoidancePolicy=8))
    resuming = (isinstance(resume_state, dict) and resume_state.get('version') in (1, 2)
                and compatible(resume_state.get('context'), current_context))
    exact_resume = resuming and resume_state['context'] == current_context
    if exact_resume:
        completed_source_metrics.update(resume_state.get('sourcesCompletedMetrics', {}))
        repair_sources.extend(copy.deepcopy(resume_state.get('repairSources', [])))
        seen.update(resume_state.get('seen', []))
    resumed_branches = 0
    reference_yaw = copy.deepcopy(resume_state.get('referenceWarmYaw')) if resuming else None
    hit_pool = wall_hit_options(route, profiles, geometry, solid_blocks, motion_bound, max_wall_candidates) \
        if geometry is not None and max_wall_hits else {'options': [], 'generated': 0, 'candidateDrops': 0}

    def offer(score, rows, passages, hits, warm_yaw=None, selected=None):
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
                              copy.deepcopy(warm_yaw)))

    if resuming:
        choices.update(copy.deepcopy(resume_state.get('interactionChoices', {})))
        choice_alternatives.update(copy.deepcopy(resume_state.get('interactionAlternatives', {})))
        pending = (([resume_state['incumbent']] if resume_state.get('incumbent') is not None else [])
                   if resume_state.get('version') == 1 else [])+resume_state.get('pending', [])
        deferred = (resume_state.get('unresolved', []) if retry_unresolved else [])+([resume_state['inFlight']] if resume_state.get('inFlight') else [])
        if not retry_unresolved:
            unresolved.extend(copy.deepcopy(resume_state.get('unresolved', [])))
        floor = max([b['score'] for b in pending]+[0.])+1
        restored = set()
        for index, saved in enumerate(pending+deferred):
            key = branch_key(saved['rows'], saved['passages'], saved['hits'])
            if key in restored:
                continue
            restored.add(key)
            seen.discard(key)
            offer(saved['score'] if index < len(pending) else floor+saved['score'],
                  saved['rows'], saved['passages'], saved['hits'], saved.get('warmYaw'))
        resumed_branches = len(queue)
    if not exact_resume:
        if initial_variants is None:
            offer(min([b[0] for b in queue]+[0.])-1, [], route.get('passages', []), route.get('hitEvents', []), route.get('warmYaw'))

    def save_branch(score, rows, passages, hits, warm_yaw):
        return copy.deepcopy(dict(score=score, rows=rows, passages=passages, hits=hits, warmYaw=warm_yaw))

    def snapshot():
        saved_best = ({k: copy.deepcopy(v) for k, v in best_result.items()
                      if k not in ('repairState', 'repairResume', 'attempts', 'contactAttempts')}
                      if best_result is not None else None)
        return dict(version=2, context=current_context, repairSources=copy.deepcopy(repair_sources),
            sourcesCompletedMetrics=dict(completed_source_metrics),
            pending=[save_branch(score, rows, passages, hits, yaw) for score, _, rows, passages, hits, yaw in sorted(queue)],
            unresolved=copy.deepcopy(unresolved), seen=sorted(seen), incumbent=copy.deepcopy(best_branch),
            acceptedVariants=copy.deepcopy(accepted_variants),
            acceptedResult=saved_best, inFlight=copy.deepcopy(in_flight), completed=copy.deepcopy(completed),
            referenceWarmYaw=copy.deepcopy(reference_yaw), interactionChoices=copy.deepcopy(choices),
            interactionAlternatives=copy.deepcopy(choice_alternatives),
            constraintGeneration=copy.deepcopy(getattr(variant_source, 'state', None)),
            attemptLog=copy.deepcopy((resume_state.get('attemptLog', []) if exact_resume else [])+inner_attempts),
            contactLog=copy.deepcopy((resume_state.get('contactLog', []) if exact_resume else [])+attempts))

    def publish():
        if on_progress is not None:
            on_progress(snapshot())

    def finish(result):
        nonlocal in_flight
        in_flight = None
        result['repairState'] = snapshot()
        metrics = completed_source_metrics.copy()
        metrics.update(getattr(variant_source, 'state', {}).get('metrics', {}))
        for record in repair_sources:
            metrics.update(record.get('state', {}).get('metrics', {}))
        result['avoidanceMetrics'] = dict(metrics, generationIncomplete=bool(repair_sources or not source_exhausted))
        result['repairResume'] = dict(requested=resume_state is not None, compatible=resuming,
                                     exact=exact_resume, resumedBranches=resumed_branches)
        publish()
        return result
    attempts, inner_attempts = [], []
    best_result = copy.deepcopy(resume_state.get('acceptedResult')) if exact_resume else None
    best_branch = copy.deepcopy(resume_state.get('incumbent')) if exact_resume else None
    completed = copy.deepcopy(resume_state.get('completed', [])) if exact_resume else []
    accepted_variants = copy.deepcopy(resume_state.get('acceptedVariants', [])) if exact_resume else []
    in_flight = None
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
    termination = 'contact_search_exhausted'
    while queue or not source_exhausted or repair_sources:
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
                                          record['hits'], record['candidate']['yaw'])
                    key = branch_key(unknown['rows'], unknown['passages'], unknown['hits'])
                    if not any(branch_key(b['rows'], b['passages'], b['hits']) == key for b in unresolved):
                        unresolved.append(unknown)
                repair_sources.pop()
                source_instances.pop(token, None)
                continue
            except TimeoutError as error:
                termination = str(error)
                break
            else:
                converted = []
                for row in variant['rows']:
                    tick = row['offset']
                    leg = max(i for i, e in enumerate(record['plan']['events']) if e['landed'] <= tick)
                    converted.append(dict(row, leg=leg, offset=tick-record['plan']['events'][leg]['jump']))
                selected = copy.deepcopy(variant.get('selectedPassages', []))
                for passage in selected:
                    passage['phaseReferences'] = []
                    for at in passage['switchPhases']:
                        leg = max(i for i, e in enumerate(record['plan']['events']) if e['landed'] <= at//3)
                        passage['phaseReferences'].append(dict(leg=leg, offset=at//3-record['plan']['events'][leg]['jump'], phase=at%3))
                offer(record['score']+variant['score']*1e-9, record['rows']+converted, record['passages'],
                      record['hits'], record['candidate']['yaw'], selected)
            continue
        if variant_source is not None and not source_exhausted and not queue:
            try:
                variant = next(variant_source)
            except StopIteration:
                source_exhausted = True
            except TimeoutError as error:
                termination = str(error)
                break
            else:
                offer(min([b[0] for b in queue]+[0.])-1, variant['rows'], variant.get('passages', []),
                      route.get('hitEvents', []), reference_yaw or route.get('warmYaw'), variant.get('selectedPassages', []))
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
        score, _, rows, passages, hits, proposal_yaw = heapq.heappop(queue)
        in_flight = save_branch(score, rows, passages, hits, proposal_yaw)
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
        branch = copy.deepcopy(route)
        branch['constraints'].extend(rows)
        branch['passages'] = passages
        branch['hitEvents'] = hits
        if reference_yaw is not None:
            branch['warmYaw'] = reference_yaw
        elif proposal_yaw is not None:
            branch['warmYaw'] = proposal_yaw
        if best_result is not None:
            branch['maxTotalRunTicks'] = min(branch['maxTotalRunTicks'], best_result['best']['cost'])
        audit_cache, audit_errors = {}, []

        def audit(best):
            key = (tuple(best['allocation']), best['plan']['ticks'], tuple(best['candidate']['yaw']))
            if key in audit_cache:
                return audit_cache[key]
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
            audit_cache[key] = checked
            return checked

        def enrich(best, checked):
            best = copy.deepcopy(best)
            selected = copy.deepcopy(choices.get(branch_key(rows, passages, hits), []))
            described = passages+[p for p in selected if p not in passages]
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
                best_branch = save_branch(score, rows, passages, hits, accepted['candidate']['yaw'])
                if work is not None:
                    work.accepted(accepted)
                    checked['reportedAcceptance'] = True
                best_result = dict(status='feasible', best=accepted, termination='validated_incumbent',
                    attempts=inner_attempts, contactAttempts=attempts, minimumProven=False)
                publish()

        result = find_route(branch, compiler, seconds=remaining, candidate_seconds=candidate_seconds,
                            retries=retries, improve=improve, rank_equal_cost=rank_equal_cost,
                            optimize_goal=optimize_goal, stop_on_feasible=stop_on_feasible, python_executable=python_executable,
                            cancel_event=cancel_event, work=work, on_candidate=retain)
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
            unresolved.append(save_branch(score, rows, passages, hits, proposal_yaw))
            termination = 'geometry_deadline'
            break
        except (ValueError, RuntimeError, OSError) as error:
            unresolved.append(save_branch(score, rows, passages, hits, proposal_yaw))
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
            unresolved.append(save_branch(score, rows, passages, hits, proposal_yaw))
            if best_result is not None:
                best_result.update(termination=result['status'], attempts=inner_attempts, contactAttempts=attempts)
                return finish(best_result)
            result.update(attempts=inner_attempts, contactAttempts=attempts)
            return finish(result)
        if result['best'] is None:
            diagnostics = [d for a in result.get('attempts', []) for d in a.get('diagnostics', [])]
            oracle_completed = oracle_rejection and diagnostics and all(
                d.get('status') == 'unknown' and d.get('termination') == 'worker_finished' and
                d.get('workerStatus') in ('Infeasible_Problem_Detected','Search_Direction_Becomes_Too_Small',
                                         'Restoration_Failed','Solve_Succeeded','Solved_To_Acceptable_Level')
                for d in diagnostics)
            completion['oracleRejected'] = bool(oracle_completed)
            if result['status'] == 'unknown' and not oracle_completed:
                unresolved.append(save_branch(score, rows, passages, hits, proposal_yaw))
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
                    offer(score + 1, rows + extra, passages, hits, proposal_yaw)
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
                    plan = result['best']['plan']
                    # Save construction inputs before any interruptible preparation.
                    # The shared builder assigns all applicable boxes jointly.
                    repair_sources.append(dict(route=branch, plan=plan, vertical=audit_result['vertical'],
                        candidate=result['best']['candidate'], score=score, rows=rows, passages=passages, hits=hits))
                    continue
                result['best']['geometryCheck'] = checked
            result['best'] = enrich(result['best'], audit_result)
            accepted_key = branch_key(rows, passages, hits)
            if not any(v['branch']==accepted_key for v in accepted_variants):
                accepted_variants.append(dict(branch=accepted_key,best=copy.deepcopy(result['best'])))
            if reference_yaw is None:
                reference_yaw = result['best']['candidate']['yaw'][:]
            if work is not None and not audit_result.get('reportedAcceptance'):
                work.accepted(result['best'])
            result.update(attempts=inner_attempts, contactAttempts=attempts)
            if not rank_equal_cost:
                if best_result is not None and candidate_rank(best_result['best'], route['objective']) < candidate_rank(result['best'], route['objective']):
                    return finish(best_result)
                best_branch = save_branch(score, rows, passages, hits, result['best']['candidate']['yaw'])
                return finish(result)
            if best_result is None or candidate_rank(result['best'], route['objective']) <= candidate_rank(best_result['best'], route['objective']):
                best_result = result
                best_branch = save_branch(score, rows, passages, hits, result['best']['candidate']['yaw'])
                reference_yaw = result['best']['candidate']['yaw'][:]
            continue
        conflict = conflicts[0]
        for row in avoid_contact_rows(conflict, max(route['supportClearance'], 2e-7)):
            new_rows = rows + [row]
            delta = abs(conflict['position'][0 if row['mode'] == 'X' else 1] - row['rhs'])
            offer(score + delta, new_rows, passages, hits, proposal_yaw)
    if best_result is not None:
        best_result.update(termination=termination, attempts=inner_attempts, contactAttempts=attempts)
        return finish(best_result)
    return finish({'status': 'cancelled' if termination == 'cancelled' else 'unknown', 'termination': termination, 'best': None,
            'attempts': inner_attempts, 'contactAttempts': attempts})
