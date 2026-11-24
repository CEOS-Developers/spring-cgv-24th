. (Join-Path $PSScriptRoot 'Common.ps1')
$health = Invoke-RestMethod 'http://127.0.0.1:19091/actuator/health' -TimeoutSec 5
if ($health.status -ne 'UP') { throw 'Start the perf application first.' }
$reservationCount = @(Invoke-PerfSql -Sql 'SELECT COUNT(*) FROM reservation;')
if ($reservationCount[-1].Trim() -ne '0') {
    throw 'Reservations already exist. Refusing to reseed or overwrite data.'
}
Invoke-PerfSql -Sql (Get-Content -LiteralPath (Join-Path $PerfProjectRoot 'docs\perf\seed.sql') -Raw)
Invoke-PerfSql -Sql @'
SELECT 'movie' AS table_name, COUNT(*) AS row_count FROM movie
UNION ALL SELECT 'cinema', COUNT(*) FROM cinema
UNION ALL SELECT 'auditorium', COUNT(*) FROM auditorium
UNION ALL SELECT 'screening', COUNT(*) FROM screening
UNION ALL SELECT 'users', COUNT(*) FROM users
UNION ALL SELECT 'reservation', COUNT(*) FROM reservation;
'@
