"""Bounded, call-site diagnostics for hashes, preparation and top-level copies.

Only the explicit tool context installs hooks. Nothing enters production state.
Digest timings and payload cardinalities are sampled, not extrapolated totals.
"""

from collections import Counter
from contextlib import contextmanager
import copy
import functools
import hashlib
import json
import sys
import time
import tracemalloc


def callsite(frame):
    return f"{frame.f_globals.get('__name__')}:{frame.f_code.co_name}:{frame.f_lineno}"


class BookkeepingProfile:
    def __init__(self, runtime, *, sample_every=1024, sample_limit=256,
                 allocation_samples=0, distinct_limit=4096):
        if sample_every < 1 or sample_limit < 1 or allocation_samples < 0 or distinct_limit < 1:
            raise ValueError('Invalid sampling limits')
        self.runtime = runtime
        self.sample_every, self.sample_limit = sample_every, sample_limit
        self.allocation_samples, self.distinct_limit = allocation_samples, distinct_limit
        self.digests, self.copies, self.caches, self.functions = {}, {}, {}, {}
        self.examples = {}

    def digest(self, original):
        @functools.wraps(original)
        def wrapped(value):
            site = callsite(sys._getframe(1))
            row = self.digests.get(site)
            if row is None:
                row = self.digests[site] = dict(calls=0, samples=0, bodySeconds=0.,
                    payloadBytes=0, maxPayloadBytes=0, diagnosticSeconds=0., values=Counter())
            row['calls'] += 1
            early_sample = row['calls'] <= self.sample_every and (row['calls'] & (row['calls']-1)) == 0
            if (not early_sample and (row['calls']-1) % self.sample_every) or row['samples'] >= self.sample_limit:
                return original(value)
            started = time.perf_counter()
            result = original(value)
            ended = time.perf_counter()
            payload = json.dumps(value, sort_keys=True, allow_nan=False).encode()
            if hashlib.sha256(payload).hexdigest() != result:
                raise AssertionError('Sampled canonical digest changed')
            row['samples'] += 1
            row['bodySeconds'] += ended-started
            row['payloadBytes'] += len(payload)
            row['maxPayloadBytes'] = max(row['maxPayloadBytes'], len(payload))
            row['values'][result] += 1
            if site not in self.examples and len(payload) <= 262144:
                self.examples[site] = payload.decode()
            row['diagnosticSeconds'] += time.perf_counter()-ended
            return result
        return wrapped

    def copy(self, original, site, *args, **kwargs):
        row = self.copies.setdefault(site, dict(calls=0, bodySeconds=0., maxSeconds=0.,
            samples=0, payloadBytes=0, maxPayloadBytes=0, diagnosticSeconds=0., allocations=[]))
        started = time.perf_counter()
        result = original(*args, **kwargs)
        ended = time.perf_counter()
        row['calls'] += 1
        row['bodySeconds'] += ended-started
        row['maxSeconds'] = max(row['maxSeconds'], ended-started)
        power_of_two = (row['calls'] & (row['calls']-1)) == 0
        if (power_of_two or (row['calls']-1) % self.sample_every == 0) and row['samples'] < min(16, self.sample_limit):
            # JSON byte size is a transport-size diagnostic, not heap size.
            try:
                size = len(json.dumps(args[0], sort_keys=True, allow_nan=False).encode())
            except (ValueError, TypeError):
                size = None  # Cycles/non-JSON values retain ordinary copy behavior.
            row['samples'] += 1
            if size is not None:
                row['payloadBytes'] += size
                row['maxPayloadBytes'] = max(row['maxPayloadBytes'], size)
            large_or_mature = (size is not None and size >= 262144) or row['calls'] >= 128
            if large_or_mature and len(row['allocations']) < self.allocation_samples and not tracemalloc.is_tracing():
                # A separate repeated copy: no allocation hooks contaminate the
                # measured search copy. Never reuse a caller's deepcopy memo.
                tracemalloc.start()
                try:
                    before = time.perf_counter()
                    measured = original(args[0])
                    current, peak = tracemalloc.get_traced_memory()
                    row['allocations'].append(dict(call=row['calls'], canonicalBytes=size,
                        currentBytes=current, peakBytes=peak,
                        seconds=time.perf_counter()-before))
                    del measured
                finally:
                    tracemalloc.stop()
        row['diagnosticSeconds'] += time.perf_counter()-ended
        return result

    def cached(self, original):
        @functools.wraps(original)
        def wrapped(gate, key, build, kind):
            row = self.caches.setdefault(kind, dict(calls=0, hits=0, misses=0,
                bodySeconds=0., keys=Counter(), keyTrackingTruncated=False, maxCacheEntries=0))
            row['calls'] += 1
            row['hits' if key in gate.cache else 'misses'] += 1
            if key in row['keys'] or len(row['keys']) < self.distinct_limit:
                row['keys'][key] += 1
            else:
                row['keyTrackingTruncated'] = True
            started = time.perf_counter()
            try:
                return original(gate, key, build, kind)
            finally:
                row['bodySeconds'] += time.perf_counter()-started
                row['maxCacheEntries'] = max(row['maxCacheEntries'], len(gate.cache))
        return wrapped

    def sampled_function(self, name, original):
        @functools.wraps(original)
        def wrapped(*args, **kwargs):
            row = self.functions.setdefault(name, dict(calls=0, samples=0, bodySeconds=0.))
            row['calls'] += 1
            if (row['calls']-1) % self.sample_every or row['samples'] >= self.sample_limit:
                return original(*args, **kwargs)
            started = time.perf_counter()
            try:
                return original(*args, **kwargs)
            finally:
                row['samples'] += 1
                row['bodySeconds'] += time.perf_counter()-started
        return wrapped

    def snapshot(self):
        digests = {site:dict({k:v for k,v in row.items() if k != 'values'},
            sampledDistinct=len(row['values']), sampledRepeated=row['samples']-len(row['values']),
            topSampledValues=row['values'].most_common(5)) for site,row in self.digests.items()}
        caches = {kind:dict({k:v for k,v in row.items() if k != 'keys'},
            distinctKeys=len(row['keys']), repeatedKeys=sum(row['keys'].values())-len(row['keys']))
            for kind,row in self.caches.items()}
        copies = {site:dict(row, allocations=[dict(v) for v in row['allocations']])
                  for site,row in self.copies.items()}
        return dict(digests=digests, copies=copies, caches=caches,
            functions={name:dict(row) for name,row in self.functions.items()},
            sampleEvery=self.sample_every, sampleLimitPerSite=self.sample_limit,
            allocationSamplesPerCopySite=self.allocation_samples,
            accounting='Call counts exact; digest body time, bytes and repeats sampled. Copy body times inclusive/overlapping. JSON bytes are not heap bytes. Allocation samples repeat copies separately. Diagnostics and hooks add overhead; no recoverable-speedup claim.')


