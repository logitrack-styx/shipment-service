$ErrorActionPreference = 'Stop'
$project = 'assignment-test-' + [guid]::NewGuid().ToString('N').Substring(0, 10)
$keyDirectory = New-Item -ItemType Directory (Join-Path ([IO.Path]::GetTempPath()) $project)
$publicKey = Join-Path $keyDirectory.FullName 'public.pem'
$rsa = [System.Security.Cryptography.RSA]::Create(2048)
$env:ASSIGNMENT_TEST_KEY_DIR = $keyDirectory.FullName
$env:ASSIGNMENT_TEST_PASSWORD = [guid]::NewGuid().ToString('N')
$composeFile = Join-Path $PSScriptRoot '../compose.assignment-test.yml'

function Invoke-Compose {
    docker compose -p $project -f $composeFile @args
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed ($LASTEXITCODE)." }
}

function Write-Sql($service, $sql) {
    $sql | docker compose -p $project -f $composeFile exec -T $service sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE" --batch'
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

try {
    Set-Content -LiteralPath $publicKey -Encoding ascii $rsa.ExportSubjectPublicKeyInfoPem()
    Invoke-Compose up --build -d --wait --wait-timeout 180
    $port = (Invoke-Compose port shipment 8083).Split(':')[-1]
    $base = "http://127.0.0.1:$port"
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
    Wait-Assignment $shipment $pending.operationId CONFIRMED
    $shipmentRow = Invoke-RestMethod "$base/envios/$shipment" -Headers $auth
    if ($shipmentRow.repartidorId -ne $courier.ToString()) { throw 'Shipment did not persist the confirmed courier.' }
    Write-Output 'Broker recovery: one operation CONFIRMED and shipment updated.'

    Write-Sql courier-db "UPDATE courier SET estado = 'INACTIVO' WHERE id = UNHEX('$($courier.ToString('N'))');"
    $otherShipment = New-ShipmentFixture
    $rejected = Send-Assignment $otherShipment $courier ([guid]::NewGuid().ToString())
    Wait-Assignment $otherShipment $rejected.operationId REJECTED
    Write-Output 'Inactive courier: operation REJECTED.'
} catch {
    Invoke-Compose logs --tail 35 shipment courier rabbit shipment-db courier-db
    throw
} finally {
    try { Invoke-Compose down -v } finally {
        $rsa.Dispose()
        Remove-Item -LiteralPath $publicKey -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $keyDirectory.FullName -ErrorAction SilentlyContinue
        Remove-Item Env:ASSIGNMENT_TEST_KEY_DIR, Env:ASSIGNMENT_TEST_PASSWORD -ErrorAction SilentlyContinue
    }
}
