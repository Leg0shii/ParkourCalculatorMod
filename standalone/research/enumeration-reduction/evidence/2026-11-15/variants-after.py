import copy
import time
import itertools
from bisect import bisect_left, bisect_right

from parkour_solver.constraints.avoidance import faces, face_chains, intervals, consecutive, blockage_counts, compile_passages, passage_kind, face_clearance
from parkour_solver.scene.geometry import expanded_box
from parkour_solver.reachability.service import query_interaction, interaction_context_key, PROOF_POLICY
from parkour_solver.reachability.intervals import interaction_reachability
from parkour_solver.search.identity import digest


class _OptionKeys:
    """Disposable keys for one call over an already prepared, unchanged item.

    Freeze effective face rows at this boundary. Nothing is cached across an
    item/route/envelope mutation, a returned option, or external resume. Hashes
    still come from the existing canonical identity functions.
    """
    LIMIT = 1024

    def __init__(self, source, item):
        self.source = source
        self.chains = tuple(tuple(chain) for chain in item['chains'])
        faces = tuple(dict(face) for face in item['faces'])
        self.chosen = tuple(tuple(faces[i] for i in chain) for chain in self.chains)
        self.rows, self.ticks, self.tokens = [], [], []
        empty = source.effective_key(())
        for face in item['faces']:
            ticks, tokens = [], []
            if face['axis'] != 'Y':
                for tick in item['required'][face['axis']]:
                    row = source.face_row(face,tick)
                    if source.effective_key((row,)) == empty:
                        continue
                    ticks.append(tick); tokens.append(len(self.rows))
                    self.rows.append(tuple(row.items()))
            self.ticks.append(tuple(ticks)); self.tokens.append(tuple(tokens))
        self.row_keys, self.node_keys = {():empty}, {}

    @staticmethod
    def remember(cache, key, value):
        if len(cache) >= _OptionKeys.LIMIT:
            cache.pop(next(iter(cache)))
        cache[key] = value
        return value

    def effective(self, chain, depth, start, gates):
        ranges = intervals(self.chosen[chain][:depth+1],gates,0,start)
        tokens = tuple(token for face,(begin,end) in zip(self.chains[chain],ranges[:-1])
            for token in self.tokens[face][bisect_left(self.ticks[face],begin):bisect_right(self.ticks[face],end)])
        if tokens in self.row_keys:
            return self.row_keys[tokens]
        # Tokens refer to frozen rows, retaining order and exact scalar types,
        # including signed zero. Python float equality is not a cache identity.
        return self.remember(self.row_keys,tokens,
            self.source.effective_key(dict(self.rows[token]) for token in tokens))

    def node(self, chain, depth, start, gates):
        key = (chain,depth,start,self.effective(chain,depth,start,gates))
        if key in self.node_keys:
            return self.node_keys[key]
        return self.remember(self.node_keys,key,digest(list(key)))


