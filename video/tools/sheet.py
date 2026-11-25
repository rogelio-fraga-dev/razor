"""Folha de quadros de um clipe. Uso: python tools/sheet.py <clipe> <saida.png> t1 t2 ..."""
import subprocess, sys, tempfile, os
from PIL import Image
src, out, *ts = sys.argv[1:]
d = tempfile.mkdtemp(); ims = []
for i, t in enumerate(ts):
    f = os.path.join(d, f"{i}.png")
    subprocess.run(f'npx remotion ffmpeg -y -loglevel error -ss {t} -i "{src}" -frames:v 1 -vf scale=560:-1 "{f}"', shell=True, check=True)
    ims.append(Image.open(f))
w, h = ims[0].size; cols = 3; rows = (len(ims) + cols - 1) // cols
s = Image.new("RGB", (w * cols, h * rows), "black")
for k, im in enumerate(ims): s.paste(im, ((k % cols) * w, (k // cols) * h))
s.save(out)
