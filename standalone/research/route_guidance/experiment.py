from tools.paths import DEFAULT_COMPILER
import argparse
import hashlib
import importlib.metadata
import itertools
import json
import math
import os
from pathlib import Path
import subprocess
import time

import numpy as np

from parkour_solver.bridge.compiler import JavaCompiler
from research.route_guidance.compact import audit_compact_witness, solve_compact
from research.route_guidance.conic import build_conic, solve_conic
from research.route_guidance.graph import build_family
from research.route_guidance.guidance import ARMS, guide_route
from research.route_guidance.physical import assignments, lift_witness, refine, rounded_assignment


def _resample(points, count=50):
    points = np.array(points)
    distance = np.r_[0., np.cumsum(np.linalg.norm(np.diff(points, axis=0), axis=1))]
    if distance[-1] < 1e-12:
        return np.repeat(points[:1], count, axis=0)
    positions = np.linspace(0, distance[-1], count)
    return np.column_stack([np.interp(positions, distance, points[:, a]) for a in (0, 1)])


def similarity(reference, candidate):
    states = np.array(reference['states'])
    positions, velocities = np.array(candidate['positions']), np.array(candidate['velocity'])
    return dict(positionRmse=float(np.sqrt(np.mean(np.sum((states[:, :2]-positions)**2, axis=1)))),
                velocityRmse=float(np.sqrt(np.mean(np.sum((states[:, 2:]-velocities)**2, axis=1)))),
                routeRmse=float(np.sqrt(np.mean(np.sum((_resample(states[:, :2])-_resample(positions))**2, axis=1)))))


def control_stats(family, reference):
    magnitudes, reductions, fractionality = [], [], []
    for t in range(family['ticks']):
        edges = [e for e in family['edges'] if e['tick'] == t]
        masses = [reference['masses'][e['index']] for e in edges]
        radius = sum(z*e['magnitude'] for z, e in zip(masses, edges))
        if reference['arm'] == 'convex_disk':
            vectors = [reference['controls'][e['index']] for e in edges]
            local_norm = sum(math.hypot(*v) for v in vectors)
            net_norm = float(np.linalg.norm(np.sum(vectors, axis=0)))
            reductions.append(max(0., radius-local_norm))
            magnitudes.append(max(0., local_norm-net_norm))
        else:
            reductions.append(max(0., radius-math.hypot(*reference['tickControls'][t])))
            magnitudes.append(0.)
        fractionality.append(1-max(masses))
    return dict(reducedAccelerationTicks=sum(v > 1e-5 for v in reductions),
                totalMagnitudeSlack=sum(reductions), fractionalCancellation=sum(magnitudes),
                fractionalTicks=sum(v > 1e-5 for v in fractionality),
                maxFlowFractionality=max(fractionality))


def _ranking(family, guide, real):
    evaluations = []
    for step in guide['trace']:
        decision, parent = step['decision'], step['parentFixed']
        choices = []
        for conditional in step['candidates']:
            fixed = conditional['fixed']
            compatible = [r for r in real if r['status'] == 'feasible'
                          and all(r['selection']['fixed'].get(k) == v for k, v in fixed.items())]
            best = max((r['value'] for r in compatible), default=None)
            choices.append(dict(choice=fixed[decision], relaxedValue=conditional['value'],
                                feasibleValue=best, status=conditional['status']))
        valid = [c for c in choices if c['feasibleValue'] is not None]
        solved = [c for c in choices if c['status'] == 'solved']
        chosen = solved[0] if solved else None
        best = max((c['feasibleValue'] for c in valid), default=None)
        pairs, correct = 0, 0
        for a, b in itertools.combinations(valid, 2):
            if a['relaxedValue'] is None or b['relaxedValue'] is None:
                continue
            real_gap, relaxed_gap = a['feasibleValue']-b['feasibleValue'], a['relaxedValue']-b['relaxedValue']
            if abs(real_gap) > 1e-5 and abs(relaxed_gap) > 1e-5:
                pairs += 1
                correct += int(real_gap*relaxed_gap > 0)
        evaluations.append(dict(decision=decision, parentFixed=parent, choices=choices,
            chosen=chosen['choice'] if chosen else None, comparablePairs=pairs, correctPairs=correct,
            chosenHasWitness=chosen is not None and chosen['feasibleValue'] is not None,
            objectiveRegret=best-chosen['feasibleValue'] if chosen and chosen['feasibleValue'] is not None else None))
    return evaluations


