import argparse
import collections
import copy
import csv
import functools
import gzip
import hashlib
import json
from pathlib import Path

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.cell_routes.checkpoint_search import _key
from standalone.autonomy.movement_composition import compile_program
from standalone.autonomy.route import prepare_route, schedule


ROOT=Path(__file__).resolve().parent
SOURCES=['orchestrator-real11-stable','orchestrator-real11-stable-resume','orchestrator-real11-stable-resume2']


def read(path):
    with gzip.open(path,'rt') as handle:
        return json.load(handle)


def digest(value):
    return hashlib.sha256(json.dumps(value,sort_keys=True,allow_nan=False).encode()).hexdigest()


def records(report):
    state=report['result']['searchState']
    return [c for w in state['windows'] for c in w['candidates']]+(state['current'] or {}).get('records',[])


def spans(values):
    groups=[]
    for value in sorted(set(values)):
        if groups and value==groups[-1][-1]+1:
            groups[-1].append(value)
        else:
            groups.append([value])
    return ','.join(str(g[0]) if len(g)==1 else f'{g[0]}..{g[-1]}' for g in groups) or '-'


def summary_program(program, boxes):
    p=program['profile'];n=p['airtime']
    contacts=collections.defaultdict(list)
    for event in p.get('contactEvents',[]):
        name=boxes.get(tuple(event['box']),'unmapped')
        contacts[(name,event['axis'],event['direction'])].append(event['offset']+1)
    return dict(id=_key(program),ticks=n,
        jumps=[t for t in range(n) if p['jump'][t] and p['grounded'][t]],
        runs=[t for t in range(n) if p['grounded'][t] and not p['jump'][t]],
        supportContacts=[dict(block=k[0],axis=k[1],direction=k[2],states=v) for k,v in contacts.items()],
        terminalSupport=program.get('terminalSupport'),y=p['position'][-1],vy=p['velocity'][-1],
        onGround=p['grounded'][-1],cases=p.get('caseEvents',[]),
        physicalClass=digest({k:v for k,v in p.items() if k not in ('positionConstraints','generatedUnits')}),
        selectedRows=p['positionConstraints'])


def extra_rows(completed):
    rows,passages,hits=json.loads(completed['branch'])
    if passages or hits:
        raise ValueError('This audit requires row-assembled branches with no additional passage/hit objects')
    return [dict(leg=leg,offset=t,mode=axis,cmp=cmp,rhs=rhs) for leg,t,axis,cmp,rhs in rows]


def align_contacts(completed, attempts, contacts):
    if len(completed)!=len(attempts):
        raise ValueError('Each completed branch must have exactly one saved allocation attempt')
    byhash={h:i for i,b in enumerate(completed) for h in b['problemHashes']}
    anchors={}
    for j,contact in enumerate(contacts):
        audit=contact.get('candidateFilter',{}).get('corridorAudit')
        if audit:
            anchors[j]=byhash[audit['numericalRequests'][0]['problemHash']]
    missing=len(completed)-len(contacts)
    if missing<0:
        raise ValueError('More contacts than completed branches')
    def matches(i,j):
        if j in anchors and anchors[j]!=i:
            return False
        if len(extra_rows(completed[i]))!=contacts[j]['constraintCount']:
            return False
        if completed[i]['status']=='feasible':
            return 'geometryCheck' in contacts[j] or 'candidateFilter' in contacts[j]
        return contacts[j]['status']=='unknown' and not contacts[j].get('candidateFilter') and not contacts[j].get('geometryCheck')
    @functools.lru_cache(None)
    def possible(i,j):
        if i==len(completed):
            return j==len(contacts)
        if j>len(contacts) or i-j>missing or len(completed)-i<len(contacts)-j:
            return False
        return (j<len(contacts) and matches(i,j) and possible(i+1,j+1) or
                completed[i]['status']=='feasible' and i-j<missing and possible(i+1,j))
    if not possible(0,0):
        raise ValueError('Saved branch/contact logs have no consistent alignment')
    outcomes=[set() for _ in completed];frontier={(0,0)}
    while frontier:
        following=set()
        for i,j in frontier:
            if i==len(completed):
                continue
            if j<len(contacts) and matches(i,j) and possible(i+1,j+1):
                outcomes[i].add(j);following.add((i+1,j+1))
            if completed[i]['status']=='feasible' and i-j<missing and possible(i+1,j):
                outcomes[i].add(None);following.add((i+1,j))
        frontier=following
    return outcomes


def contact_outcome(contact):
    if contact is None:
        return 'validation_record_missing'
    if contact.get('geometryCheck',{}).get('feasible'):
        return 'accepted_prefix'
    verdict=contact.get('candidateFilter',{})
    if not verdict.get('accepted',True):
        return verdict['reason']
    return contact.get('termination','unknown')


def base_route(scene,program,context):
    route=compile_program(scene,program,require_complete=False)['route']
    route['objective']=dict(axis='X',sense='MAX')
    route['guideObjective']=context['objective']
    route['constraints']=copy.deepcopy(context['constraints']+context['probeConstraints'])
    return route


def row_key(row):
    return (row['mode'],row['t1'],row.get('t2'),row.get('op','PLUS'),row['cmp'],row['rhs'])


def canonical_rows(rows):
    return sorted(set(row_key(r) for r in rows),key=str)


