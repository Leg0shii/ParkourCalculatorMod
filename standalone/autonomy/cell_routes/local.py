import collections
import copy
import hashlib
import json
import time

from .geometry import intersection
from .routes import audit_candidate, horizontal_trace
from .corridor_guards import movement_regions
from ..obstacle_geometry import expanded_box


class SearchWork:
    def __init__(self, max_solver_calls=None, *, deadline=None):
        if max_solver_calls is not None and (type(max_solver_calls) is not int or max_solver_calls < 1):
            raise ValueError('max_solver_calls must be a positive integer')
        self.max_solver_calls = max_solver_calls
        self.deadline = deadline
        self.started = time.monotonic()
        self.termination = None
        self.counts = collections.Counter()
        self.program_keys = set()
        self.first_proposal = None
        self.first_accepted = None
        self.program_domains = collections.Counter()

    def check(self):
        if self.deadline is not None and time.monotonic() >= self.deadline:
            self.termination = 'deadline'
        if self.max_solver_calls is not None and self.counts['solverCalls'] >= self.max_solver_calls:
            self.termination = 'numerical_call_limit'
        if self.termination:
            raise TimeoutError(self.termination)

    def program(self, program):
        profile = program['profile']
        key = hashlib.sha256(json.dumps([profile, program['ladderEvents']], sort_keys=True).encode()).hexdigest()
        self.program_keys.add(key)
        self.counts['completePrograms'] += 1
        self.counts['completeProgramInputs'] += profile['airtime']
        for u in program['movementUnits']:
            self.program_domains[u['type']] += 1
        self.counts['selectedFiniteContacts'] += len(profile.get('contactEvents', []))
        self.counts['selectedSneakInputs'] += sum(profile.get('sneak', []))

    def proposal(self, problem):
        self.counts['numericalProposals'] += 1
        if self.first_proposal is None:
            self.first_proposal = time.monotonic() - self.started

    def before_solve(self, problem):
        self.check()
        self.counts['solverCalls'] += 1

    def accepted(self, best):
        self.counts['acceptedBranches'] += 1
        if self.first_accepted is None:
            self.first_accepted = time.monotonic() - self.started

    def summary(self):
        return dict(self.counts, uniqueCompletePrograms=len(self.program_keys),
            unitsByType=dict(self.program_domains), firstProposalSeconds=self.first_proposal,
            firstAcceptedSeconds=self.first_accepted, maxSolverCalls=self.max_solver_calls,
            termination=self.termination)


class CountingCompiler:
    def __init__(self, compiler):
        self.compiler = compiler
        self.counts = collections.Counter()
        self.seconds = collections.Counter()

    def __getattr__(self, name):
        value = getattr(self.compiler, name)
        if not callable(value) or name not in ('compile', 'derive_footprints', 'derive_standing_footprints', 'check_scene'):
            return value
        def invoke(*args, **kwargs):
            started = time.monotonic()
            self.counts[name] += 1
            try:
                result = value(*args, **kwargs)
                if name == 'check_scene' and not result.get('feasible'):
                    self.counts['sceneValidationFailures'] += 1
                return result
            finally:
                self.seconds[name] += time.monotonic() - started
        return invoke

    def summary(self):
        return dict(calls=dict(self.counts), elapsedSeconds=dict(self.seconds))


