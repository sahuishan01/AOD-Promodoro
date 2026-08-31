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


def write_wav(name: str, raw_floats: list[float]) -> None:
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    
    # Peak normalization to 24000 (~ -2.7 dBFS headroom)
    max_val = max(max(abs(x) for x in raw_floats), 1e-6)
    scale = 24000.0 / max_val
    normalized = [int(x * scale) for x in raw_floats]
    
    # Crossfade 0.5s at boundary for clickless looping
    fade_len = int(SR * 0.5)
    for i in range(fade_len):
        t = i / fade_len
        # blend head and tail
        normalized[i] = int(normalized[i] * t + normalized[-fade_len + i] * (1.0 - t))

    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(
            b"".join(struct.pack("<h", max(-32767, min(32767, s))) for s in normalized)
        )
    print(f"wrote {path} ({os.path.getsize(path)} bytes)")


def rain_drift() -> None:
    """Low-passed noise with slow amplitude wander and gentle droplets."""
    random.seed(42)
    state = 0.0
    n = SR * DUR
    samples = []
    for i in range(n):
        white = random.uniform(-1, 1)
        state = 0.96 * state + 0.04 * white
        lfo = 0.7 + 0.3 * math.sin(2 * math.pi * i / (SR * 7.5))
        samples.append(state * lfo)
    write_wav("rain_drift.wav", samples)


def night_pad() -> None:
    """Warm ambient drone chord with slow harmonic evolution."""
    freqs = [110.0, 164.81, 220.0, 261.63, 329.63]
    weights = [0.4, 0.25, 0.25, 0.15, 0.1]
    n = SR * DUR
    samples = []
    for i in range(n):
        t = i / SR
        v = sum(w * math.sin(2 * math.pi * f * t) for f, w in zip(freqs, weights))
        env = 0.75 + 0.25 * math.sin(2 * math.pi * t / 11.0)
        samples.append(v * env)
    write_wav("night_pad.wav", samples)


if __name__ == "__main__":
    rain_drift()
    night_pad()
