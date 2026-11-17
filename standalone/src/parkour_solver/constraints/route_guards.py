"""Choose finite axis-sweep branches before the continuous solve.

No portal adds an input. A phase may traverse any connected forward span of
one raw route variant; contact transitions use the program's physical events.
The resulting fixed rows enter the existing ConstraintVariants product.
"""

import copy
import time

from parkour_solver.spatial.compat.corridor_guards import movement_regions
from parkour_solver.spatial.regions import geometry_key


def physical_events(scene, profile):
    result = {}
    for event in profile.get('contactEvents', []):
        if event['axis'] != 'Y' or event.get('direction') != -1:
            continue
        owners = {b.get('sourceBlock', b['id']) for b in scene['blocks']
                  if scene['collisionScene']['solids'][b['solidIndex']] == event['box']}
        result.setdefault(3*event['offset'], set()).update(owners)
    return result


def canonical_rows(rows):
    bounds = {}
    for row in rows:
        key=(row['offset'],row['mode'],row['cmp'])
        old=bounds.get(key)
        if old is None or (row['rhs']>old['rhs'] if row['cmp']=='GE' else row['rhs']<old['rhs']):
            bounds[key]=row
    return [bounds[k] for k in sorted(bounds)]


class RouteGuards:
    def __init__(self, scene, corridor, profile, through, *, complete=False, envelopes=None, deadline=None, phase_count=None):
        self.route, self.profile, self.through = corridor, profile, through
        self.regions = {r['key']:r for r in corridor.region_graph['regions']}
        self.edges = {t['key']:t for t in corridor.region_graph['transitions']}
        self.contacts = physical_events(scene, profile)
        self.complete = complete
        self.phase_count=3*through if phase_count is None else phase_count
        self.envelopes, self.deadline = envelopes, deadline
        self.uncertain = {3*e['input']+a for e in profile.get('caseEvents', []) for a in range(3)}
        self.context = geometry_key((corridor.to_dict(), profile, through, complete,self.phase_count))
        self.cache = {}

    def rows(self, box, phase, *, endpoint=None):
        tick, axis = divmod(phase, 3)
        axis = (1,0,2)[axis]
        y0, y1 = self.profile['position'][tick:tick+2]
        ys = [y0,y1] if axis == 1 else [y1]
        if endpoint is not None:
            ys = [ys[endpoint]] if axis == 1 else ys
        if min(ys) < box[1]-1e-7 or max(ys) > box[4]+1e-7:
            return None
        states = ([tick] if axis == 1 else [tick,tick+1] if axis == 0 else [tick+1],
                  [tick,tick+1] if axis == 2 else [tick])
        if endpoint is not None:
            states = tuple([s[min(endpoint,len(s)-1)]] for s in states)
        return [dict(leg=0, offset=t, mode=mode, cmp=cmp, rhs=value, name='route:axis_sweep')
                for mode, indices, lo, hi in [('X',states[0],box[0],box[3]),('Z',states[1],box[2],box[5])]
                for t in indices for cmp,value in [('GE',lo),('LE',hi)]]

    def phase_options(self, variant_index, occurrence, phase):
        key = (variant_index, occurrence, phase)
        if key in self.cache:
            return self.cache[key]
        variant = self.route.route_variants[variant_index]
        path, edges = variant['regions'], variant['transitions']
        options = []
        span = []
        for end in range(occurrence,len(path)):
            region = self.regions[path[end]]
            if region['kind'] == 'terminal_predicate':
                break
            if end > occurrence and self.edges[edges[end-1]]['kind'] not in ('open_portal','support_departure'):
                break
            span.extend(region['cover'])
            axis = (1,0,2)[phase%3]
            union = movement_regions(span, axis)
            for guard in union:
                rows = self.rows(guard,phase)
                if rows is None:
                    continue
                for before in self.regions[path[occurrence]]['cover']:
                    start_rows = self.rows(before,phase,endpoint=0)
                    if start_rows is None:
                        continue
                    for after in region['cover']:
                        end_rows = self.rows(after,phase,endpoint=1)
                        if end_rows is None:
                            continue
                        selected = rows+start_rows+end_rows
                        # A direction across a finite separating face also
                        # fixes its order within this same axis input.
                        valid = True
                        previous_plane = None
                        sign = None
                        tick = phase//3
                        for e in edges[occurrence:end]:
                            transition = self.edges[e]
                            if len(transition['cover']) != 1:
                                # Contact departure unions need no plane order;
                                # ordinary raw portals have exactly one patch.
                                continue
                            patch = transition['cover'][0]
                            if axis != 1 and not patch[1]-1e-7 <= self.profile['position'][tick+1] <= patch[4]+1e-7:
                                valid = False
                                break
                            for lateral in (0,2):
                                if lateral == axis:
                                    continue
                                state = tick+int(axis==2 and lateral==0)
                                selected.extend(dict(leg=0,offset=state,mode='XYZ'[lateral],cmp=cmp,rhs=value,name='route:finite_portal')
                                                for cmp,value in [('GE',patch[lateral]),('LE',patch[lateral+3])])
                            if patch[axis] != patch[axis+3]:
                                continue
                            a = self.regions[transition['source']]['cover'][0]
                            b = self.regions[transition['target']]['cover'][0]
                            plane = patch[axis]
                            direction = 1 if a[axis+3] == plane and b[axis] == plane else -1 if a[axis] == plane and b[axis+3] == plane else 0
                            if not direction:
                                continue
                            if sign is not None and (direction != sign or direction*(plane-previous_plane)<0):
                                valid = False
                                break
                            sign, previous_plane = direction, plane
                            if axis == 1:
                                y0,y1 = self.profile['position'][tick:tick+2]
                                if not min(y0,y1)-1e-7 <= plane <= max(y0,y1)+1e-7 or direction*(y1-y0)<0:
                                    valid=False
                                    break
                            else:
                                selected.extend([dict(leg=0,offset=tick,mode='XYZ'[axis],cmp='LE' if direction>0 else 'GE',rhs=plane,name='route:portal_order'),
                                                 dict(leg=0,offset=tick+1,mode='XYZ'[axis],cmp='GE' if direction>0 else 'LE',rhs=plane,name='route:portal_order')])
                        if not valid:
                            continue
                        contacts = self.contacts.get(phase,set())
                        # Standing on the most recent physical support is local;
                        # a new owner's contact must consume the selected visit.
                        last = next((self.regions[path[i]] for i in range(end,-1,-1)
                                     if self.regions[path[i]]['kind']=='support_surface'),None)
                        next_index = end
                        extra = []
                        if contacts:
                            if end < len(edges):
                                edge = self.edges[edges[end]]
                                target = self.regions[edge['target']]
                                if edge['kind']=='support_arrival' and contacts.intersection(target['owners']):
                                    next_index = end+1
                                    # Contact footprints can be concave; choose
                                    # their finite rectangle as a branch below.
                                    extra = [self.rows(patch,phase,endpoint=1) for patch in target['cover']]
                            if next_index == end and (last is None or not contacts.issubset(last['owners'])):
                                continue
                        for contact_rows in extra or [[]]:
                            if contact_rows is not None:
                                options.append(dict(occurrence=next_index,rows=selected+contact_rows))
        unique = {geometry_key((o['occurrence'],o['rows'])):o for o in options}
        self.cache[key] = sorted(unique.values(),key=lambda o:-o['occurrence'])
        return self.cache[key]

    def option(self, cursor):
        cursor.setdefault('pending',[dict(variant=i,occurrence=0,phase=0,rows=[])
                                     for i in reversed(range(len(self.route.route_variants)))])
        cursor.setdefault('seen',[])
        seen=set(cursor['seen'])
        while cursor['pending']:
            if self.deadline is not None and time.monotonic() >= self.deadline:
                raise TimeoutError('route_guard_generation_deadline')
            node = cursor['pending'][-1]
            if node['phase'] == self.phase_count:
                cursor['pending'].pop()
                variant = self.route.route_variants[node['variant']]
                index = node['occurrence']
                final = index >= variant.get('required',len(variant['regions'])-1)
                if not final and index < len(variant['transitions']):
                    final = self.edges[variant['transitions'][index]]['kind']=='finish'
                if self.complete and not final:
                    continue
                return dict(rows=node['rows'])
            if node['phase'] in self.uncertain:
                # Do not derive exclusions from incomplete internal probe
                # traces. The route audit will retain an explicit unknown.
                cursor['pending'].pop()
                return dict(rows=node['rows'])
            options = self.phase_options(node['variant'], node['occurrence'],node['phase'])
            at = node.get('option',0)
            if at == len(options):
                cursor['pending'].pop()
                continue
            node['option'] = at+1
            selected = options[at]
            rows = canonical_rows(node['rows']+copy.deepcopy(selected['rows']))
            if self.envelopes is not None:
                from parkour_solver.reachability.intervals import interaction_reachability
                if interaction_reachability(self.envelopes, rows)['ruledOut']:
                    cursor['screenExclusions'] = cursor.get('screenExclusions',0)+1
                    continue
            key=geometry_key((node['variant'],selected['occurrence'],node['phase']+1,
                              [(r['offset'],r['mode'],r['cmp'],r['rhs']) for r in rows]))
            if key in seen:
                cursor['duplicates']=cursor.get('duplicates',0)+1
                continue
            seen.add(key)
            cursor['seen'].append(key)
            cursor['pending'].append(dict(variant=node['variant'],occurrence=selected['occurrence'],
                phase=node['phase']+1, rows=rows))
        return None
