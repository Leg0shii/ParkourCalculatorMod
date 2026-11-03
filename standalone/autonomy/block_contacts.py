import copy
import heapq
import time

from standalone.ipopt import prepare, validate
from standalone.ipopt.ladder import f32, vertical_move, vertical_step
from .route import center_bounds, supports
from .ranking import candidate_rank
from .search import find_route
from .blockages import blockage_usage, gate_timeline, passage_options, passage_y_valid, within_caps
from .obstacle_geometry import check_geometry
from .hit_events import wall_hit_options
from .repair_state import branch_key, compatible, context as repair_context


def floor_checks(scene, block_ids, profiles, *, envelopes=None):
    checks = []
    by_id = {b['id']: b for b in scene['blocks']}
    for leg, profile in enumerate(profiles):
        target_top = by_id[block_ids[leg + 1]]['top']
        jumps = profile.get('jump', [True] + [False] * (profile['airtime'] - 1))
        config = {'threshold': profile.get('threshold', .005), 'jumpVelocity': [f32(.42) if j else None for j in jumps],
                  'sneak': profile.get('sneak', [False] * profile['airtime'])}
        for offset in range(profile['airtime']):
            y = profile['position'][offset]
            next_y = profile['position'][offset + 1]
            movement = vertical_move(config, offset, profile['velocity'][offset], profile.get('ladderTicks', [False] * (profile['airtime'] + 1))[offset])
            if movement >= 0:
                continue
            for block in scene['blocks']:
                if envelopes is not None:
                    from .interactions import block_interaction
                    box = scene['collisionScene']['solids'][block['solidIndex']]
                    if block_interaction(envelopes,box,offset,('Y',))['ruledOut']:
                        continue
                top = block['top']
                planned_floor = any(e['offset'] == offset and e.get('role') in ('landing', 'step')
                                    and e['plane'] == top and e['box'] == scene['collisionScene']['solids'][block['solidIndex']]
                                    for e in profile.get('contactEvents', []))
                if next_y <= top <= y and not planned_floor and not (offset + 1 == profile['airtime'] and top == target_top):
                    checks.append({'leg': leg, 'offset': offset, 'block': copy.deepcopy(block), 'reason': 'earlier_floor'})
        for block in scene['blocks']:
            if envelopes is not None:
                from .interactions import block_interaction
                box = scene['collisionScene']['solids'][block['solidIndex']]
                if block_interaction(envelopes,box,0)['ruledOut']:
                    continue
            selected_step = profile.get('profileKind') in ('stair', 'step') and block['id'] == block_ids[leg + 1]
            selected_step |= any(e['input'] == 0 and e['case'] in ('normal_step', 'slab_boost')
                                 and e['box'] == scene['collisionScene']['solids'][block['solidIndex']]
                                 for e in profile.get('caseEvents', []))
            step_config = dict(config, contacts=[{'state': 1, 'plane': block['top'], 'direction': -1, 'step': True}])
            _, _, _, failures = vertical_step(step_config, 0, profile['position'][0], profile['velocity'][0], False, True, 0)
            if not selected_step and not failures:
                checks.append({'leg': leg, 'offset': 1, 'block': copy.deepcopy(block), 'reason': 'unplanned_stair'})
    return checks


def contact_conflicts(checks, plan, candidate):
    conflicts = []
    for check in checks:
        tick = plan['events'][check['leg']]['jump'] + check['offset']
        x, z = candidate['positions'][tick]
        if supports(check['block'], x, z):
            conflicts.append(dict(check, state=tick, position=[x, z]))
    return conflicts


def avoid_contact_rows(conflict, clearance):
    bounds = center_bounds(conflict['block'])
    x, z = conflict['position']
    rows = []
    for axis, coordinate, lo, hi in [('X', x, bounds[0], bounds[1]), ('Z', z, bounds[2], bounds[3])]:
        for cmp, edge in [('LE', lo - clearance), ('GE', hi + clearance)]:
            rows.append((abs(coordinate - edge), {'leg': conflict['leg'], 'offset': conflict['offset'],
                                                 'mode': axis, 'cmp': cmp, 'rhs': edge,
                                                 'name': conflict['reason'] + ':' + conflict['block']['id']}))
    return [row for _, row in sorted(rows, key=lambda value: value[0])]


