$ErrorActionPreference = 'Stop'
$localEnvironment = Join-Path $PSScriptRoot '.env.local.ps1'
if (-not (Test-Path -LiteralPath $localEnvironment -PathType Leaf)) {
    throw 'Arquivo backend/.env.local.ps1 ausente. Crie-o conforme o README.md e backend/.env.example antes de iniciar.'
}

. $localEnvironment

foreach ($name in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET', 'GOOGLE_CLIENT_ID')) {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value) -or $value.Contains('COLE_AQUI')) {
        throw "Preencha $name em backend/.env.local.ps1 antes de iniciar."
    }
}
if ($env:DB_URL -notlike 'jdbc:postgresql://*' -or $env:DB_URL -match '\.railway\.internal(?=[:/])') {
    throw 'DB_URL deve ser uma URL JDBC PostgreSQL com host publico para acesso local.'
}
if ([Text.Encoding]::UTF8.GetByteCount($env:JWT_SECRET) -lt 32) {
    throw 'JWT_SECRET precisa ter pelo menos 32 bytes para HS256. Use um valor aleatorio privado.'
}

Push-Location -LiteralPath $PSScriptRoot
try {
    & .\mvnw.cmd spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw "Maven terminou com codigo $LASTEXITCODE." }
} finally {
    Pop-Location
}
