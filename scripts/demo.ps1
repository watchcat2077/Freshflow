<#
.SYNOPSIS
  FreshFlow live demonstration driver (Windows PowerShell 5.1 or later).

.DESCRIPTION
  Runs the closed loop end to end against the four services and prints every read model as it
  changes. Assumes Kafka is up and the four services are started, most simply with
  .\scripts\start-all.ps1 (or by hand, README.md "Quick start"). The Markdown Decision Agent
  (FR14) and the briefing (FR15) need insight-service to have been started with LLM_API_KEY and
  LLM_MODEL set; the rest of the demonstration works without them.

  Steps:
    1. Health check of the four services.
    2. FR8  PUT a second price rule (SKU-STR-250G) and read the seeded one.
    3. FR1  Register one batch per SKU: 400 units expiring in 70 minutes.
    4. FR4  Read the batches back, by id and by SKU.
    5. FR5/FR6a/FR2  Record a sale and show the FEFO deduction.
    6. Drive sales with the POS simulator for -DurationSeconds (default 150 s), so that
       FR12 windows close and FR13 flags the yoghurt batch as at risk.
    7. FR12/FR13  Print velocity and risks.
    8. FR14  Find the agent's trace. When the services were started by start-all.ps1 their
       consoles are in logs\, so the script reads the trace id from logs\insight-service.log,
       fetches GET /api/insight/agent-traces/{id}, and then the plan pricing-service made from
       the recommendation (FR11, from logs\pricing-service.log). Otherwise it prints how to do
       the same by hand from the insight-service window.
    9. FR9/FR11  Propose PERCENT_30 (201) and PERCENT_60 (422) by hand and read both plans.
   10. FR6b  Record a sale inside the markdown window and show markdownApplied = true.
   11. FR15  Ask for the briefing.

.PARAMETER DurationSeconds
  How long the simulator drives sales. 150 seconds is enough for two 60-second windows.

.PARAMETER Rate
  Transactions per minute. 4 keeps the yoghurt velocity well under the rate at which a 400-unit
  batch with an hour left would clear on its own and never be flagged.

.EXAMPLE
  .\scripts\demo.ps1
  .\scripts\demo.ps1 -DurationSeconds 90 -TranscriptPath logs\demo-transcript.txt
