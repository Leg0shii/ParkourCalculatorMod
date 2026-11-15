import ast
import collections
import gzip
import inspect
import json

import parkour_solver.search.movement as movement_search
from parkour_solver.movement.program import program_physics_key


def physical_key(program):
    return program_physics_key(program)+'|interactions='+str(program['interactions'])


def execution_key(program):
    p = program['profile']
    return json.dumps({k:p[k] for k in ('airtime','position','velocity','grounded','jump','sneak','ladderTicks',
                                       'contacts','contactEvents','caseEvents')},sort_keys=True)


class KeyAudit:
    def __init__(self, canonical):
        self.canonical = canonical
        self.legacy, self.physical = set(), {}
        self.metadata_duplicates, self.samples = 0, []

    def __call__(self, program):
        p = program['profile']
        legacy = json.dumps([p,[(e['column']['id'],e['wall'],e['entryFace'],e['exitFace'],
            e['schedule']['entryState'],e['schedule']['exitState']) for e in program['ladderEvents']]],sort_keys=True)
        physical = physical_key(program)
        if physical in self.physical and legacy not in self.legacy:
            self.metadata_duplicates += 1
            if len(self.samples)<8:
                self.samples.append((self.physical[physical],program))
        self.legacy.add(legacy)
        if physical not in self.physical:
            self.physical[physical] = program
        return physical if self.canonical else legacy


class QueueAudit:
    def __init__(self, goal, policy):
        self.goal, self.policy, self.cursor = goal,policy,-1
        self.offered, self.popped, self.dropped = collections.Counter(),collections.Counter(),collections.Counter()
        self.complete_offered = self.complete_dropped = 0

    def complete(self, item):
        p = item[2]
        return p['terminalSupport']==self.goal and p['profile']['grounded'][-1]

    def heapify(self, pending):
        pass

    def heappush(self, pending, item):
        self.offered[item[0]] += 1
        self.complete_offered += int(self.complete(item))
        pending.append(item)

    def heappop(self, pending):
        completed = [i for i,v in enumerate(pending) if self.complete(v)] if self.policy!='shortest' else []
        if completed:
            index = min(completed,key=lambda i:pending[i][:2])
        elif self.policy=='tick_fair':
            times = sorted({v[0] for v in pending})
            chosen = next((t for t in times if t>self.cursor),times[0])
            self.cursor = chosen
            index = min((i for i,v in enumerate(pending) if v[0]==chosen),key=lambda i:pending[i][1])
        else:
            index = min(range(len(pending)),key=lambda i:pending[i][:2])
        item = pending.pop(index)
        self.popped[item[0]] += 1
        return item

    def nsmallest(self, count, pending):
        if self.policy=='tick_fair':
            buckets = collections.defaultdict(list)
            for v in sorted(pending,key=lambda v:v[1]):
                buckets[v[0]].append(v)
            retained = []
            while buckets and len(retained)<count:
                for tick in sorted(list(buckets)):
                    retained.append(buckets[tick].pop(0))
                    if not buckets[tick]:
                        del buckets[tick]
                    if len(retained)==count:
                        break
        else:
            retained = sorted(pending,key=lambda v:(not self.complete(v),v[0],v[1])
                              if self.policy=='complete_first' else v[:2])[:count]
        keep = {v[1] for v in retained}
        for item in pending:
            if item[1] not in keep:
                self.dropped[item[0]] += 1
                self.complete_dropped += int(self.complete(item))
        return retained

    def summary(self):
        return dict(offeredByTick=dict(self.offered),poppedByTick=dict(self.popped),droppedByTick=dict(self.dropped),
                    completeOffered=self.complete_offered,completeDropped=self.complete_dropped)


def instrumented_generator(queue, keys):
    tree = ast.parse(inspect.getsource(movement_search.iter_movement_programs))
    changed = 0
    for node in ast.walk(tree):
        if isinstance(node,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='key' for t in node.targets):
            node.value = ast.Call(func=ast.Name(id='_research_key',ctx=ast.Load()),
                                  args=[ast.Name(id='child',ctx=ast.Load())],keywords=[])
            changed += 1
    if changed!=1:
        raise ValueError('The research key hook no longer matches the generator')
    namespace = dict(movement_search.__dict__,heapq=queue,_research_key=keys)
    exec(compile(ast.fix_missing_locations(tree),'<generation research hooks>','exec'),namespace)
    def generate(*args, **kwargs):
        kwargs['queue_policy'] = 'legacy'
        return namespace['iter_movement_programs'](*args, **kwargs)
    return generate


def save(path, data):
    path.parent.mkdir(parents=True,exist_ok=True)
    temporary = path.with_suffix(path.suffix+'.tmp')
    temporary.write_bytes(gzip.compress(json.dumps(data,indent=2,allow_nan=False).encode(),mtime=0))
    temporary.replace(path)
