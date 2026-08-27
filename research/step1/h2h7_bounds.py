import json, os, glob

DATA = os.path.join(os.path.dirname(__file__), '..', 'copt', 'data')
files = sorted(glob.glob(os.path.join(DATA,'h1h2-*.json')))

print("%-40s %4s %12s %12s %12s %10s %10s %10s %9s %8s"%(
  'capture','n','disk.pos','sphere.pos','warmObj','disk-sph','sph-warm','worstWViol','eig1/sum','rankR'))
for f in files:
    d=json.load(open(f))
    cap=d['capture']
    disk=d.get('disk') or {}; sph=d.get('sphere') or {}; sdp=d.get('sdp') or {}; fa=d.get('faithfulness') or {}
    n=sph.get('n') or disk.get('n')
    dp=disk.get('pos'); sp=sph.get('pos'); wo=fa.get('warmObj')
    wv=fa.get('worstWallViol')
    eig=sdp.get('eig1_over_sum'); rr=sdp.get('rankRatio_eig2_eig1')
    def g(a,b):
        try: return a-b
        except: return None
    dsp=g(dp,sp); spw=g(sp,wo)
    def fmt(x,w=12,p=6):
        return (('%'+str(w)+'.'+str(p)+'f')%x) if isinstance(x,(int,float)) else (' '*(w-2)+'na')
    print("%-40s %4s %s %s %s %s %s %s %s %s"%(
      cap[:40], str(n),
      fmt(dp),fmt(sp),fmt(wo),
      fmt(dsp,10,3) if dsp is None else ('%10.2e'%dsp),
      ('%10.2e'%spw) if isinstance(spw,float) else ' '*8+'na',
      ('%10.2e'%wv) if isinstance(wv,(int,float)) else ' '*8+'na',
      ('%9.4f'%eig) if isinstance(eig,(int,float)) else ' '*7+'na',
      ('%8.3f'%rr) if isinstance(rr,(int,float)) else ' '*6+'na'))
