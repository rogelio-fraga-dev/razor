"""Gera a narração de cada cena com o Piper (voz pt-BR local) e grava as durações.
Uso: python tools/tts.py professor/script.json public/professor"""
import json, sys, wave, subprocess, os
script, outdir = sys.argv[1], sys.argv[2]
model = os.path.join(os.path.dirname(__file__), "..", "voices", "pt_BR-faber-medium.onnx")
data = json.load(open(script, encoding="utf-8"))
durs = {}
for s in data["scenes"]:
    path = os.path.join(outdir, f"{s['id']}.wav")
    subprocess.run([sys.executable, "-m", "piper", "-m", model, "--length-scale", "0.92",
                    "--sentence-silence", "0.35", "-f", path], input=s["text"].encode("utf-8"), check=True,
                   stderr=subprocess.DEVNULL)
    with wave.open(path) as w:
        durs[s["id"]] = round(w.getnframes() / w.getframerate(), 2)
json.dump(durs, open(os.path.join(outdir, "durations.json"), "w"), indent=1)
print(durs, "total", round(sum(durs.values()), 1))
