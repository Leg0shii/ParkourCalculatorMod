"""Heuristic extra-run exclusion within one latest-jump interaction pattern."""
import copy

from parkour_solver.movement.program import empty_program, program_physics_key, truncate_program
from parkour_solver.search.identity import digest
from parkour_solver.search.comparison import comparison_label, priority_key, compare_candidates


POLICY_VERSION = 3


def _shift(event, tick):
    result=copy.deepcopy(event)
    for field in ('offset','input','state'):
        if field in result:result[field]-=tick
    if 'anchors' in result:
        result['anchors']={k:v-tick for k,v in result['anchors'].items()}
    return result


def latest_jump(program, scene):
    """Ignore only the ordinary ground hold immediately before the last jump.

    Prior decisions, in-flight contacts/cases, constraints and terminal contact
    stay in the key. Unknown or interacting ground preparations do not qualify.
    """
    p=program['profile'];n=p['airtime']
    jumps=[t for t in range(n) if p['jump'][t] and p['grounded'][t]]
    if not jumps or program.get('ladderEvents') or any(p['ladderTicks']):return None
    takeoff=jumps[-1];ground=takeoff
    while ground and p['grounded'][ground-1] and not p['jump'][ground-1]:ground-=1
    if any(p['sneak'][ground:takeoff]):return None
    events=p.get('contactEvents',[]);cases=p.get('caseEvents',[])
    boxes={tuple(b) for e in events if e['offset']==ground-1 and e['axis']=='Y' and e['direction']==-1 for b in e['boxes']}
    boxes.update(tuple(b) for e in cases if e['input']==ground-1 for b in e.get('boxes',()))
    if ground==0:
        boxes.update(tuple(scene['collisionScene']['solids'][b['solidIndex']]) for b in scene['blocks']
            if b['id']==scene['startBlock'] or b.get('sourceBlock')==scene['startBlock'])
    if not boxes:return None
    if any(ground<=e['input']<takeoff for e in cases):return None
    holds=[e for e in events if ground<=e['offset']<takeoff]
    if any(e['axis']!='Y' or e['direction']!=-1 or not any(tuple(b) in boxes for b in e['boxes']) for e in holds):return None
    choices=p.get('regionChoices',[])
    if any(ground<=c['offset']<takeoff for c in choices):return None # An open choice inside the hold.
    before=truncate_program(program,ground) if ground else empty_program(program['initial'],threshold=program.get('threshold',.005))
    rows=[]
    owners=p.get('constraintOwners',[0]*len(p['positionConstraints']))
    if len(owners)!=len(p['positionConstraints']):return None
    if ground==0:
        initial_rows=[copy.deepcopy(r) for r,o in zip(p['positionConstraints'],owners)
            if o==0 and r['t1']==0 and r.get('t2') in (None,0)]
        before['profile']['positionConstraints']=initial_rows
        before['profile']['constraintOwners']=[0]*len(initial_rows)
    for row,owner in zip(p['positionConstraints'],owners):
        end=max(row['t1'],row.get('t2') if row.get('t2') is not None else row['t1'])
        if end<=ground and owner<=ground:continue
        if not owner or owner<=ground:return None # Unclassified task row across the preparation.
        if owner<=takeoff:
            if row.get('name')!='normal:landing':return None
            continue # Ordinary finite-support hold, already identified above.
        if row['t1']<takeoff or row.get('t2') is not None and row['t2']<takeoff:return None
        normalized={k:v for k,v in row.items() if k!='name'}
        for k in ('t1','t2'):
            if normalized.get(k) is not None:normalized[k]-=takeoff
        rows.append(normalized)
    suffix={k:p[k][takeoff:] for k in ('position','velocity','grounded','jump','sneak','ladderTicks')}
    suffix.update(contacts=[_shift(e,takeoff) for e in p.get('contacts',[]) if e['state']>takeoff],
        contactEvents=[_shift(e,takeoff) for e in events if e['offset']>=takeoff],
        caseEvents=[_shift(e,takeoff) for e in cases if e['input']>=takeoff],
        regionChoices=[dict(_shift(c,takeoff),options=[[_shift(r,takeoff) for r in rows] for rows in c['options']])
                       for c in choices if c['offset']>=takeoff],
        rows=sorted(rows,key=digest),terminalSupport=program.get('terminalSupport'),
        terminalGoal=program.get('terminalGoal'),landingPhase=p.get('landingPhase'))
    return dict(pattern=digest(dict(version=POLICY_VERSION,before=program_physics_key(before),suffix=suffix)),
        runTicks=takeoff-ground,groundState=ground,jumpInput=takeoff,
        label=comparison_label(program,context=dict(basis='matched_preparation')))


def remember(representatives, description, candidate, arrival):
    if description is None:return
    key=description['pattern'];old=representatives.get(key)
    if old is None or priority_key(description['label'])<priority_key(old['label']):
        representatives[key]=dict(description,candidate=candidate,arrival=arrival)


def exclusion(representatives, description):
    if description is None:return None
    peer=representatives.get(description['pattern'])
    if peer is None:return None
    candidate=dict(description['label'],comparisonContext=description['pattern'])
    other=dict(peer['label'],comparisonContext=description['pattern'],validated=True)
    decision=compare_candidates(candidate,other,context=dict(purpose='matched_preparation',localDeferralEnabled=True))
    if decision['action']!='defer_preparation':return None
    return dict(reason='extra_runs_before_latest_jump',heuristic=True,pattern=description['pattern'],
        runTicks=description['runTicks'],minimumFeasibleRunTicks=peer['runTicks'],
        groundState=description['groundState'],jumpInput=description['jumpInput'],representative=peer['candidate'])
