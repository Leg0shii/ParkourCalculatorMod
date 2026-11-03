import copy
import math
import time

from standalone.ipopt import prepare, solve, validate
from standalone.ipopt.model import fingerprint, number
from .route import initial_guess, prepare_route, schedule, supports
from .screen import contradiction
from .ranking import objective_seed, passage_seed


def search_allocations(route, evaluate, *, seconds=60, improve=False, rank_equal_cost=False,
                       cancel_event=None, on_attempt=None):
    route = prepare_route(route)
    if number(seconds, 'search seconds') <= 0:
        raise ValueError('Search seconds must be positive')
    started = time.monotonic()
    deadline = started + seconds
    attempts, unresolved = [], []
    best = None
    backtracks = 0
    stopped = None
    minima = [bounds[0] for bounds in route['runBounds']]

    def interrupted():
        nonlocal stopped
        if cancel_event is not None and cancel_event.is_set():
            stopped = 'cancelled'
            return True
        if time.monotonic() >= deadline:
            stopped = 'deadline'
            return True
        return False

    def probe(allocation, stage, parent):
        result = evaluate(allocation, stage, parent, max(0.001, deadline - time.monotonic()))
        if not isinstance(result, dict) or result.get('status') not in ('feasible', 'unknown', 'blocked', 'error', 'cancelled'):
            raise ValueError('Invalid candidate evaluator result')
        result = copy.deepcopy(result)
        record = {k: copy.deepcopy(v) for k, v in result.items() if k not in ('candidate', 'plan')}
        record.update(allocation=list(allocation), stage=stage, leg=len(allocation) - 1)
        attempts.append(record)
        if result['status'] == 'unknown':
            unresolved.append(record)
        if on_attempt is not None:
            on_attempt(copy.deepcopy(record))
        return result

    def visit(prefix, parent):
        nonlocal best, backtracks, stopped
        index = len(prefix)
        lo, hi = route['runBounds'][index]
        for extra in range(lo, hi + 1):
            if interrupted():
                return
            allocation = prefix + [extra]
            cost = sum(allocation)
            if cost + sum(minima[index + 1:]) > route['maxTotalRunTicks']:
                break
            if best is not None and (cost + sum(minima[index + 1:]) > best['cost'] or
                                     (not rank_equal_cost and cost + sum(minima[index + 1:]) == best['cost'])):
                break
            seed = parent
            if extra:
                run = probe(allocation, 'run', parent)
                if run['status'] == 'error':
                    stopped = 'error'
                    return
                if run['status'] == 'cancelled':
                    stopped = 'cancelled'
                    return
                if run['status'] == 'blocked':
                    break
                if run['status'] == 'unknown':
                    break
                seed = run
            if interrupted():
                return
            result = probe(allocation, 'jump', seed)
            if result['status'] == 'error':
                stopped = 'error'
                return
            if result['status'] == 'cancelled':
                stopped = 'cancelled'
                return
            if result['status'] != 'feasible':
                continue
            if index + 1 == len(route['airtimes']):
                better_objective = (rank_equal_cost and best is not None and cost == best['cost'] and
                                    (1 if route['objective']['sense'] == 'MAX' else -1) *
                                    (result['candidate']['objective'] - best['candidate']['objective']) > 0)
                if best is None or cost < best['cost'] or better_objective:
                    best = {'allocation': allocation, 'cost': cost, 'candidate': result['candidate'],
                            'problemHash': result.get('problemHash'), 'plan': result.get('plan')}
                if not rank_equal_cost and (not improve or cost == sum(minima)):
                    stopped = 'first_feasible' if not improve else 'lower_bound_reached'
                    return
            else:
                visit(allocation, result)
                if stopped is not None:
                    return
        backtracks += 1

    visit([], None)
    if stopped is None:
        interrupted()
    cheaper = [item for item in unresolved if best is None or
               sum(item['allocation']) + sum(minima[len(item['allocation']):]) < best['cost']]
    equal_unknowns = [item for item in unresolved if best is not None and
                      sum(item['allocation']) + sum(minima[len(item['allocation']):]) == best['cost']]
    return {'status': 'feasible' if best else ('cancelled' if stopped == 'cancelled' else
                                             'error' if stopped == 'error' else 'unknown'),
            'best': best, 'lowerBound': sum(minima),
            'minimumProven': best is not None and best['cost'] == sum(minima),
            'unresolved': cheaper, 'unknownAttempts': len(unresolved), 'attempts': attempts, 'backtracks': backtracks,
            'unresolvedEqualCost': equal_unknowns,
            'termination': stopped or 'search_exhausted', 'elapsedSeconds': time.monotonic() - started,
            'searchPolicy': 'first-failure-increment-backtrack',
            'source': copy.deepcopy(route.get('source')),
            'validationLevel': ('continuous_horizontal_declared_regions_with_supplied_airtimes'
                                if any('centerBounds' in b for b in route['blocks']) else
                                'continuous_horizontal_support_with_supplied_airtimes'),
            'gameReplay': 'not_run'}


