#Requires -Version 5.1

[CmdletBinding()]
param(
    [Parameter()]
    [ValidateSet("Docker", "Direct")]
    [string] $Mode = "Docker",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string] $OutputDirectory,

    [Parameter()]
    [ValidatePattern("^[A-Za-z0-9][A-Za-z0-9_.-]*$")]
    [string] $Label = "gameio",

    [Parameter()]
    [ValidatePattern("^[A-Za-z0-9][A-Za-z0-9_.-]*$")]
    [string] $ComposeService = "postgres",

    [Parameter()]
    [ValidatePattern("^[A-Za-z0-9][A-Za-z0-9_.-]*$")]
    [string] $DatabaseName,

    [Parameter()]
    [ValidatePattern("^[A-Za-z0-9][A-Za-z0-9_.-]*$")]
    [string] $DatabaseUser
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$repoRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repoRoot "docker-compose.yml"
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $repoRoot ".data\backups"
}
if (-not [System.IO.Path]::IsPathRooted($OutputDirectory)) {
    $OutputDirectory = Join-Path $repoRoot $OutputDirectory
}
$resolvedOutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
$null = New-Item -ItemType Directory -Path $resolvedOutputDirectory -Force

if ([string]::IsNullOrWhiteSpace($DatabaseName)) {
    $DatabaseName = if ($Mode -eq "Direct" -and $env:PGDATABASE) {
        $env:PGDATABASE
    } elseif ($env:POSTGRES_DB) {
        $env:POSTGRES_DB
    } else {
        "gameio"
    }
}
if ([string]::IsNullOrWhiteSpace($DatabaseUser)) {
    $DatabaseUser = if ($Mode -eq "Direct" -and $env:PGUSER) {
        $env:PGUSER
    } elseif ($env:POSTGRES_USER) {
        $env:POSTGRES_USER
    } else {
        "gameio"
    }
}

$stamp = [DateTimeOffset]::UtcNow.ToString("yyyyMMddTHHmmssfffZ")
$backupPath = Join-Path $resolvedOutputDirectory "$Label-$stamp.dump"
if (Test-Path -LiteralPath $backupPath) {
    throw "Refusing to overwrite an existing backup: $backupPath"
}

function Assert-LastExitCode {
    param([Parameter(Mandatory)][string] $Operation)
    if ($LASTEXITCODE -ne 0) {
        throw "$Operation failed with exit code $LASTEXITCODE."
    }
}

if ($Mode -eq "Direct") {
    if (-not $env:PGHOST -or -not $env:PGUSER) {
        throw "Direct mode requires PGHOST and PGUSER. Set PGPASSWORD, PGPORT and PGSSLMODE when required."
    }
    $pgDump = Get-Command pg_dump -ErrorAction SilentlyContinue
    $pgRestore = Get-Command pg_restore -ErrorAction SilentlyContinue
    if (-not $pgDump -or -not $pgRestore) {
        throw "Direct mode requires pg_dump and pg_restore on PATH."
    }

    & $pgDump.Source "--dbname=$DatabaseName" --format=custom --compress=9 --no-owner --no-privileges `
        "--file=$backupPath"
    Assert-LastExitCode -Operation "pg_dump"
    & $pgRestore.Source --list $backupPath | Out-Null
    Assert-LastExitCode -Operation "pg_restore verification"
} else {
    if (-not (Test-Path -LiteralPath $composeFile -PathType Leaf)) {
        throw "Docker Compose file was not found at the repository root."
    }
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Docker CLI was not found."
    }
    $composeArgs = @("compose", "--project-directory", $repoRoot, "--file", $composeFile)
    $containerId = (& docker @composeArgs ps -q $ComposeService).Trim()
    Assert-LastExitCode -Operation "docker compose ps"
    if ([string]::IsNullOrWhiteSpace($containerId)) {
        throw "Compose service '$ComposeService' is not running."
    }

    $containerPath = "/tmp/gameio-backup-$([Guid]::NewGuid().ToString('N')).dump"
    try {
        & docker @composeArgs exec -T $ComposeService pg_dump "--username=$DatabaseUser" `
            "--dbname=$DatabaseName" --format=custom --compress=9 --no-owner --no-privileges `
            "--file=$containerPath"
        Assert-LastExitCode -Operation "container pg_dump"
        & docker @composeArgs exec -T $ComposeService pg_restore --list $containerPath | Out-Null
        Assert-LastExitCode -Operation "container pg_restore verification"
        & docker cp "${containerId}:$containerPath" $backupPath
        Assert-LastExitCode -Operation "docker cp"
    } finally {
        & docker @composeArgs exec -T $ComposeService rm -f -- $containerPath 2>$null | Out-Null
    }
}

$backup = Get-Item -LiteralPath $backupPath
if ($backup.Length -le 0) {
    throw "Backup verification failed because the dump is empty."
}
$hash = (Get-FileHash -LiteralPath $backupPath -Algorithm SHA256).Hash.ToLowerInvariant()
$checksumPath = "$backupPath.sha256"
Set-Content -LiteralPath $checksumPath -Value "$hash  $($backup.Name)" -Encoding ASCII

Write-Host "PostgreSQL backup verified."
Write-Host "Dump: $backupPath"
Write-Host "SHA-256: $checksumPath"
Write-Host "Bytes: $($backup.Length)"
