param([switch]$SkipBuild)
. (Join-Path $PSScriptRoot 'Common.ps1')

Push-Location $PerfProjectRoot
try {
    New-Item -ItemType Directory -Path $PerfOutputDirectory -Force | Out-Null
    $env:JAVA_HOME = Get-PerfJavaHome
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"

    if (-not (Test-Path -LiteralPath $PerfEnvFile)) {
        function New-PerfSecret([int]$Length, [switch]$Base64) {
            $bytes = [byte[]]::new($Length)
            $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
            try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
            if ($Base64) { return [Convert]::ToBase64String($bytes) }
            return -join ($bytes | ForEach-Object { $_.ToString('x2') })
        }
        @(
            "PERF_DB_PASSWORD=$(New-PerfSecret 24)"
            "PERF_DB_ROOT_PASSWORD=$(New-PerfSecret 24)"
            "PERF_JWT_SECRET=$(New-PerfSecret 48 -Base64)"
        ) | Set-Content -LiteralPath $PerfEnvFile -Encoding UTF8
        Write-Output 'Created .env.perf with random local credentials (ignored by Git).'
    }

    Invoke-PerfCompose -DockerArguments @('up', '-d', '--wait', '--wait-timeout', '180')
    $existing = Get-PerfAppProcess
    if ($existing) {
        Write-Output "Perf application already running (PID $($existing.ProcessId)). Stop it before rebuilding."
        return
    }
    foreach ($port in @(18080, 19091)) {
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $port)
        try { $listener.Start() }
        catch { throw "Port $port is already in use by an untracked process. Stop that process before starting perf." }
        finally { $listener.Stop() }
    }
    if (-not $SkipBuild) {
        & .\gradlew.bat --no-daemon bootJar
        if ($LASTEXITCODE -ne 0) { throw 'Gradle bootJar failed.' }
    }
    $jars = @(Get-ChildItem -LiteralPath (Join-Path $PerfProjectRoot 'build\libs') -Filter '*.jar' |
        Where-Object { $_.Name -notlike '*-plain.jar' })
    if ($jars.Count -ne 1) { throw 'Expected one bootJar in build/libs.' }
    $javaPath = Join-Path $env:JAVA_HOME 'bin\java.exe'
    $jarPath = $jars[0].FullName
    $process = Start-Process -FilePath $javaPath -ArgumentList @('-jar', "`"$jarPath`"", '--spring.profiles.active=perf') `
        -WorkingDirectory $PerfProjectRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $PerfOutputDirectory 'app.stdout.log') `
        -RedirectStandardError (Join-Path $PerfOutputDirectory 'app.stderr.log')
    $nativeProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $($process.Id)"
    [ordered]@{
        ProcessId = $process.Id
        JavaPath = $javaPath
        JarPath = $jarPath
        CreatedAtTicks = $nativeProcess.CreationDate.ToUniversalTime().Ticks.ToString()
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PerfOutputDirectory 'app.json') -Encoding UTF8

    $deadline = (Get-Date).AddSeconds(120)
    do {
        $process.Refresh()
        if ($process.HasExited) { throw 'Application exited. See build/perf/app.stdout.log and app.stderr.log.' }
        try {
            $health = Invoke-RestMethod 'http://127.0.0.1:19091/actuator/health' -TimeoutSec 3
            if ($health.status -eq 'UP') {
                Write-Output 'Perf application ready: http://127.0.0.1:18080 (management: 19091).'
                return
            }
        } catch { }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw 'Application health check timed out. See build/perf/app.stdout.log.'
} finally {
    Pop-Location
}
