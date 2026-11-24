$ErrorActionPreference = 'Stop'
$PerfProjectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$PerfEnvFile = Join-Path $PerfProjectRoot '.env.perf'
$PerfOutputDirectory = Join-Path $PerfProjectRoot 'build\perf'
$PerfComposeFile = Join-Path $PerfProjectRoot 'compose.perf.yaml'

function Get-PerfJavaHome {
    $candidates = @($env:JAVA_HOME)
    $jdkRoot = Join-Path $env:USERPROFILE '.jdks'
    if (Test-Path -LiteralPath $jdkRoot) {
        $candidates += @(Get-ChildItem -LiteralPath $jdkRoot -Directory |
            Where-Object { $_.Name -match '21' } |
            Sort-Object Name -Descending | ForEach-Object { $_.FullName })
    }
    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        $java = Join-Path $candidate 'bin\java.exe'
        $release = Join-Path $candidate 'release'
        if ((Test-Path -LiteralPath $java) -and (Test-Path -LiteralPath $release)) {
            if ((Get-Content -LiteralPath $release -Raw) -match 'JAVA_VERSION="21[.\"]') {
                return $candidate
            }
        }
    }
    throw 'JDK 21 was not found. Set JAVA_HOME to an installed JDK 21.'
}

function Assert-PerfLocalDocker {
    if (-not (Test-Path -LiteralPath $PerfEnvFile)) {
        throw 'Missing .env.perf. Run scripts/perf/Start-Perf.ps1 first.'
    }
    # Refuse remote Docker contexts: this experiment belongs on the local computer.
    $endpoint = & docker context inspect --format '{{.Endpoints.docker.Host}}'
    if ($LASTEXITCODE -ne 0 -or $endpoint -notmatch '^npipe://') {
        throw 'Select a local Docker Desktop context before running this experiment.'
    }
}

function Invoke-PerfCompose {
    param([Parameter(Mandatory = $true)][string[]]$DockerArguments)
    Assert-PerfLocalDocker
    & docker compose --env-file $PerfEnvFile -f $PerfComposeFile @DockerArguments
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed (exit $LASTEXITCODE)." }
}

function Invoke-PerfSql {
    param([Parameter(Mandatory = $true)][string]$Sql)
    Assert-PerfLocalDocker
    $OutputEncoding = [Text.UTF8Encoding]::new($false)
    $Sql | & docker compose --env-file $PerfEnvFile -f $PerfComposeFile exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --user="$MYSQL_USER" --database=cgv_perf --default-character-set=utf8mb4 --batch --raw'
    if ($LASTEXITCODE -ne 0) { throw "MySQL command failed (exit $LASTEXITCODE)." }
}

function Get-PerfAppProcess {
    $stateFile = Join-Path $PerfOutputDirectory 'app.json'
    if (-not (Test-Path -LiteralPath $stateFile)) { return $null }
    $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $([int]$state.ProcessId)"
    if ($process -and $process.ExecutablePath -eq $state.JavaPath -and
        $process.CommandLine -like '*--spring.profiles.active=perf*' -and
        $process.CommandLine.Contains([string]$state.JarPath) -and
        $process.CreationDate.ToUniversalTime().Ticks -eq [long]$state.CreatedAtTicks) {
        return $process
    }
    return $null
}
