import re, sys

AIR = 12
TAIL = 11
K = r'(?:w\+a|w\+d|s\+a|s\+d|w|a|s|d)'
NUM = r'(\d+)t'


def norm(text):
    s = text.lower()
    s = re.sub(r'\(x\d+[^)]*\)', ' ', s)
    s = re.sub(r'\([^)]*\)', ' ', s)
    s = s.replace('c4.5', 'c45')
    s = re.sub(r'\d*\.\d+', ' ', s)
    s = re.sub(r'\bw\s*\+\s*sprint\b', 'w', s)
    s = re.sub(r'\b([ws])\s*\+\s*([ad])\b', r'\1+\2', s)
    s = re.sub(r'\b([ad])\s*\+\s*([ws])\b', r'\2+\1', s)
    s = re.sub(r'\b([ws])([ad])\b', r'\1+\2', s)
    s = re.sub(r'\ba (single )?tick\b', '1t', s)
    s = re.sub(r'\b(\d+) ?ticks?\b', r'\1t', s)
    s = re.sub(r'\bthe next tick\b|\bnext tick\b|\bimmediately\b', '1t later', s)
    s = re.sub(r'\b(\d+)t after\b(?! jump| the jump| jumping| landing| you)', r'\1t later', s)
    s = re.sub(r'\bafter (\d+)t\b', r'\1t later', s)
    s = re.sub(r'\b(\d+)t (?:after jumping|after the jump|after jump)\b', r'\1t afterjump', s)
    s = re.sub(r'\b(\d+)t before (?:landing|you land|land)\b', r'\1t beforeland', s)
    s = re.sub(r'\b(?:upon landing|when you land|after landing|on landing|after you land|once you land)\b', 'onland', s)
    s = re.sub(r'\blet go of\b|\bletting go of\b', 'release', s)
    s = re.sub(r'\bhold down\b|\bholding\b|\bpress\b|\bstart holding\b', 'hold', s)
    s = re.sub(r'\bjump with\b', 'jam', s)
    s = re.sub(r'\bwalkjam\b', 'jam', s)
    s = re.sub(r'\bswitch(?:ing)? to\b', 'to', s)
    s = re.sub(r'\bjust\b|\bonly\b', 'to', s)
    s = re.sub(r'\bwith\b|\bthem\b|\bthe\b|\byour\b|\bkeys?\b|\bsprint(?:ed)?\b', ' ', s)
    s = re.sub(r'\bbackwards?\b|\bforwards?\b|\bback\b', ' ', s)
    s = re.sub(r'\s+', ' ', s)
    return s.strip()


def clauses(s):
    parts = re.split(r'[,.;!/]| then | and | before (?=jam)', s)
    return [p.strip() for p in parts if p.strip()]


def chord(tok):
    return frozenset(tok.replace('+', ''))


IGNORE = re.compile(r'^(?:turn|flick|smooth|chinese|microturn|poss|possible|ja\b|jump angle|no ja|no jump angle|l45|last 45|aim|sens|good luck|gl$|symmetry|same|this|you can|allows|very|sneak|shift|grind|tap space|hold space|can |note|required|recommended|lenient|sharp|no turn out|preturn|nondelayed|delayed$|run on|stay|sidestep$|x$|z$|or$|fa\b|\d+$|\d+ sharp)')
AIRPAT = re.compile(r'^(?:(\d+)t )?(wad|wdwa|noturn|(?:single |double |triple |quad |penta )?45(?: strafe)?|(?:1|2|3)t strafe|strafe)(?: sidestep)?(?: no ja| no jump angle| ja required| jump angle required)?$')


class Fail(Exception):
    pass


