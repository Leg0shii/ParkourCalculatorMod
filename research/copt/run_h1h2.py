import json
import sys

import coptlib as C


def run(capname):
    path = f'data/struct-{capname}.json'
    d = C.load(path)
    print(f'\n===== {capname}  n={d["numTicks"]} walls={len(d["walls"])} '
          f'obj={d["objSense"]} {d["objAxisName"]}@{d["objTick"]} free={d["startBox"]["startFree"] if d["startBox"] else False} '
          f'facingWall={d["hasFacingWall"]} =====')

    chk = C.reconstruct_from_warm(d)
    if chk:
        print(f'  faithfulness: recorded warmObj={chk["warmObj"]:.9g}  model posLinear={chk["posLinear"]:.9g}  '
              f'diff={abs(chk["warmObj"]-chk["posLinear"]):.3e}  worstWallViol(model)={chk["worstWallViol"]:.3e}  '
              f'recorded warmViol={chk["warmViol"]:.3e}')

    disk = C.solve_socp_disk(d)
    print(f'  SOCP-disk: status={disk["status"]} time={disk["time"]:.3f}s')
    if 'pos' in disk:
        print(f'    disk pos(bound) = {disk["pos"]:.9g}   maxModulusSlack={disk["maxSlack"]:.3e}  '
              f'sumSlack={disk["sumSlack"]:.3e}  throttledTicks(>1e-6)={disk["nThrottled"]}/{disk["n"]}')
        if disk['nThrottled']:
            tt = disk['throttledTicks']
            print(f'    throttled tick list={tt}')
            print(f'    slack at those = {[round(disk["slack"][t],5) for t in tt]}')

    sph = C.solve_qcqp_sphere(d, timelimit=120.0)
    line = f'  QCQP-sphere(nonconvex): status={sph["status"]} time={sph["time"]:.3f}s'
    if 'pos' in sph:
        line += f'  pos={sph["pos"]:.9g}'
    if 'pos_bound' in sph:
        line += f'  posBound={sph["pos_bound"]:.9g}'
    if 'gap' in sph and sph['gap'] is not None:
        line += f'  gap={sph["gap"]:.3e}'
    print(line)

    sdp = C.solve_shor_sdp(d, timelimit=120.0)
    line = f'  Shor-SDP: status={sdp["status"]} time={sdp["time"]:.3f}s dim={sdp["dim"]}'
    if sdp.get('pos') is not None:
        line += f'  posBound={sdp["pos"]:.9g}'
    print(line)
    if 'topEig' in sdp:
        print(f'    topEig={[round(e,6) for e in sdp["topEig"]]}  eig2/eig1={sdp.get("rankRatio_eig2_eig1")}')
        if 'recoveredSlack' in sdp:
            rs = sdp['recoveredSlack']
            nthrott = sum(1 for s in rs if abs(s) > 1e-4)
            print(f'    SDP recovered per-tick modulus slack: max|slack|={max(abs(s) for s in rs):.3e}  '
                  f'ticks with |slack|>1e-4 = {nthrott}/{len(rs)}')

    out = {'capture': capname, 'disk': {k: v for k, v in disk.items() if k not in ('modulus', 'slack', 'mMag')},
           'sphere': {k: v for k, v in sph.items() if k != 'modulus'},
           'sdp': {k: v for k, v in sdp.items() if k not in ('recoveredModulus',)},
           'faithfulness': chk}
    with open(f'data/h1h2-{capname}.json', 'w') as f:
        json.dump(out, f, indent=2, default=str)
    return disk, sph, sdp


if __name__ == '__main__':
    caps = sys.argv[1:] or ['j021-rinav1-01']
    for c in caps:
        run(c)
