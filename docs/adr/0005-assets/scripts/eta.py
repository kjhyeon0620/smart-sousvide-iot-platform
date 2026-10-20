"""Preheat ETA: Timestral vs linear / exponential-fit baselines on simulated sous-vide heating."""
import sys
import numpy as np
import torch
from timestral import TimestralForecaster

torch.set_num_threads(2)
N_SERIES, MAX_MIN, HORIZON = 100, 300, 256
QUERY_AT = [5, 10, 20, 30]           # minutes since heating started
BAND = 0.3                           # HOLDING threshold below target


def simulate(rng):
    """1-min samples from power-on until MAX_MIN. Returns readings, target, true ETA minute, disturbed flag."""
    while True:
        amb, power, loss = rng.uniform(18, 26), rng.uniform(0.3, 1.0), rng.uniform(0.005, 0.015)
        tgt = rng.choice([55.0, 57.0, 60.0, 63.0])
        if amb + power / loss >= tgt + 13:
            break
    t = rng.uniform(12, 25)                                   # tap water
    food_at = rng.integers(10, 60) if rng.random() < 0.2 else -1
    temps, eta = [], None
    for i in range(MAX_MIN):
        if i == food_at:
            t -= rng.uniform(2, 4)                             # cold food dropped in
        p = power * min(1.0, (i + 1) / 3)                      # heater ramp-up
        heating = t < tgt + BAND
        t += (p if heating else 0.0) - loss * (t - amb) + rng.normal(0, 0.03)
        temps.append(round(t + rng.normal(0, 0.05), 1))        # payload rounds to 0.1
        if eta is None and temps[-1] >= tgt - BAND:
            eta = i
    return np.array(temps, np.float32), tgt, eta, food_at >= 0


def eta_linear(ctx, goal):
    w = min(5, len(ctx) - 1)
    rate = (ctx[-1] - ctx[-1 - w]) / w
    return (goal - ctx[-1]) / rate if rate > 0 else np.nan


def eta_expfit(ctx, goal):
    # dT/dt = a - b*T  ->  T approaches Teq = a/b with time constant 1/b
    x, dy = ctx[:-1], np.diff(ctx)
    b, a = np.polyfit(x, dy, 1)
    b = -b
    if b <= 1e-4:
        return eta_linear(ctx, goal)
    teq = a / b
    if teq <= goal:
        return eta_linear(ctx, goal)
    return -np.log((teq - goal) / (teq - ctx[-1])) / b


def eta_expfit_recent(ctx, goal):
    # skip heater ramp-up, fit only the last 15 min so an earlier food drop falls out of the window
    return eta_expfit(ctx[3:][-15:], goal) if len(ctx) >= 8 else eta_expfit(ctx, goal)


def first_cross(path, goal):
    idx = np.nonzero(path >= goal)[0]
    return idx[0] + 1 if len(idx) else np.nan


def main(repo):
    rng = np.random.default_rng(11)
    sims = [s for s in (simulate(rng) for _ in range(N_SERIES)) if s[2] is not None]
    m = TimestralForecaster.from_pretrained(repo)
    levels = np.array(m.quantile_levels)
    q10, q90, med = int(np.argmin(abs(levels - 0.1))), int(np.argmin(abs(levels - 0.9))), m.median_index

    rows = []  # (query_min, disturbed, true_remaining, linear, expfit, model_med, model_lo, model_hi)
    for qa in QUERY_AT:
        batch = [(s, s[0][:qa]) for s in sims if s[2] > qa]
        ctx = np.stack([c for _, c in batch])
        cov = np.stack([np.full((1, qa + HORIZON), s[1], np.float32) for s, _ in batch])
        q = np.concatenate([
            m.forecast(ctx[i:i + 16], HORIZON, known_covariates=cov[i:i + 16], symmetric_averaging=False)
            .quantiles[:, 0].numpy() for i in range(0, len(ctx), 16)
        ])
        for k, (s, c) in enumerate(batch):
            goal = s[1] - BAND
            true_rem = s[2] - (qa - 1)
            # higher quantile path crosses first -> optimistic bound
            rows.append((qa, s[3], true_rem, eta_linear(c, goal), eta_expfit(c, goal), eta_expfit_recent(c, goal),
                         first_cross(q[k, :, med], goal), first_cross(q[k, :, q90], goal),
                         first_cross(q[k, :, q10], goal)))
    r = np.array(rows, dtype=np.float64)

    name = repo.split("/")[-1]
    for label, sel in [("all", np.ones(len(r), bool)), ("no_food", r[:, 1] == 0)]:
        for qa in QUERY_AT:
            s = r[sel & (r[:, 0] == qa)]
            if not len(s):
                continue
            out = [f"{name}\t{label}\tt={qa:>2}min\tn={len(s)}\ttrue_remaining_median={np.median(s[:, 2]):.0f}min"]
            for col, method in [(3, "linear"), (4, "expfit"), (5, "expfit_recent"), (6, "timestral")]:
                err = np.abs(s[:, col] - s[:, 2])
                miss = np.isnan(err).sum()
                out.append(f"{method}_MAE={np.nanmean(err):.1f}min(miss={miss})")
            lo, hi = s[:, 7], s[:, 8]
            cover = np.mean((lo <= s[:, 2]) & ((s[:, 2] <= hi) | np.isnan(hi)))
            out.append(f"timestral_80%_cover={cover * 100:.0f}%\tupper_bound_found={np.isfinite(hi).mean() * 100:.0f}%")
            print("\t".join(out), flush=True)


main(sys.argv[1])
