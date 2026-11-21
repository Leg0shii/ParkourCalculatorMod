"""Conditioned joint avoidance alternatives and their durable lazy frontier."""
import copy
import json
import time

from parkour_solver.constraints.avoidance import face_clearance
from parkour_solver.constraints.encounters import (
    canonical_rows, encounter_option, landing_segments, prepare_encounter, rows_entailed)
from parkour_solver.reachability.service import interaction_context_key
from parkour_solver.reachability.intervals import interaction_reachability
from parkour_solver.reachability.screen import condition_envelopes
from parkour_solver.search.identity import digest
from parkour_solver.spatial.surfaces import scene_geometry


GENERATION_POLICY = 20
STATE_VERSION = 11


class ConstraintVariants:
    @classmethod
    def for_plan(cls, route, profiles, plan, geometry, compiler, vertical, candidate, *, seconds,
                 state=None, deadline=None, owners=None, budget=None, scope=None, cancel_event=None):
        """Use the same builder for a legacy fixed-allocation collision repair.

        The external repair record owns these inputs, not serialized envelopes
        containing infinities. Coefficients/bounds can be rebuilt after restart;
        completed numerical requests and the exact generation cursor cannot.
        """
        from parkour_solver.reachability.intervals import reachable_envelopes
        coefficient = compiler.compile(route, plan, seconds)
        model = dict(coefficient, ticks=plan['ticks'], walls=plan['walls'], contacts=plan['contacts'],
            position=route['initial']['position'][::2], velocity=route['initial']['velocity'][::2],
            threshold=route.get('physicsThreshold', .005), vertical=vertical['vertical'],
            ladderTicks=plan.get('ladderTicks', [False]*(plan['ticks']+1)))
        contacts = [dict(c, axis='XYZ'[c['axis']], offset=c['state']-1, leg=0,
                         role='landing' if c['axis'] == 1 and c['direction'] == -1 else 'contact')
                    for c in plan.get('sceneContacts', [])]
        cases = [dict(c, input=c['input']+event['jump'])
                 for p, event in zip(profiles, plan['events']) for c in p.get('caseEvents', [])]
        profile = dict(position=vertical['y'], grounded=vertical['onGround'],
                       contactEvents=contacts, caseEvents=cases)
        absolute_route = dict(route, contactEvents=contacts, hitEvents=[], ladderEvents=[])
        envelope = reachable_envelopes(model, vertical['y'])
        scope = scope or dict(through=plan['ticks'], solidIndices=list(range(len(geometry['solids']))))
        return cls(absolute_route, profile, plan, geometry, envelope, scope, state=state,
                   deadline=deadline, reference=candidate['positions'], owners=owners, budget=budget, cancel_event=cancel_event,
                   objective=route.get('guideObjective', route['objective']))

    def __init__(self, route, profile, plan, geometry, envelopes, scope, *, checks=(), gates=(), signature=(), state=None,
                 deadline=None, reference=None, route_guards=None, owners=None, budget=None, cancel_event=None,
                 segment_solids=None, objective=None, settled=0, obstacles=None, walk_offs=()):
        """`settled`: the branch keeps exact rows before this tick, so obstacle stretches ending by it
        and contact checks before it get no item.

        `walk_offs`: run-off launch-support face selections the creation screen carried (program['walkOffs']),
        each one item over its options; the caller drops the floor checks they replace.

        `obstacles`: None enumerates every obstacle cluster on every stretch, screening partial
        combinations with the disk relaxation. A list of [stretch begin, solid index] pairs enumerates
        only the clusters holding those solids on those stretches (conflict-driven: the repair loop
        adds an obstacle once a solve hits it, `with_obstacles`) and screens with intervals only, since
        every branch is solved anyway."""
        if state is not None and 'obstacles' in state:
            obstacles = state['obstacles']
        self.arguments = dict(route=route, profile=profile, plan=plan, geometry=geometry, envelopes=envelopes,
            scope=scope, checks=checks, gates=gates, signature=signature, deadline=deadline, reference=reference,
            route_guards=route_guards, owners=owners, budget=budget, cancel_event=cancel_event,
            segment_solids=segment_solids, objective=objective, settled=settled, walk_offs=walk_offs)
        self.obstacles = None if obstacles is None else sorted(map(list, obstacles))
        self.settled = settled
        self.route, self.profile, self.plan = route, profile, plan
        # Relaxation screens optimize the configured progress objective at the plan horizon.
        self.objective = objective
        self.envelopes, self.deadline, self.through = envelopes, deadline, scope['through']
        self.route_guards, self.budget = route_guards, budget
        self.cancel_event = cancel_event
        context = digest(dict(version=GENERATION_POLICY, route={k: v for k, v in route.items() if k != 'warmYaw'},
            profile=profile, scope=scope, gates=list(gates), signature=signature, checks=list(checks), geometry=geometry,
            envelope=interaction_context_key(envelopes), owners=owners,
            segmentSolids=None if segment_solids is None else sorted(segment_solids.items()),
            routeGuards=route_guards.context if route_guards is not None else None,
            settled=settled, walkOffs=list(walk_offs), **({} if obstacles is None else dict(obstacles=self.obstacles))))
        if state is not None and (state.get('version') != STATE_VERSION or state.get('context') != context):
            raise ValueError('Incompatible constraint-generation continuation (policy%d/state%d)'
                             % (GENERATION_POLICY, STATE_VERSION))
        self.proof_context = context
        self.events = [dict(plan['events'][0], landing=self.through)]
        self.route_rows = []
        active = {tuple(geometry['solids'][i]) for i in scope['solidIndices']}
        selected = {token[0]: token for token in signature}
        for gate in gates:
            if gate['id'] not in selected or tuple(gate['box']) not in active:
                continue
            _, direction, side = selected[gate['id']]
            self.route_rows.append(dict(leg=0, offset=self.through, mode='XYZ'[gate['axis']],
                cmp='GE' if direction > 0 else 'LE', rhs=gate['plane'], name='route:crossing'))
        items = ([dict(routeGuard=True)] if route_guards is not None else [])
        items.extend(dict(rows=c['options'], role='contact_region', surface=c['surface'], owner=c['owner'], offset=c['offset'])
                     for c in profile.get('regionChoices', []) if c['offset'] <= self.through)
        # One option is a fixed row in effect; two (a diagonal tie) branch, without family climbs or conflict joins.
        items.extend(dict(rows=[[row] for row in w['options']], role='walk_off', solids=w['solids'], offset=w['offset'])
                     for w in walk_offs if settled <= w['offset'] < self.through)
        # Temporal order is a traversal preference. All items share the original
        # model; neither overlap nor scene order declares independent subproblems.
        self.geometry, self.scope_indices = scene_geometry(geometry), scope['solidIndices']
        names = {i: owners[i] if owners is not None else 'solid:'+str(i) for i in scope['solidIndices']}
        reachable = segment_solids or {}
        for begin, end in landing_segments(profile, self.through):
            if end <= settled:
                continue
            # The obstacle is built from the solids this stretch can reach: an unreachable member's
            # space is free for the stretch, and dropping it can split a touching cluster.
            basis = (sorted(set(self.scope_indices) & set(reachable[begin])) if begin in reachable
                     else list(self.scope_indices))
            if self.obstacles is not None:
                # A solve that hit a solid proves the stretch reaches it.
                basis = sorted(set(basis) | {s for b, s in self.obstacles if b == begin and s in self.scope_indices})
            for c in self.geometry.clusters(basis):
                if self.obstacles is not None and not any(b == begin and s in c.members for b, s in self.obstacles):
                    continue
                item = dict(clusterId=c.id, basis=basis, members=list(c.members), names=[names[i] for i in c.members],
                            begin=begin, end=end, owner='+'.join(sorted({names[i] for i in c.members})))
                items.append(item)
        items += [dict(contactCheck=check, identity=dict(solid=check['block']['solidIndex'], offset=check['offset']))
                  for check in checks
                  if check['block']['solidIndex'] in scope['solidIndices'] and settled <= check['offset'] <= self.through]
        self.state = copy.deepcopy(state) if state is not None else dict(
            version=STATE_VERSION, context=context, items=items, exclusions=[], screenExclusions=[],
            pending=[dict(item=0, rows=self.route_rows, passages=[], choices=[])], seen=[], partialSeen=[], emitted=0,
            exhausted=False, reference=copy.deepcopy(reference), preparationSteps=0, duplicateVariants=0,
            **({} if self.obstacles is None else dict(obstacles=self.obstacles)),
            metrics={}, uncertainInteractions=[], timings=dict(preparationSeconds=0., variantGenerationSeconds=0.,
                                     conditionalAnalysisSeconds=0.))
        self.state.setdefault('encounterLog', [])
        self.state.setdefault('droppedOptions', [])
        self.items, self.exclusions = self.state['items'], self.state['exclusions']
        self.reference = self.state['reference']
        self.seen, self.partial_seen = set(self.state['seen']), set(self.state['partialSeen'])
        self.analysis_cache = {}
        self.preparation_cache = {}
        self._active_envelopes = None

    @property
    def conflict_driven(self):
        return self.obstacles is not None

    def obstacle_key(self, solid, tick):
        """[stretch begin, solid] for a solve that hit `solid` on movement `tick`; None on a settled stretch.

        Rows constrain states, and the movement ends in state tick+1 (a landing's first state when it lands)."""
        state = tick+1
        for begin, end in landing_segments(self.profile, self.through):
            if begin <= state < end or state == end == self.through:
                return [begin, solid] if end > self.settled else None
        return None

    def with_obstacles(self, obstacles):
        """A fresh generator over these obstacles (same inputs, no saved state)."""
        return ConstraintVariants(**self.arguments, obstacles=obstacles)

    def metric(self, name, count=1):
        metrics = self.state['metrics']
        metrics[name] = metrics.get(name, 0)+count

    def coordinate(self, axis, tick, envelopes):
        if self.reference is not None:
            return self.reference[tick][0 if axis == 'X' else 1]
        return sum(envelopes['states'][tick][axis])/2

    def check(self):
        if self.cancel_event is not None and self.cancel_event.is_set():
            raise TimeoutError('cancelled')
        if self.budget is not None:
            self.budget()
        if self.deadline is not None and time.monotonic() >= self.deadline:
            raise TimeoutError('constraint_generation_deadline')

    def face_row(self, face, t):
        margin = face_clearance(self.route, 0, t, face, self.route['supportClearance'])
        return dict(leg=0, offset=t, mode=face['axis'], t1=t, cmp='GE' if face['direction'] > 0 else 'LE',
                    rhs=face['plane']+face['direction']*margin, name='avoidance')

    def effective_key(self, rows):
        # Base-context entailment only: two sibling branches never borrow one
        # another's tightened bounds to declare their requirements identical.
        bounds = []
        for row in canonical_rows(rows):
            t, axis = row['t1'], row['mode']
            lo, hi = self.envelopes['states'][t][axis]
            if (row['cmp'] == 'GE' and lo >= row['rhs']) or (row['cmp'] == 'LE' and hi <= row['rhs']):
                continue
            bounds.append((t, axis, row['cmp'], row['rhs']))
        return digest(bounds)

    def analyze(self, rows):
        self.check()
        key = self.effective_key(rows)
        if key in self.analysis_cache:
            self.metric('analysisHits')
            return self.analysis_cache[key]
        started = time.monotonic()
        try:
            self.metric('analysisMisses')
            result = condition_envelopes(self.envelopes, rows, refine=self.obstacles is None, objective=self.objective)
            # A cache is disposable acceleration, not a retained-alternative cap.
            if len(self.analysis_cache) >= 128:
                self.analysis_cache.pop(next(iter(self.analysis_cache)))
            self.analysis_cache[key] = result
            return result
        finally:
            self.state['timings']['conditionalAnalysisSeconds'] += time.monotonic()-started

    def _screen(self, rows):
        return self.analyze(rows)['proof']

    def excluded(self, rows, proof, item=None, option=None):
        entry = dict(rowsHash=digest(rows), proof=proof)
        if option is not None:
            entry.update(item=item, optionRows=option['rows'], passage=option.get('passage'))
        self.state['screenExclusions'].append(entry)
        self.metric('screenExclusions')

    def prepare_item(self, item):
        started = time.monotonic()
        envelopes = self._active_envelopes or self.envelopes
        try:
            if 'contactCheck' in item:
                self.check()
                from parkour_solver.constraints.contacts import avoid_contact_rows
                check = item['contactCheck']
                r, t = check['region'], check['offset']
                contact = dict(regions=[dict(tick=t, bounds=[r[0], -float('inf'), r[1], r[2], float('inf'), r[3]], interior=True)])
                if interaction_reachability(envelopes, contact)['ruledOut']:
                    item['rows'] = [[]]
                else:
                    point = self.reference[t] if self.reference is not None else [(r[0]+r[2])/2, (r[1]+r[3])/2]
                    item['rows'] = [[row] for row in avoid_contact_rows(dict(check, position=point), self.route['supportClearance'])
                                    if not interaction_reachability(envelopes, [row])['ruledOut']]
                del item['contactCheck']
            if 'clusterId' in item:
                prepare_encounter(self, item, envelopes)
        finally:
            self.state['timings']['preparationSeconds'] += time.monotonic()-started

    def option(self, item, cursor):
        self.prepare_item(item)
        if item.get('routeGuard'):
            return self.route_guards.option(cursor)
        if 'rows' in item:
            index = cursor.get('index', 0)
            if index == len(item['rows']):
                return None
            cursor['index'] = index+1
            return dict(rows=item['rows'][index])
        return encounter_option(self, item, cursor, self._active_envelopes or self.envelopes)

    def entailed(self, selected, required):
        return rows_entailed(self.envelopes, selected, required)

    def __iter__(self):
        return self

    def __next__(self):
        started = time.monotonic()
        try:
            return self._next_variant()
        finally:
            self._active_envelopes = None
            self.state['timings']['variantGenerationSeconds'] += time.monotonic()-started

    def choose_item(self, node):
        remaining = node.setdefault('remaining', list(range(node['item'], len(self.items))))
        if 'prepared' in node:
            return
        candidates = []
        context = self.effective_key(node['rows'])
        for index in remaining:
            self.check()
            key = (context, index)
            if key not in self.preparation_cache:
                item = copy.deepcopy(self.items[index])
                self.prepare_item(item)
                if len(self.preparation_cache) >= 256:
                    self.preparation_cache.pop(next(iter(self.preparation_cache)))
                self.preparation_cache[key] = item
            item = self.preparation_cache[key]
            if 'clusterId' not in item:
                rank = (-1, index, 0)
            else:
                phases = item['phases']
                rank = (phases[0]['at'] if phases else -1,
                        sum(len(p['options']) for p in phases), index)
            candidates.append((rank, index, item))
            if item.get('routeGuard') or item.get('role') == 'contact_region':
                break
        _, index, item = min(candidates, key=lambda v: v[0])
        node['chosenItem'], node['prepared'] = index, item

    def branch_choice(self, item, option, choice):
        """The intermediate choice a decision check makes for this item: a closed obstacle segment's
        side family, or a contact region's or contact check's exact rows. Open segments stay undecided."""
        if 'clusterId' in item:
            ground = self.profile.get('grounded', [])
            if item['end'] < self.through or self.through < len(ground) and ground[self.through]:
                return dict(kind='cluster', clusterId=item['clusterId'], begin=item['begin'], end=item['end'],
                            sides=choice.get('sides', []))
            return None
        rows = digest(canonical_rows(option['rows']))
        if item.get('role') == 'contact_region':
            return dict(kind='region', surface=item['surface'], offset=item['offset'], rows=rows)
        if item.get('role') == 'walk_off':
            return dict(kind='walk_off', solids=item['solids'], offset=item['offset'], rows=rows)
        if 'identity' in item:
            return dict(kind='contact', **item['identity'], rows=rows)
        return None

    def _next_variant(self):
        while self.state['pending']:
            self.check()
            node = self.state['pending'][-1]
            analysis = self.analyze(node['rows'])
            if analysis['proof']['ruledOut']:
                self.excluded(node['rows'], analysis['proof'])
                self.state['pending'].pop()
                continue
            self._active_envelopes = analysis['envelopes']
            remaining = node.setdefault('remaining', list(range(node['item'], len(self.items))))
            if not remaining:
                rows = canonical_rows(node['rows'])
                key = self.effective_key(rows)
                self.state['pending'].pop()
                ordered = sorted(node['choices'], key=lambda c: c['item'])
                choices = sorted((c['branch'] for c in ordered if 'branch' in c),
                                 key=lambda c: json.dumps(c, sort_keys=True))
                choices_key = digest(choices)
                if key in self.seen:
                    self.state['duplicateVariants'] += 1
                    continue
                self.seen.add(key)
                self.state['seen'].append(key)
                self.state['emitted'] += 1
                return dict(rows=[{k: v for k, v in r.items() if k != 't1'} for r in rows], passages=[],
                    selectedPassages=node['passages'], semanticObservations=[dict(p, kind='passage') for p in node['passages']],
                    score=self.state['emitted'], family=digest([[c['item'], c.get('sides', c.get('key'))] for c in ordered]),
                    switches=[t for c in ordered for t in c.get('switches', [])],
                    branchChoices=choices, branchKey=choices_key)
            self.choose_item(node)
            item = node['prepared']
            if 'rows' in item and any(rows_entailed(analysis['envelopes'], [], rows) for rows in item['rows']):
                remaining.remove(node.pop('chosenItem'))
                node.pop('cursor', None)
                node.pop('prepared', None)
                continue
            # An option can be expensive to analyze. Publish it before that
            # interruptible boundary, so its cursor cannot lose the selected child.
            if 'selected' not in node:
                option = self.option(item, node.setdefault('cursor', {}))
                if option is None:
                    self.state['pending'].pop()
                    continue
                node['selected'] = option
            option = node['selected']
            passage = option.get('passage')
            rows = canonical_rows(node['rows']+option['rows'])
            child_analysis = self.analyze(rows)
            if child_analysis['proof']['ruledOut']:
                self.excluded(rows, child_analysis['proof'], node['chosenItem'], option)
                del node['selected']
                continue
            choice = (dict(item=node['chosenItem'], sides=[side['key'] for side in passage['faces']],
                           switches=[at//3 for at in passage['switchPhases']]) if passage is not None
                      else dict(item=node['chosenItem'], key=self.effective_key(option['rows'])))
            branch = self.branch_choice(item, option, choice)
            if branch is not None:
                choice['branch'] = branch
            child = dict(item=0, remaining=[i for i in remaining if i != node['chosenItem']], rows=rows,
                         passages=node['passages']+([passage] if passage is not None else []),
                         choices=node['choices']+[choice])
            # Counts and nominal face histories have no remaining resource
            # capability once an encounter is assigned. The unassigned item set
            # and exact original model are part of this iterator's context.
            partial_key = digest([child['remaining'], self.effective_key(rows)])
            del node['selected']
            if partial_key in self.partial_seen:
                self.state['duplicateVariants'] += 1
                continue
            self.partial_seen.add(partial_key)
            self.state['partialSeen'].append(partial_key)
            self.state['pending'].append(child)
        self.state['exhausted'] = True
        raise StopIteration
