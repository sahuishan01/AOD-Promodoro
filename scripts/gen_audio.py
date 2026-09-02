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


def forest_stream() -> None:
    """Low-frequency water noise with gentle periodic bubbling modulation."""
    random.seed(101)
    state = 0.0
    n = SR * DUR
    samples = []
    for i in range(n):
        white = random.uniform(-1, 1)
        state = 0.92 * state + 0.08 * white
        lfo = 0.65 + 0.35 * math.sin(2 * math.pi * i / (SR * 4.2))
        bubble = 0.15 * math.sin(2 * math.pi * i / (SR * 0.35)) * math.sin(2 * math.pi * i / (SR * 2.1))
        samples.append((state + bubble) * lfo)
    write_wav("forest_stream.wav", samples)


def deep_focus() -> None:
    """Warm 54Hz binaural/binaural-style drone with gentle 108Hz harmonic layer."""
    n = SR * DUR
    samples = []
    for i in range(n):
        t = i / SR
        v1 = 0.5 * math.sin(2 * math.pi * 54.0 * t)
        v2 = 0.3 * math.sin(2 * math.pi * 108.0 * t)
        v3 = 0.15 * math.sin(2 * math.pi * 162.0 * t)
        env = 0.8 + 0.2 * math.sin(2 * math.pi * t / 15.0)
        samples.append((v1 + v2 + v3) * env)
    write_wav("deep_focus.wav", samples)


def cosmic_synth() -> None:
    """Ethereal sci-fi synth pad with dual slow-modulating sine waves."""
    freqs = [146.83, 220.0, 329.63, 440.0]
    weights = [0.35, 0.3, 0.2, 0.15]
    n = SR * DUR
    samples = []
    for i in range(n):
        t = i / SR
        v = sum(w * math.sin(2 * math.pi * (f + 1.2 * math.sin(2 * math.pi * t / 8.0)) * t) for f, w in zip(freqs, weights))
        env = 0.7 + 0.3 * math.sin(2 * math.pi * t / 14.0)
        samples.append(v * env)
    write_wav("cosmic_synth.wav", samples)


def soft_chime() -> None:
    """Short phase-transition cue chime (~2.5s decay harmonic bell)."""
    dur = 3  # seconds
    n = SR * dur
    samples = []
    # E5 (659.25Hz), B5 (987.77Hz), E6 (1318.51Hz)
    freqs = [659.25, 987.77, 1318.51]
    weights = [0.5, 0.35, 0.15]
    for i in range(n):
        t = i / SR
        v = sum(w * math.sin(2 * math.pi * f * t) for f, w in zip(freqs, weights))
        decay = math.exp(-2.2 * t)
        samples.append(v * decay)
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, "soft_chime.wav")
    max_val = max(max(abs(x) for x in samples), 1e-6)
    scale = 24000.0 / max_val
    normalized = [max(-32767, min(32767, int(x * scale))) for x in samples]
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", s) for s in normalized))
    print(f"wrote {path} ({os.path.getsize(path)} bytes)")


if __name__ == "__main__":
    rain_drift()
    night_pad()
    forest_stream()
    deep_focus()
    cosmic_synth()
    soft_chime()