class R:
    def __init__(self):
        self.keys = []
        self.cur = frozenset()
        self.jumps = []
        self.air = False
        self.land = 0
        self.ref = 0
        self.pendJump = False
        self.anchor = None
        self.side = None

    def t(self):
        return len(self.keys)

    def advance(self, to):
        if to - self.t() > 40:
            raise Fail('long')
        while self.t() < to:
            self.keys.append(self.cur)

    def note(self, k):
        for c in 'ad':
            if c in k:
                self.side = c

    def setkeys(self, at, new):
        at = max(at, self.t())
        self.advance(at)
        self.cur = frozenset(new)
        self.note(self.cur)
        self.ref = at

    def ground(self):
        if self.air:
            self.advance(max(self.t(), self.land))
            self.air = False

    def jump(self, k=None):
        self.pendJump = False
        jt = max(self.t(), self.land) if self.air else self.t()
        self.advance(jt)
        if k is not None:
            self.cur = frozenset(k)
            self.note(self.cur)
        elif not self.cur and not self.jumps:
            self.cur = frozenset('w')
        self.jumps.append(jt)
        self.keys.append(self.cur)
        self.air = True
        self.land = jt + AIR
        self.ref = jt
        return jt

    def when(self, n=None, kind=None):
        if self.anchor is not None:
            a = self.anchor
            self.anchor = None
            return a
        return self.t()

    def lastjump(self, k=None):
        if self.air and self.t() <= self.land:
            self.advance(self.land + 1)
            self.air = False
        return self.jump(k)

    def finish(self):
        if self.pendJump:
            self.jump()
        if not self.jumps:
            if not self.keys:
                raise Fail('nojump')
            self.jump()
        self.advance(max(self.t(), self.jumps[-1] + TAIL))
        return self.keys[:self.jumps[-1] + TAIL], list(self.jumps)


def timeprefix(r, c):
    m = re.match(r'^' + NUM + r' later\b ?(.*)$', c)
    if m:
        r.anchor = r.ref + int(m.group(1))
        return m.group(2)
    m = re.match(r'^' + NUM + r' afterjump\b ?(.*)$', c)
    if m:
        r.anchor = r.jumps[-1] + int(m.group(1)) if r.jumps else r.ref + int(m.group(1))
        return m.group(2)
    m = re.match(r'^' + NUM + r' beforeland\b ?(.*)$', c)
    if m:
        r.anchor = r.land - int(m.group(1))
        return m.group(2)
    m = re.match(r'^onland\b ?(.*)$', c)
    if m:
        r.anchor = r.land
        return m.group(1)
    for pat, f in ((r'^(.*) ' + NUM + r' later$', lambda n: r.ref + n), (r'^(.*) ' + NUM + r' afterjump$', lambda n: (r.jumps[-1] if r.jumps else r.ref) + n),
                   (r'^(.*) ' + NUM + r' beforeland$', lambda n: r.land - n)):
        m = re.match(pat, c)
        if m:
            r.anchor = f(int(m.group(2)))
            return m.group(1)
    m = re.match(r'^(.*) onland$', c)
    if m:
        r.anchor = r.land
        return m.group(1)
    return c


