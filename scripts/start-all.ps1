<#
.SYNOPSIS
  Starts everything FreshFlow needs for a live demonstration: Kafka, then the four services, each
  in its own PowerShell window, with the LLM configured for insight-service. Windows PowerShell 5.1.

.DESCRIPTION
  One command instead of the six-terminal procedure in README.md "5. Running it: step by step". It
    1. checks that Java 21 and Docker are available;
    2. starts the single-node Kafka from docker-compose.yml and waits until it is healthy
       (-Fresh first wipes Kafka's data and insight-service's Kafka Streams state, so the
       demonstration starts from nothing: no yesterday's batches in the risk view);
    3. builds the jars with the Maven wrapper when any is missing (or always, with -Build);
    4. opens one window per service, in the order inventory, pricing, sales, insight, each
       writing its console to logs\<service>.log (scripts\demo.ps1 reads those logs to find
       the agent's trace and the plan pricing made from it);
    5. waits until all four ports answer and prints what to do next.

  The API key is never written anywhere: insight-service reads LLM_API_KEY from this window's
  environment, which the service windows inherit. The model name is set here (-Model), so the
  user-level LLM_MODEL variable does not have to change for the demonstration.

.PARAMETER Fresh
  docker compose down -v, delete %TEMP%\kafka-streams, then start. Use it before a rehearsal or
  the presentation so that nothing from an earlier run is in Kafka or the state stores.

.PARAMETER Build
  Always run .\mvnw.cmd clean install -DskipTests first (about one minute). Without it the
  build runs only when a jar is missing.

.PARAMETER Model
  The Gemini model name for FR14 and FR15. Default gemini-3.5-flash-lite, the model of the
  5 October 2026 live run.

.PARAMETER NoAgent
  Start insight-service with no model at all (FR12 and FR13 only; FR14 logs each trigger as not
  deliberated, FR15 answers 503 AGENT_NOT_CONFIGURED). Useful when there is no key or no network.

.EXAMPLE
  .\scripts\start-all.ps1 -Fresh
  .\scripts\demo.ps1
  .\scripts\stop-all.ps1
#>
[CmdletBinding()]
param(
    [switch]$Fresh,
    [switch]$Build,
    [string]$Model = "gemini-3.5-flash-lite",
    [switch]$NoAgent
)

# "Continue", not "Stop": under "Stop", Windows PowerShell 5.1 turns anything a native command
# writes to stderr (java -version, docker compose progress) into a terminating error. Every
# failure that matters is checked explicitly below and thrown with a message that says what to do.
$ErrorActionPreference = "Continue"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root
$logs = Join-Path $root "logs"
if (-not (Test-Path $logs)) { New-Item -ItemType Directory -Path $logs | Out-Null }

function Step([string]$title) {
    Write-Host ""
    Write-Host ("=" * 78) -ForegroundColor DarkGray
    Write-Host $title -ForegroundColor Cyan
    Write-Host ("=" * 78) -ForegroundColor DarkGray
}

function Port-Open([int]$port) {
    try {
        $c = New-Object Net.Sockets.TcpClient
        $c.Connect("localhost", $port)
        $c.Close()
        return $true
    } catch { return $false }
}

# ------------------------------------------------------------------------------ 1. prerequisites
Step "1. Prerequisites"
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "java was not found on PATH. Install JDK 21 and open a new PowerShell window."
}
# java prints its version on stderr; let cmd.exe merge the streams so PowerShell sees plain text.
$javaVersion = (& cmd /c "java -version 2>&1" | Select-Object -First 1)
Write-Host "  java   : $javaVersion"
if ($javaVersion -notmatch '"21\.') { Write-Host "  WARNING: JDK 21 is required (README, Prerequisites)." -ForegroundColor Yellow }
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "docker was not found on PATH. Install Docker Desktop and open a new PowerShell window."
}
$dockerVersion = (& cmd /c "docker version --format {{.Server.Version}} 2>nul")
if (-not $dockerVersion) {
    throw "Docker Desktop is not running. Start it, wait until it says 'Engine running', then run this script again."
}
Write-Host "  docker : server $dockerVersion"

