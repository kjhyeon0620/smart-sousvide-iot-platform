"""Fault injection: Timestral (quantile band) vs simple rules on simulated sous-vide telemetry."""
import sys
import numpy as np
import torch
from timestral import TimestralForecaster

torch.set_num_threads(2)
CTX, EVAL, H = 1440, 120, 15        # 1 day history, 2 h evaluated, forecast every 15 min
N_PER_KIND = 40
FAULTS = ["normal", "heater_degrade", "sensor_stuck", "lid_open"]


def simulate(rng, kind):
    n = CTX + EVAL
    while True:
        amb, power, loss = rng.uniform(18, 26), rng.uniform(0.3, 1.0), rng.uniform(0.005, 0.015)
        tgt = rng.choice([55.0, 57.0, 60.0, 63.0])
        if amb + power / loss >= tgt + 13:   # heater can comfortably hold target
            break
    change_at = CTX + rng.integers(0, EVAL) if rng.random() < 0.3 else n  # legit setpoint change
    onset = CTX + rng.integers(15, 90) if kind != "normal" else n
    temp, target, heating = (np.empty(n, np.float32) for _ in range(3))
    t, on, stuck_val = tgt, False, None
    for i in range(n):
        if i == change_at:
            tgt += rng.choice([-3.0, 3.0])
        p, l = power, loss
        if i >= onset and kind == "heater_degrade":
            p = power * 0.5
        if onset <= i < onset + 20 and kind == "lid_open":
            l = loss * 4
        on = True if t < tgt - 0.3 else False if t > tgt + 0.3 else on
        t += (p if on else 0.0) - l * (t - amb) + rng.normal(0, 0.03)
        reading = t + rng.normal(0, 0.05)
        if kind == "sensor_stuck" and i >= onset:
            stuck_val = reading if stuck_val is None else stuck_val
            reading = stuck_val
        temp[i], target[i], heating[i] = reading, tgt, on
    return temp, target, heating, onset, change_at


def rule_alarm(temp, target, heating, change_at):
    """First alarm minute (absolute index) in the eval region, or None."""
    for i in range(CTX, CTX + EVAL):
        grace = change_at <= i < change_at + 30
        dev = all(abs(temp[j] - target[j]) > 1.0 for j in range(i - 2, i + 1))          # R1: 3 min off target
        stall = heating[i - 9:i + 1].all() and temp[i] - temp[i - 9] <= 0.0              # R2: heating 10 min, no rise
        stuck = np.ptp(temp[i - 9:i + 1]) < 1e-6                                         # R3: 10 identical readings
        if (dev and not grace) or stall or stuck:
            return i
    return None


def model_alarms(m, data, lo, hi):
    """First alarm minute per series: 3 consecutive points outside the 98% band."""
    first = [None] * len(data)
    for k in range(EVAL // H):
        start = CTX + k * H
        ctx = np.stack([d[0][start - CTX:start] for d in data])
        cov = np.stack([d[1][None, start - CTX:start + H] for d in data])
        q = np.concatenate([
            m.forecast(ctx[i:i + 16], H, known_covariates=cov[i:i + 16], symmetric_averaging=False)
            .quantiles[:, 0].numpy() for i in range(0, len(ctx), 16)
        ])
        for s, d in enumerate(data):
            if first[s] is not None:
                continue
            y = d[0][start:start + H]
            out = (y < q[s, :, lo]) | (y > q[s, :, hi])
            run = 0
            for j, o in enumerate(out):
                run = run + 1 if o else 0
                if run >= 3:
                    first[s] = start + j
                    break
    return first


def summarize(name, kind, alarms, onsets):
    if kind == "normal":
        fp = sum(a is not None for a in alarms)
        print(f"{name}\t{kind}\tfalse_alarm_series={fp}/{len(alarms)} ({fp / len(alarms) * 100:.0f}%)", flush=True)
        return
    early = sum(a is not None and a < o for a, o in zip(alarms, onsets))
    hits = [a - o for a, o in zip(alarms, onsets) if a is not None and a >= o]
    delay = f"{np.median(hits):.0f}min" if hits else "-"
    print(f"{name}\t{kind}\tdetected={len(hits)}/{len(alarms)} ({len(hits) / len(alarms) * 100:.0f}%)"
          f"\tmedian_delay={delay}\talarm_before_onset={early}", flush=True)


def main(repo):
    rng = np.random.default_rng(7)
    sets = {k: [simulate(rng, k) for _ in range(N_PER_KIND)] for k in FAULTS}
    m = TimestralForecaster.from_pretrained(repo)
    levels = np.array(m.quantile_levels)
    lo, hi = int(np.argmin(abs(levels - 0.01))), int(np.argmin(abs(levels - 0.99)))
    for kind, data in sets.items():
        onsets = [d[3] for d in data]
        summarize("rules", kind, [rule_alarm(d[0], d[1], d[2], d[4]) for d in data], onsets)
        summarize(repo.split("/")[-1], kind, model_alarms(m, data, lo, hi), onsets)


main(sys.argv[1])
