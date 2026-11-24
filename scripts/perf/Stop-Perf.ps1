. (Join-Path $PSScriptRoot 'Common.ps1')
$process = Get-PerfAppProcess
if ($process) {
    Stop-Process -Id $process.ProcessId
    Write-Output 'Stopped the perf application.'
}
Invoke-PerfCompose -DockerArguments @('stop')
Write-Output 'Stopped perf containers. The MySQL volume and .env.perf are preserved.'
