param([string]$JarPath = (Join-Path $PSScriptRoot 'target/garage-api-0.0.1-SNAPSHOT.jar'), [switch]$ValidateOnly)
$ErrorActionPreference = 'Stop'
$configPath = Join-Path $PSScriptRoot 'homologation.local.ps1'
if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
    throw 'Crie backend/homologation.local.ps1 a partir de homologation.env.example.ps1. Nenhuma configuração de produção será carregada.'
}
. $configPath
$requiredNames = @('HML_POSTGRES_PORT','HML_JWT_SECRET','HML_MERCADO_PAGO_ACCESS_TOKEN','HML_MERCADO_PAGO_WEBHOOK_SECRET','HML_MERCADO_PAGO_SUCCESS_URL','HML_MERCADO_PAGO_PENDING_URL','HML_MERCADO_PAGO_FAILURE_URL','HML_MERCADO_PAGO_NOTIFICATION_URL')
foreach ($name in $requiredNames) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name,'Process'))) { throw "Configure $name no arquivo exclusivo de homologação." }
}
$hmlPort = 0
if (-not [int]::TryParse($env:HML_POSTGRES_PORT,[ref]$hmlPort) -or $hmlPort -lt 1024 -or $hmlPort -gt 65535) { throw 'Porta PostgreSQL de homologação inválida.' }
if ([Text.Encoding]::UTF8.GetByteCount($env:HML_JWT_SECRET) -lt 32) { throw 'Gere um JWT_SECRET exclusivo de homologação com pelo menos 32 bytes.' }
foreach ($name in @('HML_MERCADO_PAGO_SUCCESS_URL','HML_MERCADO_PAGO_PENDING_URL','HML_MERCADO_PAGO_FAILURE_URL','HML_MERCADO_PAGO_NOTIFICATION_URL')) {
    $value=[Environment]::GetEnvironmentVariable($name,'Process'); $uri=$null
    if (-not [Uri]::TryCreate($value,[UriKind]::Absolute,[ref]$uri) -or $uri.Scheme -ne 'https' -or $uri.Host -in @('api.ingarage.com.br','ingarage.com.br','www.ingarage.com.br','localhost','127.0.0.1')) {
        throw "Configure $name com HTTPS de homologação; URLs de produção e localhost não são aceitas."
    }
}
$notificationUri=[Uri]$env:HML_MERCADO_PAGO_NOTIFICATION_URL
if ($notificationUri.AbsolutePath -ne '/api/webhooks/mercadopago') { throw 'A URL de notificacao deve terminar em /api/webhooks/mercadopago.' }
if ($env:HML_FRONTEND_URL) {
    $frontendUri=$null
    if (-not [Uri]::TryCreate($env:HML_FRONTEND_URL,[UriKind]::Absolute,[ref]$frontendUri) -or $frontendUri.Host -in @('ingarage.com.br','www.ingarage.com.br','api.ingarage.com.br')) { throw 'Use frontend local ou exclusivo de homologacao.' }
}
try { $account=Invoke-RestMethod -Method Get -Uri 'https://api.mercadopago.com/users/me' -Headers @{Authorization=('Bearer '+$env:HML_MERCADO_PAGO_ACCESS_TOKEN)} -TimeoutSec 15 }
catch { throw 'Não foi possível verificar a conta Mercado Pago. A inicialização foi bloqueada; nenhuma credencial foi exibida.' }
if ($account.tags -notcontains 'test_user' -or $account.site_id -ne 'MLB') { throw 'A credencial não foi comprovada como conta de teste brasileira. Inicialização bloqueada.' }
Write-Output 'Configuração exclusiva e conta de teste verificadas. Nenhum pagamento foi executado.'
if ($ValidateOnly) { return }
if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) { throw 'Compile o JAR atualizado antes de iniciar a homologação.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=[IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $JarPath).Path)
try { if ($null -eq $archive.GetEntry('BOOT-INF/classes/application-homologation.properties')) { throw 'O JAR não contém o perfil de homologação. Recompile; o backend não será iniciado.' } }
finally { $archive.Dispose() }
& java -jar $JarPath '--spring.profiles.active=homologation' ("--spring.datasource.url=jdbc:postgresql://127.0.0.1:"+$hmlPort+'/ingarage_homologation') '--spring.datasource.username=ingarage_hml' '--server.address=127.0.0.1' '--server.port=18080'
if ($LASTEXITCODE -ne 0) { throw "Backend de homologação terminou com código $LASTEXITCODE." }