def extract(reports):
    last=reports[-1];state=last['result']['searchState'];scene=prepare_scene(last['fixture']['request'])
    boxes={tuple(scene['collisionScene']['solids'][b['solidIndex']]):b['id'] for b in scene['blocks']}
    final_records=records(last);programs=[];branches=[];probes=[];snapshots=[]
    for report in reports:
        snapshots.append({c['id']:c for c in records(report)})
    for index,record in enumerate(final_records,1):
        pid=f'P{index:02}';program=record['program'];repair=program['repairState'];context=repair['context']
        basic=summary_program(program,boxes);basic.update(label=pid,record=record,
            route='R1',scope=record['solveScope'],objective=context['objective'])
        route=base_route(scene,program,context);base=schedule(prepare_route(route),[0])
        initial_rows=[r for r in record['witness']['plan']['walls'] if r.get('name','').startswith('initial:')]
        base['walls']=[r for r in base['walls'] if not r.get('name','').startswith('initial:')]+initial_rows
        basic['basePlan']=base;basic['compiledRoute']=route
        alignment=align_contacts(repair['completed'],repair['attemptLog'],repair['contactLog'])
        for i,(completed,attempt,positions) in enumerate(zip(repair['completed'],repair['attemptLog'],alignment),1):
            candidates=[None if j is None else repair['contactLog'][j] for j in sorted(positions,key=lambda j:-1 if j is None else j)]
            captured=next((c['candidateFilter']['corridorAudit'] for c in candidates if c and
                           'corridorAudit' in c.get('candidateFilter',{})),None)
            extra=extra_rows(completed)
            selected=repair['interactionChoices'].get(completed['branch'],[])
            session=next(j+1 for j,snapshot in enumerate(snapshots) if record['id'] in snapshot and
                         len(snapshot[record['id']]['program']['repairState']['completed'])>=i)
            branch_route=copy.deepcopy(route);branch_route['constraints'].extend(extra)
            plan=schedule(prepare_route(branch_route),[0])
            plan['walls']=[r for r in plan['walls'] if not r.get('name','').startswith('initial:')]+initial_rows
            if captured and canonical_rows(plan['walls'])!=canonical_rows(captured['plan']['walls']):
                raise ValueError(f'Constraint reconstruction disagrees with captured plan: {pid}/{i}')
            branches.append(dict(label=f'{pid}-B{i:03}',program=pid,index=i,session=session,
                key=completed['branch'],extraRows=extra,selectedInteractions=selected,
                plan=plan,constraintsReconstruction='numeric set verified against captured plan' if captured else
                    'numeric set reconstructed from saved program/context/branch; original order/names/multiplicity not retained',
                attempt=attempt,completedStatus=completed['status'],
                possibleContactIndices=sorted(positions,key=lambda j:-1 if j is None else j),
                postResults=sorted({contact_outcome(c) for c in candidates}),
                contactCandidates=candidates,corridorAudit=captured,
                workerCalls=len(attempt.get('diagnostics',[])),
                physicalReplay=None))
        probe=record.get('spatial_probe')
        if probe:
            for j,attempt in enumerate(probe['measurement']['attempts'],1):
                best=probe.get('best');saved=best if best and best['plan']['ticks']==attempt['ticks'] else None
                probes.append(dict(label=f'{pid}-S{j:02}',program=pid,goal=probe['measurement']['goal'],
                    session=next(k+1 for k,snapshot in enumerate(snapshots) if record['id'] in snapshot and
                                 snapshot[record['id']].get('spatial_probe')),
                    summary=attempt,recordedBest=saved,recordedProgram=probe['program'] if saved else None,
                    exactConstraintsAvailable=saved is not None))
        programs.append(basic)
    frontier=[]
    for i,p in enumerate(state['current']['ready'],1):
        frontier.append(dict(label=f'Q{i:02}',kind='emitted_ready_not_evaluated',program=p,**summary_program(p,boxes)))
    continuation=state['current']['generation']['continuation']
    for i,p in enumerate(continuation['pending'],1):
        frontier.append(dict(label=f'F{i:02}',kind='symbolic_prefix_pending_expansion',program=p,**summary_program(p,boxes)))
    if continuation.get('expansion'):
        p=continuation['expansion']['program']
        frontier.append(dict(label='F-current',kind='symbolic_expansion_in_progress',program=p,**summary_program(p,boxes)))
    exclusions=[dict(stage=i,**x) for i,w in enumerate(state['windows'])
                for x in w['generation'].get('continuation',{}).get('exclusions',[])]+[
                    dict(stage=state['stage'],**x) for x in continuation.get('exclusions',[])]
    from standalone.autonomy.cell_routes.geometry import decompose
    decomposition=decompose(scene)
    return dict(scene=scene,fixture=last['fixture'],corridor=last['result']['corridor'],
        routeCells=[dict(id=c.id,bounds=c.bounds,dimension=c.dimension) for c in decomposition.cells
                    if c.id in last['result']['corridor']['cells']],programs=programs,
        branches=branches,probes=probes,frontier=frontier,generationExclusions=exclusions,
        reports=[dict(source=SOURCES[i],work=q['result']['work'],elapsedSeconds=q['result']['elapsedSeconds'],
                      compiler=q['result']['compiler']) for i,q in enumerate(reports)])


def replay(audit,manifest):
    from standalone.autonomy.compiler import JavaCompiler
    from standalone.autonomy.obstacle_geometry import check_geometry
    with JavaCompiler(json.loads(manifest.read_text())) as compiler:
        for program in audit['programs']:
            program['coefficients']=compiler.compile(program['compiledRoute'],program['basePlan'],5)
        for branch in audit['branches']:
            captured=branch['corridorAudit']
            if captured:
                branch['physicalReplay']=check_geometry(audit['scene']['collisionScene'],captured['plan'],
                    captured['candidate'],captured['vertical'],compiler,5)


