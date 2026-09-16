# Rebuilda e rilancia il frontend garantendo che le modifiche vengano sempre prese.
# Puo' essere lanciato da qualsiasi cartella: usa il compose e il .env accanto a questo script.
$composeFile = Join-Path $PSScriptRoot "docker-compose.yml"
$envFile = Join-Path $PSScriptRoot "..\..\running\.env"

if (-not (Test-Path $envFile)) {
    Write-Host "ERRORE: file $envFile non trovato. Copia deploy\.env.example in running\.env e configuralo."
    exit 1
}

$env:CACHEBUST = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
Write-Host "Build frontend (CACHEBUST=$env:CACHEBUST)..."
docker compose -f $composeFile --env-file $envFile build frontend
if ($LASTEXITCODE -eq 0) {
    docker compose -f $composeFile --env-file $envFile up -d frontend
    Write-Host "Deploy completato."
} else {
    Write-Host "Build fallita."
}
