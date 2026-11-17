"""Scoped diagnostics; never included in search state or resume fingerprints."""

import copy
import functools
import sys
import time
from contextlib import contextmanager


class RuntimeProfile:
    def __init__(self, clock=time.perf_counter):
        self.clock = clock
        self.buckets = {}
        self.stack = []
        self.sessions = []
        self.requests = []

    @contextmanager
    def region(self, name):
        item = dict(name=name, start=self.clock(), children=0.)
        self.stack.append(item)
        try:
            yield
        finally:
            elapsed = self.clock()-item['start']
            self.stack.pop()
            if self.stack:
                self.stack[-1]['children'] += elapsed
            row = self.buckets.setdefault(name, dict(calls=0, inclusiveSeconds=0., selfSeconds=0., maxSeconds=0.))
            row['calls'] += 1
            row['inclusiveSeconds'] += elapsed
            row['selfSeconds'] += max(0., elapsed-item['children'])
            row['maxSeconds'] = max(row['maxSeconds'], elapsed)

    def call(self, name, function, *args, **kwargs):
        with self.region(name):
            return function(*args, **kwargs)

    def function(self, name, function):
        @functools.wraps(function)
        def wrapped(*args, **kwargs):
            return self.call(name, function, *args, **kwargs)
        return wrapped

    def generator(self, name, function):
        @functools.wraps(function)
        def wrapped(*args, **kwargs):
            source = self.call(name+'.create', function, *args, **kwargs)
            try:
                while True:
                    try:
                        value = self.call(name+'.next', next, source)
                    except StopIteration:
                        return
                    yield value
            finally:
                if hasattr(source, 'close'):
                    self.call(name+'.close', source.close)
        return wrapped

    def snapshot(self):
        # Include unfinished regions so a copy consuming a whole service slice
        # remains observable without walking or copying the retained search tree.
        now = self.clock()
        return dict(version=1, buckets={k:dict(v) for k,v in self.buckets.items()},
                    sessions=[dict(s) for s in self.sessions],
                    active=[dict(name=s['name'], elapsedSeconds=now-s['start']) for s in self.stack],
                    accounting='Inclusive buckets overlap; selfSeconds exclude instrumented children. Unwrapped work stays in its parent.')


class CopyProxy:
    """Replace a module's copy reference, leaving stdlib recursion untouched."""
    def __init__(self, profiler, module, original=copy):
        self.profiler, self.module, self.original = profiler, module, original

    def __getattr__(self, key):
        return getattr(self.original, key)

    def deepcopy(self, *args, **kwargs):
        caller = sys._getframe(1)
        label = 'copy:'+self.module+':'+caller.f_code.co_name+':'+str(caller.f_lineno)
        return self.profiler.call(label, self.original.deepcopy, *args, **kwargs)


@contextmanager
def instrument(profiler):
    from standalone.autonomy import movement_frontier, movement_generation, solve_spec, search, candidate_comparison
    from standalone.autonomy.interactions import InteractionGate
    from standalone.autonomy.cell_routes import anchor_time_bound, transfer_time_bounds, preparation_deferrals
    from standalone.autonomy.cell_routes import checkpoint_experiment, checkpoint_search, depth_first, event_search
    changes = []

    def replace(module, key, value):
        changes.append((module, key, getattr(module, key)))
        setattr(module, key, value)

    def session(*args, **kwargs):
        previous = kwargs.get('resume_state')
        row = dict(index=len(profiler.sessions)+1, calls=0, expanded=0,
                   budgetRemainingSeconds=max(0.,kwargs['deadline']-time.monotonic()),
                   frames=len(previous.get('frames',[])) if previous else 0,
                   windows=len(previous.get('windows',[])) if previous else 0)
        profiler.sessions.append(row)
        before = profiler.clock()
        counters = {k:dict(v) for k,v in profiler.buckets.items()}
        try:
            result = profiler.call('dfs.session', original_search, *args, **kwargs)
            row.update(calls=result['work'].get('solverCalls',0), expanded=result['expandedPrefixes'],
                       termination=result['termination'])
            return result
        finally:
            row['wallSeconds'] = profiler.clock()-before
            row['buckets'] = {k:{field:v[field]-counters.get(k,{}).get(field,0)
                                 for field in ('calls','inclusiveSeconds','selfSeconds')}
                              for k,v in profiler.buckets.items()
                              if v['calls'] != counters.get(k,{}).get('calls',0)}

    def invoke(problem, **kwargs):
        row=dict(index=len(profiler.requests)+1,fingerprint=search.fingerprint(problem),
                 ticks=len(problem['magnitude']),stopOnFeasible=kwargs.get('stop_on_feasible',False))
        profiler.requests.append(row)
        result=profiler.call('solver.invoke',original_solve,problem,**kwargs)
        row.update(status=result['status'],workerLaunched=result.get('workerLaunched'),
                   workerStatus=result.get('workerStatus'))
        return result

    original_search = depth_first.search_goals
    original_solve = search.solve
    try:
        # Only module globals change; original stdlib deepcopy's recursive
        # implementation is untouched. Aliasing/memo behavior stays identical.
        for name, module in list(sys.modules.items()):
            if name.startswith('standalone.autonomy.') and getattr(module, 'copy', None) is copy:
                replace(module, 'copy', CopyProxy(profiler, name, module.copy))
        replace(depth_first, 'search_goals', session)
        replace(checkpoint_search, 'evaluate_program', profiler.function('numerical.evaluate', checkpoint_search.evaluate_program))
        replace(search, 'solve', invoke)
        for name in ('prepare','analyze_model','condition','sequence','check_program','required'):
            replace(InteractionGate,name,profiler.function('reachability.'+name,getattr(InteractionGate,name)))
        for module,names,prefix in ((candidate_comparison,('compare_candidates','comparison_label','priority_key'),'comparison.'),
                (anchor_time_bound,('remaining_bound',),'bound.'),
                (transfer_time_bounds,('transfer_bound',),'bound.'),
                (preparation_deferrals,('assess','remember_peer'),'peer.')):
            for name in names:
                original=getattr(module,name);wrapped=profiler.function(prefix+name,original)
                for imported_name,owner in list(sys.modules.items()):
                    if imported_name.startswith('standalone.autonomy.') and getattr(owner,name,None) is original:
                        replace(owner,name,wrapped)
        for module in (movement_generation,):
            replace(module,'assess_proposal',profiler.function('proposal.assess',module.assess_proposal))
            # Consumers import aliases; cover each without wrapping twice.
            for imported_name,owner in list(sys.modules.items()):
                if imported_name.startswith('standalone.autonomy.') and owner is not module:
                    current=getattr(owner,'assess_proposal',None)
                    if current is not None and current is not module.assess_proposal:
                        replace(owner,'assess_proposal',module.assess_proposal)
        replace(checkpoint_experiment, 'replay_witness', profiler.function('validation.replay', checkpoint_experiment.replay_witness))
        replace(depth_first, 'iter_movement_programs', profiler.generator('movement.source', depth_first.iter_movement_programs))
        replace(movement_frontier, 'generate_choices', profiler.generator('movement.choices', movement_frontier.generate_choices))
        # Capture imported references as well as the definition. Avoid wrapping
        # every recursive deepcopy or Python call, which distorts this workload.
        original_digest = solve_spec.digest
        digest = profiler.function('identity.digest', original_digest)
        for name, module in list(sys.modules.items()):
            if name.startswith('standalone.autonomy.') and getattr(module, 'digest', None) is original_digest:
                replace(module, 'digest', digest)
        yield
    finally:
        for module, key, old in reversed(changes):
            setattr(module, key, old)
