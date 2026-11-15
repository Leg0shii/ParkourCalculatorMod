"""Shared test builders extracted from tests.integration.test_movement_contacts."""
from parkour_solver.physics.vertical import CLIMB_CARRY

def small_request():
    return {'version':1,'initial':{'position':.8,'velocity':CLIMB_CARRY,'onGround':False},
        'region':{'minY':0,'maxY':1},'entries':[{'family':'front','firstCandidateState':0}],
        'finishAtExit':True,'entryDelay':0,'exitKinds':['top'],'maxInputs':5,
        'maxSchedules':1000,'maxSkeletons':2000,'maxMaskWork':30000}
