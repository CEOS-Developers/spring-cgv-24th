param(
    [ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$Label = 'baseline',
    [ValidateRange(1, 10000)][int]$Requests = 30,
    [ValidateRange(0, 1000)][int]$Warmup = 10
)
. (Join-Path $PSScriptRoot 'Common.ps1')
Add-Type -AssemblyName System.Net.Http
$resultDirectory = Join-Path $PerfOutputDirectory ("{0}-{1}" -f $Label, (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $resultDirectory | Out-Null
$client = [Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(60)

function Get-PreparedSqlCount {
    $metric = Invoke-RestMethod 'http://127.0.0.1:19091/actuator/metrics/cgv.perf.sql.prepared' -TimeoutSec 5
    return [long]$metric.measurements[0].value
}

function Invoke-MeasuredGet([string]$Path) {
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $response = $client.GetAsync("http://127.0.0.1:18080$Path").GetAwaiter().GetResult()
    try {
        $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
        $timer.Stop()
        if ([int]$response.StatusCode -ne 200) { throw "HTTP $([int]$response.StatusCode) for $Path" }
        $sha = [Security.Cryptography.SHA256]::Create()
        try { $hash = [BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-', '').ToLowerInvariant() }
        finally { $sha.Dispose() }
        return [pscustomobject]@{Milliseconds=$timer.Elapsed.TotalMilliseconds; Bytes=$bytes.Length; Sha256=$hash}
    } finally { $response.Dispose() }
}

try {
    $dataset = @(Invoke-PerfSql -Sql 'SELECT COUNT(*) FROM reservation;')
    if ($dataset[-1].Trim() -ne '100000') { throw 'Expected the seeded 100,000-reservation dataset. Run Seed-Perf.ps1.' }
    $summary = @()
    $raw = @()
    foreach ($target in @(
        @{Name='movies'; Path='/api/movies'},
        @{Name='screenings'; Path='/api/cinemas/1/screenings'}
    )) {
        $expectedHash = $null
        for ($i=0; $i -lt $Warmup; $i++) { $null = Invoke-MeasuredGet $target.Path }
        $before = Get-PreparedSqlCount
        $samples = @()
        for ($i=1; $i -le $Requests; $i++) {
            $sample = Invoke-MeasuredGet $target.Path
            if ($expectedHash -and $sample.Sha256 -ne $expectedHash) { throw "Response changed during $($target.Name) measurement." }
            $expectedHash = $sample.Sha256
            $samples += $sample.Milliseconds
            $raw += [pscustomobject]@{Target=$target.Name; Request=$i; Milliseconds=$sample.Milliseconds; Bytes=$sample.Bytes; Sha256=$sample.Sha256}
        }
        $after = Get-PreparedSqlCount
        $sorted = @($samples | Sort-Object)
        $summary += [pscustomobject][ordered]@{
            Target=$target.Name; Path=$target.Path; Requests=$Requests; Warmup=$Warmup
            AverageMs=[Math]::Round(($samples | Measure-Object -Average).Average,3)
            MedianMs=[Math]::Round(($sorted[[int][Math]::Floor(($Requests-1)/2)] + $sorted[[int][Math]::Floor($Requests/2)])/2,3)
            P95Ms=[Math]::Round($sorted[[int][Math]::Ceiling($Requests*0.95)-1],3)
            MinMs=[Math]::Round($sorted[0],3); MaxMs=[Math]::Round($sorted[-1],3)
            PreparedSqlTotal=$after-$before; PreparedSqlPerRequest=($after-$before)/$Requests
            ResponseBytes=$sample.Bytes; ResponseSha256=$expectedHash
        }
    }
    $summary | Export-Csv -LiteralPath (Join-Path $resultDirectory 'http-summary.csv') -NoTypeInformation -Encoding UTF8
    $raw | Export-Csv -LiteralPath (Join-Path $resultDirectory 'http-samples.csv') -NoTypeInformation -Encoding UTF8
    Invoke-PerfSql -Sql @'
SELECT VERSION() AS mysql_version, DATABASE() AS database_name;
SHOW INDEX FROM reservation;
EXPLAIN SELECT r.id, r.cancelled_at, r.confirmed_at, r.reserved_at, r.screening_id, r.status, r.user_id
FROM reservation r WHERE r.user_id = 42 ORDER BY r.reserved_at DESC;
EXPLAIN ANALYZE SELECT r.id, r.cancelled_at, r.confirmed_at, r.reserved_at, r.screening_id, r.status, r.user_id
FROM reservation r WHERE r.user_id = 42 ORDER BY r.reserved_at DESC;
SELECT 'user_42_reservations' AS metric, COUNT(*) AS value FROM reservation WHERE user_id=42;
'@ | Set-Content -LiteralPath (Join-Path $resultDirectory 'reservation-plan.txt') -Encoding UTF8
    [ordered]@{
        RecordedAt=(Get-Date).ToString('o'); Label=$Label
        GitCommit=(& git -C $PerfProjectRoot rev-parse HEAD)
        GitStatus=@(& git -C $PerfProjectRoot status --short)
        JavaVersion=@(& (Join-Path (Get-PerfJavaHome) 'bin\java.exe') -version 2>&1 | ForEach-Object { "$_" })
        Requests=$Requests; Warmup=$Warmup; Concurrency=1
        Note='Client wall time includes full response body. SQL preparation counts are process-wide; keep other traffic idle. p95 uses nearest rank. Warm cache hits require a separate cold-miss measurement after cache implementation.'
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $resultDirectory 'environment.json') -Encoding UTF8
    $summary | Format-Table Target, AverageMs, MedianMs, P95Ms, PreparedSqlPerRequest -AutoSize
    Write-Output "Saved measurements: $resultDirectory"
} finally { $client.Dispose() }
