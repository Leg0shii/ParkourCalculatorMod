import argparse
import gzip
import importlib.util
import json
from pathlib import Path
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description='Measure current per-obstacle generation from archived physical models; never restore old cursors.')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--seconds', type=float, default=3)
    args = parser.parse_args()
    if args.seconds <= 0:
        raise ValueError('seconds must be positive')
    root = Path(__file__).resolve().parents[1]
    spec = importlib.util.spec_from_file_location('generation_audit',root/'orchestrator-fixes/audit_generation.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    with gzip.open(root/'orchestrator-fixes/audit/candidate-ledger.json.gz','rt') as stream:
        audit = json.load(stream)
    report = dict(source=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
        kind='current generator on archived physical contexts; new cursors, not exact historical resumption',
        secondsPerItem=args.seconds, items=[])
    for label in ('P06','P07'):
        p = next(p for p in audit['programs'] if p['label']==label)
        source = module.source_for(audit,p,restore=False)
        for item in source.items:
            if 'rows' in item:
                continue
            cursor, rows, count, exhausted = {}, set(), 0, False
            started = time.monotonic()
            source.deadline = started+args.seconds
            try:
                while True:
                    option = source.option(item,cursor)
                    if option is None:
                        exhausted = True
                        break
                    count += 1
                    rows.add(source.effective_key(option['rows']))
            except TimeoutError:
                pass
            report['items'].append(dict(program=label,solidIndex=item['solidIndex'],
                resourceDistinctOptions=count,distinctRowSets=len(rows),exhausted=exhausted,
                visitedNodes=len(cursor.get('seenNodes',[])),elapsedSeconds=time.monotonic()-started))
    args.output.write_text(json.dumps(report,indent=2,allow_nan=False)+'\n')
    print(json.dumps(report,indent=2))


if __name__=='__main__':
    main()
