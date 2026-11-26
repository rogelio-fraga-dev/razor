"""Monta o site da LH Barbearia (demonstração) pelo editor do painel: sobe as fotos e grava o layout.
Uso: python scripts/site-lh.py [BASE] [email] [senha]   (padrão: painel local e o login da demo LH)
As fotos ficam em documentos/lh-barbearia/fotos (fora do git)."""
import base64, http.cookiejar, io, json, os, sys, urllib.request
from PIL import Image

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:3000"
EMAIL = sys.argv[2] if len(sys.argv) > 2 else "dono@lhbarbearia.local"
PWD = sys.argv[3] if len(sys.argv) > 3 else "demo12345"
FOTOS = r"C:\projetos\razor\documentos\lh-barbearia\fotos"

cj = http.cookiejar.CookieJar()
op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))


def xsrf():
    return next((c.value for c in cj if c.name == "XSRF-TOKEN"), "")


def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method,
                                 headers={"Content-Type": "application/json", "X-XSRF-TOKEN": xsrf()})
    with op.open(req, timeout=60) as r:
        raw = r.read()
        return json.loads(raw) if raw else None


op.open(BASE + "/api/auth/csrf").read()
call("POST", "/api/auth/login", {"email": EMAIL, "password": PWD})


def upload(name, max_side, fmt="JPEG"):
    im = Image.open(os.path.join(FOTOS, name))
    im = im.convert("RGBA" if fmt == "PNG" else "RGB")
    im.thumbnail((max_side, max_side))
    buf = io.BytesIO()
    im.save(buf, fmt, quality=86) if fmt == "JPEG" else im.save(buf, fmt, optimize=True)
    mime = "image/jpeg" if fmt == "JPEG" else "image/png"
    url = call("POST", "/api/admin/site/images", {"data": f"data:{mime};base64," + base64.b64encode(buf.getvalue()).decode()})["url"]
    print("foto", name, "->", url)
    return url


antigas = [img["id"] for img in (call("GET", "/api/admin/site/images") or [])]

logo = upload("logo.png", 480, "PNG")
hero = upload("capa1.png", 1400)
gallery = [upload(n, 1100) for n in ["capa2.png", "capa3.png", "ig6.jpg", "ig7.jpg", "ig9.jpg"]] + [hero]

site = {
    "templateId": "CLASSICO", "themeMode": "DARK", "accentColor": "#DB6300",
    "headline": "Sua auto estima vale ouro!",
    "subheadline": "Mais que um corte de cabelo: a sua imagem merece o devido respeito e cuidado. Visagismo e Barberclub em Araguari.",
    "aboutText": ("Desde 2017 cuidando da imagem de homens que confiam em si mesmos. Mais de 16 mil atendimentos, "
                  "visagismo de verdade e uma comunidade para homens ousados, exigentes e competentes. "
                  "Mais que uma barbearia: um lugar de líder para líder."),
    "logoUrl": logo, "heroImageUrl": hero, "galleryUrls": "\n".join(gallery),
    "whatsappNumber": None, "instagramUrl": "https://www.instagram.com/lhbarbeariaa/", "mapsUrl": None,
    "showTeam": True, "showPlans": True, "showPrices": True, "published": True, "onlineBookingEnabled": True,
    "content": {"sections": {
        "hero": {"eyebrow": "LH Barbearia · desde 2017"},
        "story": {"eyebrow": "De líder para líder", "title": "Está na cara a diferença entre homens e meninos."},
        "gallery": {"eyebrow": "Da nossa cadeira", "title": "Mais de 16 mil atendimentos"},
        "cta": {"eyebrow": "Sua cadeira espera", "title": "Bora cuidar da sua auto estima?"},
    }},
}
call("PUT", "/api/admin/site", site)
for i in antigas:  # fotos da versão anterior, agora sem uso
    try:
        call("DELETE", f"/api/admin/site/images/{i}")
    except Exception:
        pass
print("site publicado:", BASE + "/b/lh-barbearia")
