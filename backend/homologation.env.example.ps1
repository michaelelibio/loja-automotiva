# Copy to homologation.local.ps1 (gitignored). Do not copy production credentials.
# Dedicated cluster: host 127.0.0.1, database ingarage_homologation, user ingarage_hml.
$env:HML_POSTGRES_PORT = '55439'
$env:HML_POSTGRES_PASSWORD = ''
$env:HML_JWT_SECRET = '' # Generate a new random secret of at least 32 bytes.
$env:HML_FRONTEND_URL = 'http://localhost:3000' # Or the FRONTEND temporary HTTPS URL.
$env:HML_MERCADO_PAGO_ACCESS_TOKEN = '' # Seller TEST ACCOUNT credential from Developers.
$env:HML_MERCADO_PAGO_WEBHOOK_SECRET = '' # Secret for this sandbox webhook only.
$env:HML_MERCADO_PAGO_SUCCESS_URL = '' # https://FRONTEND-HML/pagamento/sucesso
$env:HML_MERCADO_PAGO_PENDING_URL = '' # https://FRONTEND-HML/pagamento/pendente
$env:HML_MERCADO_PAGO_FAILURE_URL = '' # https://FRONTEND-HML/pagamento/falhou
$env:HML_MERCADO_PAGO_NOTIFICATION_URL = '' # https://BACKEND-HML/api/webhooks/mercadopago
# Enable only after dedicated sandbox credentials and HTTPS webhook are verified.
$env:HML_PAYMENT_CREATION_ENABLED = 'false'
