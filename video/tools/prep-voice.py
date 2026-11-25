"""Prepara a narração gravada pelo dono: corta silêncio, normaliza volume (-16 LUFS) e grava
public/professor/voice/<cena>.wav + durations.json. Uso: python tools/prep-voice.py <ffmpeg> <pasta> <primeiro número>"""
import json, subprocess, sys, wave, os
import numpy as np
FF, SRC, PATTERN, FIRST = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
scenes = [s["id"] for s in json.load(open(os.path.join(ROOT, "professor/script.json"), encoding="utf-8"))["scenes"]]
out = os.path.join(ROOT, "public/professor/voice"); os.makedirs(out, exist_ok=True)
tmp = os.path.join(out, "_tmp.wav"); durs = {}
for i, sid in enumerate(scenes):
    src = os.path.join(SRC, PATTERN.format(n=FIRST + i))
    subprocess.run([FF, "-hide_banner", "-loglevel", "error", "-y", "-i", src, "-ac", "1", "-ar", "48000", tmp], check=True)
    w = wave.open(tmp); sr = w.getframerate(); x = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(float); w.close()
    fr = int(sr * 0.02); e = np.array([np.sqrt(np.mean(x[k*fr:(k+1)*fr]**2)) + 1e-9 for k in range(len(x)//fr)])
    db = 20*np.log10(e); on = np.where(db > np.percentile(db, 10) + 15)[0]
    a = max(0, on[0]*0.02 - 0.12); b = min(len(x)/sr, (on[-1]+1)*0.02 + 0.25)
    dst = os.path.join(out, f"{sid}.wav")
    subprocess.run([FF, "-hide_banner", "-loglevel", "error", "-y", "-i", tmp, "-ss", f"{a:.2f}", "-to", f"{b:.2f}",
                    "-af", "highpass=f=70,afftdn=nf=-30,loudnorm=I=-16:TP=-1.5:LRA=9,afade=t=in:d=0.05",
                    "-ar", "48000", dst], check=True)
    durs[sid] = round(b - a, 2)
    print(sid, durs[sid])
os.remove(tmp)
json.dump(durs, open(os.path.join(out, "durations.json"), "w"), indent=1)
