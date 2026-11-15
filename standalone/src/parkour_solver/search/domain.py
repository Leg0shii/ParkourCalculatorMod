class InitialJumpDomain:
    """Initial input restriction independent of spatial support discovery."""
    def __init__(self,initial_jump_inputs,*,max_inputs):
        self.initial_jump_inputs=sorted(set(initial_jump_inputs)) if initial_jump_inputs is not None else None
        if self.initial_jump_inputs is not None and (not self.initial_jump_inputs or any(
                type(t) is not int or not 0<=t<max_inputs for t in self.initial_jump_inputs)):
            raise ValueError('Initial jump inputs must fit the solve horizon')
        self.checkpoint_identity=dict(initialJumpInputs=self.initial_jump_inputs,searchHorizon=max_inputs)

    def __call__(self,program):
        if self.initial_jump_inputs is None:
            return True
        p=program['profile']
        jumps=[t for t,j in enumerate(p['jump']) if j and p['grounded'][t]]
        return jumps[0] in self.initial_jump_inputs if jumps else p['airtime']<=max(self.initial_jump_inputs)