# Is FreshFlow already running? (java processes started from one of the four service jars)
$serviceJars = @("inventory-service", "pricing-service", "sales-service", "insight-service") | ForEach-Object { "$_-0.1.0-SNAPSHOT.jar" }
$running = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
    $cmd = $_.CommandLine
    $cmd -and ($serviceJars | Where-Object { $cmd -like "*$_*" })
})
$portsBusy = @(8081, 8082, 8083, 8084 | Where-Object { Port-Open $_ })

if ($running.Count -gt 0 -and -not $Fresh) {
    Write-Host ""
    Write-Host "  FreshFlow is already running ($($running.Count) service process(es); ports $($portsBusy -join ', ') answering)." -ForegroundColor Green
    Write-Host "  There is nothing to start. Next step:" -ForegroundColor Green
    Write-Host ""
    Write-Host "      .\scripts\demo.ps1" -ForegroundColor Green
    Write-Host ""
    Write-Host "  To restart from scratch instead: .\scripts\start-all.ps1 -Fresh  (it stops the running services itself)."
    return
}
if ($running.Count -gt 0 -and $Fresh) {
    Write-Host "  -Fresh: FreshFlow is running; stopping the $($running.Count) service process(es) first"
    & (Join-Path $root "scripts\stop-all.ps1") | ForEach-Object { Write-Host "  $_" }
    Start-Sleep -Seconds 3
    $portsBusy = @(8081, 8082, 8083, 8084 | Where-Object { Port-Open $_ })
}
if ($portsBusy.Count -gt 0) {
    throw "Port(s) $($portsBusy -join ', ') are in use by something that is not a FreshFlow service jar (an IntelliJ run, or another program). Stop it, then run this script again."
}

# ------------------------------------------------------------------------------------- 2. Kafka
Step "2. Kafka (docker compose)"
if ($Fresh) {
    Write-Host "  -Fresh: wiping Kafka data and insight-service's Kafka Streams state"
    & cmd /c "docker compose down -v 2>&1" | Out-Null
    $stateDir = Join-Path $env:TEMP "kafka-streams"
    if (Test-Path $stateDir) { Remove-Item -Recurse -Force $stateDir -ErrorAction SilentlyContinue }
}
& cmd /c "docker compose up -d 2>&1" | Out-Null
$deadline = (Get-Date).AddSeconds(150)
do {
    Start-Sleep -Seconds 5
    $health = (& cmd /c "docker inspect --format {{.State.Health.Status}} freshflow-kafka 2>nul")
    Write-Host "  kafka  : $health"
} while ($health -ne "healthy" -and (Get-Date) -lt $deadline)
if ($health -ne "healthy") { throw "Kafka did not become healthy in 150 s. Run 'docker compose ps' and 'docker compose logs kafka'." }

# -------------------------------------------------------------------------------------- 3. jars
Step "3. Jars"
$services = @("inventory-service", "pricing-service", "sales-service", "insight-service")
$missing = @($services | Where-Object { -not (Test-Path (Join-Path $root "$_\target\$_-0.1.0-SNAPSHOT.jar")) })
if ($Build -or $missing.Count -gt 0 -or -not (Test-Path (Join-Path $root "pos-simulator\target\classes"))) {
    Write-Host "  building: .\mvnw.cmd clean install -DskipTests (about one minute; run .\mvnw.cmd test for the suite)"
    & .\mvnw.cmd -q clean install -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "The build failed (exit $LASTEXITCODE). Run .\mvnw.cmd clean install without -q to see why." }
    Write-Host "  build  : BUILD SUCCESS"
} else {
    Write-Host "  jars   : present (use -Build to rebuild after a code change)"
}

