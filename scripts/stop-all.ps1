<#
.SYNOPSIS
  Stops the four FreshFlow services started by scripts\start-all.ps1 (and any other FreshFlow
  service jar running on this machine). Windows PowerShell 5.1.

.DESCRIPTION
  Finds every java process whose command line names one of the FreshFlow service jars and stops
  it, then closes the PowerShell windows start-all.ps1 opened. Kafka is left running unless
  -Kafka is given, because Docker Desktop is slow to start and a stopped broker keeps its data
  anyway (start-all.ps1 -Fresh wipes it when you want a clean start).

.PARAMETER Kafka
  Also run docker compose down (keeps the data; add -Wipe to discard it).

.PARAMETER Wipe
  With -Kafka: docker compose down -v, and delete %TEMP%\kafka-streams.

.EXAMPLE
  .\scripts\stop-all.ps1
  .\scripts\stop-all.ps1 -Kafka -Wipe
#>
[CmdletBinding()]
param(
    [switch]$Kafka,
    [switch]$Wipe
)

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

$jars = @("inventory-service", "pricing-service", "sales-service", "insight-service") | ForEach-Object { "$_-0.1.0-SNAPSHOT.jar" }
$stopped = 0
Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | ForEach-Object {
    $cmd = $_.CommandLine
    foreach ($jar in $jars) {
        if ($cmd -and $cmd -like "*$jar*") {
            Write-Host ("  stopping {0,-32} pid {1}" -f $jar, $_.ProcessId)
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
            $stopped++
            break
        }
    }
}
if ($stopped -eq 0) { Write-Host "  no FreshFlow service was running" }

$pidFile = Join-Path $root "logs\service-pids.json"
if (Test-Path $pidFile) {
    $pids = Get-Content $pidFile | ConvertFrom-Json
    foreach ($svc in $pids.PSObject.Properties.Name) {
        Stop-Process -Id $pids.$svc -Force -ErrorAction SilentlyContinue
    }
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

if ($Kafka) {
    if ($Wipe) {
        Write-Host "  docker compose down -v (data discarded)"
        & cmd /c "docker compose down -v 2>&1" | Out-Null
        $stateDir = Join-Path $env:TEMP "kafka-streams"
        if (Test-Path $stateDir) { Remove-Item -Recurse -Force $stateDir -ErrorAction SilentlyContinue }
    } else {
        Write-Host "  docker compose down (data kept)"
        & cmd /c "docker compose down 2>&1" | Out-Null
    }
} else {
    Write-Host "  Kafka left running (use -Kafka to stop it, -Kafka -Wipe to also discard its data)"
}
Write-Host "  done. To start again: .\scripts\start-all.ps1 -Fresh   (then .\scripts\demo.ps1)"