def action(r, c):
    if r.pendJump and not re.match(r'^(?:jam|double jam|triple jam)$', c) and not re.match(r'^(?:walk|run|wait) ', c):
        r.jump()
    m = re.match(r'^(?:(?:td)?hh )?(?:(double|triple|quad) )?(?:jam|jump)(?: again)?(?: (' + K + r'|nothing))?(?: again| twice)?(?: for ' + NUM + r')?$', c)
    if m:
        reps = {'double': 2, 'triple': 3, 'quad': 4}.get(m.group(1), 1)
        if 'twice' in c:
            reps = 2
        for i in range(reps):
            jt = r.jump((() if m.group(2) == 'nothing' else chord(m.group(2))) if m.group(2) and i == 0 else None)
        if m.group(3):
            r.setkeys(jt + int(m.group(3)), ())
        return
    m = re.match(r'^(?:max pessi|pessi max)(?: (' + K + r'))?(?: for ' + NUM + r')?$', c)
    if m:
        k = chord(m.group(1)) if m.group(1) else frozenset('w')
        jt = r.jump(())
        r.setkeys(jt + 1, k)
        if m.group(2):
            r.setkeys(jt + 1 + int(m.group(2)), ())
        return
    m = re.match(r'^(?:' + NUM + r' pessi|pessi ' + NUM + r')(?: (' + K + r'))?(?: for ' + NUM + r')?$', c)
    if m:
        n = int(m.group(1) or m.group(2))
        k = chord(m.group(3)) if m.group(3) else frozenset('w')
        jt = r.jump(())
        r.setkeys(jt + n, k)
        if m.group(4):
            r.setkeys(jt + n + int(m.group(4)), ())
        return
    m = re.match(r'^(?:' + NUM + r' mark|mark ' + NUM + r'|mark)(?: (' + K + r'))?$', c)
    if m and m.group(3):
        n = int(m.group(1) or m.group(2) or 1)
        k = chord(m.group(3))
        jt = r.jump(k - frozenset('w') if len(k) > 1 else ())
        r.setkeys(jt + n, k)
        return
    m = re.match(r'^(?:max |' + NUM + r' )?(?:c45|fmm|pane fmm|burst|rex)(?: ' + NUM + r')?(?: (' + K + r'))?$', c)
    if m:
        r.jump(chord(m.group(3)) if m.group(3) else None)
        return
    m = re.match(r'^(hh|a7|' + NUM + r' a7)(?: (' + K + r'))?$', c)
    if m:
        k = chord(m.group(3)) if m.group(3) else frozenset('w')
        if m.group(1) != 'hh':
            if not r.air or r.t() >= r.land:
                r.jump(())
            r.setkeys(r.land - 1, k)
            return
        r.ground()
        r.setkeys(r.t(), k)
        r.advance(r.t() + 1)
        r.pendJump = True
        return
    m = re.match(r'^(walk|run|wait)(?: ' + NUM + r')?(?: on (?:ground|head|pane|edge|last block|block|land|last momentum))?(?: (' + K + r'))?(?: on (?:ground|head|pane|edge|last block|block))?$', c)
    if m:
        n = int(m.group(2) or 1)
        if r.anchor is not None:
            r.setkeys(r.when(), r.cur)
        r.ground()
        if m.group(1) == 'wait':
            r.setkeys(r.t(), ())
        elif m.group(3):
            r.setkeys(r.t(), chord(m.group(3)))
        elif not r.cur and not r.jumps:
            r.setkeys(r.t(), 'w')
        r.advance(r.t() + n)
        return
    m = re.match(r'^(?:nothing for ' + NUM + r'|' + NUM + r' (?:wait|nothing)|nothing ' + NUM + r')$', c)
    if m:
        n = int(m.group(1) or m.group(2) or m.group(3))
        r.ground()
        r.setkeys(r.t(), ())
        r.advance(r.t() + n)
        return
    m = re.match(r'^(?:' + NUM + r' (?:walk |run |hold |of )?(' + K + r')|(?:hold |walk |run )?(' + K + r') for ' + NUM + r'|(?:hold|walk|run) ' + NUM + r' (' + K + r'))(?: unsprinted| without)?$', c)
    if m:
        n = int(m.group(1) or m.group(4) or m.group(5))
        k = chord(m.group(2) or m.group(3) or m.group(6))
        at = r.when()
        r.setkeys(at, k)
        r.advance(r.t() + n)
        return
    m = re.match(r'^hold for ' + NUM + r'$', c)
    if m:
        r.advance(max(r.t(), r.ref) + int(m.group(1)))
        return
    m = re.match(r'^(?:you )?(?:start )?straf(?:e|ing)(?: in)?(?: (' + K + r'))?(?: on jump)?$', c)
    if m and r.anchor is not None:
        k = chord(m.group(1)) if m.group(1) else frozenset(r.side or 'a')
        r.setkeys(r.when(), r.cur | k)
        return
    m = re.match(r'^tap (' + K + r')(?: for ' + NUM + r')?$', c)
    if m:
        at = r.when()
        base = r.cur
        r.setkeys(at, base | chord(m.group(1)))
        r.setkeys(at + int(m.group(2) or 1), base)
        return
    m = re.match(r'^release(?: (' + K + r'|strafe))?$', c)
    if m:
        at = r.when()
        drop = frozenset('ad') if m.group(1) in (None, 'strafe') else chord(m.group(1))
        r.setkeys(at, r.cur - drop)
        return
    m = re.match(r'^add (' + K + r')$', c)
    if m:
        r.setkeys(r.when(), r.cur | chord(m.group(1)))
        return
    m = re.match(r'^(?:to |hold )?(' + K + r')$', c)
    if m:
        if r.anchor is None and not r.jumps:
            raise Fail('barekeys')
        r.setkeys(r.when(), chord(m.group(1)))
        return
    m = re.match(r'^wobbl(?:e|ing) w(?: for ' + NUM + r')?$', c)
    if m and r.jumps:
        n = int(m.group(1) or 1)
        at = r.anchor if r.anchor is not None else r.jumps[-1] + 1
        r.anchor = None
        base = r.cur
        r.setkeys(at, base - frozenset('w'))
        r.setkeys(at + n, base | frozenset('w'))
        return
    m = AIRPAT.match(c)
    if m:
        pat = m.group(2)
        side = r.side or 'a'
        other = 'd' if side == 'a' else 'a'
        if pat == 'noturn':
            r.lastjump(None)
        elif pat == 'wdwa':
            r.lastjump('w' + other)
        else:
            delay = int(m.group(1)) if m.group(1) and pat == 'wad' else 1
            sm = re.match(r'^(\d)t strafe', pat)
            if sm:
                delay = int(sm.group(1))
            jt = r.lastjump('w')
            r.setkeys(jt + delay, 'w' + side)
        return
    if IGNORE.match(c) or not re.search(r'\b(?:' + K + r'|jam|pessi|mark|hh|a7|c45|fmm|bwmm|rex|burst|run|walk|wait|release|hold|tap|wobble|strafe|\d+t)\b', c):
        r.anchor = None
        return
    raise Fail(c)


