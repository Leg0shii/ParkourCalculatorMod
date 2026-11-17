"""Conditioned joint avoidance alternatives and their durable lazy frontier."""
import copy
import time

from parkour_solver.constraints.avoidance import face_clearance
from parkour_solver.constraints.encounters import (
    canonical_rows, encounter_option, landing_segments, prepare_encounter, rows_entailed)
from parkour_solver.reachability.service import interaction_context_key
from parkour_solver.reachability.intervals import interaction_reachability
from parkour_solver.reachability.screen import condition_envelopes
from parkour_solver.search.identity import digest


GENERATION_POLICY = 8
STATE_VERSION = 3


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
                   deadline=deadline, reference=candidate['positions'], owners=owners, budget=budget, cancel_event=cancel_event)

    def __init__(self, route, profile, plan, geometry, envelopes, scope, *, checks=(), gates=(), signature=(), state=None,
                 deadline=None, reference=None, route_guards=None, required_alternatives=None, owners=None, budget=None, cancel_event=None):
        self.route, self.profile, self.plan = route, profile, plan
        self.envelopes, self.deadline, self.through = envelopes, deadline, scope['through']
        self.route_guards, self.budget = route_guards, budget
        self.cancel_event = cancel_event
        context = digest(dict(version=GENERATION_POLICY, route={k: v for k, v in route.items() if k != 'warmYaw'},
            profile=profile, scope=scope, gates=list(gates), signature=signature, checks=list(checks), geometry=geometry,
            envelope=interaction_context_key(envelopes), owners=owners,
            routeGuards=route_guards.context if route_guards is not None else None,
            requiredAlternatives=required_alternatives))
        if state is not None and (state.get('version') != STATE_VERSION or state.get('context') != context):
            raise ValueError('Incompatible constraint-generation continuation (policy8/state3)')
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
        if required_alternatives is not None:
            items.append(dict(rows=required_alternatives, role='required_contact'))
        # Temporal order is a traversal preference. All items share the original
        # model; neither overlap nor scene order declares independent subproblems.
        for begin, end in landing_segments(profile, self.through):
            items.extend(dict(solidIndex=i, box=geometry['solids'][i], begin=begin, end=end,
                              owner=owners[i] if owners is not None else 'solid:'+str(i))
                         for i in scope['solidIndices'])
        items += [dict(contactCheck=check) for check in checks
                  if check['block']['solidIndex'] in scope['solidIndices'] and check['offset'] <= self.through]
        self.state = copy.deepcopy(state) if state is not None else dict(
            version=STATE_VERSION, context=context, items=items, exclusions=[], screenExclusions=[],
            pending=[dict(item=0, rows=self.route_rows, passages=[])], seen=[], partialSeen=[], emitted=0,
            exhausted=False, reference=copy.deepcopy(reference), preparationSteps=0, duplicateVariants=0,
            metrics={}, uncertainInteractions=[], timings=dict(preparationSeconds=0., variantGenerationSeconds=0.,
                                     conditionalAnalysisSeconds=0.))
        self.items, self.exclusions = self.state['items'], self.state['exclusions']
        self.reference = self.state['reference']
        self.seen, self.partial_seen = set(self.state['seen']), set(self.state['partialSeen'])
        self.analysis_cache = {}
        self.preparation_cache = {}
        self._active_envelopes = None

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
            result = condition_envelopes(self.envelopes, rows)
            # A cache is disposable acceleration, not a retained-alternative cap.
            if len(self.analysis_cache) >= 128:
                self.analysis_cache.pop(next(iter(self.analysis_cache)))
            self.analysis_cache[key] = result
            return result
        finally:
            self.state['timings']['conditionalAnalysisSeconds'] += time.monotonic()-started

    def _screen(self, rows):
        return self.analyze(rows)['proof']

    def excluded(self, rows, proof):
        self.state['screenExclusions'].append(dict(rowsHash=digest(rows), proof=proof))
        self.metric('screenExclusions')

    def prepare_item(self, item):
        started = time.monotonic()
        envelopes = self._active_envelopes or self.envelopes
        try:
            if 'contactCheck' in item:
                self.check()
                from parkour_solver.constraints.contacts import avoid_contact_rows
                from parkour_solver.scene.route import center_bounds
                check = item['contactCheck']
                b, t = center_bounds(check['block']), check['offset']
                contact = dict(regions=[dict(tick=t, bounds=[b[0], -float('inf'), b[2], b[1], float('inf'), b[3]], interior=True)])
                if interaction_reachability(envelopes, contact)['ruledOut']:
                    item['rows'] = [[]]
                else:
                    point = self.reference[t] if self.reference is not None else [(b[0]+b[1])/2, (b[2]+b[3])/2]
                    item['rows'] = [[row] for row in avoid_contact_rows(dict(check, position=point), self.route['supportClearance'])
                                    if not interaction_reachability(envelopes, [row])['ruledOut']]
                del item['contactCheck']
            if 'solidIndex' in item:
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
            if 'solidIndex' not in item:
                rank = (-1, index, 0)
            else:
                phases = item['phases']
                rank = (phases[0]['at'] if phases else -1,
                        sum(len(p['options']) for p in phases), index)
            candidates.append((rank, index, item))
            if item.get('routeGuard') or item.get('role') == 'required_contact':
                break
        _, index, item = min(candidates, key=lambda v: v[0])
        node['chosenItem'], node['prepared'] = index, item

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
                if key in self.seen:
                    self.state['duplicateVariants'] += 1
                    continue
                self.seen.add(key)
                self.state['seen'].append(key)
                self.state['emitted'] += 1
                return dict(rows=[{k: v for k, v in r.items() if k != 't1'} for r in rows], passages=[],
                    selectedPassages=node['passages'], semanticObservations=[dict(p, kind='passage') for p in node['passages']],
                    score=self.state['emitted'])
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
            rows = canonical_rows(node['rows']+option['rows'])
            child_analysis = self.analyze(rows)
            if child_analysis['proof']['ruledOut']:
                self.excluded(rows, child_analysis['proof'])
                del node['selected']
                continue
            child = dict(item=0, remaining=[i for i in remaining if i != node['chosenItem']], rows=rows,
                         passages=node['passages']+([option['passage']] if 'passage' in option else []))
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
