import copy
import time
import functools
import itertools

from .blockages import faces, face_chains, intervals, consecutive, blockage_counts, compile_passages, passage_kind, face_clearance
from .obstacle_geometry import expanded_box
from .interactions import block_interaction
from .reachability import interaction_reachability
from .solve_spec import digest


class ConstraintVariants:
    def __init__(self, route, profile, plan, geometry, envelopes, scope, *, checks=(), gates=(), signature=(), state=None, deadline=None, reference=None):
        self.route, self.profile, self.plan = route, profile, plan
        self.envelopes, self.deadline, self.through = envelopes, deadline, scope['through']
        context = digest(dict(version=5, route={k: v for k, v in route.items() if k != 'warmYaw'},
                              profile=profile, scope=scope, gates=list(gates), signature=signature,
                              modelKey=envelopes.get('modelKey')))
        if state and state.get('context') == context:
            reference = state.get('reference', reference)
        self.events = [dict(plan['events'][0], landing=self.through)]
        self.items, self.exclusions, self.route_rows = [], [], []
        active = set(scope['solidIndices'])
        selected = {token[0]: token for token in signature}
        for gate in gates:
            if gate['id'] not in selected or tuple(gate['box']) not in {tuple(geometry['solids'][i]) for i in active}:
                continue
            _, direction, side = selected[gate['id']]
            self.route_rows.append(dict(leg=0, offset=self.through, mode='XYZ'[gate['axis']],
                cmp='GE' if direction > 0 else 'LE', rhs=gate['plane'], name='route:crossing'))
        for i in scope['solidIndices']:
            box = geometry['solids'][i]
            expanded = expanded_box(box)
            possible = []
            excluded_phases = []
            for tick in range(self.through):
                for phase in ('Y', 'X', 'Z'):
                    checked = block_interaction(envelopes,box,tick,(phase,),interior=True)
                    if not checked['ruledOut']:
                        possible.append((tick, phase))
                    else:
                        excluded_phases.append(dict(checked,tick=tick,phase=phase))
            if not possible:
                self.exclusions.append(dict(solidIndex=i, reason='all_contact_phases_unreachable',
                                            phaseExclusions=excluded_phases))
                continue
            relevant = {t+offset for t, _ in possible for offset in (0, 1)}
            required = {axis: set() for axis in ('X','Z')}
            for t, phase in possible:
                required['X'].update((t,) if phase == 'Y' else (t,t+1) if phase == 'X' else (t+1,))
                required['Z'].update((t,t+1) if phase == 'Z' else (t,))
            chain_faces = faces(box)
            masks = []
            guaranteed = []
            for face in chain_faces:
                mask = []
                known = []
                for t in range(self.through+1):
                    inactive = t not in (relevant if face['axis'] == 'Y' else required[face['axis']])
                    if inactive:
                        mask.append(True)
                        known.append(True)
                    elif face['axis'] == 'Y':
                        mask.append(face['direction']*(profile['position'][t]-face['plane']) >= 0)
                        known.append(mask[-1])
                    else:
                        mask.append(not interaction_reachability(envelopes, [self.face_row(face, t)])['ruledOut'])
                        row = self.face_row(face,t)
                        lo,hi = envelopes['states'][t][face['axis']]
                        known.append(lo >= row['rhs'] if face['direction'] > 0 else hi <= row['rhs'])
                masks.append(mask)
                guaranteed.append(known)
            chains = []
            costs = []
            for face in chain_faces:
                values = []
                for t in range(self.through+1):
                    coordinate = (profile['position'][t] if face['axis'] == 'Y' else
                        reference[t][0 if face['axis'] == 'X' else 1] if reference is not None else
                        sum(envelopes['states'][t][face['axis']])/2)
                    values.append(max(0, face['direction']*(face['plane']-coordinate))**2
                                  if t in (relevant if face['axis']=='Y' else required[face['axis']]) else 0.)
                costs.append(values)
            caps = route['passageCaps']
            for length in range(caps['total']+1):
                for chain in face_chains(length):
                    if any(j >= len(chain_faces) for j in chain):
                        continue
                    chosen = [chain_faces[j] for j in chain]
                    if any(a['axis'] == b['axis'] for a, b in zip(chosen, chosen[1:])):
                        continue
                    counts = blockage_counts(chosen)
                    if any(counts[k] > caps[k] for k in caps):
                        continue
                    if masks[chain[0]][0] and masks[chain[-1]][self.through]:
                        chains.append(chain)
            prefixes = [[0.]+list(itertools.accumulate(values)) for values in costs]
            tables = {}
            def chain_cost(chain):
                table = {}
                @functools.lru_cache(None)
                def cost(depth, start):
                    face = chain[depth]
                    if depth == len(chain)-1:
                        value = prefixes[face][-1]-prefixes[face][start] if all(masks[face][start:]) else float('inf')
                    else:
                        value = float('inf')
                        for gate in range(start, self.through+1):
                            if not masks[face][gate]:
                                break
                            beginning = gate+int(consecutive(chain_faces[face],chain_faces[chain[depth+1]]))
                            if beginning <= self.through:
                                value = min(value, prefixes[face][gate+1]-prefixes[face][start]+cost(depth+1,beginning))
                    table[(depth,start)] = value
                    return value
                value = cost(0,0)
                tables[chain] = table
                return value
            ranks = {chain: chain_cost(chain) for chain in chains}
            chains = sorted((chain for chain in chains if ranks[chain] != float('inf')),
                            key=lambda chain:(ranks[chain],len(chain),chain))
            self.items.append(dict(solidIndex=i, faces=chain_faces, masks=masks, chains=chains, costs=costs, tables=tables,
                                   possiblePhases=possible, relevantStates=sorted(relevant), required=required, guaranteed=guaranteed))
        for check in checks:
            if check['block']['solidIndex'] not in active or check['offset'] > self.through:
                continue
            from .block_contacts import avoid_contact_rows
            from .route import center_bounds
            b, t = center_bounds(check['block']), check['offset']
            contact = dict(regions=[dict(tick=t, bounds=[b[0], -float('inf'), b[2], b[1], float('inf'), b[3]], interior=True)])
            if interaction_reachability(envelopes, contact)['ruledOut']:
                continue
            point = reference[t] if reference is not None else [(b[0]+b[1])/2, (b[2]+b[3])/2]
            conflict = dict(check, position=point)
            options = avoid_contact_rows(conflict, route['supportClearance'])
            options.sort(key=lambda r: max(0, (r['rhs']-point[0 if r['mode']=='X' else 1])*(1 if r['cmp']=='GE' else -1)))
            self.items.append(dict(rows=[[row] for row in options]))
        self.state = copy.deepcopy(state) if state and state.get('context') == context else dict(
            version=1, context=context, pending=[dict(item=0, rows=self.route_rows, passages=[])], seen=[], emitted=0,
            exhausted=False, reference=copy.deepcopy(reference))
        self.seen = set(self.state['seen'])
        self.state.setdefault('screenExclusions', [])
        self.state.setdefault('duplicateVariants', 0)
        self.state.setdefault('partialSeen', [])
        self.partial_seen = set(self.state['partialSeen'])

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
            ranges = intervals(chosen[:depth+1], gates, 0, start)
            paid = [row for face, (a, b) in zip(chosen, ranges[:-1])
                    for row in self.segment_rows(item, face, a, b)]
            node_key = digest([cursor['chain'], depth, start, self.effective_key(paid)])
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
                    next_nodes.append([depth+1, beginning, gates+[gate]])
            def rank(node):
                ranges = intervals(chosen[:depth+2], node[2], 0, node[1])
                paid = sum(sum(item['costs'][chain[j]][a:b+1]) for j, (a, b) in enumerate(ranges[:-1])) if ranges else float('inf')
                return paid+item['tables'][chain].get((depth+1,node[1]), float('inf'))
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
        while self.state['pending']:
            self.check()
            node = self.state['pending'][-1]
            if node['item'] == len(self.items):
                self.state['pending'].pop()
                rows = [dict(r, t1=r.get('t1', r['offset'])) for r in node['rows']]
                key = self.effective_key(rows)
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
                            selectedPassages=node['passages'], score=self.state['emitted'])
            item = self.items[node['item']]
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
