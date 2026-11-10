$ErrorActionPreference = 'Stop'
$project = 'backend-s4-test-' + [guid]::NewGuid().ToString('N').Substring(0, 10)
$directory = Join-Path ([IO.Path]::GetTempPath()) $project
$envFile = & (Join-Path $PSScriptRoot 'prepare-sprint4-local.ps1') -Directory $directory
Add-Content -LiteralPath $envFile -Value @'
S4_GATEWAY_PORT=0
MAILPIT_UI_PORT=0
STYX_CONFIG_DIR=../styx-config
SMTP_HOST=mailpit
SMTP_PORT=1025
SMTP_AUTH=false
SMTP_STARTTLS=false
SMTP_USERNAME=
SMTP_PASSWORD=
MP_ACCESS_TOKEN_TEST=
MP_WEBHOOK_SECRET_TEST=
MP_WEBHOOK_PUBLIC_URL=
TEST_QUOTE_AMOUNT_ARS=3500.00
'@
$savedEnvironment = @{}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=') {
        $name = $Matches[1]
        $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
    }
}
$root = Split-Path $PSScriptRoot
$compose = @('compose', '-p', $project, '--env-file', $envFile, '--profile', 'test',
    '-f', (Join-Path $root 'compose.sprint3.release.yml'),
    '-f', (Join-Path $root 'compose.sprint4.backend.yml'))

function Invoke-Compose {
    docker @compose @args
    if ($LASTEXITCODE -ne 0) { throw "Compose failed ($LASTEXITCODE)." }
}

function Post-Json($path, $body, $headers = @{}) {
    $request = @{ Method = 'Post'; ContentType = 'application/json'; Headers = $headers
        Body = $body | ConvertTo-Json -Depth 5 -Compress }
    Invoke-RestMethod "$base$path" @request
}

function Get-Token($username, $password) {
    $result = Post-Json '/auth/login' @{ username = $username; password = $password }
    @{ Authorization = "Bearer $($result.accessToken)" }
}

function Wait-Assignment($id, $operation) {
    for ($i = 0; $i -lt 45; $i++) {
        $current = Invoke-RestMethod "$base/shipments/$id/assignment-requests/$operation" -Headers $admin
        if ($current.status -eq 'CONFIRMED') { return }
        if ($current.status -ne 'PENDING') { throw "Assignment ended as $($current.status)." }
        Start-Sleep 2
    }
    throw 'Assignment did not reach CONFIRMED.'
}

