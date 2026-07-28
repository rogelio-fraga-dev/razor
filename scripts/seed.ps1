# RAZOR · popula o banco com a barbearia de demonstracao.
#
#   .\scripts\seed.ps1
#
# Roda scripts/seed-demo.sql dentro do container do Postgres. O script so mexe no tenant da
# demo (slug demo-navalha), entao rodar de novo apenas regenera os dados ficticios — nenhuma
# outra barbearia do banco e tocada.

$ErrorActionPreference = "Stop"

$raiz = Split-Path $PSScriptRoot -Parent
$sql  = Join-Path $PSScriptRoot "seed-demo.sql"

if (-not (Test-Path $sql)) {
    Write-Host "ERRO: $sql nao encontrado." -ForegroundColor Red
    exit 1
}

# O banco precisa estar de pe; sem isso o psql falha com uma mensagem bem menos clara.
$estado = & docker compose -f "$raiz\docker-compose.yml" ps --status running --services 2>$null
if ($estado -notcontains "postgres") {
    Write-Host "Postgres nao esta rodando. Subindo..." -ForegroundColor Yellow
    & docker compose -f "$raiz\docker-compose.yml" up -d postgres
    if ($LASTEXITCODE -ne 0) { exit 1 }
    Start-Sleep -Seconds 3
}

Write-Host "populando a barbearia de demonstracao..." -ForegroundColor Cyan

# ON_ERROR_STOP faz o psql sair com codigo != 0 no primeiro erro, em vez de seguir e mentir
# que deu certo.
Get-Content $sql -Raw -Encoding UTF8 | & docker compose -f "$raiz\docker-compose.yml" exec -T postgres `
    psql -U barberbot_user -d barberbot_db -v ON_ERROR_STOP=1

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERRO: o seed falhou." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "pronto. Painel: http://localhost:3000" -ForegroundColor Green
Write-Host "  dono@navalhademo.local     / demo12345   (OWNER)"     -ForegroundColor Gray
Write-Host "  recepcao@navalhademo.local / demo12345   (RECEPTION)" -ForegroundColor Gray
Write-Host "  barbeiro@navalhademo.local / demo12345   (BARBER)"    -ForegroundColor Gray