def run_case(request, compiler, *, seconds=3, refine_seconds=2, max_assignments=64):
    started = time.monotonic()
    family = build_family(request, compiler)
    selections = list(itertools.islice(assignments(family), max_assignments+1))
    if len(selections) > max_assignments:
        raise ValueError('Benchmark enumeration exceeds max_assignments; restrict the declared fixture')
    model = build_conic(family)
    conic = solve_conic(family, seconds=seconds, built=model)
    roots = {'convex_disk': conic}
    for arm in ARMS[:2]:
        roots[arm] = solve_compact(family, arm, seconds=seconds, seed=conic)
    real, lifts = [], []
    for selection in selections:
        conditional = solve_conic(family, selection['fixed'], seconds=seconds)
        result = refine(family, selection, compiler, seed=conditional if conditional['status'] == 'solved' else conic,
                        extra_seeds=[conic], seconds=refine_seconds)
        result.update(conditionalRelaxedValue=conditional['value'], conditionalUpperBound=conditional['upperBound'],
                      conditionalSolverStatus=conditional['solverStatus'])
        real.append(result)
        if result['status'] == 'feasible':
            audits = {arm: audit_compact_witness(family, arm, selection, result['candidate']) for arm in ARMS[:2]}
            audits['convex_disk'] = lift_witness(family, model, selection, result['candidate'])
            lifts.append(dict(selection=selection, audits=audits,
                              violation=max(a['violation'] for a in audits.values())))
    feasible = [r for r in real if r['status'] == 'feasible']
    best = max(feasible, key=lambda r: r['value']) if feasible else None
    arms = {}
    for arm in ARMS:
        root = roots[arm]
        if root['status'] != 'solved':
            arms[arm] = dict(root=root, status='unknown')
            continue
        rounded = rounded_assignment(family, root)
        root_refined = refine(family, rounded, compiler, seed=root, seconds=refine_seconds)
        guide = guide_route(family, arm, compiler, seconds=seconds, refine_seconds=refine_seconds, root=root, beam_width=1)
        beam = guide_route(family, arm, compiler, seconds=seconds, refine_seconds=refine_seconds, root=root, beam_width=2)
        entry = dict(root=root, controlStats=control_stats(family, root), rootRefinement=root_refined,
                     guided=guide, beam=beam, rankings=_ranking(family, guide, real))
        if best:
            entry.update(referenceSimilarity=similarity(root, best['candidate']), objectiveGap=root['value']-best['value'],
                         rootCoversObserved=root['value']+2e-7 >= best['value'])
            if guide.get('refinement', {}).get('status') == 'feasible':
                entry['guidedRegret'] = best['value']-guide['refinement']['value']
                entry['committedSimilarity'] = similarity(guide['finalReference'], guide['refinement']['candidate'])
            if beam.get('refinement', {}).get('status') == 'feasible':
                entry['beamRegret'] = best['value']-beam['refinement']['value']
        arms[arm] = entry
    return dict(name=family['name'], scope=family['scope'], ticks=family['ticks'],
                nodes=len(family['nodes']), edges=len(family['edges']),
                initialState=family['bounds'][0][0], assignments=len(selections), acceptedAssignments=len(feasible),
                exhaustiveDiscreteEnumeration=True, numericalUnknowns=len(real)-len(feasible),
                unknownsWithImprovementPotential=sum(r['status'] != 'feasible' and
                    (r['conditionalUpperBound'] is None or best is None or r['conditionalUpperBound'] > best['value']+1e-7)
                    for r in real),
                bestFeasible=best, real=real, lifts=lifts, arms=arms,
                elapsedSeconds=time.monotonic()-started,
                feasibleOptimality='best_accepted_in_exhaustive_declared_discrete_family_with_IPOPT_refinement')


def summary(case):
    result = dict(name=case['name'], ticks=case['ticks'], assignments=case['assignments'],
                  acceptedAssignments=case['acceptedAssignments'],
                  numericalUnknowns=case['numericalUnknowns'],
                  unknownsWithImprovementPotential=case['unknownsWithImprovementPotential'],
                  bestFeasible=case['bestFeasible']['value'] if case['bestFeasible'] else None,
                  worstLiftViolation=max((v['violation'] for v in case['lifts']), default=None), arms={})
    for arm, data in case['arms'].items():
        root = data['root']
        row = {k: root[k] for k in ('status', 'value', 'upperBound', 'variables', 'rows', 'buildSeconds', 'solveSeconds')}
        if root['status'] == 'solved':
            row.update(objectiveGap=data.get('objectiveGap'), similarity=data.get('referenceSimilarity'),
                       rootRefined=data['rootRefinement']['status'], guided=data['guided']['status'],
                       guidedRegret=data.get('guidedRegret'), controlStats=data['controlStats'],
                       beam=data['beam']['status'], beamRegret=data.get('beamRegret'),
                       correctPairs=sum(r['correctPairs'] for r in data['rankings']),
                       comparablePairs=sum(r['comparablePairs'] for r in data['rankings']),
                       guidedSeconds=data['guided']['elapsedSeconds'], rankings=data['rankings'])
        result['arms'][arm] = row
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', required=True, type=Path)
    parser.add_argument('--compiler', type=Path, default=DEFAULT_COMPILER)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--seconds', type=float, default=3)
    parser.add_argument('--refine-seconds', type=float, default=2)
    parser.add_argument('--repeats', type=int, default=1)
    args = parser.parse_args()
    if args.seconds <= 0 or args.refine_seconds <= 0 or args.repeats < 1:
        parser.error('Budgets and repeats must be positive')
    inputs = sorted(args.input.glob('*.json')) if args.input.is_dir() else [args.input]
    source = subprocess.run(['git', 'rev-parse', 'HEAD'], check=True, capture_output=True, text=True).stdout.strip()
    output = dict(source=source, environment=dict(python=os.sys.version,
        packages={name: importlib.metadata.version(name) for name in ('casadi', 'numpy', 'clarabel', 'scipy')}),
        compilerConfigSha256=hashlib.sha256(args.compiler.read_bytes()).hexdigest(),
        budgets=dict(seconds=args.seconds, refineSeconds=args.refine_seconds, repeats=args.repeats),
        cases=[], summaries=[])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with JavaCompiler(json.loads(args.compiler.read_text())) as compiler:
        for repeat in range(args.repeats):
            for path in inputs:
                case = run_case(json.loads(path.read_text()), compiler, seconds=args.seconds,
                                refine_seconds=args.refine_seconds)
                case['repeat'] = repeat
                output['cases'].append(case)
                brief = summary(case)
                brief['repeat'] = repeat
                output['summaries'].append(brief)
                args.output.write_text(json.dumps(output, indent=2, allow_nan=False)+'\n')
                print(json.dumps(brief, allow_nan=False), flush=True)


if __name__ == '__main__':
    main()