def validate_vertical_schedule(route, profiles, plan):
    n = plan['ticks']
    contacts = []
    for event, profile in zip(plan['events'], profiles):
        if event['landing'] is not None:
            for contact in profile['contacts']:
                contacts.append(dict(contact, state=event['jump'] + contact['state']))
        for tick in range(event['landed'], event['jump']):
            contacts.append({'state': tick + 1, 'plane': profile['position'][0], 'direction': -1})
    from .contact_combinations import merge_contacts
    contacts = merge_contacts([dict(c, axis=1) for c in contacts] + plan.get('verticalContacts', []))
    vertical = {'profile': 'minecraft-1.8.9', 'position': route['initial']['position'][1],
                'velocity': route['initial']['velocity'][1], 'onGround': True,
                'previousHorizontalCollision': False, 'threshold': route.get('physicsThreshold', .005),
                'jumpVelocity': [f32(.42) if jump else None for jump in plan['jump']],
                'sneak': plan.get('sneak', [False] * n),
                'contacts': [{k: v for k, v in c.items() if k != 'axis'} for c in contacts]}
    x, synthetic_hits = 0., []
    hit_states = {c['state'] for c in plan.get('contacts', [])}
    for t in range(n):
        x += .05 if t + 1 in hit_states else .1
        if t + 1 in hit_states:
            synthetic_hits.append({'state': t + 1, 'axis': 0, 'direction': 1, 'plane': x})
    problem = prepare({'version': 1, 'ticks': n, 'position': [0, 0], 'velocity': [0, 0],
                       'magnitude': [.1] * n, 'friction': [0] * n, 'base': [0] * n, 'yaw': [0] * n,
                       'contacts': synthetic_hits, 'ladderTicks': plan.get('ladderTicks', [False] * (n + 1)),
                       'objective': {'axis': 'X', 'sense': 'MAX', 'tick': n}, 'vertical': vertical})
    checked = validate(problem, problem['yaw'])
    last_profile = profiles[len(plan['events']) - 1] if profiles else {}
    terminal_ground = last_profile['grounded'][-1] if plan['stage'] == 'jump' and last_profile.get('composition') == 'movement_program' else True
    matched = True
    for event, profile in zip(plan['events'], profiles):
        if profile.get('composition') == 'movement_program' and event['landing'] is not None:
            first, end = event['jump'], event['landing'] + 1
            matched &= checked['y'][first:end] == profile['position'] and checked['vy'][first:end] == profile['velocity']
    return {'feasible': matched and checked['feasible'] and checked['onGround'] == plan['grounded'] + [terminal_ground],
            'vertical': vertical, 'y': checked['y'], 'vy': checked['vy'], 'onGround': checked['onGround']}


