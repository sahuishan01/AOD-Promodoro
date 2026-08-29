#!/usr/bin/env python3
"""Generate the bundled ambient tracks (DEVELOPMENT-PLAN.md §Phase 5).

Pure-stdlib synthesis (wave/struct/math) so CI needs no extra deps.
Output: app/src/main/res/raw/{rain_drift,night_pad}.wav — loopable, license-clean.
"""

import math
import os
import random
import struct
import wave

SR = 22050  # mono 22.05 kHz keeps files small
DUR = 45    # seconds; ExoPlayer REPEAT_MODE_ONE loops it seamlessly
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")


def write_wav(name: str, samples: list[int]) -> None:
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(
            b"".join(struct.pack("<h", max(-32767, min(32767, int(s)))) for s in samples)
        )
    print(f"wrote {path} ({os.path.getsize(path)} bytes)")


def rain_drift() -> None:
    """Low-passed noise with slow amplitude wander."""
    random.seed(7)
    state = 0.0
    n = SR * DUR
    samples = []
    for i in range(n):
        white = random.uniform(-1, 1)
        state = 0.985 * state + 0.015 * white
        lfo = 0.55 + 0.45 * math.sin(2 * math.pi * i / (SR * 9.0))
        samples.append(state * 4000 * lfo)
    write_wav("rain_drift.wav", samples)


def night_pad() -> None:
    """Stacked slow sines (A-minor-ish pad) with breathing envelope."""
    freqs = [110.0, 164.81, 220.0, 329.63]
    weights = [0.5, 0.3, 0.35, 0.2]
    n = SR * DUR
    samples = []
    for i in range(n):
        t = i / SR
        v = sum(w * math.sin(2 * math.pi * f * t) for f, w in zip(freqs, weights))
        env = 0.6 + 0.4 * math.sin(2 * math.pi * t / 13.0)
        samples.append(v * 3200 * env)
    write_wav("night_pad.wav", samples)


if __name__ == "__main__":
    rain_drift()
    night_pad()
