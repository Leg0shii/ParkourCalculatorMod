"""Separate raw, semantic-language and materialized-guide coverage evidence."""

from parkour_solver.search.support_profiles import prepare_scene
from research.legacy_cell_routes.checkpoint_experiment import replay_witness
from parkour_solver.spatial.event_routes import build_route_domain, build_event_graph, initial_cursor, next_goals, support_episodes
from parkour_solver.search.events.engine import discovery_settings
from parkour_solver.spatial.cells import decompose, segment_covered
from parkour_solver.spatial.guides import guide_for, task_reference
from parkour_solver.spatial.compat.progress import phase_trace
from parkour_solver.search.compat.corridor_queue import collision_request
from parkour_solver.spatial.compat.corridors import conditioned_anchor


def discover(fixture, compiler):
    """No trajectory, input decisions or witness arguments enter discovery."""
    request=fixture['request']; task=fixture.get('terminalGoal')
    if task and task['kind']=='landing_height_region':
        request=collision_request(request,compiler,5)
    effective=request
    scene=prepare_scene(request); d=decompose(scene)
    data=build_route_domain(scene,task,decomposition=d).to_dict();graph=build_event_graph(data)
    limits=discovery_settings(fixture.get('discoverySettings'))
    origin=scene['initial']['position']
    sources=[dict(id='initial',cursor=initial_cursor(data),entry=origin)]
    for event in graph['events']:
        if event['terminal']:
            continue
        if set(event['attachments'])==set(data['geometry']['starts']):
            continue
        entry=conditioned_anchor(event['cover'],origin,task_reference(data,origin))
        sources.append(dict(id=event['id'],cursor=dict(initial_cursor(data),attachments=event['attachments'],
            occurrences=[dict(event=event['id'],occurrence=0)]),entry=entry))
    import math
    sources[1:]=sorted(sources[1:],key=lambda s:(math.dist(s['entry'],origin),s['id']))
    materialized=[];residual=[];expansions=0
    for source in sources:
        quota=max(0,limits['maxGuideMaterializations']-len(materialized))
        requests,state,report=next_goals(data,graph,source['cursor'],
            max_materializations=quota,max_expansions=max(0,limits['maxGraphExpansions']-expansions))
        expansions+=report['expansions']
        for request in requests:
            guide=guide_for(data,graph,request,source['entry'])
            materialized.append(dict(source=source['id'],entry=source['entry'],request=request,guide=guide))
        residual.append(dict(source=source['id'],continuation=state))
    return dict(request=fixture['request'],effectiveRequest=effective,
        domain=data,graph=graph,materialized=materialized,residual=residual,
        work=dict(expansions=expansions,materializations=len(materialized)),settings=limits),d


def audit(fixture, compiler, witness):
    discovery,d=discover(fixture,compiler)
    # The reference first enters the pipeline here, after materialization.
    data=discovery['domain'];graph=discovery['graph'];scene=data['scene']
    replay=replay_witness(scene,witness,compiler)
    phases=phase_trace(witness['candidate'])
    raw=all(segment_covered(d,p['start'],p['end']) for p in phases)
    episodes=support_episodes(data,witness['movementProgram']['profile'])
    source='initial';word=[];checks=[];finished=False
    for episode in episodes:
        if episode['kind']!='support_visit':
            continue
        event=next((e for e in graph['events'] if e.get('surface') in episode['surfaces']),None)
        final=next(e for e in graph['events'] if e['terminal'])
        if final['goal']['kind']=='landing':
            solid=next(b['solidIndex'] for b in scene['blocks'] if b['id']==final['goal']['block'])
            if solid in episode['solids']:
                event=final
        if event is None:
            checks.append(dict(episode=episode,represented=False,materialized=False));continue
        matches=[m for m in discovery['materialized'] if m['source']==source and m['request']['event']==event['id']]
        checks.append(dict(event=event['id'],tick=episode['tick'],represented=True,
            materialized=any(m['guide']['status']=='witness' for m in matches),
            guides=[m['guide']['id'] for m in matches]))
        source=('initial' if set(event['attachments'])==set(data['geometry']['starts']) else event['id'])
        word.append(event['id'])
        if event['terminal']:
            finished=True;break
    final=next(e for e in graph['events'] if e['terminal'])
    matches=[m for m in discovery['materialized'] if m['source']==source and m['request']['event']==final['id']]
    if not finished:
        checks.append(dict(event=final['id'],represented=True,materialized=any(m['guide']['status']=='witness' for m in matches),
            guides=[m['guide']['id'] for m in matches]))
    return dict(discovery=discovery,witnessReplay=replay,rawGeometryAdmitted=raw,
        semanticProjectionRepresented=all(c['represented'] for c in checks),
        usefulGuidesMaterialized=all(c['materialized'] for c in checks),
        projection=checks,contactWord=word,namedEvents=[e for e in episodes if e['kind']=='named_contact'],
        interpretation='Unseeded geometry/guide coverage followed by archived replay; not cold search or a dynamic reachability proof')
