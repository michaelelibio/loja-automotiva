# inGarage — execução local

Para as variáveis Railway e a validação de CORS dos dois domínios de produção, consulte [CORS em produção](backend/CORS-PRODUCTION.md).

Pré-requisitos: JDK 17, Node.js/npm e PostgreSQL acessível. O backend usa o Maven Wrapper incluído. Instale as dependências do frontend com `cd frontend` e `npm.cmd install` na primeira execução.

## Configuração persistente

Preencha `backend/.env.local.ps1` uma vez. Esse arquivo contém secrets locais e é ignorado pelo Git. Use strings PowerShell com aspas simples; se um valor contiver uma aspa simples, duplique-a (`''`). `backend/.env.example` contém todos os nomes lidos pela configuração atual, sem valores, e pode ser versionado. Spring Boot não carrega esse checklist automaticamente.

Para PostgreSQL da Railway, copie manualmente o **host e a porta públicos** para `PGHOST` e `PGPORT`, além de `PGDATABASE`, `PGUSER` e `PGPASSWORD`. Não use `*.railway.internal`. O arquivo converte esses dados em:

```powershell
$env:DB_URL = "jdbc:postgresql://$($env:PGHOST):$($env:PGPORT)/$($env:PGDATABASE)"
$env:DB_USERNAME = $env:PGUSER
$env:DB_PASSWORD = $env:PGPASSWORD
```

O backend lê `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`, não lê diretamente `PG*`, `POSTGRES_*` ou `DATABASE_URL`. `POSTGRES_DB`, `POSTGRES_USER` e `POSTGRES_PASSWORD` correspondem ao nome do banco, usuário e senha. Uma `DATABASE_URL` com esquema `postgresql://` não é uma URL JDBC: se preferir preencher `DB_URL` diretamente, use `jdbc:postgresql://HOST_PUBLICO:PORTA_PUBLICA/BANCO`, mantendo usuário e senha nas variáveis separadas. Preserve parâmetros de conexão exigidos pelo seu banco.

Preencha `JWT_SECRET` com um valor aleatório privado de pelo menos 32 bytes e `GOOGLE_CLIENT_ID` com o ID público do cliente Google. As demais variáveis têm defaults no backend. Credenciais opcionais começam vazias: preencha Mercado Pago, CJ e Resend somente para utilizar essas integrações. Para enviar e-mails, configure `ACCOUNT_EMAIL_MODE=resend`, `RESEND_API_KEY` e `ACCOUNT_EMAIL_FROM`. URLs de retorno de pagamento e notificação são configuráveis; a notificação precisa de um endereço acessível pelo provedor.

O script não ativa profiles automaticamente. O profile existente `local` habilita o simulador DSers; a configuração padrão mantém `DSERS_SIMULATION_ENABLED=false`. A configuração atual também usa `JPA_DDL_AUTO=update` e inicialização SQL: escolha um banco de desenvolvimento ao apontar para a Railway.

O frontend já possui `frontend/.env.local`; preserve seus dados. Em outro checkout, copie `frontend/.env.local.example` para `frontend/.env.local`. Configure `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080` e o mesmo ID Google em `NEXT_PUBLIC_GOOGLE_CLIENT_ID`. `NEXT_PUBLIC_API_URL` é um fallback em parte dos clientes; use preferencialmente `NEXT_PUBLIC_API_BASE_URL`. Reinicie o frontend após mudar essas variáveis. Nunca coloque tokens, senhas ou secrets em `NEXT_PUBLIC_*`.

## Inicialização

Em um terminal PowerShell:

```powershell
cd backend
.\start-local.ps1
```

Em outro terminal:

```powershell
cd frontend
npm.cmd run dev
```

URLs esperadas: frontend <http://localhost:3000> e backend <http://localhost:8080> (com as portas livres).

Também é possível usar, da raiz, `.\start-local.ps1 backend` e `.\start-local.ps1 frontend`, cada um em seu terminal. O script executa o serviço no próprio terminal; encerre com Ctrl+C.

Se `backend/.env.local.ps1` não existir, recrie-o usando os nomes de `backend/.env.example` no formato `$env:NOME = 'VALOR'`, incluindo os três `DB_*` diretamente ou o mapeamento acima. O iniciador explica a ausência e bloqueia placeholders nos campos obrigatórios.

Nunca commite secrets. Arquivos locais são ignorados; os arquivos de exemplo podem ser versionados. Não há configuração atual para `GOOGLE_CLIENT_SECRET` ou `CJ_API_SECRET`. Para a troca OAuth AliExpress, configure `ALIEXPRESS_APP_KEY`, `ALIEXPRESS_APP_SECRET` e `ALIEXPRESS_CALLBACK_URL`; consulte [o contrato e o teste manual](backend/ALIEXPRESS-OAUTH.md).
