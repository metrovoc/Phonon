import re,sys,statistics as st
def rd(p):
    o=[]
    for l in open(p,errors='replace'):
        # The time each check was taken: its wall= field, or else the log line's (NeoForge logs milliseconds).
        m=re.search(r'^\S*?\[(?:\d+\w+\d+ )?(\d+):(\d+):(\d+)(?:\.(\d+))?\].*\[check\] (\S+) playing=(\w+) paused=(\w+).*posMs=(\d+).*soundActive=(\w+)(?: wall=(\d+))?',l)
        if m:
            t=int(m[10]) if m[10] else (int(m[1])*3600+int(m[2])*60+int(m[3]))*1000+int(m[4] or 0)
            o.append(dict(n=m[5],t=t,playing=m[6]=='true',paused=m[7]=='true',pos=int(m[8]),snd=m[9]=='true',off=int(m[8])-t))
    return o
a,b=rd(sys.argv[1]),rd(sys.argv[2])
A0=[x for x in a if x['n'].startswith('s1_a') and x['playing']]; A1=[x for x in a if x['n'].startswith('s1_seek') and x['playing']]
if not A0: print('NO SYNC DATA client1'); sys.exit()
o0=st.median(x['off'] for x in A0); o1=st.median(x['off'] for x in A1) if A1 else None
Bp=[x for x in b if x['playing']]; B0=[x for x in Bp if abs(x['off']-o0)<5000]; B1=[x for x in Bp if o1 is not None and abs(x['off']-o1)<5000]
print(f'client2 samples playing={len(Bp)} before_seek={len(B0)} after_seek={len(B1)} soundActive={sum(x["snd"] for x in Bp)}/{len(Bp)}')
if B0: print(f'late-join offset ms (client2-client1): {round(st.median(x["off"] for x in B0)-o0)} spread {max(x["off"] for x in B0)-min(x["off"] for x in B0)}')
if B1: print(f'after-seek offset ms (client2-client1): {round(st.median(x["off"] for x in B1)-o1)}')
pa=[x for x in b if x['paused']]
print(f'client2 saw pause: {len(pa)} samples; saw playing/stopped states: {sorted(set(("play" if x["playing"] else "pause" if x["paused"] else "stop") for x in b))}')