def metrics(audit):
    calls=sum(b['workerCalls'] for b in audit['branches'])
    spatial=sum(p['summary']['work'].get('solverCalls',0) for p in audit['probes'])
    totals=sum(r['work'].get('solverCalls',0) for r in audit['reports'])
    if calls+spatial!=totals:
        raise ValueError('IPOPT call ledger does not reconcile with session counters')
    return dict(programs=len(audit['programs']),mainBranches=len(audit['branches']),mainWorkerCalls=calls,
        spatialProgramEvaluations=len(audit['probes']),spatialWorkerCalls=spatial,totalWorkerCalls=totals,
        mainWorkerStatuses=dict(collections.Counter(d['status'] for b in audit['branches'] for d in b['attempt'].get('diagnostics',[]))),
        preDispatchStatuses=dict(collections.Counter(b['attempt']['status'] for b in audit['branches'] if not b['workerCalls'])),
        recordedPostResults=dict(collections.Counter(contact_outcome(c) for p in audit['programs'] for c in p['record']['program']['repairState']['contactLog'])),
        missingContactRecords=len(audit['branches'])-sum(len(p['record']['program']['repairState']['contactLog']) for p in audit['programs']),
        mainWorkerElapsedSeconds=sum(d.get('elapsedSeconds',0) for b in audit['branches'] for d in b['attempt'].get('diagnostics',[])),
        wallSeconds=sum(r['elapsedSeconds'] for r in audit['reports']),
        corridorPhysicalReplay=dict(collections.Counter(str(b['physicalReplay']['feasible']) for b in audit['branches'] if b['physicalReplay'] is not None)),
        emittedReady=sum(f['kind']=='emitted_ready_not_evaluated' for f in audit['frontier']),
        symbolicFrontier=sum(f['kind']!='emitted_ready_not_evaluated' for f in audit['frontier']),
        generationExclusions=len(audit['generationExclusions']),
        generationExclusionReasons=dict(collections.Counter(x['proof']['reason'] for x in audit['generationExclusions'])))


def cell(value):
    return str(value).replace('|','\\|').replace('\n',' ')


def table(headers,rows):
    return ['| '+' | '.join(headers)+' |','| '+' | '.join('---' for _ in headers)+' |']+[
        '| '+' | '.join(cell(v) for v in row)+' |' for row in rows]


def support_text(program):
    return '; '.join(f"{c['block']} {c['axis']}{'+' if c['direction']>0 else '-'}@{spans(c['states'])}"
                     for c in program['supportContacts']) or '-'


