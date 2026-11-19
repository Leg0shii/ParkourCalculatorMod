"""Tool-only branch counters for the unchanged constraint-option enumerator."""
import ast
from collections import Counter
from contextlib import contextmanager
import copy
import hashlib
import inspect
import json
import textwrap
import weakref


EXITS = ('duplicate_node', 'covered', 'terminal_mask', 'terminal_previous_y',
         'duplicate_option', 'compile_failed', 'returned', 'expanded')


class BoundOption:
    def __init__(self, profile, owner, item, metadata):
        self.profile, self.owner, self.item = profile, weakref.ref(owner), item
        self.metadata, self.rows, self.current = metadata, [], None
        self.first_options = {}
        for chain in item['chains']:
            labels = [f["axis"]+('+' if f['direction'] > 0 else '-')
                      for f in (item['faces'][i] for i in chain)]
            key = (metadata['context'],metadata['solidIndex'],tuple(labels))
            row = profile.by_key.get(key)
            if row is None:
                if len(profile.rows) >= profile.max_rows:
                    raise RuntimeError('Enumeration diagnostic row cap exceeded; use a larger explicit cap')
                row = dict(metadata, chain=labels, evaluations=[], visits=0, emptyEffective=0,
                    generatedChildren=0, childrenWithNoSuffix=0,
                    exits=dict.fromkeys(EXITS, 0), samples=[], examples=[])
                profile.rows.append(row)
                profile.by_key[key] = row
            if metadata['evaluation'] not in row['evaluations']:
                row['evaluations'].append(metadata['evaluation'])
            self.rows.append(row)

    def node(self, cursor, depth, start, gates, effective):
        row = self.current = self.rows[cursor['chain']]
        row['visits'] += 1
        row['emptyEffective'] += effective == self.profile.empty_key
        n = row['visits']
        if len(row['samples']) < 32 and (n & (n-1) == 0 or n % 4096 == 0):
            row['samples'].append(dict(depth=depth, start=start, gates=list(gates), effective=effective))
        if n == 1024:
            self.profile.capture(self)

    def exit(self, reason):
        self.current['exits'][reason] += 1

    def expand(self, cursor, depth, nodes):
        row = self.current
        row['exits']['expanded'] += 1
        row['generatedChildren'] += len(nodes)
        table = self.item['tables'][cursor['chain']][depth+1]
        row['childrenWithNoSuffix'] += sum(table[node[1]] is None for node in nodes)

    def option(self, key, effective, counts, passage, duplicate):
        row = self.current
        example = dict(chain=list(row['chain']), gates=list(passage['gates']),
                       effective=effective, counts=dict(counts))
        if duplicate and len(row['examples']) < 3 and key in self.first_options:
            row['examples'].append(dict(first=self.first_options[key], repeated=example))
        if not duplicate and len(self.first_options) < 128:
            self.first_options.setdefault(key, example)


class EnumerationProfile:
    def __init__(self, *, max_rows=16384, max_cases=4):
        from parkour_solver.search.identity import digest
        self.empty_key = digest([])
        self.max_rows, self.max_cases = max_rows, max_cases
        self.rows, self.evaluations, self.cases, self.capture_errors = [], [], [], []
        self.by_key = {}
        self.owners = weakref.WeakKeyDictionary()
        self.active = None
        self.source_hash = None

    def bind(self, owner, item):
        data = self.owners.setdefault(owner, {})
        index = item['solidIndex']
        if index not in data:
            active = self.active or dict(index=None, blocks={})
            meta = dict(evaluation=active['index'], context=owner.state['context'],
                block=active['blocks'].get(index, str(index)), solidIndex=index,
                box=list(item['box']), through=owner.through,
                required=copy.deepcopy(item['required']))
            data[index] = BoundOption(self, owner, item, meta)
        return data[index]

    def capture(self, bound):
        if len(self.cases) >= self.max_cases:
            return
        if any(c['metadata']['through'] == bound.metadata['through'] and
               c['metadata']['solidIndex'] == bound.metadata['solidIndex'] for c in self.cases):
            return
        owner = bound.owner()
        value = dict(metadata=bound.metadata, item=bound.item,
            attributes={k:getattr(owner,k) for k in ('route','profile','through','events')},
            envelopes=dict(states=owner.envelopes['states']))
        try:
            encoded = json.dumps(value, allow_nan=False)
            if len(encoded) > 2*1024*1024:
                raise ValueError('Prepared option capture exceeds 2 MiB')
            self.cases.append(json.loads(encoded))
        except (ValueError, TypeError) as error:
            if len(self.capture_errors) < 3:
                self.capture_errors.append(str(error))

    def snapshot(self):
        totals = Counter()
        for row in self.rows:
            totals.update({k:row[k] for k in ('visits','emptyEffective','generatedChildren','childrenWithNoSuffix')})
            totals.update(row['exits'])
        return dict(version=1, originalOptionSourceSHA256=self.source_hash,
            totals=dict(totals), exitsPartitionVisits=totals['visits']==sum(totals[k] for k in EXITS),
            evaluations=self.evaluations, rows=self.rows, captureErrors=self.capture_errors,
            accounting='Exact visited-entry/exit/child counts within this instrumented run. Samples/examples are bounded and selective. Visits are not unique routes. No-suffix children are observed, not pruned. Hooks add overhead; wall-budget work is not a speedup comparison.')


