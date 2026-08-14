#Requires -Version 5.1

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string] $BackupPath,

    [Parameter()]
    [ValidateSet("Docker", "Direct")]
    [string] $Mode = "Docker",

    [Parameter()]
    [ValidatePattern("^[A-Za-z0-9][A-Za-z0-9_.-]*$")]
    [string] $ComposeService = "postgres",

    [Parameter()]
    [ValidatePattern("^[A-Za-z0-9][A-Za-z0-9_.-]*$")]
    [string] $DatabaseName,

    [Parameter()]
    [ValidatePattern("^[A-Za-z0-9][A-Za-z0-9_.-]*$")]
    [string] $DatabaseUser,

    [Parameter()]
    [switch] $ListOnly,

    [Parameter()]
    [switch] $Clean,

    [Parameter()]
    [switch] $AllowMissingChecksum,

    [Parameter()]
    [switch] $ConfirmRestore
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$resolvedBackup = (Resolve-Path -LiteralPath $BackupPath -ErrorAction Stop).Path
if (-not (Test-Path -LiteralPath $resolvedBackup -PathType Leaf)) {
    throw "BackupPath must identify one regular dump file."
}
$backup = Get-Item -LiteralPath $resolvedBackup
if ($backup.Length -le 0) {
    throw "The selected backup is empty."
}

$checksumPath = "$resolvedBackup.sha256"
if (Test-Path -LiteralPath $checksumPath -PathType Leaf) {
    $checksumText = (Get-Content -LiteralPath $checksumPath -Raw).Trim()
    $expectedHash = ($checksumText -split "\s+")[0]
    if ($expectedHash -notmatch "^[A-Fa-f0-9]{64}$") {
        throw "The checksum sidecar is malformed: $checksumPath"
    }
    $actualHash = (Get-FileHash -LiteralPath $resolvedBackup -Algorithm SHA256).Hash
    if (-not $actualHash.Equals($expectedHash, [StringComparison]::OrdinalIgnoreCase)) {
        throw "SHA-256 verification failed. The dump will not be restored."
    }
} elseif (-not $AllowMissingChecksum) {
    throw "Checksum sidecar not found. Use -AllowMissingChecksum only for a separately verified dump."
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repoRoot "docker-compose.yml"
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

if (-not $ListOnly -and -not $ConfirmRestore) {
    throw "Restore changes database '$DatabaseName'. Inspect with -ListOnly, then rerun with -ConfirmRestore."
}

function Assert-LastExitCode {
    param([Parameter(Mandatory)][string] $Operation)
    if ($LASTEXITCODE -ne 0) {
        throw "$Operation failed with exit code $LASTEXITCODE."
    }
}

function Get-RestoreArguments {
    param([Parameter(Mandatory)][string] $DumpPath)
    $arguments = @(
        "--username=$DatabaseUser",
        "--dbname=$DatabaseName",
        "--no-owner",
        "--no-privileges",
        "--single-transaction"
    )
    if ($Clean) {
        $arguments += @("--clean", "--if-exists")
    }
    $arguments += $DumpPath
    return $arguments
}

if ($Mode -eq "Direct") {
    if (-not $env:PGHOST -or -not $env:PGUSER) {
        throw "Direct mode requires PGHOST and PGUSER. Set PGPASSWORD, PGPORT and PGSSLMODE when required."
    }
    $pgRestore = Get-Command pg_restore -ErrorAction SilentlyContinue
    if (-not $pgRestore) {
        throw "Direct mode requires pg_restore on PATH."
    }
    if ($ListOnly) {
        & $pgRestore.Source --list $resolvedBackup
        Assert-LastExitCode -Operation "pg_restore verification"
        Write-Host "Dump contents are readable; no database changes were made."
        exit 0
    }

    Write-Host "Restoring into PostgreSQL database '$DatabaseName' in Direct mode."
    $restoreArgs = @(Get-RestoreArguments -DumpPath $resolvedBackup)
    & $pgRestore.Source @restoreArgs
    Assert-LastExitCode -Operation "pg_restore"
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

    $containerPath = "/tmp/gameio-restore-$([Guid]::NewGuid().ToString('N')).dump"
    try {
        & docker cp $resolvedBackup "${containerId}:$containerPath"
        Assert-LastExitCode -Operation "docker cp"
        if ($ListOnly) {
            & docker @composeArgs exec -T $ComposeService pg_restore --list $containerPath
            Assert-LastExitCode -Operation "container pg_restore verification"
            Write-Host "Dump contents are readable; no database changes were made."
            exit 0
        }

        Write-Host "Restoring into PostgreSQL database '$DatabaseName' in Docker mode."
        $restoreArgs = @(Get-RestoreArguments -DumpPath $containerPath)
        & docker @composeArgs exec -T $ComposeService pg_restore @restoreArgs
        Assert-LastExitCode -Operation "container pg_restore"
    } finally {
        & docker @composeArgs exec -T $ComposeService rm -f -- $containerPath 2>$null | Out-Null
    }
}

Write-Host "Restore completed successfully. Run backend health and application smoke checks before reopening traffic."
