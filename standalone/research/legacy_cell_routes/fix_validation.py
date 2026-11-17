from tools.paths import DEFAULT_COMPILER
import argparse
from pathlib import Path
import subprocess

from parkour_solver.bridge.compiler import JavaCompiler
from research.legacy_cell_routes.failure_diagnostic import read, run_generation, run_warm, verify_generation
from research.legacy_cell_routes.generation_diagnostic import save


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--part', choices=['warm', 'generation', 'verify'], required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--generation-input', type=Path)
    parser.add_argument('--compiler', type=Path, default=DEFAULT_COMPILER)
    args = parser.parse_args()
    report = dict(source=subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
                  implementation='carry_fallback_validated_incumbent_balanced_generation', runs=[])
    with JavaCompiler(read(args.compiler)) as compiler:
        if args.part == 'warm':
            report['runs'] = run_warm(compiler)
        elif args.part == 'generation':
            report['runs'] = run_generation(compiler, 'balanced', False)
        else:
            if args.generation_input is None:
                parser.error('--generation-input is required for verification')
            report['runs'] = verify_generation(args.generation_input, compiler)
    save(args.output, report)


if __name__ == '__main__':
    main()
