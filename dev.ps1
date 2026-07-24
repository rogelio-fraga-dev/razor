# RAZOR · sobe o ambiente local de desenvolvimento.
#   1. Containers: razor-postgres (5433) e razor-evolution (8080)
#   2. Backend Spring (8081) com as variaveis do .env.local
# Painel: rode `cd web; npm run dev` em outro terminal (http://localhost:5173).

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

# Containers (ignora erro se ja estiverem rodando)
docker start razor-postgres 2>$null | Out-Null
docker start razor-evolution 2>$null | Out-Null
Write-Host "containers: razor-postgres (5433) + razor-evolution (8080)" -ForegroundColor DarkGray

Write-Host "subindo backend em http://localhost:8081 ..." -ForegroundColor Cyan
& "$PSScriptRoot\mvnw" -o spring-boot:run
