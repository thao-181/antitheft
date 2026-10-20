"""Generate four original prototype alarms. Pure synthesized PCM; no third-party samples."""
from pathlib import Path
import math
import struct
import wave

RATE = 22050
DURATION = 2.4
OUTPUT = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"
OUTPUT.mkdir(parents=True, exist_ok=True)
for tone in range(1, 5):
    samples = []
    phase = 0.0
    for i in range(int(RATE * DURATION)):
        t = i / RATE
        if tone == 1:
            frequency = 880 if int(t / .3) % 2 == 0 else 1320
            gate = min(1.0, (t % .3) / .008, (.3 - t % .3) / .008)
        elif tone == 2:
            frequency = 450 + 1350 * ((t % 1.2) / 1.2)
            gate = 1.0
        elif tone == 3:
            frequency = 1046.5
            local = t % .8
            pulse = local % .2
            gate = max(0.0, min(1.0, pulse / .008, (.13 - pulse) / .008)) if local < .6 else 0.0
        else:
            frequency = 750 + 300 * math.sin(2 * math.pi * t / 1.2)
            gate = 1.0
        phase += 2 * math.pi * frequency / RATE
        fade = max(0.0, min(1.0, t / .01, (DURATION - t) / .01))
        value = (.8 * math.sin(phase) + .2 * math.sin(2 * phase)) * .7 * gate * fade
        samples.append(struct.pack("<h", round(max(-1, min(1, value)) * 32767)))
    with wave.open(str(OUTPUT / f"alarm_tone_{tone}.wav"), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(b"".join(samples))
print("Generated four distinct original PCM alarm resources")