class CorridorAdapter:
    def __init__(self, scene, corridor, gates, *, decomposition=None, forward=True):
        self.decomposition, self.forward = decomposition, forward
        self.progress_cache = {}
        self.scene, self.corridor, self.gates = scene, corridor, gates
        self.counts = collections.Counter()
        self.blocks = [b for b in scene['blocks'] if self.box_allowed(scene['collisionScene']['solids'][b['solidIndex']])]
        self.counts.update(availableBlocks=len(scene['blocks']), allowedBlocks=len(self.blocks))
        self.initial = copy.deepcopy(scene['initial'])

    def box_allowed(self, box):
        b = expanded_box(box)
        projected = (b[0], 0, b[2], b[3], 0, b[5])
        return any(intersection(projected, p) is not None for p in self.corridor.projection)

    def region_allowed(self, region):
        volumes = region.get('volumes', [region])
        allowed = any(any(intersection((r['x'], 0, r['z'], r['maxX'], 0, r['maxZ']), p) is not None
                          for p in self.corridor.projection) for r in volumes)
        self.counts['ladderRegionChecks'] += 1
        self.counts['ladderRegionDrops'] += int(not allowed)
        return allowed

    def context(self, context):
        self.counts['prefixesConditioned'] += 1
        self.counts['blockOpportunitiesRemoved'] += len(self.scene['blocks']) - len(self.blocks)
        context['scene'] = dict(context['scene'], blocks=self.blocks)
        context['regionAllowed'] = self.region_allowed
        context['boxAllowed'] = self.box_allowed
        if self.forward and self.decomposition is not None and self.corridor.stage_paths:
            context['prefixFilter'] = self.prefix

    def prefix(self, program):
        profile = program['profile']
        n = profile['airtime']
        rows = profile.get('positionConstraints', [])
        contacts = profile.get('contactEvents', [])
        key = json.dumps([n, rows, contacts], sort_keys=True)
        self.counts['forwardPrefixChecks'] += 1
        if key in self.progress_cache:
            self.counts['forwardCacheHits'] += 1
            accepted = self.progress_cache[key]
        else:
            domains = [[[-float('inf'), float('inf')], [-float('inf'), float('inf')]] for _ in range(n+1)]
            for axis, a in enumerate((0, 2)):
                value = self.initial['position'][a]
                domains[0][axis] = [value, value]
            def tighten(state, axis, lo, hi):
                domains[state][axis][0] = max(domains[state][axis][0], lo)
                domains[state][axis][1] = min(domains[state][axis][1], hi)
            for row in rows:
                if row['mode'] not in ('X', 'Z') or row.get('t2') is not None:
                    continue
                lo = row['rhs'] if row['cmp'] in ('GE','EQ') else -float('inf')
                hi = row['rhs'] if row['cmp'] in ('LE','EQ') else float('inf')
                tighten(row['t1'], 0 if row['mode']=='X' else 1, lo, hi)
            for contact in contacts:
                at, axis = contact['offset'], contact['axis']
                box = expanded_box(contact['box'])
                if axis == 'Y':
                    tighten(at, 0, box[0], box[3])
                    tighten(at, 1, box[2], box[5])
                elif axis == 'X':
                    tighten(at+1, 0, contact['plane'], contact['plane'])
                    tighten(at, 1, box[2], box[5])
                elif axis == 'Z':
                    tighten(at+1, 0, box[0], box[3])
                    tighten(at+1, 1, contact['plane'], contact['plane'])
            phases = [(domains[0][0], domains[0][1])]
            for t in range(n):
                phases.extend([(domains[t][0], domains[t][1]), (domains[t+1][0], domains[t][1]),
                               (domains[t+1][0], domains[t+1][1])])
            accepted = False
            for path in self.corridor.stage_paths:
                earliest = 0
                viable = True
                for x, z in phases:
                    self.counts['forwardPhaseUpdates'] += 1
                    found = None
                    for index in range(earliest, len(path)):
                        box = self.decomposition.cells[path[index]].bounds
                        if max(x[0],box[0]) <= min(x[1],box[3])+1e-7 and max(z[0],box[2]) <= min(z[1],box[5])+1e-7:
                            found = index
                            break
                    if found is None:
                        viable = False
                        break
                    earliest = found
                if viable:
                    accepted = True
                    break
            self.progress_cache[key] = accepted
        self.counts['forwardPrefixRejections'] += int(not accepted)
        return accepted

    def row(self, mode, state, cmp, value):
        return dict(leg=0, offset=state, mode=mode, cmp=cmp, rhs=value, name='cell_corridor:' + str(self.corridor.id))

    def scoped(self, scope):
        result = copy.copy(self)
        result.corridor = copy.deepcopy(self.corridor)
        active = {tuple(self.scene['collisionScene']['solids'][i]) for i in scope['solidIndices']}
        result.gates = [g for g in self.gates if tuple(g['box']) in active]
        ids = {g['id'] for g in result.gates}
        result.corridor.signature = tuple(token for token in self.corridor.signature if token[0] in ids)
        if scope['purpose'] == 'preliminary' and len(active) != len(self.scene['collisionScene']['solids']):
            if self.decomposition is not None:
                from .routes import projected_boxes
                result.corridor.projection = projected_boxes(self.decomposition, [c.id for c in self.decomposition.cells])
        result.validation_scope = scope
        return result

    def eligible(self, envelopes, through):
        from ..interactions import block_interaction
        result = copy.copy(self)
        result.corridor = copy.deepcopy(self.corridor)
        result.gates = [g for g in self.gates if any(not block_interaction(envelopes,g['box'],t)['ruledOut']
                                                  for t in range(through))]
        ids = {g['id'] for g in result.gates}
        result.corridor.signature = tuple(token for token in self.corridor.signature if token[0] in ids)
        result.counts['unreachableInteractionGatesRemoved'] += len(self.gates)-len(result.gates)
        return result

    def failure_diagnostics(self, checked, plan, candidate, vertical, partial, alternatives):
        failure = dict(reason=checked['reason'])
        if checked['reason'] == 'corridor_projection':
            points, references = horizontal_trace(dict(candidate, y=vertical['y']))
            segment = checked['segment']
            tick, phase = divmod(segment, 3)
            failure.update(segment=segment, input=tick, phase=('Y', 'X', 'Z')[phase],
                references=references[segment], start=points[segment], end=points[segment+1], tolerance=1e-7)
        else:
            mismatch = checked['mismatch']
            actual = checked['actual']
            expected = self.corridor.signature
            failure.update(mismatch=mismatch, expected=expected[mismatch] if mismatch < len(expected) else None,
                actual=actual[mismatch] if mismatch < len(actual) else None,
                actualSignature=actual, crossings=checked['events'])
        corridor = self.corridor.to_dict()
        key = hashlib.sha256(json.dumps(corridor, sort_keys=True).encode()).hexdigest()
        fields = ('positions', 'velocity', 'yaw', 'rawMovement', 'movement', 'friction', 'objective')
        return copy.deepcopy(dict(version=1, corridorHash=key, corridor=corridor, gates=self.gates,
            scope=dict(horizonInputs=len(candidate['positions'])-1, partialSignature=partial,
                       projection='modeled_horizon' if hasattr(self, 'validation_scope') else 'whole_trajectory',
                       modeledInteractionScope=getattr(self, 'validation_scope', 'unspecified')),
            failure=failure, plan=plan, candidate={k: candidate[k] for k in fields if k in candidate},
            vertical=vertical, repairAlternatives=alternatives))

    def candidate(self, plan, candidate, vertical, *, partial=False):
        self.counts['corridorAudits'] += 1
        n = len(candidate['positions']) - 1
        terminal_pre = any(tick == n-1 and kind == 'landing' for tick, _, kind in plan.get('supportChecks', []))
        checked = audit_candidate(self.corridor, dict(candidate, y=vertical['y']), self.gates,
                                  terminal_pre=terminal_pre, partial=partial)
        if checked['accepted']:
            return checked
        self.counts['corridorRejections'] += 1
        self.counts['corridorProjectionRejections' if checked['reason'] == 'corridor_projection'
                    else 'corridorSignatureRejections'] += 1
        alternatives = []
        if len(plan['events']) != 1 or plan['events'][0]['jump'] != 0:
            raise ValueError('Cell corridor adapter expects one original-state movement program')
        if checked['reason'] == 'corridor_projection':
            i = checked['segment']
            t, phase = divmod(i, 3)
            xs = [t, t+1] if phase == 1 else [t+1] if phase == 2 else [t]
            zs = [t, t+1] if phase == 2 else [t]
            if not hasattr(self, 'phase_regions'):
                self.phase_regions = {0: self.corridor.projection,
                    1: movement_regions(self.corridor.projection, 0),
                    2: movement_regions(self.corridor.projection, 2)}
            for p in self.phase_regions[phase]:
                rows = [self.row(mode, state, cmp, value) for mode, states, lo, hi in
                        [('X', xs, p[0], p[3]), ('Z', zs, p[2], p[5])]
                        for state in states for cmp, value in [('GE', lo), ('LE', hi)]]
                alternatives.append(rows)
        else:
            i = checked['mismatch']
            if i < len(self.corridor.signature) and i < len(checked['events']):
                expected, actual = self.corridor.signature[i], checked['events'][i]
                if expected[:2] == actual['token'][:2]:
                    gate = next(g for g in self.gates if g['id'] == expected[0])
                    refs = checked['references'][actual['segment']]
                    mode, state = ('X', refs[0]) if gate['lateral'] == 0 else ('Z', refs[1])
                    side = expected[2]
                    rows = [self.row(mode, state, 'LE', gate['lo'])] if side < 0 else [
                        self.row(mode, state, 'GE', gate['hi'])] if side > 0 else [
                        self.row(mode, state, 'GE', gate['lo'] + 2e-7), self.row(mode, state, 'LE', gate['hi'] - 2e-7)]
                    alternatives.append(rows)
        self.counts['corridorRepairBranches'] += len(alternatives)
        return dict(accepted=False, reason=checked['reason'], alternatives=alternatives,
                    repairComplete=False, actual=checked.get('actual'),
                    corridorAudit=self.failure_diagnostics(checked, plan, candidate, vertical, partial, alternatives))

    def summary(self):
        return dict(self.counts, forwardEnabled=self.forward and self.decomposition is not None and bool(self.corridor.stage_paths),
                    stagePathVariants=len(self.corridor.stage_paths), filtering='before_block_and_ladder_products;lazy_corridor_repairs_before_shared_scene_acceptance',
                    originalInitialState=self.initial)
