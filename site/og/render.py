"""Gera site/og-image.png (1200x630) a partir de site/og/og-image.html.

Uso:  python site/og/render.py
Requer Playwright para Python (pip install playwright) e o Chrome instalado.
"""
from pathlib import Path

from playwright.sync_api import sync_playwright

HERE = Path(__file__).resolve().parent
SRC = HERE / "og-image.html"
OUT = HERE.parent / "og-image.png"

with sync_playwright() as p:
    browser = p.chromium.launch(channel="chrome")
    page = browser.new_page(viewport={"width": 1200, "height": 630}, device_scale_factor=1)
    page.goto(SRC.as_uri())
    # As fontes vêm do Google Fonts: espera carregarem para não sair com a fonte de sistema.
    page.wait_for_load_state("networkidle")
    page.evaluate("document.fonts.ready")
    page.screenshot(path=str(OUT), clip={"x": 0, "y": 0, "width": 1200, "height": 630})
    browser.close()

print(f"ok: {OUT} ({OUT.stat().st_size // 1024} KB)")