class CopyProxy:
    def __init__(self, profile, original):
        self.profile, self.original = profile, original

    def __getattr__(self, name):
        return getattr(self.original, name)

    def deepcopy(self, *args, **kwargs):
        return self.profile.copy(self.original.deepcopy, callsite(sys._getframe(1)), *args, **kwargs)


@contextmanager
def instrument(profile):
    from parkour_solver.search import identity, engine, ordered_route
    from parkour_solver.reachability.service import InteractionGate
    from parkour_solver.movement import program
    from parkour_solver.constraints.variants import ConstraintVariants
    changes, alias_replacements = [], []

    def replace(owner, name, value):
        changes.append((owner, name, getattr(owner, name)))
        setattr(owner, name, value)

    def aliases(original, name, wrapped):
        alias_replacements.append((name, original, wrapped))
        for module_name, module in list(sys.modules.items()):
            if module_name.startswith('parkour_solver.') and getattr(module, name, None) is original:
                replace(module, name, wrapped)

    try:
        from parkour_solver.numerics.disk import RelaxedDispatch
        def recorded(original):
            # The relaxed dispatch answers find_route's requests itself, so both entry points are recorded.
            def solve(*args, **kwargs):
                problem = args[-1]
                row = dict(index=len(profile.runtime.requests)+1,
                    fingerprint=ordered_route.fingerprint(problem), ticks=len(problem['magnitude']),
                    stopOnFeasible=kwargs.get('stop_on_feasible',False))
                profile.runtime.requests.append(row)
                result = profile.runtime.call('solver.invoke',original,*args,**kwargs)
                row.update(status=result['status'],workerLaunched=result.get('workerLaunched'),
                    workerStatus=result.get('workerStatus'),seconds=result.get('elapsedSeconds'),
                    **{k: result[k] for k in ('backend', 'fallback', 'confirmation') if k in result})
                return result
            return solve
        replace(ordered_route, 'solve', recorded(ordered_route.solve))
        replace(RelaxedDispatch, 'solve', recorded(RelaxedDispatch.solve))
        aliases(identity.digest, 'digest', profile.digest(identity.digest))
        for name in ('program_physics_key', 'program_search_key', 'compile_program'):
            original = getattr(program, name)
            aliases(original, name, profile.runtime.function('program.'+name, original))
        for name in ('prepare', 'analyze_model', 'analyze', 'condition'):
            replace(InteractionGate, name, profile.runtime.function('preparation.'+name, getattr(InteractionGate, name)))
        replace(InteractionGate, '_cached', profile.cached(InteractionGate._cached))
        for name in ('solve_scope', 'solve_identity', 'validation_prefix'):
            original = getattr(identity, name)
            aliases(original, name, profile.runtime.function('identity.'+name, original))
        replace(engine, 'evaluate_program', profile.runtime.function('numerical.evaluate', engine.evaluate_program))
        from parkour_solver.reachability import screen
        from parkour_solver.numerics import disk
        for module, name in ((screen, 'condition_envelopes'), (disk, 'screen')):
            original = getattr(module, name)
            aliases(original, name, profile.runtime.function('reachability.'+name, original))
        for name in ('prepare_item', 'effective_key', 'analyze', 'choose_item'):
            replace(ConstraintVariants, name, profile.sampled_function('variants.'+name, getattr(ConstraintVariants, name)))
        for module_name, module in list(sys.modules.items()):
            if module_name.startswith('parkour_solver.') and getattr(module, 'copy', None) is copy:
                replace(module, 'copy', CopyProxy(profile, copy))
        yield
    finally:
        # Modules imported during the context can acquire wrapped from-imports.
        for name, original, wrapped in reversed(alias_replacements):
            for module_name, module in list(sys.modules.items()):
                if module_name.startswith('parkour_solver.') and getattr(module, name, None) is wrapped:
                    setattr(module, name, original)
        for owner, name, original in reversed(changes):
            setattr(owner, name, original)
