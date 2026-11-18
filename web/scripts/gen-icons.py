"""Gera os PNGs do PWA a partir da geometria do public/favicon.svg (simbolo "The Edge").

Uso (na pasta web/):  python scripts/gen-icons.py
Precisa so do Pillow. Os PNGs gerados ficam versionados em public/icons/ — o build do Vite nao
roda este script; rode de novo so se o simbolo mudar.

Por que redesenhar em vez de converter o SVG: o Windows/CI nao tem rasterizador de SVG garantido
(cairo, rsvg, inkscape), e o simbolo sao 2 poligonos e 1 degrade. Desenhar em 4x e reduzir com
LANCZOS da a mesma borda suave do navegador.
"""
from pathlib import Path

from PIL import Image, ImageDraw

OBSIDIAN = (10, 10, 12, 255)  # --bg / #0A0A0C
CHROME = (233, 235, 239, 255)  # #E9EBEF
VOLTAGE = (62, 99, 255)  # #3E63FF
EDGE = (55, 226, 255)  # #37E2FF

# Geometria do favicon.svg (viewBox 0 0 100 100).
BLADE = [(34, 86), (52, 86), (78, 14), (60, 14)]
GLINT = [(60, 14), (78, 14), (75.5, 21), (57.5, 21)]

OUT = Path(__file__).resolve().parent.parent / "public" / "icons"
SS = 4  # supersampling


def render(size: int, *, rounded: bool, scale: float) -> Image.Image:
    """`scale` encolhe o simbolo em torno do centro (icone maskable precisa de margem de 10%)."""
    s = size * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if rounded:
        d.rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.22), fill=OBSIDIAN)
    else:
        d.rectangle([0, 0, s, s], fill=OBSIDIAN)

    def pt(x: float, y: float) -> tuple[float, float]:
        cx = 50 + (x - 50) * scale
        cy = 50 + (y - 50) * scale
        return (cx / 100 * s, cy / 100 * s)

    d.polygon([pt(*p) for p in BLADE], fill=CHROME)

    # Degrade diagonal (x1=0,y1=1 -> x2=1,y2=0) do brilho, recortado pelo poligono.
    grad = Image.new("RGBA", (s, s))
    gp = grad.load()
    for y in range(s):
        for x in range(s):
            t = (x + (s - y)) / (2 * s)
            gp[x, y] = tuple(int(VOLTAGE[i] + (EDGE[i] - VOLTAGE[i]) * t) for i in range(3)) + (255,)
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).polygon([pt(*p) for p in GLINT], fill=255)
    img.paste(grad, (0, 0), mask)

    return img.resize((size, size), Image.LANCZOS)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    # "any": cantos arredondados como o favicon.
    for size in (192, 512):
        render(size, rounded=True, scale=1.0).save(OUT / f"icon-{size}.png", optimize=True)
    # "maskable": fundo cheio e simbolo dentro da zona segura (circulo de 80%).
    render(512, rounded=False, scale=0.72).save(OUT / "maskable-512.png", optimize=True)
    # iOS ignora transparencia (pinta de preto) e arredonda sozinho: fundo cheio, 180px.
    render(180, rounded=False, scale=0.8).save(OUT / "apple-touch-icon.png", optimize=True)
    print("icones gerados em", OUT)


if __name__ == "__main__":
    main()