def render(text):
    r = R()
    for c in clauses(norm(text)):
        rest = timeprefix(r, c)
        rest = rest.strip()
        if not rest:
            continue
        action(r, rest)
    return r.finish()


LABEL = {frozenset(): '-', frozenset('w'): 'W', frozenset('wa'): 'WA', frozenset('wd'): 'WD', frozenset('s'): 'S', frozenset('sa'): 'SA',
         frozenset('sd'): 'SD', frozenset('a'): 'A', frozenset('d'): 'D'}


def labels(keys):
    out = []
    for k in keys:
        if k not in LABEL:
            raise Fail('chord ' + ''.join(sorted(k)))
        out.append(LABEL[k])
    return out


def describe(lab):
    out = []
    i = 0
    while i < len(lab):
        j = i
        while j + 1 < len(lab) and lab[j + 1] == lab[i]:
            j += 1
        out.append(lab[i] + ('x%d' % (j - i + 1) if j > i else ''))
        i = j + 1
    return ' '.join(out)



MIRK = {'-': '-', 'W': 'W', 'WA': 'WD', 'WD': 'WA', 'S': 'S', 'SA': 'SD', 'SD': 'SA', 'A': 'D', 'D': 'A'}
JUMPS = [108, 111, 119, 120, 121, 122, 126, 127, 128, 129, 132, 133, 134, 135, 138, 140, 143, 144, 146, 147, 148, 149, 150, 152, 153, 154, 155, 187, 246, 248,
         298, 315, 318, 319, 320, 321, 328, 330, 335, 342, 343, 344, 345, 346, 347, 422, 424, 425, 703, 716, 717, 718, 757, 816, 828, 925, 1099, 1149, 1150]

