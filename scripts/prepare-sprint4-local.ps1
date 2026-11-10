param([Parameter(Mandatory)][string] $Directory)

$ErrorActionPreference = 'Stop'
$target = New-Item -ItemType Directory -Path $Directory

function New-Secret {
    [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)).ToLowerInvariant()
}

function New-BrokerUser($name, $password) {
    $salt = [Security.Cryptography.RandomNumberGenerator]::GetBytes(4)
    $inputBytes = [byte[]]($salt + [Text.Encoding]::UTF8.GetBytes($password))
    $digest = [Security.Cryptography.SHA256]::HashData($inputBytes)
    @{
        name = $name
        password_hash = [Convert]::ToBase64String([byte[]]($salt + $digest))
        hashing_algorithm = 'rabbit_password_hashing_sha256'
        tags = @()
    }
}

function New-BrokerPermissions($name, $queuePrefix) {
    $resources = '^(logitrack\.domain\.v1|' + $queuePrefix + '(\.dlq)?)$'
    @{
        user = $name
        vhost = '/'
        configure = $resources
        read = $resources
        write = $resources.Replace('^(', '^(amq\.default|')
    }
}

$rsa = [Security.Cryptography.RSA]::Create(2048)
try {
    Set-Content -LiteralPath (Join-Path $target.FullName 'private.pem') -Encoding ascii $rsa.ExportPkcs8PrivateKeyPem()
    Set-Content -LiteralPath (Join-Path $target.FullName 'public.pem') -Encoding ascii $rsa.ExportSubjectPublicKeyInfoPem()
} finally {
    $rsa.Dispose()
}

$shipmentPassword = New-Secret
$courierPassword = New-Secret
$definitions = Join-Path $target.FullName 'rabbit-definitions.json'
@{
    users = @((New-BrokerUser shipment $shipmentPassword), (New-BrokerUser courier $courierPassword))
    vhosts = @(@{ name = '/' })
    permissions = @((New-BrokerPermissions shipment 'shipment\.assignment\.eligibility\.v1'),
        (New-BrokerPermissions courier 'courier\.assignment\.requests\.v1'))
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $definitions -Encoding utf8

$envFile = Join-Path $target.FullName '.env'
$keyDir = $target.FullName.Replace('\', '/')
$definitionsPath = $definitions.Replace('\', '/')
@"
DB_USERNAME=logitrack_user
DB_PASSWORD=$(New-Secret)
DB_ROOT_PASSWORD=$(New-Secret)
ADMIN_USERNAME=admin
ADMIN_EMAIL=admin@example.invalid
ADMIN_PASSWORD=$(New-Secret)
S4_KEY_DIR='$keyDir'
S4_RABBIT_DEFINITIONS='$definitionsPath'
SHIPMENT_RABBITMQ_PASSWORD=$shipmentPassword
COURIER_RABBITMQ_PASSWORD=$courierPassword
PAYMENT_DB_PASSWORD=$(New-Secret)
PAYMENT_DB_ROOT_PASSWORD=$(New-Secret)
MAIL_NOTIFICATIONS_ENABLED=true
MP_ENV=TEST
MP_ADAPTER=FAKE
"@ | Set-Content -LiteralPath $envFile -Encoding utf8

$envFile
