$ErrorActionPreference = 'Stop'
$project = 'assignment-test-' + [guid]::NewGuid().ToString('N').Substring(0, 10)
$keyDirectory = New-Item -ItemType Directory (Join-Path ([IO.Path]::GetTempPath()) $project)
$publicKey = Join-Path $keyDirectory.FullName 'public.pem'
$rsa = [System.Security.Cryptography.RSA]::Create(2048)
$env:ASSIGNMENT_TEST_KEY_DIR = $keyDirectory.FullName
$env:ASSIGNMENT_TEST_PASSWORD = [guid]::NewGuid().ToString('N')
$env:COURIER_TEST_POLL_DELAY_MS = '1000'
$composeFile = Join-Path $PSScriptRoot '../compose.assignment-test.yml'

function Invoke-Compose {
    docker compose -p $project -f $composeFile @args
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed ($LASTEXITCODE)." }
}

function Write-Sql($service, $sql) {
    $sql | docker compose -p $project -f $composeFile exec -T $service sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE" --batch --skip-column-names'
    if ($LASTEXITCODE -ne 0) { throw "Fixture SQL failed for $service." }
}

function ConvertTo-Base64Url([byte[]] $bytes) {
    [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-AdminToken {
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $claims = @{ iss = 'logitrack-security'; sub = [guid]::NewGuid().ToString(); roles = @('ADMIN')
        iat = $now; exp = $now + 600 } | ConvertTo-Json -Compress
    $header = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes('{"alg":"RS256","typ":"JWT"}'))
    $payload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($claims))
    $data = "$header.$payload"
    $signature = $rsa.SignData([Text.Encoding]::UTF8.GetBytes($data),
        [Security.Cryptography.HashAlgorithmName]::SHA256, [Security.Cryptography.RSASignaturePadding]::Pkcs1)
    "$data.$(ConvertTo-Base64Url $signature)"
}

function New-ShipmentFixture {
    $id = [guid]::NewGuid()
    Write-Sql shipment-db @"
INSERT INTO clientes (id, created_date) VALUES (UNHEX('$($id.ToString('N'))'), CURRENT_TIMESTAMP(6));
INSERT INTO envios (id, codigo_seguimiento, cliente_id, estado, created_date, last_modified_date,
    peso, ancho, alto, largo, origen_calle, origen_numero, origen_ciudad, origen_codigo_postal,
    destino_calle, destino_numero, destino_ciudad, destino_codigo_postal, version)
VALUES (UNHEX('$($id.ToString('N'))'), UNHEX('$([guid]::NewGuid().ToString('N'))'),
    UNHEX('$($id.ToString('N'))'), 'PENDIENTE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6),
    1, 10, 10, 10, 'Origen', '1', 'Ciudad', '1234', 'Destino', '2', 'Ciudad', '1234', 0);
"@
    $id
}

function Send-Assignment($id, $courier, $key) {
    $request = @{ Method = 'Post'; ContentType = 'application/json'
        Headers = @{ Authorization = $auth.Authorization; 'Idempotency-Key' = $key }
        Body = @{ repartidorId = $courier } | ConvertTo-Json -Compress }
    Invoke-RestMethod "$base/envios/$id/assignment-requests" @request
}