def solve_contact_branches(route, profiles, checks, compiler, *, seconds, candidate_seconds, retries,
                           max_branches, improve=False, rank_equal_cost=False, optimize_goal=False,
                           python_executable=None, cancel_event=None, geometry=None, motion_bound=None,
                           max_blockages=3, max_horizontal=2, max_vertical=2,
                           max_passage_candidates=128, solid_blocks=None, max_wall_hits=0,
                           max_wall_candidates=24, candidate_filter=None, work=None, resume_state=None, repair_scope=None,
                           on_progress=None, validation_scope=None, initial_variants=None, retry_unresolved=True):
    route = copy.deepcopy(route)
    if solid_blocks is not None:
        for passage in route.get('passages', []):
            passage['blockId'] = solid_blocks[passage['solidIndex']]
    deadline = time.monotonic() + seconds
    queue, seen, serial, unresolved = [], set(), 0, []
    variant_source = iter(initial_variants) if initial_variants is not None else None
    source_exhausted = variant_source is None or getattr(variant_source, 'state', {}).get('exhausted', False)
    choices = {}
    current_context = repair_context(route, profiles, geometry, repair_scope)
    resuming = (isinstance(resume_state, dict) and resume_state.get('version') in (1, 2)
                and compatible(resume_state.get('context'), current_context))
    exact_resume = resuming and resume_state['context'] == current_context
    if exact_resume:
        seen.update(resume_state.get('seen', []))
    resumed_branches = 0
    reference_yaw = copy.deepcopy(resume_state.get('referenceWarmYaw')) if resuming else None
    hit_pool = wall_hit_options(route, profiles, geometry, solid_blocks, motion_bound, max_wall_candidates) \
        if geometry is not None and max_wall_hits else {'options': [], 'generated': 0, 'candidateDrops': 0}

    def offer(score, rows, passages, hits, warm_yaw=None, selected=None):
        nonlocal serial
        key = branch_key(rows, passages, hits)
        if key in seen:
            return
        seen.add(key)
        if selected is not None:
            choices[key] = copy.deepcopy(selected)
        serial += 1
        heapq.heappush(queue, (score, serial, copy.deepcopy(rows), copy.deepcopy(passages), copy.deepcopy(hits),
                              copy.deepcopy(warm_yaw)))

    if resuming:
        choices.update(copy.deepcopy(resume_state.get('interactionChoices', {})))
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
        return dict(version=2, context=current_context,
            pending=[save_branch(score, rows, passages, hits, yaw) for score, _, rows, passages, hits, yaw in sorted(queue)],
            unresolved=copy.deepcopy(unresolved), seen=sorted(seen), incumbent=copy.deepcopy(best_branch),
            acceptedVariants=copy.deepcopy(accepted_variants),
            acceptedResult=saved_best, inFlight=copy.deepcopy(in_flight), completed=copy.deepcopy(completed),
            referenceWarmYaw=copy.deepcopy(reference_yaw), interactionChoices=copy.deepcopy(choices),
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
            from .solve_spec import scoped_geometry, validation_prefix
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
    while (queue or not source_exhausted) and len(attempts) < max_branches:
        in_flight = None
        publish()
        if cancel_event is not None and cancel_event.is_set():
            if best_result is not None:
                best_result.update(termination='cancelled', attempts=inner_attempts, contactAttempts=attempts)
                return finish(best_result)
            return finish({'status': 'cancelled', 'termination': 'cancelled', 'best': None,
                    'attempts': inner_attempts, 'contactAttempts': attempts})
        if work is not None and work.termination:
            termination = work.termination
            break
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            termination = 'deadline'
            break
        if variant_source is not None and not source_exhausted and (not queue or len(attempts) % 2 == 0):
            try:
                variant = next(variant_source)
            except StopIteration:
                source_exhausted = True
            except TimeoutError:
                termination = 'constraint_generation_deadline'
                break
            else:
                offer(min([b[0] for b in queue]+[0.])-1, variant['rows'], variant.get('passages', []),
                      route.get('hitEvents', []), reference_yaw or route.get('warmYaw'), variant.get('selectedPassages', []))
            if not queue:
                continue
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
                            from .solve_spec import scoped_geometry, validation_prefix
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
            best.update(route=best.get('route', branch), profiles=profiles, contactChecks=checks,
                passages=passages, hitEvents=hits, ceilingEvents=branch.get('ceilingEvents', []),
                blockageTimeline=gate_timeline(passages, best['plan']['events']),
                blockageUsage=[{'blockId': value if kind == 'block' else 'solid:' + str(value), **counts}
                    for (kind, value), counts in blockage_usage(passages).items()], vertical=checked['vertical']['vertical'])
            best['selectedInteractions'] = copy.deepcopy(choices.get(branch_key(rows, passages, hits), passages))
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
                            optimize_goal=optimize_goal, python_executable=python_executable,
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
                         'passageKinds': [p['kind'] for p in passages],
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
            if result['status'] == 'unknown':
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
                    leg = next(i for i, event in enumerate(plan['events'])
                               if event['landed'] <= checked['tick'] < event['landing'])
                    index = checked['solidIndex']
                    alternatives = passage_options(geometry['solids'][index], index, leg, profiles[leg], branch,
                                                   plan, result['best']['candidate'], motion_bound,
                                                   max_blockages=max_blockages, max_horizontal=max_horizontal,
                                                   max_vertical=max_vertical, max_candidates=max_passage_candidates,
                                                   block_id=solid_blocks[index] if solid_blocks is not None else None,
                                                   deadline=deadline)
                    attempts[-1]['passageGeneration'] = {k: v for k, v in alternatives.items() if k != 'options'}
                    for option in alternatives['options']:
                        new_passages = [p for p in passages if (p['leg'], p['solidIndex']) != (leg, index)] + [option['passage']]
                        if not within_caps(new_passages, branch['passageCaps']):
                            continue
                        offer(score + option['score'], rows, new_passages, hits, proposal_yaw)
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
    if (queue or not source_exhausted) and len(attempts) >= max_branches:
        termination = 'contact_branch_limit'
    if best_result is not None:
        best_result.update(termination=termination, attempts=inner_attempts, contactAttempts=attempts)
        return finish(best_result)
    return finish({'status': 'unknown', 'termination': termination, 'best': None,
            'attempts': inner_attempts, 'contactAttempts': attempts})
