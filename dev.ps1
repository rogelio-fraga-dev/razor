# RAZOR · sobe o ambiente de DESENVOLVIMENTO (backend fora do Docker, para recarga rapida).
#   1. Container do banco pelo compose: postgres em 5433
#   2. Backend Spring (8081) com as variaveis do .env.local
# Painel: rode `cd web; npm run dev` em outro terminal (http://localhost:5173).
#
# Para so rodar o sistema (sem editar codigo), use `docker compose up -d --build`: sobe banco,
# API e painel juntos, sem precisar de JDK nem Node na maquina. Veja INICIALIZACAO.md.

$ErrorActionPreference = "Stop"

$envFile = Join-Path $PSScriptRoot ".env.local"
if (-not (Test-Path $envFile)) {
    Write-Host "ERRO: .env.local nao encontrado." -ForegroundColor Red
    Write-Host "Copie .env.local.example para .env.local e preencha (principalmente OPENAI_API_KEY)."
    exit 1
}

# Carrega as variaveis do .env.local no processo
Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        $name = $Matches[1].Trim()
        $value = $Matches[2].Trim()
        [Environment]::SetEnvironmentVariable($name, $value)
        Write-Host "env: $name" -ForegroundColor DarkGray
    }
}

if (-not $env:WEBHOOK_TOKEN) {
    Write-Host "AVISO: WEBHOOK_TOKEN nao definido — o webhook fica DESABILITADO (fail-closed)." -ForegroundColor Yellow
}

if ($env:OPENAI_API_KEY -like "*SUA-CHAVE*" -or $env:OPENAI_API_KEY -like "*dummy*") {
    Write-Host "AVISO: OPENAI_API_KEY parece placeholder — a IA NAO vai responder clientes." -ForegroundColor Yellow
}

# Banco pelo compose — mesma definicao usada pelo stack completo, para nao existirem dois
# Postgres diferentes disputando a porta 5433. A Evolution nao sobe aqui: use
# `docker compose --profile bot up -d evolution-api` quando for testar o bot.
& docker compose -f "$PSScriptRoot\docker-compose.yml" up -d postgres
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERRO: nao consegui subir o Postgres. O Docker Desktop esta rodando?" -ForegroundColor Red
    exit 1
}
Write-Host "banco: postgres em localhost:5433" -ForegroundColor DarkGray

Write-Host "subindo backend em http://localhost:8081 ..." -ForegroundColor Cyan
& "$PSScriptRoot\mvnw" -o spring-boot:run
