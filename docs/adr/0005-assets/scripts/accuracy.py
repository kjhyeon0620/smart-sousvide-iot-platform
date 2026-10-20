import sys
import numpy as np
import torch
from timestral import TimestralForecaster

torch.set_num_threads(1)
CTX, H = 1440, 15  # 1 day @ 1 min -> 15 min


def simulate(rng, n):
    """Sous-vide water bath: first-order thermal model + hysteresis heater control."""
    temp = np.empty(n, np.float32)
    target = np.empty(n, np.float32)
    t, heating = rng.uniform(20, 25), True
    amb = rng.uniform(18, 26)
    power = rng.uniform(0.8, 1.6)    # C/min when heating
    loss = rng.uniform(0.01, 0.03)   # 1/min toward ambient
    tgt = rng.choice([55.0, 57.0, 60.0, 63.0])
    change_at = rng.integers(n // 3, n) if rng.random() < 0.5 else n
    for i in range(n):
        if i == change_at:
            tgt = tgt + rng.choice([-3.0, 3.0])
        if t < tgt - 0.3:
            heating = True
        elif t > tgt + 0.3:
            heating = False
        t += (power if heating else 0.0) - loss * (t - amb) + rng.normal(0, 0.03)
        temp[i] = t + rng.normal(0, 0.05)  # sensor noise
        target[i] = tgt
    return temp, target


def main(repo):
    rng = np.random.default_rng(42)
    ctxs, covs, truths = [], [], []
    for _ in range(64):
        temp, target = simulate(rng, CTX + H)
        ctxs.append(temp[:CTX]); covs.append(target[None]); truths.append(temp[CTX:])
    ctx, cov, y = np.stack(ctxs), np.stack(covs), np.stack(truths)

    m = TimestralForecaster.from_pretrained(repo)
    levels = np.array(m.quantile_levels)
    lo, hi = int(np.argmin(abs(levels - 0.01))), int(np.argmin(abs(levels - 0.99)))
    med = m.median_index

    res = {}
    for sym in (True, False):
        q = np.concatenate([
            m.forecast(ctx[i:i + 8], H, known_covariates=cov[i:i + 8], symmetric_averaging=sym)
            .quantiles[:, 0].numpy() for i in range(0, len(ctx), 8)
        ])  # (B, H, Q)
        err = y[..., None] - q
        wql = np.mean(np.maximum(levels * err, (levels - 1) * err)) * 2 / np.mean(np.abs(y))
        mae = np.mean(np.abs(y - q[..., med]))
        cover = np.mean((y >= q[..., lo]) & (y <= q[..., hi]))
        width = np.mean(q[..., hi] - q[..., lo])
        res[sym] = q
        print(f"{repo}\tsym={sym}\tMAE={mae:.3f}C\tWQL={wql:.5f}\tcover98={cover * 100:.1f}%\twidth98={width:.2f}C",
              flush=True)
    diff = np.mean(np.abs(res[True][..., med] - res[False][..., med]))
    print(f"{repo}\tmean|median(sym)-median(nosym)|={diff:.3f}C", flush=True)


main(sys.argv[1])
