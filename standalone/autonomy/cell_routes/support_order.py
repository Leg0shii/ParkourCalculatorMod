class SupportOrder:
    def __init__(self,scene,supports,terminal_tick,initial_jump_inputs=None,*,max_inputs=None):
        ids={b['id'] for b in scene['blocks']}
        if not isinstance(supports,(list,tuple)) or len(set(supports))!=len(supports) or any(s not in ids for s in supports):
            raise ValueError('Support order requires distinct scene block IDs')
        self.supports=list(supports)
        self.start=scene['startBlock']
        self.terminal_tick=terminal_tick
        horizon=terminal_tick if terminal_tick is not None else max_inputs if max_inputs is not None else 200
        self.initial_jump_inputs = sorted(set(initial_jump_inputs)) if initial_jump_inputs is not None else None
        if self.initial_jump_inputs is not None and (not self.initial_jump_inputs or any(
                type(t) is not int or not 0 <= t < horizon for t in self.initial_jump_inputs)):
            raise ValueError('Initial jump inputs must fit the solve horizon')
        self.checkpoint_identity = dict(supports=self.supports, start=self.start, terminalTick=terminal_tick,
                                       initialJumpInputs=self.initial_jump_inputs)
        if terminal_tick is None:
            self.checkpoint_identity['searchHorizon']=horizon
        self.boxes={tuple(scene['collisionScene']['solids'][b['solidIndex']]):b['id'] for b in scene['blocks']}

    def audit(self,program):
        p=program['profile']
        if self.initial_jump_inputs is not None:
            jumps = [t for t, j in enumerate(p['jump']) if j and p['grounded'][t]]
            if jumps and jumps[0] not in self.initial_jump_inputs or not jumps and p['airtime'] > max(self.initial_jump_inputs):
                return dict(accepted=False, progress=0, observed=[], reason='initial_jump_domain')
        events=[]
        for e in p.get('contactEvents',[]):
            if e.get('axis')=='Y' and e.get('direction')==-1 and tuple(e['box']) in self.boxes:
                events.append((e['offset']+1,self.boxes[tuple(e['box'])]))
        for e in p.get('caseEvents',[]):
            if e.get('type')=='stepping' and 'box' in e and tuple(e['box']) in self.boxes:
                events.append((e['input']+1,self.boxes[tuple(e['box'])]))
        index,current=0,self.start
        observed=[]
        ranks={name:i for i,name in enumerate(self.supports)}
        for tick,name in sorted(set(events),key=lambda e:(e[0],ranks.get(e[1],-1))):
            if name==current:continue
            observed.append(dict(tick=tick,block=name))
            if index==len(self.supports):
                current=name
                continue
            if name!=self.supports[index]:
                return dict(accepted=False,progress=index,observed=observed,reason='support_order')
            current=name
            index+=1
        accepted=self.terminal_tick is None or p['airtime']!=self.terminal_tick or index==len(self.supports)
        return dict(accepted=accepted,progress=index,observed=observed,
                    reason=None if accepted else 'unfinished_support_order_at_clock')

    def __call__(self,program):return self.audit(program)['accepted']

    def remaining(self, program):
        return self.supports[self.audit(program)['progress']:]

    def priority(self,program):return -self.audit(program)['progress']