class ConstraintVariants:
    def __init__(self, route, profile, plan, geometry, envelopes, scope, *, checks=(), gates=(), signature=(), state=None, deadline=None, reference=None, route_guards=None, required_alternatives=None):
        self.route, self.profile, self.plan = route, profile, plan
        self.envelopes, self.deadline, self.through = envelopes, deadline, scope['through']
        self.route_guards = route_guards
        context = digest(dict(version=7, route={k: v for k, v in route.items() if k != 'warmYaw'},
            profile=profile, scope=scope, gates=list(gates), signature=signature, checks=list(checks), geometry=geometry,
            envelope=interaction_context_key(envelopes),
            routeGuards=route_guards.context if route_guards is not None else None,
            requiredAlternatives=required_alternatives))
        if state is not None and (state.get('version') != 2 or state.get('context') != context):
            raise ValueError('Incompatible constraint-generation continuation (policy7/state2)')
        self.proof_context = context
        self.events = [dict(plan['events'][0], landing=self.through)]
        self.route_rows = []
        active = set(scope['solidIndices'])
        selected = {token[0]: token for token in signature}
        for gate in gates:
            if gate['id'] not in selected or tuple(gate['box']) not in {tuple(geometry['solids'][i]) for i in active}:
                continue
            _, direction, side = selected[gate['id']]
            self.route_rows.append(dict(leg=0, offset=self.through, mode='XYZ'[gate['axis']],
                cmp='GE' if direction > 0 else 'LE', rhs=gate['plane'], name='route:crossing'))
        items = ([dict(routeGuard=True)] if route_guards is not None else [])
        if required_alternatives is not None:
            items.append(dict(rows=required_alternatives,role='required_contact'))
        items += [dict(solidIndex=i, box=geometry['solids'][i], preparation=dict(stage='phases', phase=0,
            possible=[], excluded=[])) for i in scope['solidIndices']]
        items += [dict(contactCheck=check) for check in checks
                  if check['block']['solidIndex'] in active and check['offset'] <= self.through]
        self.state = copy.deepcopy(state) if state is not None else dict(
            version=2, context=context, items=items, exclusions=[],
            pending=[dict(item=0, rows=self.route_rows, passages=[])], seen=[], emitted=0,
            exhausted=False, reference=copy.deepcopy(reference), preparationSteps=0)
        self.items, self.exclusions = self.state['items'], self.state['exclusions']
        self.reference = self.state['reference']
        self.seen = set(self.state['seen'])
        self.state.setdefault('screenExclusions', [])
        self.state.setdefault('duplicateVariants', 0)
        self.state.setdefault('partialSeen', [])
        self.partial_seen = set(self.state['partialSeen'])
        self.state.setdefault('timings',dict(preparationSeconds=0.,variantGenerationSeconds=0.))

    def prepare_item(self, item):
        started=time.monotonic()
        try:
            return self._prepare_item(item)
        finally:
            self.state['timings']['preparationSeconds']+=time.monotonic()-started

    def _prepare_item(self, item):
        """Resume between phases, mask states and dynamic-program table rows.

        All mutable preparation is owned by this iterator and JSON serializable.
        A timeout leaves the exact unprocessed unit at the saved cursor.
        """
        if 'contactCheck' in item:
            self.check()
            from parkour_solver.constraints.contacts import avoid_contact_rows
            from parkour_solver.scene.route import center_bounds
            check = item['contactCheck']
            b, t = center_bounds(check['block']), check['offset']
            contact = dict(regions=[dict(tick=t, bounds=[b[0], -float('inf'), b[2], b[1], float('inf'), b[3]], interior=True)])
            if interaction_reachability(self.envelopes, contact)['ruledOut']:
                item['rows'] = [[]]
            else:
                point = self.reference[t] if self.reference is not None else [(b[0]+b[1])/2, (b[2]+b[3])/2]
                options = avoid_contact_rows(dict(check, position=point), self.route['supportClearance'])
                options.sort(key=lambda r: max(0, (r['rhs']-point[0 if r['mode']=='X' else 1])*(1 if r['cmp']=='GE' else -1)))
                item['rows'] = [[row] for row in options]
            del item['contactCheck']
        prep = item.get('preparation')
        if prep is None:
            return
        n = self.through
        while prep['stage'] != 'done':
            self.check()
            self.state['preparationSteps'] += 1
            stage = prep['stage']
            if stage == 'phases':
                at = prep['phase']
                if at < 3*n:
                    tick, phase = at//3, ('Y', 'X', 'Z')[at%3]
                    checked = query_interaction(dict(box=item['box'], role='unwanted_contact', interior=True,
                        windows=[dict(tick=tick, phase=phase)], owner=item['solidIndex']),
                        dict(envelopes=self.envelopes), context_key=self.proof_context)
                    if checked['ruledOut']:
                        prep['excluded'].append(dict(checked, tick=tick, phase=phase))
                    else:
                        prep['possible'].append([tick, phase])
                    prep['phase'] += 1
                    continue
                possible = prep['possible']
                if not possible:
                    self.exclusions.append(dict(solidIndex=item['solidIndex'], reason='all_contact_phases_unreachable',
                        phaseExclusions=prep['excluded'], role='unwanted_contact', proofPolicy=PROOF_POLICY))
                    item['rows'] = [[]]
                    prep['stage'] = 'done'
                    continue
                required = {axis: set() for axis in ('X', 'Z')}
                for t, phase in possible:
                    required['X'].update((t,) if phase == 'Y' else (t,t+1) if phase == 'X' else (t+1,))
                    required['Z'].update((t,t+1) if phase == 'Z' else (t,))
                item.update(faces=faces(item['box']), possiblePhases=possible,
                    relevantStates=sorted({t+offset for t, _ in possible for offset in (0,1)}),
                    required={axis: sorted(ts) for axis, ts in required.items()}, masks=[], guaranteed=[], costs=[],
                    chains=[], tables=[])
                prep.update(stage='masks', face=0, tick=0)
            elif stage == 'masks':
                j, t = prep['face'], prep['tick']
                if j == len(item['faces']):
                    caps = self.route['passageCaps']
                    chains = []
                    for length in range(caps['total']+1):
                        for chain in face_chains(length):
                            if any(k >= len(item['faces']) for k in chain):
                                continue
                            chosen = [item['faces'][k] for k in chain]
                            if any(a['axis'] == b['axis'] for a,b in zip(chosen,chosen[1:])):
                                continue
                            counts = blockage_counts(chosen)
                            if any(counts[k] > caps[k] for k in caps):
                                continue
                            if item['masks'][chain[0]][0] and item['masks'][chain[-1]][n]:
                                chains.append(list(chain))
                    item['chains'] = chains
                    prep.update(stage='tables', chain=0, depth=None)
                    continue
                face = item['faces'][j]
                if t == 0:
                    item['masks'].append([]); item['guaranteed'].append([]); item['costs'].append([])
                relevant = item['relevantStates'] if face['axis'] == 'Y' else item['required'][face['axis']]
                inactive = t not in relevant
                if inactive:
                    mask, known = True, True
                elif face['axis'] == 'Y':
                    mask = face['direction']*(self.profile['position'][t]-face['plane']) >= 0
                    known = mask
                else:
                    row = self.face_row(face, t)
                    mask = not interaction_reachability(self.envelopes, [row])['ruledOut']
                    lo, hi = self.envelopes['states'][t][face['axis']]
                    known = lo >= row['rhs'] if face['direction'] > 0 else hi <= row['rhs']
                coordinate = (self.profile['position'][t] if face['axis'] == 'Y' else
                    self.reference[t][0 if face['axis'] == 'X' else 1] if self.reference is not None else
                    sum(self.envelopes['states'][t][face['axis']])/2)
                item['masks'][j].append(mask); item['guaranteed'][j].append(known)
                item['costs'][j].append(max(0, face['direction']*(face['plane']-coordinate))**2 if not inactive else 0.)
                prep['tick'] += 1
                if prep['tick'] == n+1:
                    prep['face'] += 1; prep['tick'] = 0
            elif stage == 'tables':
                j = prep['chain']
                if j == len(item['chains']):
                    ranks = [(item['tables'][i][0][0], chain, item['tables'][i]) for i,chain in enumerate(item['chains'])
                             if item['tables'][i][0][0] is not None]
                    ranks.sort(key=lambda v: (v[0], len(v[1]), v[1]))
                    item['chains'] = [v[1] for v in ranks]; item['tables'] = [v[2] for v in ranks]
                    prep['stage'] = 'done'
                    continue
                chain = item['chains'][j]
                if prep['depth'] is None:
                    item['tables'].append([[None]*(n+1) for _ in chain])
                    prep['depth'] = len(chain)-1
                depth = prep['depth']
                face = chain[depth]
                table = item['tables'][j]
                # Backwards suffix minimum is the old chain-cost recurrence,
                # with O(n) work per depth rather than nested gate rescans.
                best = None
                suffix = 0.
                for start in range(n, -1, -1):
                    if not item['masks'][face][start]:
                        best = None; suffix = 0.
                        continue
                    cost = item['costs'][face][start]
                    if depth == len(chain)-1:
                        suffix += cost
                        if start == n or table[depth][start+1] is not None:
                            table[depth][start] = suffix
                    else:
                        beginning = start+int(consecutive(item['faces'][face], item['faces'][chain[depth+1]]))
                        following = table[depth+1][beginning] if beginning <= n else None
                        candidates = ([following] if following is not None else []) + ([best] if best is not None else [])
                        best = cost+min(candidates) if candidates else None
                        table[depth][start] = best
                prep['depth'] -= 1
                if prep['depth'] < 0:
                    prep['chain'] += 1; prep['depth'] = None
        # Drop scan evidence only after completion; exclusion proofs are retained.
        item.pop('preparation', None)

    def excluded(self, rows, proof):
        self.state['screenExclusions'].append(dict(rowsHash=digest(rows),proof=proof))

    def face_row(self, face, t):
        margin = face_clearance(self.route, 0, t, face, self.route['supportClearance'])
        return dict(mode=face['axis'], t1=t, cmp='GE' if face['direction'] > 0 else 'LE',
                    rhs=face['plane']+face['direction']*margin)

    def check(self):
        if self.deadline is not None and time.monotonic() >= self.deadline:
            raise TimeoutError('constraint_generation_deadline')

    def effective_key(self, rows):
        bounds = {}
        for row in rows:
            t, axis = row.get('t1', row.get('offset')), row['mode']
            lo, hi = self.envelopes['states'][t][axis]
            if row['cmp'] == 'GE' and lo >= row['rhs'] or row['cmp'] == 'LE' and hi <= row['rhs']:
                continue
            key = (t, axis, row['cmp'])
            value = row['rhs']
            bounds[key] = max(bounds.get(key, value), value) if row['cmp'] == 'GE' else min(bounds.get(key, value), value)
        return digest(sorted((t, axis, cmp, value) for (t, axis, cmp), value in bounds.items()))

    def segment_rows(self, item, face, begin, end):
        if face['axis'] == 'Y':
            return []
        return [self.face_row(face, t) for t in range(begin, end+1) if t in item['required'][face['axis']]]

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
        cursor.setdefault('chain', 0)
        cursor.setdefault('frontier', [])
        cursor.setdefault('started', False)
        cursor.setdefault('seenOptions', [])
        option_seen = set(cursor['seenOptions'])
        cursor.setdefault('seenNodes', [])
        node_seen = set(cursor['seenNodes'])
        keys = _OptionKeys(self,item)
        while cursor['chain'] < len(item['chains']):
            self.check()
            chain = item['chains'][cursor['chain']]
            chosen = [item['faces'][j] for j in chain]
            if not cursor['started']:
                cursor['frontier'] = [[0, 0, []]]
                cursor['started'] = True
            if not cursor['frontier']:
                cursor['chain'] += 1
                cursor['started'] = False
                continue
            depth, start, gates = cursor['frontier'].pop()
            node_key = keys.node(cursor['chain'],depth,start,gates)
            if node_key in node_seen:
                continue
            node_seen.add(node_key)
            cursor['seenNodes'].append(node_key)
            covered = False
            for j in range(depth+1):
                beginning = 0 if j == 0 else gates[j-1]+int(consecutive(chosen[j-1],chosen[j]))
                if j < len(chain)-1 and item['masks'][chain[j]][beginning] and all(
                        item['guaranteed'][chain[j]][beginning+int(chosen[j]['axis']!='Y'):self.through+1]):
                    covered = True
                    break
            if covered:
                continue
            mask = item['masks'][chain[depth]]
            if depth == len(chain)-1:
                if not all(mask[start:self.through+1]):
                    continue
                if depth and chosen[-2]['axis'] == 'Y':
                    previous = 0 if depth == 1 else gates[-2]+int(consecutive(chosen[-3], chosen[-2]))
                    if all(item['masks'][chain[-2]][previous:self.through+1]):
                        continue
                counts = blockage_counts(chosen)
                passage = dict(leg=0, solidIndex=item['solidIndex'], faces=chosen, gates=gates,
                    kind=passage_kind(chosen), blockageCounts=counts, blockageCount=len(gates))
                rows, failed = compile_passages(dict(self.route, passages=[passage]), self.events)
                if not failed:
                    rows = [dict(r, leg=0, offset=r['t1']) for r in rows if r['t1'] in item['required'][r['mode']]]
                    key = digest([self.effective_key(rows), counts])
                    if key in option_seen:
                        self.state['duplicateVariants'] += 1
                        continue
                    option_seen.add(key)
                    cursor['seenOptions'].append(key)
                    return dict(passage=passage, rows=rows)
                continue
            next_nodes = []
            for gate in range(start, self.through+1):
                if not mask[gate]:
                    break
                beginning = gate+int(consecutive(chosen[depth], chosen[depth+1]))
                if beginning <= self.through:
                    child = [depth+1, beginning, gates+[gate]]
                    # Only omit states already popped, never states merely
                    # queued: the first-popped gate history must still win.
                    if keys.node(cursor['chain'],*child) not in node_seen:
                        next_nodes.append(child)
            def rank(node):
                ranges = intervals(chosen[:depth+2], node[2], 0, node[1])
                paid = sum(sum(item['costs'][chain[j]][a:b+1]) for j, (a, b) in enumerate(ranges[:-1])) if ranges else float('inf')
                return paid+(item['tables'][cursor['chain']][depth+1][node[1]] if item['tables'][cursor['chain']][depth+1][node[1]] is not None else float('inf'))
            next_nodes.sort(key=rank)
            cursor['frontier'].extend(reversed(next_nodes))
        return None

    def __iter__(self):
        return self

    def entailed(self, selected, required):
        bounds = {}
        for row in selected:
            t, axis = row.get('t1',row['offset']), row['mode']
            lo,hi = bounds.get((t,axis),self.envelopes['states'][t][axis])
            bounds[(t,axis)] = [max(lo,row['rhs']) if row['cmp']=='GE' else lo,
                               min(hi,row['rhs']) if row['cmp']=='LE' else hi]
        for row in required:
            t,axis = row.get('t1',row['offset']),row['mode']
            lo,hi = bounds.get((t,axis),self.envelopes['states'][t][axis])
            if row['cmp']=='GE' and lo < row['rhs'] or row['cmp']=='LE' and hi > row['rhs']:
                return False
        return True

    def __next__(self):
        started=time.monotonic()
        try:
            return self._next_variant()
        finally:
            self.state['timings']['variantGenerationSeconds']+=time.monotonic()-started

    def _next_variant(self):
        while self.state['pending']:
            self.check()
            node = self.state['pending'][-1]
            if node['item'] == len(self.items):
                self.state['pending'].pop()
                rows = [dict(r, t1=r.get('t1', r['offset'])) for r in node['rows']]
                key = digest([self.effective_key(rows), [(p['solidIndex'], p['blockageCounts']) for p in node['passages']]])
                if key in self.seen:
                    self.state['duplicateVariants'] += 1
                    continue
                proof = interaction_reachability(self.envelopes, rows, refine=True)
                if proof['ruledOut']:
                    self.excluded(rows,proof)
                    self.seen.add(key)
                    self.state['seen'].append(key)
                    continue
                self.seen.add(key)
                self.state['seen'].append(key)
                self.state['emitted'] += 1
                return dict(rows=[{k: v for k, v in r.items() if k != 't1'} for r in rows], passages=[],
                            selectedPassages=node['passages'], semanticObservations=[dict(kind='passage', solidIndex=p['solidIndex'], faces=p['faces'], gates=p['gates'], blockageCounts=p['blockageCounts']) for p in node['passages']], score=self.state['emitted'])
            item = self.items[node['item']]
            self.prepare_item(item)
            if 'rows' in item and any(self.entailed(node['rows'],rows) for rows in item['rows']):
                node['item'] += 1
                node.pop('cursor',None)
                continue
            cursor = node.setdefault('cursor', {})
            option = self.option(item, cursor)
            if option is None:
                self.state['pending'].pop()
                continue
            rows = node['rows']+option['rows']
            proof = interaction_reachability(self.envelopes, rows)
            if proof['ruledOut']:
                self.excluded(rows,proof)
                continue
            child = dict(item=node['item']+1, rows=rows,
                         passages=node['passages']+([option['passage']] if 'passage' in option else []))
            # Keep resource usage in a partial identity: equal rows alone do not
            # make different remaining blockage budgets interchangeable.
            partial_key = digest([child['item'], self.effective_key(rows),
                [(p['solidIndex'], p['blockageCounts']) for p in child['passages']]])
            if partial_key in self.partial_seen:
                self.state['duplicateVariants'] += 1
                continue
            self.partial_seen.add(partial_key)
            self.state['partialSeen'].append(partial_key)
            self.state['pending'].append(child)
        self.state['exhausted'] = True
        raise StopIteration
