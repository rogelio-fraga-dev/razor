# Script para rodar o backend RAZOR com as variaveis do .env.local
$ErrorActionPreference = "Stop"

$envFile = Join-Path $PSScriptRoot ".env.local"
if (Test-Path $envFile) {
    Get-Content $envFile -Encoding UTF8 | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
            $name = $Matches[1].Trim()
            $value = $Matches[2].Trim()
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
            Write-Host "env: $name" -ForegroundColor DarkGray
        }
    }
}

Write-Host "Iniciando backend Spring Boot..." -ForegroundColor Cyan
& ".\mvnw.cmd" spring-boot:run