#>
[CmdletBinding()]
param(
    [int]$DurationSeconds = 150,
    # 4 per minute keeps the yoghurt velocity well under the ~395 units/hour above which a
    # 400-unit batch with one hour left would clear on its own and never be flagged.
    [int]$Rate = 4,
    # When set, everything printed (including the coloured headings) is also written here.
    [string]$TranscriptPath = "",
    [string]$Inventory = "http://localhost:8081",
    [string]$Sales = "http://localhost:8082",
    [string]$Pricing = "http://localhost:8083",
    [string]$Insight = "http://localhost:8084"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
if ($TranscriptPath) { Start-Transcript -Path $TranscriptPath -Force | Out-Null }

function Step([string]$title) {
    Write-Host ""
    Write-Host ("=" * 78) -ForegroundColor DarkGray
    Write-Host $title -ForegroundColor Cyan
    Write-Host ("=" * 78) -ForegroundColor DarkGray
}

function Show($object) {
    if ($null -eq $object) { Write-Host "(empty)"; return }
    $object | ConvertTo-Json -Depth 6 | Write-Host
}

function Try-Rest {
    param([string]$Method, [string]$Uri, [string]$Body)
    try {
        if ($Body) {
            return Invoke-RestMethod -Method $Method -Uri $Uri -ContentType 'application/json' -Body $Body
        }
        return Invoke-RestMethod -Method $Method -Uri $Uri
    } catch {
        $response = $_.Exception.Response
        $status = if ($response) { [int]$response.StatusCode } else { "no response" }
        $detail = $_.ErrorDetails.Message
        if (-not $detail -and $response) {
            # Windows PowerShell 5.1 does not always populate ErrorDetails for a 4xx; read the body
            # ourselves, because the 422's body (resulting price, margin, floor) is the point.
            try {
                $reader = New-Object IO.StreamReader($response.GetResponseStream())
                $detail = $reader.ReadToEnd()
                $reader.Close()
            } catch { }
        }
        Write-Host "HTTP $status" -ForegroundColor Yellow
        if ($detail) {
            try { $detail | ConvertFrom-Json | ConvertTo-Json -Depth 6 | Write-Host } catch { Write-Host $detail }
        }
        return $null
    }
}

function Wait-Healthy([string]$name, [string]$uri) {
    for ($i = 0; $i -lt 30; $i++) {
        try {
            Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 2 | Out-Null
            Write-Host ("  {0,-18} up   {1}" -f $name, $uri) -ForegroundColor Green
            return
        } catch {
            if ($_.Exception.Response) {
                # A 404 or 400 still proves the service is answering.
                Write-Host ("  {0,-18} up   {1}" -f $name, $uri) -ForegroundColor Green
                return
            }
            Start-Sleep -Seconds 2
        }
    }
    throw "$name is not answering at $uri. The services are not running (a stop-all.ps1 run stops them). Run .\scripts\start-all.ps1, wait for 'Ready', then run this script again. Do not run start-all.ps1 a second time in between."
}

Step "1. Health check"
Wait-Healthy "inventory-service" "$Inventory/api/batches/HEALTH-PROBE"
Wait-Healthy "sales-service"     "$Sales/api/transactions"
Wait-Healthy "pricing-service"   "$Pricing/api/prices/SKU-YOG-1KG"
Wait-Healthy "insight-service"   "$Insight/api/insight/risks"

Step "2. FR8  Price rules and margin floors (pricing-service)"
Write-Host "PUT /api/prices/SKU-STR-250G  (create: 201, replace: 200)"
Show (Try-Rest Put "$Pricing/api/prices/SKU-STR-250G" '{"basePrice":{"amount":6.00,"currency":"AUD"},"unitCost":{"amount":3.00,"currency":"AUD"},"category":"PRODUCE","marginFloorRatio":0.20}')
Write-Host "GET /api/prices/SKU-YOG-1KG  (seeded: base 4.50, cost 2.10, floor 0.15)"
Show (Try-Rest Get "$Pricing/api/prices/SKU-YOG-1KG")

Step "3. FR1  Register goods receipts (inventory-service)"
# An empty JSON array comes back from Invoke-RestMethod as $null, and @($null) has a Count of 1,
# so the null is filtered out before counting.
$earlier = @(Try-Rest Get "$Inventory/api/batches?sku=SKU-YOG-1KG" | Where-Object { $null -ne $_ })
if ($earlier.Count -gt 0) {
    Write-Host "Note: SKU-YOG-1KG already has $($earlier.Count) active batch(es) from an earlier run in this session." -ForegroundColor Yellow
    Write-Host "      FEFO takes each sale from the batch that expires FIRST, so the deduction in step 5 will show on the" -ForegroundColor Yellow
    Write-Host "      oldest batch, not necessarily on the one registered now. For a clean run: .\scripts\start-all.ps1 -Fresh" -ForegroundColor Yellow
}
$expiry = (Get-Date).ToUniversalTime().AddMinutes(70).ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
$yog = Try-Rest Post "$Inventory/api/batches" ('{"sku":"SKU-YOG-1KG","quantity":400,"expiryAt":"' + $expiry + '","unitCost":{"amount":2.10,"currency":"AUD"},"supplierRef":"DEMO-1"}')
Show $yog
$str = Try-Rest Post "$Inventory/api/batches" ('{"sku":"SKU-STR-250G","quantity":120,"expiryAt":"' + $expiry + '","unitCost":{"amount":3.00,"currency":"AUD"},"supplierRef":"DEMO-2"}')
Show $str
Write-Host "A rejected receipt (quantity 0) answers 400:"
Try-Rest Post "$Inventory/api/batches" '{"sku":"SKU-YOG-1KG","quantity":0,"expiryAt":"2027-01-01T00:00:00Z","unitCost":{"amount":2.10,"currency":"AUD"}}' | Out-Null

Step "4. FR4  Stock position and remaining shelf life"
Show (Try-Rest Get "$Inventory/api/batches/$($yog.batchId)")
Show (Try-Rest Get "$Inventory/api/batches?sku=SKU-YOG-1KG")

Step "5. FR5 / FR6a / FR2  One sale at the base price, then the FEFO deduction"
Show (Try-Rest Post "$Sales/api/transactions" '{"tillId":"TILL-02","lineItems":[{"sku":"SKU-YOG-1KG","quantity":2}]}')
Start-Sleep -Seconds 2
Write-Host "The active batches of SKU-YOG-1KG after the sale, in FEFO order (earliest expiry first)."
Write-Host "The 2 units came off the FIRST batch in this list; a batch registered a moment ago is only first when nothing older is on the shelf:"
Show (Try-Rest Get "$Inventory/api/batches?sku=SKU-YOG-1KG")

Step "6. Drive sales with the POS simulator for $DurationSeconds s at $Rate/min"
$classpath = "$root\pos-simulator\target\classes;$root\contracts\target\classes"
if (-not (Test-Path "$root\pos-simulator\target\classes")) {
    throw "pos-simulator is not built. Run .\mvnw.cmd clean install first."
}
& java -cp $classpath com.freshflow.possimulator.PosSimulator "--sales-url=$Sales" "--inventory-url=$Inventory" "--rate=$Rate" "--duration=$DurationSeconds" "--skus=SKU-YOG-1KG:0.7,SKU-STR-250G:0.3" "--till=TILL-SIM"

Step "7. FR12 / FR13  Velocity and risk (insight-service, Kafka Streams)"
Show (Try-Rest Get "$Insight/api/insight/velocity/SKU-YOG-1KG")
Show (Try-Rest Get "$Insight/api/insight/risks")

Step "8. FR14  The Markdown Decision Agent, and what pricing did with its recommendation (FR11)"
$insightLog = Join-Path $root "logs\insight-service.log"
$pricingLog = Join-Path $root "logs\pricing-service.log"
$trace = $null
$traceId = $null
if (Test-Path $insightLog) {
    Write-Host "Reading the trace id from $insightLog (the agent answers within about 15 s of the risk event; waiting up to 120 s)"
    $batchIds = @($yog.batchId, $str.batchId) | Where-Object { $_ }
    $deadline = (Get-Date).AddSeconds(120)
    do {
        $hits = Select-String -Path $insightLog -Pattern 'agentTraceId=(TRC-[0-9a-fA-F-]+) batchId=(BAT-[0-9A-F]+)' |
            Where-Object { $batchIds -contains $_.Matches[0].Groups[2].Value }
        $hit = $hits | Select-Object -Last 1
        if ($hit) {
            $traceId = $hit.Matches[0].Groups[1].Value
            $trace = Try-Rest Get "$Insight/api/insight/agent-traces/$traceId"
        }
        if (-not $trace) { Start-Sleep -Seconds 5 }
    } while (-not $trace -and (Get-Date) -lt $deadline)

    if ($trace) {
        Write-Host "GET /api/insight/agent-traces/$traceId  (every tool call in order, then the recommendation):"
        Show $trace
        $unavailable = Select-String -Path $insightLog -Pattern "fr14-agent-unavailable" | Select-Object -Last 1
        if ($unavailable) { Write-Host "Note: at least one trigger was not deliberated because the model refused the call:"; Write-Host $unavailable.Line }
        if (Test-Path $pricingLog) {
            Start-Sleep -Seconds 3
            $planHit = Select-String -Path $pricingLog -Pattern "agentTraceId=$traceId" |
                Where-Object { $_.Line -match 'MDP-[0-9A-F]{8}' } | Select-Object -Last 1
            if ($planHit -and $planHit.Line -match '(MDP-[0-9A-F]{8})') {
                $planId = $Matches[1]
                Write-Host "pricing-service judged that recommendation by the margin floor; GET /api/markdown-plans/$planId :"
                Show (Try-Rest Get "$Pricing/api/markdown-plans/$planId")
            } elseif ($trace.finalRecommendation -eq "NO_MARKDOWN") {
                Write-Host "The agent recommended nothing for this batch, so pricing-service received no proposal (that is a decision too, and it is in the trace)."
            } else {
                Write-Host "pricing-service's log has no plan for this trace yet; look for [fr11-rejected] or an ACTIVE plan in its window."
            }
        }
    } else {
        $notConfigured = Select-String -Path $insightLog -Pattern "fr14-not-configured" | Select-Object -Last 1
        $unavailable = Select-String -Path $insightLog -Pattern "fr14-agent-unavailable" | Select-Object -Last 1
        if ($notConfigured) { Write-Host "No agent is configured (LLM_API_KEY was not set when insight-service started):"; Write-Host $notConfigured.Line }
        elseif ($unavailable) { Write-Host "The model refused the call (a spent daily quota, a timeout); nothing was stored, FR12 and FR13 are unaffected:"; Write-Host $unavailable.Line; Write-Host "Restart insight-service with another model name (start-all.ps1 -Model ...) and run the demo again." }
        else { Write-Host "No trace for this run's batches yet. If the risk list above was empty, keep -Rate at 4 and give it another minute." }
    }
} else {
    Write-Host "If insight-service was started with LLM_API_KEY, its console now shows"
    Write-Host "  [fr14-trace] agentTraceId=TRC-xxxxxxxx batchId=$($yog.batchId) finalRecommendation=..."
    Write-Host "Read it with:  Invoke-RestMethod -Uri $Insight/api/insight/agent-traces/TRC-xxxxxxxx"
    Write-Host "and pricing-service's console shows [fr11-...] for the plan it created from markdown-recommended."
    Write-Host "(Start the services with .\scripts\start-all.ps1 and this step finds both for you.)"
}

Step "9. FR9 / FR11  A human proposal within the floor (201) and one that breaches it (422)"
$window = '"effectiveFrom":"' + (Get-Date).ToUniversalTime().AddMinutes(-1).ToString("yyyy-MM-dd'T'HH:mm:ss'Z'") + '","effectiveTo":"' + $expiry + '"'
$plan = Try-Rest Post "$Pricing/api/markdown-plans" ('{"batchId":"' + $str.batchId + '","sku":"SKU-STR-250G","discountTier":"PERCENT_30",' + $window + ',"origin":"HUMAN","rationale":"Supervisor clearance of strawberries"}')
Show $plan
Write-Host "PERCENT_60 on SKU-YOG-1KG gives a negative margin; the domain refuses it:"
Try-Rest Post "$Pricing/api/markdown-plans" ('{"batchId":"' + $yog.batchId + '","sku":"SKU-YOG-1KG","discountTier":"PERCENT_60",' + $window + ',"origin":"AGENT","rationale":"Aggressive clearance","agentTraceId":"TRC-DEMO-0002"}') | Out-Null
if ($plan) {
    Write-Host "The accepted plan, read back:"
    Show (Try-Rest Get "$Pricing/api/markdown-plans/$($plan.markdownPlanId)")
}

Step "10. FR6b  The markdown reaches the till"
Start-Sleep -Seconds 2
Show (Try-Rest Get "$Pricing/api/prices/SKU-STR-250G")
Show (Try-Rest Post "$Sales/api/transactions" '{"tillId":"TILL-02","lineItems":[{"sku":"SKU-STR-250G","quantity":1}]}')

Step "11. FR15  End-of-day briefing (needs the LLM key; 503 otherwise)"
$briefing = Try-Rest Post "$Insight/api/insight/briefings" '{}'
Show $briefing

Step "Done"
Write-Host "Read models to show on screen: $Insight/api/insight/risks, $Pricing/api/prices/SKU-STR-250G"
if ($traceId) { Write-Host "The agent's trace: $Insight/api/insight/agent-traces/$traceId" }
if ($TranscriptPath) { Stop-Transcript | Out-Null }
