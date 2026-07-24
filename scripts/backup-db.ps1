# RAZOR · backup do Postgres.
#   .\scripts\backup-db.ps1                      -> backup em .\backups
#   .\scripts\backup-db.ps1 -OutDir D:\backups   -> destino customizado
#
# Gera um dump comprimido (formato custom do pg_dump, restaurável com pg_restore),
# verifica que o arquivo não saiu vazio e aplica retenção de 14 dias.
# Agende no Windows: Task Scheduler -> diariamente -> powershell -File <caminho>

param(
    [string]$OutDir = "$PSScriptRoot\..\backups",
    [string]$Container = "razor-postgres",
    [string]$Db = "barberbot_db",
    [string]$DbUser = "barberbot_user",
    [string]$DbPassword = "barberbot_pass",
    [int]$RetentionDays = 14
)

$ErrorActionPreference = "Stop"

if (-not (docker ps --filter "name=$Container" --format "{{.Names}}")) {
    Write-Host "ERRO: container '$Container' não está rodando." -ForegroundColor Red
    exit 1
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$stamp = Get-Date -Format "yyyy-MM-dd_HHmmss"
$file = Join-Path $OutDir "razor_${Db}_$stamp.dump"

Write-Host "Gerando backup de '$Db'..." -ForegroundColor Cyan
# -Fc = formato custom (comprimido). O redirecionamento é feito pelo cmd: o pipeline do
# PowerShell converteria a saída binária em texto e corromperia o dump.
$file = [System.IO.Path]::GetFullPath($file)
cmd /c "docker exec -e PGPASSWORD=$DbPassword $Container pg_dump -U $DbUser -d $Db -Fc > ""$file"""
if ($LASTEXITCODE -ne 0) { Write-Host "ERRO: pg_dump falhou (código $LASTEXITCODE)." -ForegroundColor Red; exit 1 }

if (-not (Test-Path $file)) { Write-Host "ERRO: arquivo não foi criado." -ForegroundColor Red; exit 1 }

$size = (Get-Item $file).Length
if ($size -lt 1024) {
    Write-Host "ERRO: backup suspeito ($size bytes). Verifique o banco." -ForegroundColor Red
    exit 1
}

$mb = [math]::Round($size / 1MB, 2)
Write-Host "OK: $file ($mb MB)" -ForegroundColor Green

# Retenção
$cut = (Get-Date).AddDays(-$RetentionDays)
$old = Get-ChildItem -Path $OutDir -Filter "razor_*.dump" | Where-Object { $_.LastWriteTime -lt $cut }
if ($old) {
    $old | Remove-Item -Force
    Write-Host "Removidos $($old.Count) backup(s) com mais de $RetentionDays dias." -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "Para restaurar:" -ForegroundColor Yellow
Write-Host "  docker exec -i $Container pg_restore -U $DbUser -d $Db --clean --if-exists < `"$file`""