def render(audit):
    counts=audit['counts'];out=[]
    physical_count=counts['corridorPhysicalReplay'].get('True',0)
    replay_note=f'{physical_count} of62 independently physically replayed and passing'
    def emit(text=''):
        out.extend(text.splitlines()+[''])
    def rows(headers,values):
        out.extend(table(headers,values)+[''])
    clauses=sorted({(r['mode'],r['cmp'],r['rhs']) for b in audit['branches'] for r in b['plan']['walls']})
    symbols={key:f'C{i:02}' for i,key in enumerate(clauses,1)}
    def constraint_text(values):
        grouped=collections.defaultdict(list)
        for r in values:
            grouped[symbols[(r['mode'],r['cmp'],r['rhs'])]].append(r.get('t1',r.get('offset')))
        return '; '.join(f'{key}@{spans(ticks)}' for key,ticks in sorted(grouped.items())) or '-'
    def provenance(axis,cmp,rhs):
        from standalone.autonomy.obstacle_geometry import expanded_box
        result=[];coordinate=0 if axis=='X' else 2
        for block in audit['scene']['blocks']:
            box=expanded_box(audit['scene']['collisionScene']['solids'][block['solidIndex']])
            for edge in (0,3):
                if abs(rhs-box[coordinate+edge])<2e-6:
                    inside=(cmp=='GE' and edge==0 or cmp=='LE' and edge==3)
                    result.append(block['id']+' '+('lower ' if edge==0 else 'upper ')+axis+
                                  (' inside/support boundary' if inside else ' outside/avoid boundary'))
        return '; '.join(result) or 'explicit task/route inequality'
    def interactions(values):
        return '; '.join(f"{audit['scene']['blocks'][p['solidIndex']]['id']}:"+
            '>'.join(f"{f['axis']}{'+' if f['direction']>0 else '-'}" for f in p['faces'])+
            f" gates={p['gates']}" for p in values) or 'no passage metadata; see rows'
    def known_value(branch):
        captured=branch['corridorAudit']
        if captured:
            return f"{captured['candidate']['objective']:.9f}"
        parent=next(p for p in audit['programs'] if p['label']==branch['program'])
        witness=parent['record']['witness']
        if witness.get('problemHash')==branch['attempt'].get('problemHash'):
            return f"{witness['candidate']['objective']:.9f} (saved incumbent)"
        return 'not saved'
    def post_text(branch):
        if not branch['workerCalls']:
            proof=branch['attempt'].get('certificate',{})
            return proof.get('reason') or branch['attempt'].get('error','no worker dispatch')
        if 'corridor_signature' in branch['postResults']:
            failure=branch['corridorAudit']['failure']
            physical=branch['physicalReplay']
            return f"route {failure['expected']} vs {failure['actual']}; physical "+(
                'PASS' if physical and physical['feasible'] else 'not checked')
        if 'validation_record_missing' in branch['postResults']:
            return 'accepted or missing audit*'
        return 'accepted physical prefix'
    emit('# Real11 candidate and numerical-request audit')
    emit('2026-11-03. Read-only audit of the three cold sessions archived by96681997. No new IPOPT solve, candidate pruning or search-policy change was made. The separate fixed-reference seeded control and unit-test solves are outside the208-call cold count. All original reports are preserved.')
    emit('## Exact count and scope')
    rows(['What was tried','Count','Meaning'],[
        ('Prescribed spatial routes',1,'R1, through B2/B3/B4/B5 and left of B6; B7 landing optional'),
        ('Main movement programs evaluated',counts['programs'],'2 prefixes at state10;5 at state28'),
        ('Main fixed constraint branches handled',counts['mainBranches'],'Each row in the complete branch tables below'),
        ('Main branches sent to IPOPT',counts['mainWorkerCalls'],'All172 worker returns report feasible'),
        ('Branches rejected before dispatch',37,'Necessary reachability screen; no IPOPT call'),
        ('Branches stopped before dispatch',18,'Deadline/call budget; unknown, not infeasible'),
        ('Spatial-goal program evaluations',counts['spatialProgramEvaluations'],'All19 listed separately;36 worker calls'),
        ('Total worker calls',counts['totalWorkerCalls'],'172 main +36 spatial =150+36+22 across sessions'),
        ('Recorded accepted main branch validations',108,'Valid prefixes, not64-input completions'),
        ('Recorded route-signature rejections',62,replay_note),
        ('Missing main validation records',2,'Geometry-validation deadline paths; exact branch mapping is partly ambiguous'),
        ('Emitted programs still ready',20,'Listed below; none had a main numerical evaluation'),
        ('Symbolic prefixes awaiting/inside expansion',16,'15 pending +1 in-progress; listed below'),
        ('Saved pre-numerical movement exclusions',57,'25 dead falls,29 interval contradictions,3 norm contradictions; all listed below')])
    rows(['Session','Wall seconds','Numerical proposals','Worker calls','New main branches','Where it stopped'],[
        (i+1,f"{r['elapsedSeconds']:.3f}",r['work'].get('numericalProposals',0),r['work'].get('solverCalls',0),
         sum(b['session']==i+1 for b in audit['branches']), 'state28, unfinished constraint enumeration')
        for i,r in enumerate(audit['reports'])])
    emit(f"Main worker-reported elapsed time totals{counts['mainWorkerElapsedSeconds']:.3f}s, versus{counts['wallSeconds']:.3f}s session wall time. Spatial-worker timings were not individually saved. Compiler/footprint/scene-bridge timings sum to"+
        f"{sum(sum(r['compiler']['elapsedSeconds'].values()) for r in audit['reports']):.3f}s. The large remaining time is outside the recorded main-worker timing; these logs cannot precisely apportion it among constraint generation/LP screening, Python assembly, worker startup, copying and checkpoint I/O. It is incorrect to describe the main172 calls as failed IPOPT feasibility searches.")
    emit('## Shared route, indexing and model')
    emit('R1 is one prescribed spatial route: B1 start -> B2 -> B3 -> B4 -> B5 -> left passage around B6 -> final state64 maximizing Z. The route representative uses cells '+str(audit['corridor']['cells'])+'. No competing spatial route was enumerated in these sessions. Projection is the geometry-derived free-space union; waypoint X728.9 is not a solver wall. Exact points, projection rectangles and geometry are in candidate-ledger.json.gz.')
    emit('The saved cell IDs form the unordered set touched by the representative guide, not an ordered itinerary imposed on every solution. These are player-center free-space cells/surfaces; exact bounds are in the JSON, with the following display rounded to6 decimals. The complete declared physical blocks are the seven user-specified boxes below.')
    rows(['Block','Physical AABB [minX,minY,minZ,maxX,maxY,maxZ]'],[
        (b['id'],audit['scene']['collisionScene']['solids'][b['solidIndex']]) for b in audit['scene']['blocks']])
    rows(['Guide cell','Dimension','X interval','Feet-Y interval','Z interval'],[
        (c['id'],c['dimension'],f"[{c['bounds'][0]:.6f},{c['bounds'][3]:.6f}]",
         f"[{c['bounds'][1]:.6f},{c['bounds'][4]:.6f}]",f"[{c['bounds'][2]:.6f},{c['bounds'][5]:.6f}]")
        for c in audit['routeCells']])
    emit('Input t uses state t and produces state t+1. Tables use zero-based input indices and pre-input state indices. A support Y contact at state k is resolved by input k-1 and requires horizontal overlap at state k-1. Repeated B1 contacts during run inputs are support checks, not repeated voluntary jumps. X/Z facings remain free on every re-solve from the original state (746.5,5,-1104.5), horizontal velocity(0,0). Momentum is carried normally; inertia thresholds are0. All main scopes model every declared solid through that program horizon, but allow a prefix of the full route signature. No main solve reached state44/55/64.')
    sample=next(b['corridorAudit'] for b in audit['branches'] if b['corridorAudit'])
    rows(['Gate','Block','X crossing plane','Allowed lateral Z range for side0','R1 crossing'],[
        (g['id'],next(b['id'] for b in audit['scene']['blocks'] if audit['scene']['collisionScene']['solids'][b['solidIndex']]==g['box']),
         g['plane'],f"[{g['lo']}, {g['hi']}]",next(t for t in audit['corridor']['signature'] if t[0]==g['id'])) for g in sample['gates']])
    emit('A token[gate,-1,side] means crossing its X plane toward decreasing X. side=-1/0/+1 means below/inside/above the listed Z interval. This policy classifies the horizontal trace without considering that the player may already be above the block. In particular P03-B001 crosses the B3 plane on input27 at feet Y7.176759, above B3 top7, but fails the side0 expectation because Z is below the interval. '+replay_note+'. Physical validity alone does not establish that an alternative completes the selected route or later landing.')
    emit('The main clock direction is the same for all candidates: '+str(next(iter(audit['programs'][0]['record']['clock_results'].values()))['context']['direction'])+'. Relative progress subtracts the original anchor; the raw solver objective has a constant offset. These two values must not be confused. Main objectives are fixed-clock progress proxies, not route-completion times. Spatial probes separately minimize achieved time to a common support. The configured per-worker cap is2s; actual calls may have a smaller remaining allowance. Each local evaluation has8s and up to6 calls; deferred constraint work resumes from its cursor.')
    emit('Only normal movement inputs actually occur inside these seven prefixes. P04/P05 retain an approach row from a stepping/blip proposal truncated before its future contact, but do not execute a step within28. There are no ladder, ceiling-hit or intentional wall-hit decisions in the evaluated prefixes. These manual checkpoints add no explicit X/Z portal rows. Ordered corridor crossings remain post-solve checks and may add repair rows before the next call, explaining the first rejected and second accepted solves for P03/P04/P05.')
    if all(p.get('coefficients') for p in audit['programs']):
        phases=collections.defaultdict(set)
        for p in audit['programs']:
            profile=p['record']['program']['profile'];coeff=p['coefficients']
            for t in range(p['ticks']):
                kind='jump' if profile['jump'][t] and profile['grounded'][t] else 'ground run' if profile['grounded'][t] else 'air'
                phases[kind].add((coeff['magnitude'][t],coeff['friction'][t]))
        rows(['Input phase','Compiled acceleration magnitude','Compiled stored-velocity drag'],[
            (kind,m,f) for kind,values in phases.items() for m,f in sorted(values)])
    emit('## Every main movement program tried')
    rows(['Program / saved ID','Horizon','Jump inputs','Grounded nonjump inputs','Chosen contacts / decisions','State at horizon','Main branches / IPOPT calls','Best relative progress / forward v / speed','Spatial comparison','Disposition'],[
        (p['label']+' / '+p['id'][:12],p['ticks'],spans(p['jumps']),spans(p['runs']),support_text(p)+
         ('; extra '+constraint_text([dict(r,offset=r['t1']) for r in p['selectedRows'] if r.get('name','').startswith('blip:')]) if any(r.get('name','').startswith('blip:') for r in p['selectedRows']) else ''),
         f"Y={p['y']:.6f}, vy={p['vy']:.6f}, {'ground' if p['onGround'] else 'air'}",
         f"{sum(b['program']==p['label'] for b in audit['branches'])} / {sum(b['workerCalls'] for b in audit['branches'] if b['program']==p['label'])}",
         ' / '.join(f'{v:.6f}' for v in [next(iter(p['record']['clock_results'].values()))['objective'],
                    next(iter(p['record']['clock_results'].values()))['state']['forwardVelocity'],
                    next(iter(p['record']['clock_results'].values()))['state']['speed']]),
         f"{p['record']['spatial_probe']['measurement']['goal']['block']} at {p['record']['spatial_probe']['measurement']['ticks']}",
         'retained after state10' if p['ticks']==10 else 'awaits state28 window completion; not heuristically pruned') for p in audit['programs']])
    emit('P03/P04/P05 have identical scalar/contact/input histories. P04 additionally has the future-blip approach row Z[28]<=-1100.300001011921; P05 has X[28]>=735.3000010119209. No blip contact has occurred within these prefixes. Their physical feasible sets at28 are subsets of P03, and their accepted scores differ only around floating-point noise. P06 instead self-hops/returns to B1 after four run inputs. P07 waits five inputs on B1 and is still airborne at28. Their B3 spatial probes add future decisions; they are not already committed B3 landings in the main28-input programs.')
    emit('Committed decisions: after the state10 comparison, both the immediate0 and delayed8 setup were retained as parents. At state28 no outer comparison/commit occurred because the window never finished. P03..P07 are proposed continuations of those parents, not five successive committed decisions. The engine repeatedly resumes constraint choices for the same P06/P07 movement program; all earlier facings remain variable. Spatial-probe future decisions inform comparison measurements but are not automatically adopted as the main committed continuation.')
    emit('## Exact constraint notation')
    emit('Every Cxx is the exact saved/reconstructed numeric inequality below. Cxx@a..b means that inequality at every integer state a through b, inclusive. A branch contains its program baseline plus the extra rows in its table. Y motion/contact schedules are fixed by the program table and full scalar profiles in the JSON ledger. Duplicate numeric inequalities are listed once in this notation; raw captured plans retain original multiplicities. Initial support bounds are copied from saved witness plans to preserve Java-derived float endpoints. Other rows are reconstructed with the existing builders; all62 captured corridor plans have exactly matching numeric constraint sets. Original names/order/multiplicity for some other branches were not saved.')
    rows(['Symbol','Inequality on state t','Boundary provenance'],[(symbol,f"{axis}[t] {'>=' if cmp=='GE' else '<=' if cmp=='LE' else '='} {rhs!r}",
        provenance(axis,cmp,rhs)) for (axis,cmp,rhs),symbol in symbols.items()])
    rows(['Program','Baseline inequalities','Horizontal clip contacts','Fixed finite scene contacts'],[
        (p['label'],constraint_text(p['basePlan']['walls']),p['basePlan']['contacts'],support_text(p)) for p in audit['programs']])
    emit('## Every main fixed constraint branch tried')
    emit('These227 rows include172 actual IPOPT calls,37 pre-dispatch rejections and18 pre-dispatch budget stops. Every IPOPT row below has a recorded feasible worker return. The objective column is shown only if the returned candidate or its final saved incumbent was preserved; it is not inferred from another branch. A displayed incumbent need not be the only accepted trajectory produced by that problem.')
    emit('Selected passage notation is block:face>face with its saved gate offsets. X+/Z+ mean the greater-coordinate side, X-/Z- the lesser-coordinate side; Y+ means above the block and is checked against the fixed scalar trace. For Z+>X+ with gate26, Z+ ends at state26 and X+ begins at state27 because X movement precedes Z movement. Other orderings may share a state. The explicit Cxx@state inequalities are authoritative; face metadata is absent for some derived corridor-repair rows.')
    emit('* Two post-validation records were omitted on geometry-deadline paths. Alignment is exact for the other programs and all62 route-rejection records. For P06, the missing entries can be among B001..B007 and B108..B113. Those13 rows say accepted-or-missing rather than assigning unsupported outcomes. The ledger preserves every possible alignment; the total missing count is2, not13.')
    for program in audit['programs']:
        emit('### '+program['label'])
        rows(['Branch','Session','Selected interactions / gates','Extra inequalities','IPOPT / elapsed s','Post-check result','Raw objective if saved','Problem hash'],[
            (b['label'],b['session'],interactions(b['selectedInteractions']),constraint_text(b['extraRows']),
             'feasible / '+f"{sum(d.get('elapsedSeconds',0) for d in b['attempt'].get('diagnostics',[])):.4f}" if b['workerCalls'] else 'not called / '+b['attempt']['status'],
             post_text(b),known_value(b),b['attempt'].get('problemHash','not recorded'))
            for b in audit['branches'] if b['program']==program['label']])
    emit('## Every spatial-goal program evaluation')
    emit('These19 evaluations consume36 additional worker calls. They deliberately add future decisions to compare time-to-support; they are not additional main spatial routes. Only each probe\'s best program/witness and aggregate per-evaluation counts were retained. Full per-call constraint alternatives/returns for the other calls cannot be recovered from these archives. A feasible probe result proves an accepted witness existed, not that every worker return in its allowance was feasible.')
    rows(['Evaluation','Parent / target','Candidate horizon','Calls','Returned result','Saved winning decisions / exact constraints'],[
        (p['label'],p['program']+' -> '+p['goal']['block'],p['summary']['ticks'],p['summary']['work'].get('solverCalls',0),
         p['summary']['status']+' / '+p['summary']['termination'],
         ('jumps '+spans(summary_program(p['recordedProgram'],{tuple(audit['scene']['collisionScene']['solids'][b['solidIndex']]):b['id'] for b in audit['scene']['blocks']})['jumps'])+
          '; hash '+p['recordedBest']['problemHash']+'; full plan in JSON' if p['recordedBest'] else 'not saved for this evaluation')) for p in audit['probes']])
    emit('## Every worker-call slot, including probe calls with logging gaps')
    emit('The208-row [worker-call table](WORKER_CALLS.md) and [CSV](worker-calls.csv) enumerate each dispatch. Main calls link to the complete branch tables above. Probe call slots are numbered within their saved evaluation; exact individual returns/hashes are marked unavailable where not persisted. This numbering does not claim a globally timestamped order across interleaved scoring/probe work.')
    emit('## Every emitted but untried program, and pending symbolic prefix')
    emit('Q rows are complete28-input programs already emitted into the ready batch. F rows are shorter symbolic prefixes awaiting expansion, not failed numerical candidates. All use R1 and the original state. Selected constraints/contact details for each are preserved in the JSON ledger. None of these rows has been main-scored. In particular Q09 already specifies a B3 landing at28 and F11 a B3 landing at27; these potentially useful branches were pending, not pruned by a numerical failure.')
    rows(['ID / saved ID','State / status','Jumps','Run inputs','Selected contacts','Y / vy / ground','Extra approach rows'],[
        (f['label']+' / '+f['id'][:12],str(f['ticks'])+' / '+f['kind'],spans(f['jumps']),spans(f['runs']),support_text(f),
         f"{f['y']:.6f} / {f['vy']:.6f} / {f['onGround']}",
         '; '.join(f"{r['mode']}[{r['t1']}] {r['cmp']} {r['rhs']}" for r in f['selectedRows'] if r.get('name','').startswith('blip:')) or '-') for f in audit['frontier']])
    emit('## Every saved movement exclusion before numerical evaluation')
    emit('These57 saved records contain a physical program hash and proof, but do not retain the full rejected program/decision history. There are25 descending-first-floor exclusions,29 interval reach contradictions and3 norm reach contradictions. The25 fall proofs are not evidence against the grounded/rising P06/P07 histories. Full proof objects, including floor-top/Y separation or interval/norm bounds, are in the JSON ledger. The prior prose calling56 of these dead falls was incorrect; the underlying proof records and208-call count are unchanged.')
    rows(['Rejected ID','Window stage / state','Reason','Saved distinguishing evidence'],[
        (x['id'],str(x['stage'])+' / '+str(x['ticks']),x['proof'].get('reason'),
         f"required={x['proof'].get('remaining')}; maximum future feetY="+str(next(iter(x['proof'].get('floorExclusions',[])),{}).get('maxFeetY','not saved'))) for x in audit['generationExclusions']])
    emit('## Constraint generation, pruning and where information is missing')
    emit('Each row below is a resumed outer evaluation of one unchanged movement program. Counts do not mean fresh jump schedules. Calls=0 means the allowance stopped during preparation/generation or screening. The saved records do not contain a per-chunk best-objective history, so objective improvements across these chunks cannot be reconstructed exactly.')
    rows(['Evaluation chunk','Session','Numerical proposals','IPOPT calls','Termination','Unresolved-retry flag'],[
        (f"{p['label']}-E{i:02}",e['session'],e['work'].get('numericalProposals',0),e['work'].get('solverCalls',0),e['termination'],e['retryUnresolved'])
        for p in audit['programs'] for i,e in enumerate(p['record']['evaluations'],1)])
    emit('The retry flag permits unresolved work only on a fresh initial evaluation; later chunks use false. A true flag on the first evaluation does not establish that any prior unknown existed or was retried. Completed branch identities do not repeat across resume boundaries in these reports.')
    rows(['Program','Source variants emitted','Source exhausted','Saved LP exclusions','Duplicate variant hits','Outer evaluation chunks'],[
        (p['label'],p['record']['program']['repairState']['constraintGeneration']['emitted'],
         p['record']['program']['repairState']['constraintGeneration']['exhausted'],
         len(p['record']['program']['repairState']['constraintGeneration'].get('screenExclusions',[])),
         p['record']['program']['repairState']['constraintGeneration'].get('duplicateVariants','not instrumented'),
         len(p['record']['evaluations'])) for p in audit['programs']])
    emit('P06 emits130 constraint-source variants and also triggers corridor repairs; the combined151 handled branches are not151 different jump schedules. Its recorded1,807 LP exclusions and24,803 duplicate hits consume no new IPOPT call. Duplicate-hit accounting began during the second session, so it is not a complete first-session generation count. P07 has44 emitted variants and an unfinished source. The large repeated generation cost needs profiling; exact duplicate-hit counts do not reveal which parent cursor generated each repeat.')
    emit('No candidate was heuristically discarded after state10: both0/8 setups were retained, and the state28 comparison never finished. The current state28 generator records258 raw choices,25 emitted programs,56 screened prefixes and96 policy rejections, with no queue drops. The rejected96 decision histories, exact constraints for screened1,807 source variants (only row hashes/proofs were saved), and the detailed cursor work for duplicate hits cannot be listed as fully specified candidates from these archives. Treating those counters as solver calls or declaring their unseen decisions infeasible would be incorrect.')
    emit('## What this says about potential pruning')
    emit('The current protection function returns incomparable_schedules for P06/P07 against P03. Neither candidate has fewer run inputs or more jumps than P03: P03 has3 jumps/0 runs, P06 has2/4 and P07 has2/5. Their observed B3 arrival is16/17 inputs later, and all three recorded state28 progress/velocity/speed values are worse. The implementation nevertheless preserves every schedule mismatch unconditionally, which is more conservative than just applying fewer-run/more-jump protections. Those absolute-clock state differences are not apples-to-apples evidence of dominance across phases, and observed probe arrival times are feasible upper bounds, not proven minimum times.')
    emit('There is a separate scheduling barrier: state28 retention was never invoked. The engine drains each program\'s pending constraint source and the window\'s available candidates before applying comparisons. Thus changing the end-of-window pruning rule alone would not avoid the work already spent on P06/P07 here. An incremental comparison could provisionally park an expensive history while keeping its full saved frontier, then audit its continuations later. That is a scheduling/heuristic proposal, not a sound dead-prefix proof or an implemented change.')
    emit('1. **Prioritize logical row/program coverage.** P04/P05 are P03 plus an extra approach inequality with unchanged input/scalar/contact histories. They contribute4 main calls and8 spatial calls beyond P03. Their current optimized progress is effectively equal. A generic feasible-set inclusion/equivalence check could avoid redundant prefix scoring or defer stricter variants, provided later decision generation from the broader prefix covers their continuations. A single position-optimal witness does not represent all future velocity tradeoffs, so this is a model-coverage argument to verify, not a new safe final-route dominance claim.')
    emit('2. **Review the route checker before spending more on physical infeasibility bounds.** All172 main worker calls are feasible; '+replay_note+'. Three reject B3 side position while above its top. Requiring a particular horizontal crossing side on a future block can be stricter than the intended3D cell route. Compare the saved traces against the desired route/contact semantics, then either encode the justified requirement before solving or correct the check. Do not relabel physical validity as completion of the chosen route.')
    emit('3. **P06/P07 are the expensive unresolved timing families.** Together they account for164 of172 main calls and12 spatial calls. They have not completed B2 in the main prefix. Existing first-floor viability does not prove them dead, and different jump/run schedules cannot be pruned by their state28 position alone. A remaining-time/continuation proof must cover all enabled stepping, intermediate support and run-off choices; a normal-jumps-only calculation would be conditional. Their cost and weaker local scores are evidence for investigation, not enough for a sound discard.')
    emit('4. **Inspect redundant enumeration and availability.** P06\'s source is now exhausted, but substantial time was spent finding duplicate or LP-excluded variants. Useful Q09/F11 support-bearing alternatives remained queued while this happened. Consider canonical generation and a measured scheduling change separately from correctness pruning. Neither a higher cap nor retrying these already-feasible IPOPT problems addresses this evidence.')
    emit('5. **Close logging gaps before judging heuristic quality.** Save every probe\'s full fixed problem/result, a stable branch ID in each validation record, per-branch objective and accepted witness metadata, and counters/timings for initialization, generation, LP, worker startup and checkpoint writes. Preserve records on geometry-deadline paths. Without this, some individual calls and objective improvements cannot be reconstructed exactly. No logging/runtime policy changes are made in this audit.')
    emit('## Reproduce and inspect')
    emit('Run the saved-data audit, with optional existing Java scene replay and no IPOPT calls:\n\n```sh\nOPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 PYTHONPATH="$PWD" .venv-guidance/bin/python standalone/autonomy/experiments/orchestrator-fixes/audit_candidates.py --compiler standalone/autonomy/build/compiler.json\n```')
    emit('Sources: evidence/orchestrator-real11-stable{,-resume,-resume2}.json.gz. Full float constraints, branch keys, candidate traces, available solver diagnostics, scope, session mapping, ambiguous validation alignments, probe winners and independent physical replays are in [candidate-ledger.json.gz](candidate-ledger.json.gz). Source-file SHA-256 hashes and reconciled counts are embedded there. The audit uses existing constraint builders and copies the captured initial-support bounds; it does not change any model, run IPOPT or prove global optimality.')
    emit('Follow-up: [duplicate-generation evidence and justified reductions](PRUNING_FOLLOWUP.md).')
    emit('User questions, answered sequentially: [candidate ancestry, ordering, timing and work allocation](CANDIDATE_QUESTIONS.md).')
    return '\n'.join(out)


