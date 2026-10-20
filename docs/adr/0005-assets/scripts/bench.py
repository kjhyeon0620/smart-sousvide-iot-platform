import resource, sys, time
import numpy as np
import torch
from timestral import TimestralForecaster

repo, threads = sys.argv[1], int(sys.argv[2])
torch.set_num_threads(threads)
rng = np.random.default_rng(0)

t0 = time.perf_counter()
m = TimestralForecaster.from_pretrained(repo)
load_s = time.perf_counter() - t0


def series(b, t):
    # sous-vide-like: around 60C with slow drift and noise
    base = 60 + 0.5 * np.sin(np.linspace(0, 20, t))
    return (base + rng.normal(0, 0.1, (b, t))).astype(np.float32)


# name, batch, context, horizon, known targetTemp covariate, symmetric_averaging
scenarios = [
    ("official 3w@15m->1d", 1, 2016, 96, False, True),
    ("ours 1d@1m->15m", 1, 1440, 15, True, True),
    ("ours 1d@1m->15m nosym", 1, 1440, 15, True, False),
    ("ours batch32", 32, 1440, 15, True, True),
]

for name, b, t, h, cov, sym in scenarios:
    ctx = series(b, t)
    kw = {"known_covariates": np.full((b, 1, t + h), 60.0, np.float32)} if cov else {}
    runs = 5 if b > 1 else 20
    for _ in range(2):
        m.forecast(ctx, h, symmetric_averaging=sym, **kw)
    ts = []
    for _ in range(runs):
        s = time.perf_counter()
        m.forecast(ctx, h, symmetric_averaging=sym, **kw)
        ts.append((time.perf_counter() - s) * 1000)
    ts = np.array(ts)
    print(f"{repo}\tthreads={threads}\t{name}\tp50={np.median(ts):.0f}ms\tp95={np.percentile(ts, 95):.0f}ms"
          f"\tper_series={np.median(ts) / b:.1f}ms", flush=True)

rss_mb = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024
print(f"{repo}\tthreads={threads}\tload={load_s:.1f}s\tpeak_rss={rss_mb:.0f}MB", flush=True)
