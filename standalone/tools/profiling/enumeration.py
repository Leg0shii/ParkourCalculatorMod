"""Scoped observations of the maintained encounter builder's phase counters."""
from collections import Counter
from contextlib import contextmanager
import copy
import functools
import hashlib
import inspect
import json
import weakref


class EnumerationProfile:
    def __init__(self, *, max_rows=16384, max_cases=4):
        self.max_rows, self.max_cases = max_rows, max_cases
        self.rows, self.evaluations, self.cases, self.capture_errors = [], [], [], []
        self.by_key = {}
        self.active, self.source_hash = None, None

    def bind(self, owner, item):
        key = (owner.state['context'], item['solidIndex'], item['begin'], item['end'])
        if key not in self.by_key:
            if len(self.rows) >= self.max_rows:
                raise RuntimeError('Enumeration diagnostic row cap exceeded; use a larger explicit cap')
            active = self.active or dict(index=None, blocks={})
            row = dict(context=key[0], solidIndex=key[1], segment=list(key[2:]), through=owner.through,
                block=active['blocks'].get(key[1], str(key[1])), evaluations=[], counts={})
            self.rows.append(row)
            self.by_key[key] = row
        row = self.by_key[key]
        evaluation = (self.active or {}).get('index')
        if evaluation not in row['evaluations']:
            row['evaluations'].append(evaluation)
        return row

    def capture(self, owner, item):
        if len(self.cases) >= self.max_cases or any(c['item']['box'] == item['box'] and
                c['item']['begin'] == item['begin'] and c['item']['end'] == item['end'] for c in self.cases):
            return
        value = dict(version=2, metadata=dict(context=owner.state['context'], solidIndex=item['solidIndex'],
            block=item.get('owner', str(item['solidIndex'])), through=owner.through), item=item,
            attributes={k: getattr(owner, k) for k in ('route', 'profile', 'through', 'events', 'reference')},
            envelopes=owner._active_envelopes or owner.envelopes)
        try:
            self.cases.append(json.loads(json.dumps(value, allow_nan=False)))
        except (ValueError, TypeError) as error:
            if len(self.capture_errors) < 3:
                self.capture_errors.append(str(error))

    def snapshot(self):
        totals = Counter()
        for row in self.rows:
            totals.update(row['counts'])
        totals['visits'] = totals['optionNodes']+totals['optionLeaves']
        return dict(version=2, originalOptionSourceSHA256=self.source_hash, totals=dict(totals),
            exitsPartitionVisits=totals['optionLeaves']==totals['returned']+totals['duplicateOptions']+totals['dominatedOptions'],
            evaluations=self.evaluations, rows=self.rows, captureErrors=self.capture_errors,
            accounting='Deltas of production phase/choice/analysis counters, grouped by exact context and landing segment. Returned options are complete box covers, not numerical feasibility. Preparation/check work can repeat after interruption. Observations add overhead; no old/new raw-cursor equality claim.')


def instrumented_option(original, profile):
    if original.__name__ != 'option' or original.__module__ != 'parkour_solver.constraints.variants':
        raise RuntimeError('ConstraintVariants.option source changed; review diagnostic insertion points')
    profile.source_hash = hashlib.sha256(inspect.getsource(original).encode()).hexdigest()
    @functools.wraps(original)
    def observed(owner, item, cursor):
        if 'solidIndex' not in item:
            return original(owner, item, cursor)
        row = profile.bind(owner, item)
        before = dict(owner.state['metrics'])
        result = None
        try:
            result = original(owner, item, cursor)
            return result
        finally:
            counts = Counter(row['counts'])
            counts.update({k: v-before.get(k, 0) for k, v in owner.state['metrics'].items()})
            counts['returned'] += result is not None
            row['counts'] = dict(counts)
            if 'phases' in item:
                profile.capture(owner, item)
    return observed


@contextmanager
def instrument(profile, parent):
    from parkour_solver.constraints.variants import ConstraintVariants
    from parkour_solver.search import engine
    with parent:
        original_option, original_evaluate = ConstraintVariants.option, engine.evaluate_program
        def evaluate(scene, program, *args, **kwargs):
            row = dict(index=len(profile.evaluations)+1,
                blocks={b['solidIndex']: b['sourceBlock'] for b in scene['blocks']},
                ticks=program['profile']['airtime'], jumpInputs=[i for i, j in enumerate(program['profile']['jump']) if j])
            profile.evaluations.append(row)
            previous, profile.active = profile.active, row
            try:
                result = original_evaluate(scene, program, *args, **kwargs)
                row.update(status=result['status'], termination=result['termination'], work=result.get('work'))
                return result
            finally:
                profile.active = previous
        try:
            ConstraintVariants.option = instrumented_option(original_option, profile)
            engine.evaluate_program = evaluate
            yield
        finally:
            ConstraintVariants.option, engine.evaluate_program = original_option, original_evaluate