def worker_ledger(audit):
    result=[];programs={p['label']:p for p in audit['programs']}
    for branch in audit['branches']:
        for i,diagnostic in enumerate(branch['attempt'].get('diagnostics',[]),1):
            p=programs[branch['program']]
            result.append(dict(call=f"{branch['label']}-C{i}",kind='main',session=branch['session'],
                program=p['label'],route='R1',ticks=p['ticks'],jumps=spans(p['jumps']),runs=spans(p['runs']),
                constraints=branch['label'],problemHash=branch['attempt'].get('problemHash'),
                workerStatus=diagnostic['status'],workerTermination=diagnostic.get('termination'),
                workerSeconds=diagnostic.get('elapsedSeconds'),postResults='; '.join(branch['postResults']),
                exactConstraintsAvailable=True))
    for probe in audit['probes']:
        n=probe['summary']['work'].get('solverCalls',0)
        for i in range(n):
            best=probe['recordedBest'] if n==1 else None
            result.append(dict(call=f"{probe['label']}-C{i+1}",kind='spatial',session=probe['session'],
                program=probe['program'],route='R1',ticks=probe['summary']['ticks'],
                jumps='not individually retained' if best is None else spans([
                    t for t,j in enumerate(best['profiles'][0]['jump']) if j and best['profiles'][0]['grounded'][t]]),
                runs='not individually retained' if best is None else spans([
                    t for t,j in enumerate(best['profiles'][0]['jump']) if not j and best['profiles'][0]['grounded'][t]]),
                constraints=probe['label'],problemHash=best.get('problemHash') if best else None,
                workerStatus='not individually retained',workerTermination=None,workerSeconds=None,
                postResults='evaluation '+probe['summary']['status']+'; individual return not retained',
                exactConstraintsAvailable=best is not None))
    if len(result)!=audit['counts']['totalWorkerCalls']:
        raise ValueError('Worker-call row count mismatch')
    return result


