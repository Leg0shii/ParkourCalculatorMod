"""Persistence adapter: full checkpoints at transitions, small repair updates.

An evaluation only changes its program's repairState and cumulative call count
in the outer DFS state. Store those changes against the dispatch checkpoint,
including every alias of the active program. Audit windows are immutable
snapshots created by DFS; archive each once outside numerical callbacks.
"""
def identity_paths(value, target):
    memo = {}

    def paths(node):
        if node is target:
            return [()]
        if not isinstance(node, (dict,list,tuple)):
            return []
        if id(node) not in memo:
            children = node.items() if isinstance(node,dict) else enumerate(node)
            memo[id(node)] = [(key,)+tail for key,child in children for tail in paths(child)]
        return memo[id(node)]

    return paths(value)


class SearchCheckpoint:
    def __init__(self, store, metadata):
        self.store, self.metadata = store, metadata
        self.windows = []
        self.base, self.program = None, None
        self.paths = []

    def state(self, state):
        dfs=state.get('dfs') if state.get('kind')=='event_route_search' else state
        if dfs is None:return dict(state)
        if len(dfs['windows']) < len(self.windows):
            raise ValueError('Checkpoint audit windows cannot shrink')
        for window in dfs['windows'][len(self.windows):]:
            reference, _ = self.store.archive(window)
            self.windows.append(reference)
        return dict(state,dfs=dict(dfs,windows=self.windows)) if dfs is not state else dict(state,windows=self.windows)

    def __call__(self, state, program=None, extra_targets=()):
        saved = dict(self.metadata, windows=self.windows, searchState=self.state(state))
        self.base, _ = self.store.archive(saved)
        self.store.publish(self.base)
        dfs=state.get('dfs') if state.get('kind')=='event_route_search' else state
        self.dfs_path=['searchState','dfs'] if dfs is not state else ['searchState']
        active = dfs['stack'][-1].get('inFlight') if dfs and dfs['stack'] else None
        self.program = program if program is not None else active['program'] if active else None
        self.paths = identity_paths(saved,self.program) if self.program is not None else []
        self.extra_targets=[(identity_paths(saved,target),target) for target in extra_targets]

    def begin_repair(self,state,program,extra_targets=()):
        self(state,program,extra_targets)

    def repair_progress(self, state):
        dfs=state.get('dfs') if state.get('kind')=='event_route_search' else state
        active = dfs['stack'][-1].get('inFlight') if dfs and dfs['stack'] else None
        if self.base is None or self.program is None or active and active['program'] is not self.program:
            # Defensive fallback; normal DFS publishes dispatch before repair.
            self(state)
            return
        changes = [(list(path)+['repairState'],self.program['repairState']) for path in self.paths]
        changes += [(list(path),target) for paths,target in self.extra_targets for path in paths]
        changes.append((self.dfs_path+['cumulativeCalls'],dfs['cumulativeCalls']))
        self.store.patch(self.base,changes)

    def finish(self, result, validation):
        state = self.state(result['searchState'])
        result = dict(result, windows=self.windows, searchState=state)
        self.store.save(dict(self.metadata,windows=self.windows,result=result,validation=validation))
        self.store.compact()
