[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$composeFile = Join-Path $PSScriptRoot '..\docker-compose.yml'
$topic = 'freshflow-smoke-' + [DateTimeOffset]::UtcNow.ToString('yyyyMMddHHmmss')
$message = 'freshflow-smoke-ok-' + [Guid]::NewGuid().ToString('N')

docker compose -f $composeFile up -d
if ($LASTEXITCODE -ne 0) {
    throw 'docker compose up failed.'
}

$healthy = $false
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    $status = docker inspect freshflow-kafka --format '{{.State.Health.Status}}' 2>$null
    if ($status -eq 'healthy') {
        $healthy = $true
        break
    }
    Start-Sleep -Seconds 2
}

if (-not $healthy) {
    docker compose -f $composeFile logs --tail 100 kafka
    throw 'Kafka did not become healthy within 60 seconds.'
}

docker compose -f $composeFile exec -T kafka /opt/kafka/bin/kafka-topics.sh `
    --bootstrap-server localhost:9092 `
    --create `
    --topic $topic `
    --partitions 1 `
    --replication-factor 1
if ($LASTEXITCODE -ne 0) {
    throw "Could not create smoke-test topic $topic."
}

$message | docker compose -f $composeFile exec -T kafka `
    /opt/kafka/bin/kafka-console-producer.sh `
    --bootstrap-server localhost:9092 `
    --topic $topic
if ($LASTEXITCODE -ne 0) {
    throw 'Kafka producer smoke test failed.'
}

$received = docker compose -f $composeFile exec -T kafka `
    /opt/kafka/bin/kafka-console-consumer.sh `
    --bootstrap-server localhost:9092 `
    --topic $topic `
    --from-beginning `
    --max-messages 1 `
    --timeout-ms 10000
if ($LASTEXITCODE -ne 0 -or $received -notcontains $message) {
    throw "Kafka consumer smoke test failed. Expected: $message; received: $received"
}

Write-Host "PASS: Kafka produced and consumed $message on $topic"
Write-Host 'Kafka remains running for the FreshFlow demo.'
