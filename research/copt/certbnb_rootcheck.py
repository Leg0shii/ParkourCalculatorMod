"""Root-bound validation for the M2a certified B&B (issue 422).

Compares the Java CertifiedBnb root bound (CertBnbProbe TSV, nodeCap=1) against the COPT
continuous oracle on the matching struct-*.json exports: the root bound must sit at or above
the sphere (|u|=m) global optimum minus 1e-6, because the root relaxation contains every
constant-modulus point (and more: table bulge, gate slack). Also reports the disk optimum
for tightness context and the sphere modulus residual max|u^2-mMag^2| (FeasTol trap).

Usage:
  1) from the repo root, build test classes, then:
     java -cp "$(cat core/build/test-classpath.txt)" org.junit.runner.JUnitCore \
         de.legoshi.parkourcalc.anglesolver.CertBnbProbe
     with PKC_CERTBNB_CAPTURES=<comma list> PKC_CERTBNB_OUT=research/copt/data/certbnb-roots.tsv
  2) python certbnb_rootcheck.py data/certbnb-roots.tsv
"""

import json
import math
import sys

import coptlib as C

STRUCT_BY_CAPTURE = {
    'j001': 'struct-j001.json',
    'j003': 'struct-j003.json',
    'j005': 'struct-j005.json',
    'j008b-2jump': 'struct-j008b-2jump.json',
    'j016-X2jmmp2p': 'struct-j016-X2jmmp2p.json',
    'j019-3jmmtruenix': 'struct-j019-3jmmtruenix.json',
    'j021-rinav1-01': 'struct-j021-rinav1-01.json',
    'j022-1bmhbfly': 'struct-j022-1bmhbfly.json',
    'loopmm-3jump-lands': 'struct-loopmm-3jump-lands.json',
    'thousand-1-dup2': 'struct-thousand-1dup2.json',
}


def main(tsv_path):
    rows = []
    with open(tsv_path) as f:
        header = f.readline().rstrip('\n').split('\t')
        idx = {h: i for i, h in enumerate(header)}
        for line in f:
            p = line.rstrip('\n').split('\t')
            if len(p) < len(header):
                continue
            rows.append(p)

    print(f"{'capture':28} {'n':>4} {'rootBound':>16} {'coptSphere':>16} {'coptDisk':>16} "
          f"{'root-sph':>12} {'sphResid':>10} verdict")
    failures = 0
    for p in rows:
        cap = p[idx['capture']]
        struct = STRUCT_BY_CAPTURE.get(cap)
        if struct is None:
            print(f'{cap:28} SKIP (no struct export)')
            continue
        d = C.load('data/' + struct)
        n_java = int(p[idx['n']])
        if d['numTicks'] != n_java:
            print(f'{cap:28} SKIP (n mismatch java={n_java} struct={d["numTicks"]})')
            continue
        root = float(p[idx['rootBound']])
        sense = p[idx['sense']]

        disk = C.solve_socp_disk(d)
        sph = C.solve_qcqp_sphere(d, timelimit=300.0)
        disk_pos = disk.get('pos')
        sph_pos = sph.get('pos')
        resid = None
        if 'modulus' in sph:
            mmags = [t['mMag'] for t in d['ticks']]
            resid = max(abs(m * m - mm * mm) for m, mm in zip(sph['modulus'], mmags))
        if sph_pos is None:
            print(f'{cap:28} SKIP (sphere no solution, status={sph["status"]})')
            continue
        if sense == 'MAX':
            ok = root >= sph_pos - 1.0e-6
            margin = root - sph_pos
        else:
            ok = root <= sph_pos + 1.0e-6
            margin = sph_pos - root
        if not ok:
            failures += 1
        print(f'{cap:28} {d["numTicks"]:>4} {root:>16.9f} {sph_pos:>16.9f} '
              f'{disk_pos if disk_pos is not None else float("nan"):>16.9f} '
              f'{margin:>12.3e} {resid if resid is not None else float("nan"):>10.2e} '
              f'{"OK" if ok else "FAIL"}')
    print(f'\n{failures} failures')
    return 1 if failures else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else 'data/certbnb-roots.tsv'))
