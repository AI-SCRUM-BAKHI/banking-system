$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $backendRoot
Set-Location $backendRoot

if (-not (Test-Path '.env')) {
    Copy-Item '.env.example' '.env'
    Write-Host 'Created .env from .env.example'
}

docker compose -f "$repoRoot\infra\docker-compose.yml" up -d postgres
.\gradlew.bat :app-bootstrap:bootRun