try {
    Invoke-Compose config --quiet
    Invoke-Compose up --build -d --wait --wait-timeout 240
    $gatewayPort = (Invoke-Compose port api-gateway 8066).Split(':')[-1]
    $mailpitPort = (Invoke-Compose port mailpit 8025).Split(':')[-1]
    $base = "http://127.0.0.1:$gatewayPort/api/v1"
    $adminPassword = ((Get-Content -LiteralPath $envFile | Where-Object { $_ -like 'ADMIN_PASSWORD=*' }) -split '=', 2)[1]
    for ($i = 0; $i -lt 40; $i++) {
        try { $admin = Get-Token 'admin' $adminPassword; break } catch {
            if ($i -eq 39) { throw }
            Start-Sleep 2
        }
    }
    $password = 'Smoke-' + [guid]::NewGuid().ToString('N')
    $null = Post-Json '/auth/register' @{ username = 'smoke'; email = 'smoke@example.invalid'; password = $password }
    $customer = Get-Token 'smoke' $password
    $user = Post-Json '/users' @{
        username = 'courier-smoke'; email = 'courier@example.invalid'; password = $password; role = 'REPARTIDOR'
    } $admin
    $courier = Post-Json '/couriers' @{ userId = $user.id; nombre = 'Courier prueba'; tipoVehiculo = 'Bicicleta' } $admin
    $address = @{ calle = 'Prueba'; numero = '1'; ciudad = 'Prueba'; codigoPostal = '1234' }
    $shipment = Post-Json '/shipments' @{
        origen = $address; destino = $address; peso = 1; ancho = 10; alto = 10; largo = 10
    } $customer
    $secondShipment = Post-Json '/shipments' @{
        origen = $address; destino = $address; peso = 2; ancho = 10; alto = 10; largo = 10
    } $customer

    $origin = 'http://localhost:5173'
    foreach ($path in '/payments/quotes', '/payments/checkouts', "/shipments/$($shipment.id)/assignment-requests") {
        $preflight = Invoke-WebRequest "$base$path" -Method Options -Headers @{
            Origin = $origin
            'Access-Control-Request-Method' = 'POST'
            'Access-Control-Request-Headers' = 'authorization,content-type,idempotency-key'
        }
        if ($preflight.Headers['Access-Control-Allow-Origin'] -notcontains $origin -or
                ($preflight.Headers['Access-Control-Allow-Headers'] -join ',') -notmatch 'idempotency-key') {
            throw "CORS preflight failed for $path."
        }
    }
    $forbiddenOrigin = Invoke-WebRequest "$base/payments/quotes" -Method Options -SkipHttpErrorCheck -Headers @{
        Origin = 'https://unlisted.example.invalid'; 'Access-Control-Request-Method' = 'POST'
    }
    if ($forbiddenOrigin.StatusCode -ne 403) { throw 'CORS did not reject the unlisted origin.' }
    $pages = 0, 1 | ForEach-Object {
        $page = Invoke-WebRequest "$base/shipments/mine?page=$_&size=1" -Headers ($customer + @{ Origin = $origin })
        $rows = @($page.Content | ConvertFrom-Json)
        if ($rows.Count -ne 1 -or $page.Headers['X-Total-Count'] -notcontains '2' -or
                $page.Headers['X-Total-Pages'] -notcontains '2' -or
                ($page.Headers['Access-Control-Expose-Headers'] -join ',') -notmatch 'X-Total-Pages') {
            throw 'Owner pagination or exposed headers are invalid.'
        }
        $rows[0].id
    }
    if ($pages -notcontains $shipment.id -or $pages -notcontains $secondShipment.id) {
        throw 'The owner shipment pages did not return two distinct shipments.'
    }
    $denied = Invoke-WebRequest "$base/shipments/mine" -Headers $admin -SkipHttpErrorCheck
    $anonymous = Invoke-WebRequest "$base/shipments/mine" -SkipHttpErrorCheck
    if ($denied.StatusCode -ne 403 -or $anonymous.StatusCode -ne 401) { throw 'Shipment list RBAC mismatch.' }
    Write-Output 'Gateway: three CORS preflights, origin rejection, RBAC and two owner pages verified.'

    $assignmentHeaders = $admin + @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() }
    $request = Post-Json "/shipments/$($shipment.id)/assignment-requests" @{ repartidorId = $courier.id } $assignmentHeaders
    if ($request.status -ne 'PENDING') { throw 'Assignment was not accepted as PENDING.' }
    Wait-Assignment $shipment.id $request.operationId
    $courierToken = Get-Token 'courier-smoke' $password
    $assigned = @(Invoke-RestMethod "$base/shipments/assigned-to-me" -Headers $courierToken)
    if ($assigned.Count -ne 1 -or $assigned[0].id -ne $shipment.id) { throw 'Courier assignment list mismatch.' }
    Write-Output 'Gateway and RabbitMQ: assignment CONFIRMED.'

    $quoteHeaders = $customer + @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() }
    $quote = Post-Json '/payments/quotes' @{ shipmentId = $shipment.id } $quoteHeaders
    $checkoutHeaders = $customer + @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() }
    $checkout = Post-Json '/payments/checkouts' @{ shipmentId = $shipment.id; quoteId = $quote.quoteId } $checkoutHeaders
    $payment = Invoke-RestMethod "$base/payments/$($checkout.paymentId)" -Headers $customer
    if ($payment.status -ne 'PENDING' -or $payment.mode -ne 'TEST' -or $payment.amount -ne '3500.00') {
        throw 'The FAKE payment did not match the quote.'
    }
    Write-Output 'Gateway and Payment: FAKE checkout persisted as TEST/PENDING.'

    for ($i = 0; $i -lt 25; $i++) {
        $messages = (Invoke-RestMethod "http://127.0.0.1:$mailpitPort/api/v1/messages").messages
        if ($messages.Subject -contains 'Bienvenida a LogiTrack') { break }
        if ($i -eq 24) { throw 'Welcome message not found in Mailpit.' }
        Start-Sleep 2
    }
    Write-Output 'Security and Mailpit: welcome message delivered.'
} catch {
    Invoke-Compose ps -a
    Invoke-Compose logs --tail 25 config-server eureka-server api-gateway security-service shipment-service courier-service rabbitmq payment-service mailpit
    throw
} finally {
    try { Invoke-Compose down -v } finally {
        foreach ($name in $savedEnvironment.Keys) {
            if ($null -eq $savedEnvironment[$name]) {
                Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
            } else {
                Set-Item -LiteralPath "Env:$name" -Value $savedEnvironment[$name]
            }
        }
        foreach ($file in '.env', 'private.pem', 'public.pem', 'rabbit-definitions.json') {
            Remove-Item -LiteralPath (Join-Path $directory $file) -ErrorAction SilentlyContinue
        }
        Remove-Item -LiteralPath $directory -ErrorAction SilentlyContinue
    }
}