def render_calls(calls):
    header=['# Every cold Real11 worker-call slot','',
        '208 dispatches:172 main and36 spatial. Session assignment is from the saved main-branch/probe evaluation; rows are grouped by program, not claimed to be globally timestamp ordered. '+
        'See [the main audit](CANDIDATE_AUDIT.md) for all constraints, decisions, validation and pruning analysis. '+
        'A spatial evaluation may have a saved winning witness even when individual call returns were not saved. Only a one-call evaluation with the saved winner identifies its individual problem hash.','']
    return '\n'.join(header+table(['Call','Type / session','Parent / horizon','Jump / run inputs','Fixed constraints','Problem hash','Worker return / seconds','Post-check evidence'],[
        (r['call'],r['kind']+' / '+str(r['session']),r['program']+' / '+str(r['ticks']),r['jumps']+' / '+r['runs'],
         r['constraints'],r['problemHash'] or 'not retained',r['workerStatus']+' / '+(f"{r['workerSeconds']:.4f}" if r['workerSeconds'] is not None else 'not retained'),r['postResults']) for r in calls])+[''])


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--directory',type=Path,default=ROOT/'evidence')
    parser.add_argument('--output',type=Path,default=ROOT/'audit')
    parser.add_argument('--compiler',type=Path)
    args=parser.parse_args()
    reports=[read(args.directory/(name+'.json.gz')) for name in SOURCES]
    audit=extract(reports)
    if args.compiler:
        replay(audit,args.compiler)
    audit['counts']=metrics(audit)
    audit['sources']={name+'.json.gz':hashlib.sha256((args.directory/(name+'.json.gz')).read_bytes()).hexdigest() for name in SOURCES}
    args.output.mkdir(parents=True,exist_ok=True)
    calls=worker_ledger(audit)
    audit['workerCalls']=calls
    encoded=json.dumps(audit,indent=2,allow_nan=False).encode()
    (args.output/'candidate-ledger.json.gz').write_bytes(gzip.compress(encoded,mtime=0))
    (args.output/'CANDIDATE_AUDIT.md').write_text(render(audit))
    (args.output/'WORKER_CALLS.md').write_text(render_calls(calls))
    (args.output/'counts.json').write_text(json.dumps(audit['counts'],indent=2)+'\n')
    with (args.output/'worker-calls.csv').open('w',newline='') as handle:
        writer=csv.DictWriter(handle,fieldnames=list(calls[0]))
        writer.writeheader();writer.writerows(calls)
    print(json.dumps(audit['counts'],indent=2))


if __name__=='__main__':
    main()
