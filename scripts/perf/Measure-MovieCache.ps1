param(
    [ValidateSet('Disabled', 'Miss', 'Hit', 'Mixed')][string]$Mode = 'Hit',
    [ValidateRange(1, 10000)][int]$Requests = 30,
    [ValidateRange(0, 1000)][int]$Warmup = 10
)
. (Join-Path $PSScriptRoot 'Common.ps1')
Add-Type -AssemblyName System.Net.Http
$cacheKey = 'cgv:v1:movies:list::all'
$directory = Join-Path $PerfOutputDirectory ("cache-{0}-{1}" -f $Mode.ToLowerInvariant(), (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $directory | Out-Null
$client = [Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(30)

function Get-MetricValue([string]$Name) {
    $metric = Invoke-RestMethod "http://127.0.0.1:19091/actuator/metrics/$Name" -TimeoutSec 5
    return [long]$metric.measurements[0].value
}

function Remove-MovieCache {
    # Only the known list key in the dedicated perf Redis is removed.
    $null = Invoke-PerfCompose -DockerArguments @('exec', '-T', 'redis', 'redis-cli', 'DEL', $cacheKey)
}

function Invoke-MovieGet {
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $response = $client.GetAsync('http://127.0.0.1:18080/api/movies').GetAwaiter().GetResult()
    try {
        $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
        $timer.Stop()
        if ([int]$response.StatusCode -ne 200) { throw "Movie API returned $([int]$response.StatusCode)." }
        $sha = [Security.Cryptography.SHA256]::Create()
        try { $hash = [BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-', '').ToLowerInvariant() }
        finally { $sha.Dispose() }
        return [pscustomobject]@{
            Milliseconds=$timer.Elapsed.TotalMilliseconds; Bytes=$bytes.Length; Sha256=$hash
            Body=[Text.Encoding]::UTF8.GetString($bytes)
        }
    } finally { $response.Dispose() }
}

try {
    for ($i=0; $i -lt $Warmup; $i++) {
        if ($Mode -eq 'Miss') { Remove-MovieCache }
        $null = Invoke-MovieGet
    }
    if ($Mode -eq 'Hit') { $null = Invoke-MovieGet }
    if ($Mode -eq 'Mixed') { Remove-MovieCache }
    $sqlBefore = Get-MetricValue 'cgv.perf.sql.prepared'
    if ($Mode -ne 'Disabled') {
        $hitsBefore = Get-MetricValue 'cgv.cache.hits'
        $missesBefore = Get-MetricValue 'cgv.cache.misses'
    }
    $samples = @()
    $hash = $null
    for ($i=1; $i -le $Requests; $i++) {
        if ($Mode -eq 'Miss') { Remove-MovieCache }
        $sample = Invoke-MovieGet
        if ($hash -and $sample.Sha256 -ne $hash) { throw 'Movie response changed during measurement.' }
        $hash = $sample.Sha256
        $samples += [pscustomobject]@{
            Request=$i; Milliseconds=$sample.Milliseconds; Bytes=$sample.Bytes; Sha256=$hash
        }
    }
    $sqlTotal = (Get-MetricValue 'cgv.perf.sql.prepared') - $sqlBefore
    $hits = 0L
    $misses = 0L
    if ($Mode -ne 'Disabled') {
        $hits = (Get-MetricValue 'cgv.cache.hits') - $hitsBefore
        $misses = (Get-MetricValue 'cgv.cache.misses') - $missesBefore
    }
    $expectedSql = switch ($Mode) { Disabled {$Requests} Miss {$Requests} Hit {0} Mixed {1} }
    if ($sqlTotal -ne $expectedSql) { throw "Expected $expectedSql SQL preparations, got $sqlTotal. Check cache mode or other traffic." }
    if ($Mode -ne 'Disabled' -and $hits + $misses -ne $Requests) { throw 'Unexpected hit/miss count. Check other traffic.' }
    $ordered = @($samples.Milliseconds | Sort-Object)
    $summary = [pscustomobject][ordered]@{
        Mode=$Mode; Requests=$Requests; Warmup=$Warmup; Concurrency=1
        AverageMs=[Math]::Round(($samples.Milliseconds | Measure-Object -Average).Average,3)
        MedianMs=[Math]::Round(($ordered[[int][Math]::Floor(($Requests-1)/2)] + $ordered[[int][Math]::Floor($Requests/2)])/2,3)
        P95Ms=[Math]::Round($ordered[[int][Math]::Ceiling($Requests*0.95)-1],3)
        PreparedSqlTotal=$sqlTotal; PreparedSqlPerRequest=$sqlTotal/$Requests
        Hits=$hits; Misses=$misses
        HitRatePercent=$(if ($hits+$misses -gt 0) { [Math]::Round(100*$hits/($hits+$misses),2) } else { $null })
        ResponseBytes=$sample.Bytes; ResponseSha256=$hash
    }
    $samples | Export-Csv -LiteralPath (Join-Path $directory 'samples.csv') -NoTypeInformation -Encoding UTF8
    $summary | Export-Csv -LiteralPath (Join-Path $directory 'summary.csv') -NoTypeInformation -Encoding UTF8
    [IO.File]::WriteAllText((Join-Path $directory 'response.json'), $sample.Body, [Text.UTF8Encoding]::new($false))
    if ($Mode -ne 'Disabled') {
        Invoke-PerfCompose -DockerArguments @('exec','-T','redis','redis-cli','PTTL',$cacheKey) |
            Set-Content -LiteralPath (Join-Path $directory 'redis-pttl-ms.txt') -Encoding UTF8
    }
    [ordered]@{
        RecordedAt=(Get-Date).ToString('o'); Mode=$Mode; Key=$cacheKey
        GitCommit=(& git -C $PerfProjectRoot rev-parse HEAD)
        JarSha256=(Get-FileHash -LiteralPath (Join-Path $PerfProjectRoot 'build\libs\cgv-0.0.1-SNAPSHOT.jar') -Algorithm SHA256).Hash.ToLowerInvariant()
        Note='Sequential localhost HTTP requests; timer includes full response body. DEL for Miss runs outside the timer. Stats are local to this application. Other traffic must be idle.'
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory 'environment.json') -Encoding UTF8
    $summary | Format-List
    Write-Output "Saved movie cache measurements: $directory"
} finally { $client.Dispose() }