def instrumented_option(original, profile):
    """Insert observations into a private AST copy, with source-shape assertions.

    Production source/globals/state stay untouched. Branches, keys, ordering,
    returns and cursor operations remain those of the original method.
    """
    source = textwrap.dedent(inspect.getsource(original))
    profile.source_hash = hashlib.sha256(source.encode()).hexdigest()
    tree = ast.parse(source)

    class Observe(ast.NodeTransformer):
        def __init__(self):
            self.continues = 0
            self.markers = Counter()

        def statements(self, code, location):
            return [ast.copy_location(s, location) for s in ast.parse(code).body]

        def visit_Continue(self, node):
            self.continues += 1
            names = {2:'duplicate_node',3:'covered',4:'terminal_mask',
                     5:'terminal_previous_y',6:'duplicate_option',7:'compile_failed'}
            if self.continues not in names:
                return node
            return self.statements(f"_enum_stats.exit('{names[self.continues]}')",node)+[node]

        def visit_Assign(self, node):
            if len(node.targets)==1 and isinstance(node.targets[0], ast.Name):
                target = node.targets[0].id
                if target == 'node_seen':
                    self.markers['bind'] += 1
                    return [node]+self.statements('_enum_stats = _enumeration_probe.bind(self, item)',node)
                if target in ('node_key','key'):
                    element = node.value.args[0].elts[-1 if target=='node_key' else 0]
                    variable = '_enum_effective' if target=='node_key' else '_enum_leaf_effective'
                    replacement = ast.NamedExpr(target=ast.Name(id=variable,ctx=ast.Store()),value=element)
                    node.value.args[0].elts[-1 if target=='node_key' else 0] = replacement
                    self.markers[target] += 1
                    code = ('_enum_stats.node(cursor, depth, start, gates, _enum_effective)' if target=='node_key' else
                        '_enum_stats.option(key, _enum_leaf_effective, counts, passage, key in option_seen)')
                    return [node]+self.statements(code,node)
            return self.generic_visit(node)

        def visit_Return(self, node):
            if isinstance(node.value, ast.Call) and isinstance(node.value.func, ast.Name) and node.value.func.id=='dict' and any(k.arg=='passage' for k in node.value.keywords):
                self.markers['returned'] += 1
                return self.statements("_enum_stats.exit('returned')",node)+[node]
            return node

        def visit_Expr(self, node):
            if ast.unparse(node) == "cursor['frontier'].extend(reversed(next_nodes))":
                self.markers['expanded'] += 1
                return [node]+self.statements('_enum_stats.expand(cursor, depth, next_nodes)',node)
            return self.generic_visit(node)

    observer = Observe()
    tree = observer.visit(tree)
    if observer.continues != 7 or observer.markers != Counter(dict(bind=1,node_key=1,key=1,returned=1,expanded=1)):
        raise RuntimeError('ConstraintVariants.option source changed; review diagnostic insertion points')
    ast.fix_missing_locations(tree)
    namespace = dict(original.__globals__, _enumeration_probe=profile)
    exec(compile(tree, original.__code__.co_filename+'[enumeration-probe]', 'exec'), namespace)
    return namespace[original.__name__]


@contextmanager
def instrument(profile, parent):
    from parkour_solver.constraints.variants import ConstraintVariants
    from parkour_solver.search import engine
    with parent:
        original_option, original_evaluate = ConstraintVariants.option, engine.evaluate_program
        def evaluate(scene, program, *args, **kwargs):
            row = dict(index=len(profile.evaluations)+1,
                blocks={b['solidIndex']:b['sourceBlock'] for b in scene['blocks']},
                ticks=program['profile']['airtime'],
                jumpInputs=[i for i,j in enumerate(program['profile']['jump']) if j])
            profile.evaluations.append(row)
            previous, profile.active = profile.active, row
            try:
                result = original_evaluate(scene,program,*args,**kwargs)
                row.update(status=result['status'],termination=result['termination'],work=result.get('work'))
                return result
            finally:
                profile.active = previous
        try:
            ConstraintVariants.option = instrumented_option(original_option,profile)
            engine.evaluate_program = evaluate
            yield
        finally:
            ConstraintVariants.option, engine.evaluate_program = original_option, original_evaluate