# --------------------------------------------------------------------------------------- 4. LLM
Step "4. LLM configuration for insight-service"
if ($NoAgent) {
    $env:LLM_API_KEY = ""
    $env:LLM_MODEL = ""
    Write-Host "  -NoAgent: FR14 and FR15 are not configured in this run (FR12 and FR13 work)."
} elseif (-not $env:LLM_API_KEY) {
    Write-Host "  WARNING: LLM_API_KEY is not set in this window. insight-service will start without an agent." -ForegroundColor Yellow
    Write-Host "           Set it with  `$env:LLM_API_KEY = 'your key'  and run this script again." -ForegroundColor Yellow
} else {
    $env:LLM_MODEL = $Model
    Write-Host "  model  : $Model (key present in the environment; it is not written anywhere)"
}

# ----------------------------------------------------------------------------------- 5. services
Step "5. Starting the four services, one window each"
$ports = @{ "inventory-service" = 8081; "pricing-service" = 8083; "sales-service" = 8082; "insight-service" = 8084 }
$pids = @{}
foreach ($svc in $services) {
    $jar = Join-Path $root "$svc\target\$svc-0.1.0-SNAPSHOT.jar"
    $log = Join-Path $logs "$svc.log"
    if (Test-Path $log) { Remove-Item $log -Force }
    $title = "FreshFlow $svc ($($ports[$svc]))"
    # The console is echoed to the window and written to the log as UTF-8 (Tee-Object in Windows
    # PowerShell 5.1 would write UTF-16, which grep and most editors misread).
    $command = "`$host.UI.RawUI.WindowTitle = '$title'; Set-Location '$root'; " +
               "Write-Host 'Starting $svc; log: $log' -ForegroundColor Cyan; " +
               "`$w = New-Object IO.StreamWriter('$log', `$false, (New-Object Text.UTF8Encoding(`$false))); `$w.AutoFlush = `$true; " +
               "try { & java -jar '$jar' 2>&1 | ForEach-Object { `$line = [string]`$_; `$line; `$w.WriteLine(`$line) } } finally { `$w.Close() }"
    $p = Start-Process -FilePath "powershell.exe" -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $command) -PassThru
    $pids[$svc] = $p.Id
    Write-Host ("  {0,-18} window pid {1,-6} port {2}" -f $svc, $p.Id, $ports[$svc])
    Start-Sleep -Seconds 3
}
$pids | ConvertTo-Json | Out-File (Join-Path $logs "service-pids.json") -Encoding utf8

$deadline = (Get-Date).AddSeconds(180)
do {
    Start-Sleep -Seconds 5
    $up = @($services | Where-Object { Port-Open $ports[$_] })
    Write-Host ("  {0:HH:mm:ss}  up: {1}" -f (Get-Date), (($up | Sort-Object) -join ", "))
} while ($up.Count -lt 4 -and (Get-Date) -lt $deadline)

Step "Ready"
if ($up.Count -lt 4) {
    $down = @($services | Where-Object { $up -notcontains $_ })
    Write-Host "  Not answering yet: $($down -join ', '). Look at that service's window or logs\<service>.log." -ForegroundColor Yellow
} else {
    Write-Host "  inventory  http://localhost:8081/api/batches?sku=SKU-YOG-1KG"
    Write-Host "  sales      http://localhost:8082/api/transactions   (POST)"
    Write-Host "  pricing    http://localhost:8083/api/prices/SKU-YOG-1KG"
    Write-Host "  insight    http://localhost:8084/api/insight/risks"
    if (-not $NoAgent -and $env:LLM_API_KEY) {
        $cfg = Select-String -Path (Join-Path $logs "insight-service.log") -Pattern "FR14: LLM provider" | Select-Object -Last 1
        if ($cfg) { Write-Host ("  agent      " + ($cfg.Line -replace '^.*FR14: ', 'FR14: ')) }
    }
    Write-Host ""
    Write-Host "  Next:  .\scripts\demo.ps1          (the whole loop, about four minutes)" -ForegroundColor Green
    Write-Host "  Stop:  .\scripts\stop-all.ps1      (-Kafka also stops the broker)" -ForegroundColor Green
    Write-Host ""
    Write-Host "  Leave the four service windows open. Do not run start-all.ps1 again before the demo;" -ForegroundColor Yellow
    Write-Host "  if you do, it will simply tell you that FreshFlow is already running." -ForegroundColor Yellow
}
