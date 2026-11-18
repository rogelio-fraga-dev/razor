# Nomes e domínios — levantamento de 18/11/2026

A disponibilidade foi consultada por RDAP: Registro.br para .com.br, Verisign para .com e Google
Registry para .app. "Livre" significa que **naquele dia** o domínio não estava registrado. Isso pode
mudar a qualquer momento: domínio bom se registra logo (.com.br custa cerca de R$ 40/ano).

**Não consultamos marca no INPI** porque ele não tem consulta automática. Antes de imprimir qualquer
coisa, pesquise em busca.inpi.gov.br nas classes 42 (software/SaaS), 35 (gestão) e 9 (aplicativos).

## Marca de barbearias (hoje "Razor")

| Opção | .com.br | .com | Comentário |
|---|---|---|---|
| razor | registrado | registrado | Nome curto e forte, mas genérico em inglês. É muito usado no mundo (patinete Razor; parecido com a Razer, de games). Registrar a marca pode ser difícil. |
| **razorapp** | **LIVRE** | registrado | Mantém a marca atual. `razorapp.com.br` + `navalha.razorapp.com.br` para cada barbearia. |
| razorapp.app | — | — | **LIVRE** (.app exige HTTPS, o que é bom) |
| getrazor / razorhq / razoria / razorai / razorbot | LIVRE | registrado | Alternativas com o mesmo nome |
| razorbr | LIVRE | LIVRE | Livre nos dois, mas soa genérico |
| meurazor / razorgestao | LIVRE | LIVRE | Livres nos dois |
| **navalhaia** ("Navalha IA") | **LIVRE** | **LIVRE** | Português, liga direto a barbearia e IA, livre nos dois |
| barbeiai | LIVRE | LIVRE | Livre, mas difícil de falar |
| agendacorte | LIVRE | registrado | Descritivo, bom para SEO, fraco como marca |
| navalha, cortai, corteia, barbix, barberbot | registrado | registrado | Indisponíveis |

**Recomendação:** manter **Razor** como marca e registrar já `razorapp.com.br`, e se possível também
`razorapp.app`. Como reserva, registrar `navalhaia.com.br` + `navalhaia.com`: se o INPI barrar
"Razor", é a troca mais natural para o público brasileiro.

## Marca de salões e estética (mesma plataforma, outra marca)

| Opção | .com.br | .com | Comentário |
|---|---|---|---|
| **belmora** | **LIVRE** | registrado | Soa a "bela" + "mora"; elegante, fácil de falar |
| **biella** | **LIVRE** | registrado | Curta e feminina; confirmar no INPI (existe uma cidade italiana com esse nome) |
| sereniabeleza | LIVRE | LIVRE | Livre nos dois, mas longa |
| salaoia | LIVRE | LIVRE | Descritiva (salão + IA) |
| unhaia / cachoia | LIVRE | LIVRE | Nichos (unhas, cachos): úteis como sub-marca ou landing de SEO |
| meuglow / lumiebeleza | LIVRE | registrado | — |
| belamente.app | — | — | .app LIVRE; .com.br registrado |
| lumora, aurelle, vellora, belvy, glamia, salonia, esteticai, belezai… | registrado | registrado | Indisponíveis |

**Recomendação:** shortlist **Belmora** e **Biella**. Decidir depois do piloto de barbearias, mas
registrar o .com.br das duas agora, porque o custo é baixo e evita perder o nome.

## Estrutura sugerida
- **Uma empresa** (CNPJ único, ver MVP-PILOTO.md → "Empresa, marca e domínio"), duas marcas:
  Razor (barbearias) e Belmora/Biella (salões e estética).
- Mesmo backend e painel, com `vertical` por cliente. Cada marca tem domínio, landing e identidade próprios.
- Subdomínio por cliente: `{slug}.razorapp.com.br` e `{slug}.belmora.com.br`.
- Redes sociais: verificar @razorapp / @navalhaia / @belmora no Instagram antes de decidir.