def find_route(request, compiler, *, seconds=60, candidate_seconds=2, retries=1, improve=False,
               rank_equal_cost=False, optimize_goal=False, python_executable=None, cancel_event=None, on_attempt=None, work=None, on_candidate=None):
    started = time.monotonic()
    route = prepare_route(request)
    if number(seconds, 'search seconds') <= 0:
        raise ValueError('Search seconds must be positive')
    if number(candidate_seconds, 'candidate seconds') <= 0:
        raise ValueError('Candidate seconds must be positive')
    if type(retries) is not int or not 0 <= retries <= 10:
        raise ValueError('Retry count must be between zero and ten')
    if hasattr(compiler, 'derive_footprints') and (cancel_event is None or not cancel_event.is_set()):
        footprints = compiler.derive_footprints(route, min(seconds, candidate_seconds))
        if len(footprints) != len(route['blocks']):
            raise ValueError('The footprint compiler returned the wrong number of supports')
        for block, footprint in zip(route['blocks'], footprints):
            if not isinstance(footprint, list) or len(footprint) != 4:
                raise ValueError('Invalid derived footprint')
            for value in footprint:
                number(value, 'derived footprint')
            block['_derivedFootprint'] = footprint[:]
        if any('standingBounds' in block for block in route['blocks']):
            standing = compiler.derive_standing_footprints(route, min(seconds, candidate_seconds))
            for block, footprint in zip(route['blocks'], standing):
                block['_derivedStandingFootprint'] = footprint[:]

    def evaluate(allocation, stage, parent, available):
        start = time.monotonic()
        plan = schedule(route, allocation, stage)
        if plan['passageFailures']:
            return {'status': 'blocked', 'plan': plan, 'certificate': {'reason': 'passage_timing'}}
        if not plan['ticks']:
            return {'status': 'feasible', 'candidate': {'yaw': [], 'positions': [[route['initial']['position'][0],
                                                                                 route['initial']['position'][2]]]},
                    'plan': plan}
        try:
            if route.get('verticalProfiles'):
                from .block_contacts import validate_vertical_schedule
                vertical = validate_vertical_schedule(route, route['verticalProfiles'], plan)
                if not vertical['feasible']:
                    return {'status': 'blocked', 'plan': plan, 'certificate': {'reason': 'vertical_schedule'}}
                plan['vertical'] = dict(vertical['vertical'], referencePosition=vertical['y'], referenceVelocity=vertical['vy'])
            coefficients = compiler.compile(route, plan, max(0.001, min(available, candidate_seconds)))
            problem = dict(coefficients, version=1, ticks=plan['ticks'],
                           position=[route['initial']['position'][0], route['initial']['position'][2]],
                           velocity=[route['initial']['velocity'][0], route['initial']['velocity'][2]],
                           yaw=initial_guess(route, plan, parent), threshold=route.get('physicsThreshold', .005), walls=plan['walls'],
                           contacts=plan.get('contacts', []),
                           objective=dict(route.get('guideObjective', route['objective']), tick=plan['ticks']))
            if 'ladderTicks' in plan:
                problem['ladderTicks'] = plan['ladderTicks']
            problem = prepare(problem)
            if work is not None:
                work.proposal(problem)
            impossible = contradiction(problem)
            if impossible:
                return {'status': 'blocked', 'plan': plan, 'certificate': impossible,
                        'problemHash': fingerprint(problem)}
            if route['passages']:
                problem['yaw'] = passage_seed(problem, route, plan)
            if route['hitEvents']:
                from .hit_events import hit_seed
                problem['yaw'] = hit_seed(problem, route, plan)
            if route['ladderEvents']:
                from .movement_problem import waypoint_seed
                from .route import center_bounds
                waypoints = []
                for event in plan['events']:
                    if event['landing'] is not None:
                        target = center_bounds(route['blocks'][event['leg'] + 1])
                        waypoints.append((event['landing'] - 1, [(target[a] + target[a + 1]) / 2 for a in (0, 2)]))
                from .movement_problem import ladder_waypoints
                selections = [(plan['events'][e['leg']]['jump'], e) for e in route['ladderEvents']
                              if e['leg'] < len(plan['events']) and plan['events'][e['leg']]['landing'] is not None]
                waypoints.extend(ladder_waypoints(problem, selections))
                problem['yaw'] = waypoint_seed(problem, waypoints)
            polish = optimize_goal and stage == 'jump' and len(allocation) == len(route['airtimes'])
            fallback = objective_seed(problem) if polish else initial_guess(route, plan)
            if route['ladderEvents']:
                fallback = initial_guess(route, plan)
                normal = fallback[:]
                for selected in route['ladderEvents']:
                    if selected['leg'] >= len(plan['events']):
                        continue
                    event = plan['events'][selected['leg']]
                    if event['landing'] is None:
                        continue
                    wall = selected['wall']
                    angle = (0 if wall['direction'] > 0 else math.pi) if wall['axis'] == 'X' else (math.pi / 2 if wall['direction'] > 0 else -math.pi / 2)
                    for tick in range(event['jump'], event['landing']):
                        normal[tick] = math.degrees(angle - problem['base'][tick])
                if validate(problem, normal)['feasible']:
                    fallback = normal
                if validate(problem, fallback)['feasible']:
                    problem['yaw'], fallback = fallback, problem['yaw']
            if fallback != problem['yaw']:
                problem['fallbackYaw'] = fallback
            def checked_candidate(yaw, failures=None):
                candidate = validate(problem, yaw)
                failed = [{'state': tick, 'block': index, 'kind': kind}
                          for tick, index, kind in plan['supportChecks']
                          if not supports(route['blocks'][index], *candidate['positions'][tick], kind=kind)]
                failed.extend({'state': tick, 'block': index, 'kind': 'run_off_still_supported'}
                              for tick, index in plan['exitChecks']
                              if supports(route['blocks'][index], *candidate['positions'][tick], kind='run'))
                if failures is not None:
                    failures.extend(failed)
                if not candidate['feasible'] or failed:
                    return None
                candidate['rawMovement'] = [[(value if abs(value) >= problem['threshold'] else 0)
                    + problem['magnitude'][t] * (math.cos(angle) if axis == 0 else math.sin(angle))
                    for axis, value in enumerate(candidate['velocity'][t])]
                    for t, yaw in enumerate(candidate['yaw'])
                    for angle in [problem['base'][t] + math.radians(yaw)]]
                if 'movement' in problem:
                    candidate['movement'] = problem['movement']
                candidate['friction'] = problem['friction']
                return candidate

            def publish(candidate):
                if on_candidate is None or stage != 'jump' or len(allocation) != len(route['airtimes']):
                    return
                checked = checked_candidate(candidate['yaw'])
                if checked is not None:
                    on_candidate(dict(allocation=list(allocation), cost=sum(allocation), candidate=checked,
                        plan=copy.deepcopy(plan), problemHash=fingerprint(problem), route=copy.deepcopy(route),
                        groundRunTicks=sum(allocation)+sum(kind == 'step' for kind in route['kinds'])))

            diagnostics = []
            for attempt in range(retries + 1):
                if cancel_event is not None and cancel_event.is_set():
                    return {'status': 'cancelled'}
                remaining = available - (time.monotonic() - start)
                if remaining <= 0:
                    break
                budget = min(candidate_seconds * (attempt + 1), remaining)
                if attempt:
                    problem['yaw'] = fallback
                if work is not None:
                    work.before_solve(problem)
                result = solve(problem, seconds=budget, stop_on_feasible=not polish,
                               python_executable=python_executable, cancel_event=cancel_event, on_incumbent=publish)
                diagnostics.append({'status': result['status'], 'termination': result.get('termination'),
                                    'elapsedSeconds': result.get('elapsedSeconds'), 'error': result.get('error'),
                                    'problemHash': fingerprint(problem), 'request': copy.deepcopy(problem),
                                    'callOptions': dict(seconds=budget,stopOnFeasible=not polish)})
                if result['status'] == 'cancelled':
                    return {'status': 'cancelled', 'diagnostics': diagnostics}
                if result['feasible']:
                    failed = []
                    candidate = checked_candidate(result['candidate']['yaw'], failed)
                    diagnostics[-1]['supportFailures'] = failed
                    if candidate is not None:
                        return {'status': 'feasible', 'candidate': candidate, 'plan': plan,
                                'problemHash': fingerprint(problem), 'diagnostics': diagnostics}
                if result['status'] == 'error':
                    return {'status': 'error', 'diagnostics': diagnostics, 'error': result.get('error')}
            return {'status': 'unknown', 'plan': plan, 'diagnostics': diagnostics,
                    'problemHash': fingerprint(problem)}
        except TimeoutError as error:
            return {'status': 'unknown', 'error': str(error)}
        except (ValueError, RuntimeError, OSError) as error:
            return {'status': 'error', 'error': str(error)}

    remaining = max(1e-9, seconds - (time.monotonic() - started))
    result = search_allocations(route, evaluate, seconds=remaining, improve=improve, rank_equal_cost=rank_equal_cost,
                                cancel_event=cancel_event, on_attempt=on_attempt)
    result['elapsedSeconds'] = time.monotonic() - started
    result['mandatoryGroundTicks'] = sum(kind == 'step' for kind in route['kinds'])
    if result['best'] is not None:
        result['best']['groundRunTicks'] = result['best']['cost'] + result['mandatoryGroundTicks']
        result['best']['route'] = copy.deepcopy(route)
    return result
