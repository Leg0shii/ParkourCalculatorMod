"""Atomic reports and compact summaries; no dependency on experiment runners."""
import gzip
import json


def save(path, report):
    path.parent.mkdir(parents=True,exist_ok=True)
    compressed = path.suffix=='.gz'
    data = json.dumps(report,allow_nan=False,**(dict(separators=(',',':')) if compressed else dict(indent=2))).encode()
    temporary = path.with_suffix(path.suffix+'.tmp')
    temporary.write_bytes(gzip.compress(data,compresslevel=1,mtime=0) if compressed else data)
    temporary.replace(path)



def brief(result):
    best = result.get('best')
    return dict(status=result['status'], termination=result['termination'], elapsedSeconds=result['elapsedSeconds'],
        ticks=best['plan']['ticks'] if best else None, objective=best['candidate']['objective'] if best else None,
        calls=result['work'].get('solverCalls', 0), firstAcceptedSeconds=result['work']['firstAcceptedSeconds'],
        expandedPrefixes=result.get('expandedPrefixes'),
        scoreComparisons=sum(w['scored'] for w in result['windows']),
        checkpointUnknowns=sum(w['unknown'] for w in result['windows']),
        heuristicDrops=sum(w['heuristicDrops'] for w in result['windows']))