SELFTEST = [
    ('4t D, wait 1t, Jam A, 3t later S+A, wait 1t on ground, Max c4.5 W+D, WAD.', 'Dx4 - Ax3 SAx9 - WDx13 W WDx10', [5, 18, 31]),
    ('1t A, HH S+D, Jam, walk 1t S, Jam W+A, Wobble W, run 1t, WAD.', 'A SDx13 S WA A WAx11 W WAx10', [2, 15, 28]),
    ('6t Pessi D, 4t later S+D, Jam, just S 1t later, walk 1t, Jam W+A, wobble W, run 1t, WAD, ja 0.115 in, l45 3.1 out', '-x6 Dx4 SDx3 Sx12 WA A WAx11 W WAx10', [0, 12, 25, 38]),
    ('Walkjam with W+D, let go of W 1t later. Walk 1t with D, then max pessi with A. 1t before landing, switch to S and run 2 ticks, jam with S+A and switch to W+A 1t later and run a tick. WAD', 'WD Dx12 - Ax10 Sx3 SA WAx12 W WAx10', [0, 13, 27, 40]),
    ('a7 W+D, run 1t W, 2t W+A, WAD Sidestep. (x2)', '-x11 WD W WAx2 W WAx10', [0, 15]),
    ('Max pessi S, next tick hold S+D, walk 1t S+D on the ground then 1t mark W+A, WAD Sidestep.', '- S SDx11 A WAx12 W WAx10', [0, 13, 26]),
    ('2t c4.5 W, 4t before landing hold down W+A, WDWA Sidestep.', 'Wx8 WAx5 WDx11', [0, 13]),
    ('Max Pessi S+D, nothing for 3t, Max c4.5 W+A, run 1t with W, WAD Sidestep', '- SDx11 -x3 WAx12 Wx2 WAx10', [0, 15, 28]),
    ('Jam S, a7 S+D, walk 1t, Jam W+D, next tick W+A and turn 5px left, run 1t, WAD.', 'Sx11 SDx2 WD WAx12 W WAx10', [0, 13, 26]),
    ('10t Pessi W, 1t later W+A, run 3t, WAD. JA -7.906 FA 47.12. 173% sens or /mm ss 0.997826', '-x10 W WAx4 W WAx10', [0, 15]),
    ('Jam, triple 45. Possible by 0.004769', 'Wx14 WAx10', [0, 13]),
    ('Run 5t.', 'Wx16', [5]),
    ('Max pessi with W, run 1t then hold W+A for 2t. WDWA Sidestep.', '- Wx12 WAx2 WDx11', [0, 15]),
]


def selftest():
    for text, want, wj in SELFTEST:
        k, j = render(text)
        got = describe(labels(k))
        if got != want or j != wj:
            raise SystemExit('SELFTEST FAILED on: ' + text + ' got ' + got + ' ' + str(j))
    print('selftest ok', len(SELFTEST))


def main():
    selftest()
    import os, math, collections
    if len(sys.argv) < 2:
        raise SystemExit('usage: translate_strats.py <corpus folder>')
    base = sys.argv[1]
    loc = {}
    for j in JUMPS:
        p = os.path.join(base, 'hpk', 'onejump', '%d.yml' % j)
        if not os.path.exists(p):
            continue
        txt = open(p, encoding='utf-8', errors='replace').read()
        m = re.search(r'location:.*?x:\s*(-?[\d.]+).*?y:\s*(-?[\d.]+).*?z:\s*(-?[\d.]+)', txt, re.S)
        if m:
            loc[j] = (math.floor(float(m.group(1))), math.floor(float(m.group(2))), math.floor(float(m.group(3))))
    agg = collections.Counter()
    fails = collections.Counter()
    total = ok = 0
    rows = open(os.path.join(base, '_export', 'all_strats.tsv'), encoding='utf-8', errors='replace').read().split('\n')
    for row in rows:
        parts = row.split('\t')
        if len(parts) < 5:
            continue
        try:
            x, y, z = int(float(parts[1])), int(float(parts[2])), int(float(parts[3]))
        except ValueError:
            continue
        total += 1
        text = parts[4]
        try:
            keys, jumps = render(text)
            lab = labels(keys)
        except Fail as e:
            fails[str(e)[:60]] += 1
            continue
        except Exception as e:
            fails['EXC ' + type(e).__name__] += 1
            continue
        ok += 1
        excl = sorted(j for j, (jx, jy, jz) in loc.items() if parts[0].startswith('hpk') and jy == y and abs(jx - x) <= 8 and abs(jz - z) <= 8)
        a = describe(lab)
        b = describe([MIRK[k] for k in lab])
        agg[(min(a, b), ','.join(str(j) for j in jumps), ','.join(str(j) for j in excl))] += 1
    out = os.path.join(base, '_export', 'rendered.tsv')
    with open(out, 'w', encoding='utf-8') as f:
        for (k, js, ex), c in sorted(agg.items(), key=lambda kv: -kv[1]):
            f.write('%d;%s;%s;%s\n' % (c, ex, js, k))
    with open(os.path.join(base, '_export', 'render_fails.tsv'), 'w', encoding='utf-8') as f:
        for k, c in fails.most_common():
            f.write('%d\t%s\n' % (c, k))
    print('texts %d rendered %d distinct %d jumpsLocated %d' % (total, ok, len(agg), len(loc)))
    print('top fails: ' + ' | '.join('%d %s' % (c, k) for k, c in fails.most_common(70)))


if __name__ == '__main__':
    main()
