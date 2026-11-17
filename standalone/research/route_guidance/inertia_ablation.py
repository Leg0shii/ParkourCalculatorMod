from tools.paths import RESEARCH_ROOT, DEFAULT_COMPILER
import argparse
import json
from pathlib import Path

from parkour_solver.bridge.compiler import JavaCompiler
from research.route_guidance.conic import build_conic, solve_conic
from research.route_guidance.graph import build_family
from research.route_guidance.physical import lift_witness
from research.route_guidance.report import read_report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--fixtures', type=Path, default=RESEARCH_ROOT/'route-guidance/fixtures')
    parser.add_argument('--compiler', type=Path, default=DEFAULT_COMPILER)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report, rows = read_report(args.input), []
    with JavaCompiler(json.loads(args.compiler.read_text())) as compiler:
        for case in report['cases']:
            if case['repeat'] != 0:
                continue
            family = build_family(json.loads((args.fixtures/(case['name']+'.json')).read_text()), compiler)
            for real in case['real']:
                if real['status'] != 'feasible':
                    continue
                candidate, fixed = real['candidate'], real['selection']['fixed']
                pattern = [[0 if abs(v) < family['threshold'] else 1 if v > 0 else -1 for v in row]
                           for row in candidate['velocity'][:-1]]
                model = build_conic(family, fixed, carry_pattern=pattern)
                restricted = solve_conic(family, fixed, built=model)
                lift = lift_witness(family, model, real['selection'], candidate)
                rows.append(dict(case=case['name'], selection=real['selection'], feasibleValue=real['value'],
                    hullValue=real['conditionalRelaxedValue'], restrictedValue=restricted['value'],
                    restrictedUpperBound=restricted['upperBound'], restrictedStatus=restricted['solverStatus'],
                    liftViolation=lift['violation'], pattern=pattern,
                    scope='only_this_fixed_timing_passage_and_inertia_pattern',
                    routeWideBound=False))
    result = dict(description='Diagnostic only: fixing observed inertia branches narrows the declared family', rows=rows)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, allow_nan=False)+'\n')
    for row in rows:
        print(row['case'], row['selection']['allocation'], row['feasibleValue'], row['hullValue'], row['restrictedValue'])


if __name__ == '__main__':
    main()
