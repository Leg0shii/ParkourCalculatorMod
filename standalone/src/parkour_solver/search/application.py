"""Automatic physical/virtual task application service; no CLI or research dependency."""
import copy

from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.search.engine import find_checkpoint_route
from parkour_solver.search.domain import InitialJumpDomain
from parkour_solver.validation.witness import replay_witness


def task_settings(fixture):
    # Archived fixtures still set fair_parents, removed from the search API in 1df37d63.
    settings=copy.deepcopy(fixture.get('settings',{}))
    settings.pop('fair_parents',None)
    return settings


def run(fixture,compiler=None,*,python_executable=None,resume_state=None,on_progress=None,cancel_event=None):
    if compiler is None:
        from parkour_solver.bridge.python_compiler import PythonCompiler
        compiler=PythonCompiler()
    request=fixture['request']
    if 'spatialSettings' in fixture:
        raise ValueError('Legacy spatialSettings is not event discovery; use discoverySettings version1')
    from parkour_solver.movement.settings import movement_settings
    cfg=movement_settings(prepare_scene(request))
    policy=InitialJumpDomain(fixture.get('initialJumpInputs'),max_inputs=cfg['maxInputs'])
    result=find_checkpoint_route(request,compiler,task_goal=fixture.get('terminalGoal'),
        anchor_search=fixture.get('anchorSearch'),
        discovery_settings=fixture.get('discoverySettings'),program_filter=policy,
        resume_state=resume_state,on_progress=on_progress,cancel_event=cancel_event,
        python_executable=python_executable,**task_settings(fixture))
    validation=[]
    if result['best'] is not None:
        checked=replay_witness(prepare_scene(result['effectiveRequest']),result['best'],compiler)
        validation.append(checked)
        if not checked['passed']:
            raise RuntimeError('Automatic route incumbent failed independent physical replay')
    return result,validation