function Wait-Assignment($id, $operation, $expected) {
    for ($attempt = 0; $attempt -lt 50; $attempt++) {
        $result = Invoke-RestMethod "$base/envios/$id/assignment-requests/$operation" -Headers $auth
        if ($result.status -eq $expected) { return }
        if ($result.status -ne 'PENDING') { throw "Expected $expected, got $($result.status)." }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for $expected."
}

function Get-Queue($name) {
    Invoke-RestMethod "$management/api/queues/%2F/$name" -Headers $brokerAuth
}

function Publish-TestMessage($body) {
    $request = @{ Method = 'Post'; Headers = $brokerAuth; ContentType = 'application/json'
        Body = @{
            properties = @{ content_type = 'application/json'; delivery_mode = 2 }
            routing_key = 'courier.assignment.requested.v1'
            payload_encoding = 'string'
            payload = $body
        } | ConvertTo-Json -Depth 5 -Compress
    }
    $published = Invoke-RestMethod "$management/api/exchanges/%2F/logitrack.domain.v1/publish" @request
    if (-not $published.routed) { throw 'Test message was not routed to Courier.' }
}

function Wait-BrokerCondition([scriptblock] $condition, [string] $failure) {
    for ($i = 0; $i -lt 30; $i++) {
        try { if (& $condition) { return } } catch {
            if ($i -eq 29) { throw }
        }
        Start-Sleep 2
    }
    throw $failure
}

try {
    Set-Content -LiteralPath $publicKey -Encoding ascii $rsa.ExportSubjectPublicKeyInfoPem()
    Invoke-Compose up --build -d --wait --wait-timeout 180
    $port = (Invoke-Compose port shipment 8083).Split(':')[-1]
    $base = "http://127.0.0.1:$port"
    $brokerAuth = @{ Authorization = 'Basic ' + [Convert]::ToBase64String(
        [Text.Encoding]::UTF8.GetBytes("assignment_test:$env:ASSIGNMENT_TEST_PASSWORD")) }
    $auth = @{ Authorization = "Bearer $(New-AdminToken)" }
    $courier = [guid]::NewGuid()
    Write-Sql courier-db "INSERT INTO courier (id, nombre, tipo_vehiculo, estado, created_date, last_modified_date) VALUES (UNHEX('$($courier.ToString('N'))'), 'Prueba', 'Bicicleta', 'ACTIVO', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));"
    $shipment = New-ShipmentFixture
    $key = [guid]::NewGuid().ToString()
    Invoke-Compose stop rabbit
    $pending = Send-Assignment $shipment $courier $key
    $replay = Send-Assignment $shipment $courier $key
    if ($pending.status -ne 'PENDING' -or $pending.operationId -ne $replay.operationId) {
        throw 'Expected one pending operation during broker downtime.'
    }
    Start-Sleep -Seconds 3
    Invoke-Compose start rabbit
    $managementPort = (Invoke-Compose port rabbit 15672).Split(':')[-1]
    $management = "http://127.0.0.1:$managementPort"
    Wait-Assignment $shipment $pending.operationId CONFIRMED
    $shipmentRow = Invoke-RestMethod "$base/envios/$shipment" -Headers $auth
    if ($shipmentRow.repartidorId -ne $courier.ToString()) { throw 'Shipment did not persist the confirmed courier.' }
    Write-Output 'Broker recovery: one operation CONFIRMED and shipment updated.'

    $commandQueue = 'courier.assignment.requests.v1'
    Wait-BrokerCondition { (Get-Queue $commandQueue).message_stats.ack -ge 1 } 'Courier did not acknowledge the command.'
    $acknowledged = (Get-Queue $commandQueue).message_stats.ack
    $eventId = Write-Sql shipment-db "SELECT event_id FROM outbox_events WHERE operation_id = '$($pending.operationId)';"
    $resultId = Write-Sql courier-db "SELECT result_event_id FROM courier_assignment_eligibility WHERE operation_id = '$($pending.operationId)';"
    $command = @{
        eventId = $eventId; operationId = $pending.operationId; schemaVersion = 1
        occurredAt = [DateTimeOffset]::UtcNow.ToString('o'); shipmentId = $shipment; courierId = $courier
    } | ConvertTo-Json -Compress
    Publish-TestMessage $command
    Wait-BrokerCondition { (Get-Queue $commandQueue).message_stats.ack -gt $acknowledged } 'Duplicate command was not acknowledged.'
    $resultAfterDuplicate = Write-Sql courier-db "SELECT result_event_id FROM courier_assignment_eligibility WHERE operation_id = '$($pending.operationId)';"
    if ($resultAfterDuplicate -ne $resultId) { throw 'A duplicate changed the persisted result.' }
    Publish-TestMessage '{invalid'
    Wait-BrokerCondition { (Get-Queue "$commandQueue.dlq").messages_ready -ge 1 } 'Invalid command did not reach the DLQ.'
    Write-Output 'Courier: duplicate acknowledged with the same result and invalid command sent to DLQ.'

    $waitingShipment = New-ShipmentFixture
    $cancelledShipment = New-ShipmentFixture
    Invoke-Compose stop courier
    $waiting = Send-Assignment $waitingShipment $courier ([guid]::NewGuid().ToString())
    $cancelled = Send-Assignment $cancelledShipment $courier ([guid]::NewGuid().ToString())
    Wait-BrokerCondition { (Get-Queue $commandQueue).messages_ready -ge 2 } 'The offline Courier did not retain both commands.'
    $response = Invoke-WebRequest "$base/envios/$cancelledShipment/cancelacion" -Method Patch -Headers $auth
    if ($response.StatusCode -ne 204) { throw 'Cancellation fixture failed.' }
    Invoke-Compose start courier
    Wait-Assignment $waitingShipment $waiting.operationId CONFIRMED
    Wait-Assignment $cancelledShipment $cancelled.operationId REJECTED
    Write-Output 'Courier recovery: queued command persisted and operation CONFIRMED.'
    Write-Output 'Cancelled shipment: late eligibility REJECTED.'

    $env:COURIER_TEST_POLL_DELAY_MS = '60000'
    Invoke-Compose up -d --no-deps --force-recreate courier
    $restartShipment = New-ShipmentFixture
    $restart = Send-Assignment $restartShipment $courier ([guid]::NewGuid().ToString())
    $pendingSql = "SELECT result_event_id FROM courier_assignment_eligibility WHERE operation_id = '$($restart.operationId)' AND publication_status = 'PENDING';"
    Wait-BrokerCondition { [bool](Write-Sql courier-db $pendingSql) } 'Courier did not persist an unpublished decision.'
    $pendingResult = Write-Sql courier-db $pendingSql
    if (-not $pendingResult) { throw 'The decision was published before the crash fixture.' }
    Invoke-Compose kill courier
    $env:COURIER_TEST_POLL_DELAY_MS = '1000'
    Invoke-Compose up -d --no-deps --force-recreate courier
    Wait-Assignment $restartShipment $restart.operationId CONFIRMED
    $sentSql = "SELECT result_event_id FROM courier_assignment_eligibility WHERE operation_id = '$($restart.operationId)' AND publication_status = 'SENT';"
    Wait-BrokerCondition { [bool](Write-Sql courier-db $sentSql) } 'Courier did not mark the recovered result as sent.'
    $sentResult = Write-Sql courier-db $sentSql
    if ($sentResult -ne $pendingResult) { throw 'Courier did not publish the same persisted decision after restart.' }
    Write-Output 'Courier outbox: unpublished decision recovered after process kill.'

    Write-Sql courier-db "UPDATE courier SET estado = 'INACTIVO' WHERE id = UNHEX('$($courier.ToString('N'))');"
    $otherShipment = New-ShipmentFixture
    $rejected = Send-Assignment $otherShipment $courier ([guid]::NewGuid().ToString())
    Wait-Assignment $otherShipment $rejected.operationId REJECTED
    Write-Output 'Inactive courier: operation REJECTED.'

    $missingShipment = New-ShipmentFixture
    $missing = Send-Assignment $missingShipment ([guid]::NewGuid()) ([guid]::NewGuid().ToString())
    Wait-Assignment $missingShipment $missing.operationId REJECTED
    Write-Output 'Missing courier: operation REJECTED.'
} catch {
    Invoke-Compose logs --tail 35 shipment courier rabbit shipment-db courier-db
    throw
} finally {
    try { Invoke-Compose down -v } finally {
        $rsa.Dispose()
        Remove-Item -LiteralPath $publicKey -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $keyDirectory.FullName -ErrorAction SilentlyContinue
        Remove-Item Env:ASSIGNMENT_TEST_KEY_DIR, Env:ASSIGNMENT_TEST_PASSWORD, Env:COURIER_TEST_POLL_DELAY_MS -ErrorAction SilentlyContinue
    }
}
