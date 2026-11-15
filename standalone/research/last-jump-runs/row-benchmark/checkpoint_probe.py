"""Measure storage on saved evidence and one fixed NLP, never route discovery."""
import argparse
import copy
import gzip
import hashlib
import json
from pathlib import Path
import runpy
import statistics
import subprocess
import sys
import tempfile
import time
import zlib

from standalone.autonomy.checkpoint_store import CheckpointStore, read_report
from standalone.autonomy.cell_routes.checkpoint_persistence import SearchCheckpoint
from standalone.ipopt.solver import solve


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args = parser.parse_args()
    original = read_report(args.input)
    state = copy.deepcopy(original['result']['searchState'])
    record = next(r for r in state['finalAttempts'] if r.get('witness'))
    state['stack'][-1]['inFlight'] = dict(id=record['id'],program=copy.deepcopy(record['program']))
    program = state['stack'][-1]['inFlight']['program']
    metadata = {k:v for k,v in original.items() if k not in ('windows','result','validation','searchState')}
    payload = dict(metadata,windows=state['windows'],searchState=state)
    began = time.perf_counter()
    raw = json.dumps(payload,indent=2,allow_nan=False).encode()
    old_bytes, old_seconds = len(raw), time.perf_counter()-began
    began = time.perf_counter(); compressed = gzip.compress(raw,mtime=0)
    old_seconds += time.perf_counter()-began
    old_compressed = len(compressed)
    del raw, compressed
    result = dict(source=original['source'],benchmarkBaseRevision=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
        fullRouteSearch=False,legacyPrettyBytes=old_bytes,legacyGzipBytes=old_compressed,
        legacyEncodeCompressSeconds=old_seconds,
        originalReportGzipBytes=args.input.stat().st_size,
        originalReportCompactBytes=len(json.dumps(original,separators=(',',':'),allow_nan=False).encode()),
        codeHashes={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in
            [Path('standalone/autonomy/checkpoint_store.py'),Path('standalone/autonomy/cell_routes/checkpoint_persistence.py')]})
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory)/'probe.json.gz'
        with CheckpointStore(path) as store:
            writer = SearchCheckpoint(store,metadata)
            began = time.perf_counter(); writer(state)
            result['initialCheckpointSeconds'] = time.perf_counter()-began
            result['initialStoreBytes'] = store.database.stat().st_size+path.stat().st_size
            times = []
            for i in range(5):
                program['repairState']['referenceWarmYaw'] = [float(i)]*program['profile']['airtime']
                state['cumulativeCalls'] += 1
                began = time.perf_counter(); writer.repair_progress(state)
                times.append(time.perf_counter()-began)
            result['repairSaveSeconds'] = times
            result['repairSaveMedianSeconds'] = statistics.median(times)
            result['repairStateRoundTrip'] = read_report(path)['searchState']==state
            helpers = runpy.run_path(str(Path(__file__).with_name('benchmark.py')))
            _, request, _ = helpers['find_request'](args.input,'f05d5e3a7884')
            for label in ('none','stored_repair'):
                costs = []
                def incumbent(candidate):
                    program['repairState']['referenceWarmYaw'] = candidate['yaw']
                    began = time.perf_counter(); writer.repair_progress(state)
                    costs.append(time.perf_counter()-began)
                numerical = solve(request,seconds=2.,stop_on_feasible=False,python_executable=sys.executable,
                    on_incumbent=incumbent if label=='stored_repair' else None)
                result.setdefault('fixedRequest',[]).append(dict(format=label,elapsedSeconds=numerical['elapsedSeconds'],
                    status=numerical['status'],termination=numerical['termination'],phases=numerical['phases'],
                    callbackSeconds=costs,objective=numerical['candidate']['objective'] if numerical['candidate'] else None))
            result['callbackStateRoundTrip'] = read_report(path)['searchState']==state
            began = time.perf_counter(); writer.finish(original['result'],original['validation'])
            result['finalSaveAndCompactSeconds'] = time.perf_counter()-began
            result['finalStoreBytes'] = store.database.stat().st_size+path.stat().st_size
            result['finalUniqueEncodedBytes'] = sum(len(zlib.decompress(row[0])) for row in store.connection.execute('SELECT data FROM objects'))
            result['finalReportRoundTrip'] = read_report(path)==original
    assert result['repairStateRoundTrip'] and result['callbackStateRoundTrip'] and result['finalReportRoundTrip']
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,indent=2,allow_nan=False)+'\n')
    print(json.dumps(result),flush=True)


if __name__=='__main__':
    main()
