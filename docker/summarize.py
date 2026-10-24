#!/usr/bin/env python3
import csv, statistics as st, sys
from collections import defaultdict

rows = list(csv.DictReader(open(sys.argv[1])))
g = defaultdict(list)
for r in rows:
    g[(r["target"], int(r["rate"]))].append(r)

def med(rs, k): return st.median(float(x[k]) for x in rs)

print(f"{'target':8} {'rate':>6} {'rps':>8} {'p50':>7} {'p99':>8} {'p99.9':>8} {'max':>8} {'p99 min..max':>16} {'err':>5} ok")
for (t, rate) in sorted(g, key=lambda k: (k[1], k[0])):
    rs = g[(t, rate)]
    errs = sum(int(x["errors"]) for x in rs)
    rps = med(rs, "rps")
    v = [float(x["p99_ms"]) for x in rs]
    ok = "yes" if errs == 0 and rps >= 0.99 * rate else "NO"
    print(f"{t:8} {rate:>6} {rps:>8.0f} {med(rs,'p50_ms'):>7.2f} {med(rs,'p99_ms'):>8.2f} "
          f"{med(rs,'p999_ms'):>8.2f} {med(rs,'max_ms'):>8.1f} {min(v):>7.2f}..{max(v):<7.2f} {errs:>5} {ok}")

rates = sorted({k[1] for k in g if ("gateway", k[1]) in g and ("direct", k[1]) in g})
if rates:
    print("\nGateway overhead (median gateway minus median direct, ms)")
    print(f"{'rate':>6} {'d p50':>8} {'d p99':>8} {'d p99.9':>8}")
    for rate in rates:
        a, b = g[("gateway", rate)], g[("direct", rate)]
        print(f"{rate:>6} {med(a,'p50_ms')-med(b,'p50_ms'):>8.2f} "
              f"{med(a,'p99_ms')-med(b,'p99_ms'):>8.2f} {med(a,'p999_ms')-med(b,'p999_ms'):>8.2f}")
else:
    print("\nNo DIRECT_URL baseline, overhead not computed.")
