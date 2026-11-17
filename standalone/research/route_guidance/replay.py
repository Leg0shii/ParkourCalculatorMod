from tools.paths import RESEARCH_ROOT, DEFAULT_COMPILER
import argparse
import hashlib
import json
from pathlib import Path

from parkour_solver.bridge.compiler import JavaCompiler
from research.route_guidance.compact import audit_compact_witness
from research.route_guidance.conic import build_conic
from research.route_guidance.graph import build_family
from research.route_guidance.physical import accept_candidate, lift_witness, make_problem
from research.route_guidance.report import read_report


def replay(report, fixtures, compiler):
    seen, records = set(), []
    for case in report['cases']:
        family = build_family(json.loads((fixtures/(case['name']+'.json')).read_text()), compiler)
        model = build_conic(family)
        proposals = [r for r in case['real'] if r['status'] == 'feasible']
        for data in case['arms'].values():
            proposals.append(data.get('rootRefinement', {}))
            proposals.extend(data.get('guided', {}).get('refinementAttempts', []))
            proposals.extend(data.get('beam', {}).get('refinementAttempts', []))
        for proposal in proposals:
            if proposal.get('status') != 'feasible':
                continue
            selection, candidate = proposal['selection'], proposal['candidate']
            identity = hashlib.sha256(json.dumps([case['name'], selection, candidate['yaw']], sort_keys=True).encode()).hexdigest()
            if identity in seen:
                continue
            seen.add(identity)
            route, plan, vertical, problem = make_problem(family, selection, compiler)
            accepted = accept_candidate(family, selection, problem, route, plan, vertical, compiler, candidate['yaw'])
            audits = {arm: audit_compact_witness(family, arm, selection, candidate)
                      for arm in ('compact_circle', 'compact_disk')}
            audits['convex_disk'] = lift_witness(family, model, selection, candidate)
            deviation = max(abs(x-y) for actual, expected in zip(accepted.get('candidate', {}).get('positions', []),
                            candidate['positions']) for x, y in zip(actual, expected)) if accepted['status'] == 'feasible' else None
            violation = max(a['violation'] for a in audits.values())
            bound = case['arms']['convex_disk']['root']['upperBound']
            covered = bound is not None and bound+1e-7 >= proposal['value']
            conditional_checks, conditional_misses = 0, 0
            for data in case['arms'].values():
                candidates = [data['root']]
                for guide in (data.get('guided', {}), data.get('beam', {})):
                    candidates.extend(c for step in guide.get('trace', []) for c in step['candidates'])
                for conditional in candidates:
                    if conditional['status'] != 'solved' or not all(
                            selection['fixed'].get(k) == v for k, v in conditional['fixed'].items()):
                        continue
                    upper = conditional['upperBound'] if conditional['upperBound'] is not None else conditional['value']
                    conditional_checks += 1
                    conditional_misses += int(upper+1e-6 < proposal['value'])
            records.append(dict(id=identity, case=case['name'], status=accepted['status'],
                                positionDeviation=deviation, worstLiftViolation=violation, coveredByRootBound=covered,
                                conditionalChecks=conditional_checks, conditionalMisses=conditional_misses))
    passed = bool(records) and all(r['status'] == 'feasible' and r['positionDeviation'] == 0
                                 and r['worstLiftViolation'] <= 1e-7 and r['coveredByRootBound']
                                 and r['conditionalMisses'] == 0 for r in records)
    return dict(passed=passed, uniqueWitnesses=len(records),
                conditionalChecks=sum(r['conditionalChecks'] for r in records),
                conditionalMisses=sum(r['conditionalMisses'] for r in records), records=records)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--fixtures', type=Path, default=RESEARCH_ROOT/'route-guidance/fixtures')
    parser.add_argument('--compiler', type=Path, default=DEFAULT_COMPILER)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    with JavaCompiler(json.loads(args.compiler.read_text())) as compiler:
        result = replay(read_report(args.input), args.fixtures, compiler)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, allow_nan=False)+'\n')
    print(json.dumps({k: v for k, v in result.items() if k != 'records'}))
    raise SystemExit(0 if result['passed'] else 1)


if __name__ == '__main__':
    main()
