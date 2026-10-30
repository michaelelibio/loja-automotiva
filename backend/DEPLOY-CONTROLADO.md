# Deploy controlado — Cotação Assistida

Escopo: testar solicitação → confirmação administrativa → aceite em https://ingarage.com.br, sem habilitar pagamentos nem pedidos ao fornecedor. A homologação externa Mercado Pago continua pendente; este procedimento NÃO libera vendas pagas.

## Configuração obrigatória antes de publicar

Não foram consultadas variáveis remotas nem conectado o PostgreSQL Railway. Conferir manualmente no painel sem copiar segredos para chat, terminal ou Git. Preservar credenciais existentes.

Railway, serviço backend:

| Variável | Valor para este teste |
|---|---|
| SPRING_PROFILES_ACTIVE | controlled (não combinar com homologation) |
| PAYMENT_CREATION_ENABLED | false |
| APP_PAYMENT_CREATION_ENABLED | false (remover qualquer override true equivalente) |
| JPA_DDL_AUTO | validate |
| SPRING_JPA_HIBERNATE_DDL_AUTO | validate |
| SPRING_SQL_INIT_MODE | never |
| PRODUCT_SEED_ENABLED | false |
| ALIEXPRESS_ORDERS_ENABLED | false |
| ALIEXPRESS_ORDERS_CONTRACT_CONFIRMED | false |
| DSERS_SIMULATION_ENABLED | false |
| MERCADO_PAGO_DIAGNOSTIC_LOGGING | false |
| FRONTEND_URL / CORS_ALLOWED_ORIGINS | https://ingarage.com.br (incluir www apenas se realmente usado) |

DB_URL/DB_USERNAME/DB_PASSWORD, JWT_SECRET, GOOGLE_CLIENT_ID e segredos de integração: manter referências existentes. DB_URL deve continuar no banco correto, com JDBC; não imprimir seu valor. Remover argumentos de inicialização/JAVA_TOOL_OPTIONS que sobreponham os bloqueios ou usem create/create-drop/update. O perfil controlled usa validate + SQL init never: a aplicação não altera tabelas ao iniciar. Conferir configuração efetiva e log do perfil no deploy.

Vercel, escopo Production:

- NEXT_PUBLIC_API_BASE_URL=https://api.ingarage.com.br
- NEXT_PUBLIC_PAYMENT_CREATION_ENABLED=false
- Se NEXT_PUBLIC_API_URL também existir, apontar para o mesmo backend.
- Manter identificador Google e demais configurações atuais. Nunca expor secrets em NEXT_PUBLIC_.
- Fazer novo build: variáveis públicas são incorporadas ao JavaScript. O botão de pagamento fica oculto por padrão, mesmo se a variável estiver ausente.

O bloqueio decisivo está no backend: PaymentAttemptService verifica a flag antes de ler o pedido, criar tentativa, devolver preferência existente ou chamar createPreference. Ambos os pontos de entrada estão protegidos. Webhooks continuam processando pagamentos anteriores para não perder reconciliações. Este bloqueio NÃO revoga links de pagamento já emitidos antes do deploy; inventariar preferências pendentes anteriores antes da janela. Não abrir nem usar esses links no ensaio. Se for necessário impedir também qualquer pagamento externo por links anteriores, isso exige tratamento separado no Mercado Pago antes do teste; não foi executado.

## Banco: revisão e migração

O projeto usa Spring SQL init, NÃO Flyway. Não há histórico/checksum Flyway. application.properties normalmente usa ddl-auto=update, SQL init always e executa schema.sql e oito scripts em toda inicialização.

A inicialização normal NÃO pode ser considerada somente leitura: schema.sql faz backfills de expires_at, frete legado, fulfillment_type e datas ausentes; recria constraint de método de pagamento. V20261015 recria a constraint dos estados de fulfillment. Hibernate update também pode alterar tipos/constraints. Não há DROP TABLE, TRUNCATE ou DELETE nos scripts revisados. ON DELETE CASCADE define comportamento futuro de exclusão; a criação da FK não exclui linhas. Alterações de constraints podem falhar por dados incompatíveis e exigir locks.

A única migração nova desta entrega é V20261030_01__assisted_quote.sql:

- orders.assisted_quote JSONB, nullable, sem backfill;
- orders.request_key VARCHAR(36), nullable, índice único;
- manual_supplier_order_refs com FK, unicidade do ID do fornecedor e chave composta.

Scripts anteriores publicados como recursos, já rastreados no repositório: V20260820_01, _02, _03; V20260821_01; V20261014_01; V20261015_01. Não reaplicá-los indiscriminadamente em produção. O wrapper ops/controlled-migration.sql aplica SOMENTE a migração nova, em uma transação, com lock_timeout=5s e statement_timeout=30s; se a base anterior faltar, aborta. Não apaga/recria tabelas. O índice pode bloquear escrita brevemente: executar em janela com backend parado e sem outras instâncias escrevendo.

Cotações antigas sem contextDigest não serão aceitas para cobrança/envio. Recriar pedidos não pagos; reconciliar manualmente os já pagos. Não gerar digest ou aceite retroativo.

## Backup e ensaio de restauração — operador, antes da migração

1. Registrar deployment IDs atuais Railway/Vercel e versão PostgreSQL com SHOW server_version. Pausar auto deploy de ambos antes do push e interromper escritas durante a janela final. Manter o registro dos webhooks para reprocessamento se houver indisponibilidade.
2. Criar backup Railway no painel e verificar conclusão. Fazer também dump lógico completo com cliente pg_dump da mesma major do servidor ou mais novo. Usar host/porta do proxy autorizado ou executar dentro da rede Railway; não colocar URL com senha na linha de comando.
3. Em uma sessão PowerShell de operação, fora de gravação/transcript, preencher parâmetros não secretos por prompt. pg_dump -W solicita senha sem mostrá-la. Guardar o backup criptografado/ACL restrita fora do repositório; contém dados pessoais.

```powershell
$env:PGHOST = Read-Host 'Host PostgreSQL autorizado'
$env:PGPORT = Read-Host 'Porta PostgreSQL'
$env:PGDATABASE = Read-Host 'Banco PostgreSQL'
$env:PGUSER = Read-Host 'Usuario PostgreSQL'
$env:PGSSLMODE = 'require'
$backupFile = Read-Host 'Caminho absoluto seguro para backup .dump, fora do repositorio'
& 'C:/Program Files/PostgreSQL/18/bin/pg_dump.exe' -W --format=custom --file $backupFile
if ($LASTEXITCODE -ne 0) { throw 'Backup falhou: nao migrar' }
Get-FileHash -Algorithm SHA256 -LiteralPath $backupFile
& 'C:/Program Files/PostgreSQL/18/bin/pg_restore.exe' --list $backupFile
if ($LASTEXITCODE -ne 0) { throw 'Arquivo de backup invalido: nao migrar' }
```

4. Restaurar o dump em banco NOVO, isolado e restrito, nunca apontando pg_restore para produção. pg_restore --list sozinho não comprova restauração. Usar um ambiente protegido: cópia de produção contém dados reais, não usar na navegação de teste; anonimizar antes de qualquer teste funcional. Conferir esquema, contagens, soma dos totais e estados por tabela antes/depois. Nunca publicar dump ou logs com dados pessoais.
5. No clone, executar o wrapper abaixo e iniciar o JAR com controlled e credenciais sintéticas. Confirmar validate, HTTP 200 e ausência de DDL/DML de inicialização. Se faltar estrutura anterior, preparar migração específica revisada; NÃO ligar update para contornar.
6. Somente com restauração e ensaio aprovados, parar backend de produção, refazer dump final após parar escritas, e aplicar o mesmo wrapper. Para produção, reconfirmar PGHOST/PGDATABASE com o operador; os comandos abaixo não foram executados remotamente.

```powershell
# A partir da raiz; PG* devem apontar para o alvo previamente conferido.
& 'C:/Program Files/PostgreSQL/18/bin/psql.exe' -X -W -v ON_ERROR_STOP=1 -f backend/ops/controlled-migration.sql
if ($LASTEXITCODE -ne 0) { throw 'Migracao falhou: manter backend parado e investigar' }
```

Não usar pg_restore --clean, dropdb, Hibernate create/create-drop nem rollback SQL destrutivo. Guardar evidência do backup, restauração e transação aplicada. O PostgreSQL real não foi inspecionado nesta execução; esses são gates operacionais obrigatórios, não etapas já realizadas.

## Commit e push — somente execução manual

O manifesto DEPLOY-CONTROLADO-ARQUIVOS.txt contém os arquivos exatos desta entrega; exclui .env.local.ps1, homologation.local.ps1, .env.local, target, node_modules e next-env.d.ts sem alteração funcional. Pausar auto deploy antes do push.

```powershell
git switch -c release/cotacao-assistida-controlada
$releaseFiles = Get-Content .\backend\DEPLOY-CONTROLADO-ARQUIVOS.txt
git add -- $releaseFiles
git diff --cached --check
git diff --cached --stat
# Revisar o conteudo staged localmente, sem compartilhar secrets.
git commit -m "feat: cotacao assistida com publicacao controlada e pagamentos bloqueados"
git push -u origin release/cotacao-assistida-controlada
```

Não fazer git add . nem enviar .local.ps1. Push da branch não é autorização para promover Production. Revisar PR/merge e selecionar publicação manual na sequência abaixo.

## Ordem de publicação

1. Pausar auto deploy; registrar versões anteriores; revisar variáveis e overrides. Confirmar inexistência de links antigos relevantes para o ensaio.
2. Backup + restauração isolada + ensaio de migração/validate. Configurar gates Railway/Vercel conforme tabela.
3. Parar escritas, backup final, aplicar somente migração nova.
4. Publicar BACKEND com perfil controlled, validate e SQL init never; preservar credenciais. Verificar log do perfil, GET /api/products = 200 e POST autenticado /api/orders/{id}/payments = 409 sem checkoutUrl. Verificar zero nova tentativa/preferência associada ao teste e sem chamada createPreference. Não basta observar a ausência de botão.
5. Só então publicar FRONTEND com build Production e pagamentos false. Verificar domínio, API e CORS. Não usar o perfil local homologation no Railway.
6. Executar roteiro abaixo; registrar IDs internos e capturas sem dados pessoais. Manter bloqueios após o ensaio. Liberar Mercado Pago somente em entrega posterior, após homologação externa e autorização explícita.

## Roteiro no site, sem pagamento

Usar conta cliente exclusiva de homologação e administrador autorizado, em sessões separadas. Não usar contas/pedidos de clientes existentes. Não alterar funções de usuários reais. Para criar administrador de teste, usar procedimento administrativo já adotado, sem endpoint de promoção novo.

1. Em /produtos, selecionar uma variante AliExpress ativa de produto com fulfillment DROPSHIPPING. Usar um produto dedicado marcado HOMOLOGACAO, preservando SKU e vínculo de variante reais; não alterar preço/estoque de item vendido a clientes só para o teste. Registrar slug, ID do produto e supplierVariantId. Nenhum ID de produção foi presumido.
2. Cliente: /login; em /conta#addresses cadastrar destinatário fictício "Cliente Homologacao", CEP 89229040, "Rua Homologacao", número 10, Joinville/SC, complemento "TESTE SEM ENVIO". CPF/data/telefone: usar exclusivamente fixtures sintéticas aprovadas pela equipe (as dos testes locais estão em AssistedQuoteIntegrationTests); nunca dados de cliente. Não realizar envio para esse endereço.
3. Abrir /produtos/{slug}, escolher variante e quantidade 1; adicionar ao carrinho; seguir /checkout. Confirmar endereço e destinatário. Solicitar cotação. Esperado: pedido interno WAITING, frete/total "A confirmar", nenhum checkout Mercado Pago. Repetir clique/requisição com mesmo requestKey não deve duplicar pedido.
4. Admin: /admin/pedidos → /admin/pedidos/{id}. Painel "Cotação assistida AliExpress". Conferir preço completo, impostos e frete/endereço no portal sem criar ordem ou pagar. Preencher cada SKU exatamente uma vez nas remessas, código e nome logísticos, origem, frete, prazo, custo total, validade (por exemplo 2 horas) e referência de evidência. Só marcar conferência se realmente feita. Total da venda = subtotal do pedido + fretes, custo do fornecedor <= total. Se portal recusar endereço fictício, não atestar preço real: parar essa etapa e usar evidência sintética apenas em ambiente isolado; não confirmar falsamente no site.
5. Cliente: /conta/pedidos/{id}; conferir total/remessas e clicar no aceite. Esperado CONFIRMED → ACCEPTED. O pedido continua PENDING_PAYMENT. Mensagem "Pagamento indisponível" e nenhum botão/link Mercado Pago.
6. No DevTools, usando a sessão do próprio cliente, verificar POST /api/orders/{id}/payments com {"method":"MERCADO_PAGO"}: HTTP 409, mensagem de indisponibilidade e nenhum checkoutUrl. Não abrir links antigos. Confirmar também que usuário não proprietário não acessa/aceita o pedido e cliente não confirma cotação via endpoint admin.
7. Admin: pedido não pago não oferece encaminhamento. POST manual-claim/recheck de pedido não pago deve falhar; não chamar manual-complete nem inventar IDs de fornecedor. Não marcar PAID no banco nem fabricar webhook em produção.
8. Usar OUTRO pedido sintético para expiração: validade curta (formulário mínimo 0,1 hora = 6 minutos), aguardar expirar e confirmar aceite recusado. Novo endereço/carrinho exige novo pedido/cotação: alteração no catálogo ou no cadastro de endereço não reescreve o snapshot existente. Não editar JSON do banco para testar mutações em produção; cobertura de alteração do snapshot é local.
9. Manter pedidos de teste identificados e sem pagamento; usar cancelamento administrativo suportado ou deixar expirar, sem apagar registros. Verificar que os pagamentos continuam bloqueados para toda a loja, inclusive produtos de estoque local.

### Endpoints

| Etapa | Endpoint |
|---|---|
| Solicitar | POST /api/orders, shippingCode=ASSISTED_QUOTE, requestKey UUID |
| Cliente consulta | GET /api/orders/{id} |
| Admin consulta | GET /api/admin/orders/{id} |
| Auditoria cotação | GET /api/admin/orders/{id}/quote |
| Confirmar | POST /api/admin/orders/{id}/quote |
| Aceitar | POST /api/orders/{id}/quote/accept, revision + expectedTotal |
| Pagamento BLOQUEADO | POST /api/orders/{id}/payments |
| Reconferência após pagamento, fora deste ensaio | POST /api/admin/orders/{id}/quote/recheck |
| Encaminhamento após pagamento, fora deste ensaio | POST /api/admin/orders/{id}/quote/manual-claim |

## Rollback

- Falha da migração: a transação é revertida, não iniciar nova aplicação; investigar causa/lock e repetir somente após revisão. Não apagar tabelas nem remover colunas para tentar novamente.
- Falha do frontend: voltar a um frontend anterior mantendo o backend atual protegido. A interface antiga pode mostrar botão, mas o backend deve continuar respondendo 409. Revalidar gate após rollback.
- Falha do backend: interromper tráfego/escritas e manter pagamentos indisponíveis. Preferir uma versão de contingência que CONTENHA o mesmo bloqueio. Não voltar cegamente ao backend antigo: ele ignora PAYMENT_CREATION_ENABLED e pode voltar a cobrar. Se não houver artefato anterior com gate, manter backend parado até correção; não promover uma imagem insegura para recuperar disponibilidade.
- O rollback Railway pode restaurar variáveis anteriores junto com a imagem: reconferir todos os gates e validate/never antes de reabrir o serviço. A reversão Vercel usa build anterior e suas variáveis incorporadas: conferir flag/URL novamente.
- Manter colunas/tabela aditivas após rollback de aplicação. Restaurar dump somente em um banco novo e reconciliar escritas ocorridas após o backup; nunca sobrescrever produção com dump antigo como primeiro recurso.

Referências: [backup e restore Railway](https://docs.railway.com/guides/postgres-backups-restores), [rollback Railway](https://docs.railway.com/guides/roll-back-bad-deploy), [Instant Rollback Vercel](https://vercel.com/docs/instant-rollback), [variáveis Vercel](https://vercel.com/docs/environment-variables).
## Evidências locais da versão preparada

- Regressão Maven completa: 657 testes reportados, 0 falhas, 0 erros, 1 probe externo ignorado; BUILD SUCCESS.
- Após os últimos ajustes de configuração: 4 testes de bloqueio/configuração aprovados e novo BUILD SUCCESS/JAR.
- Frontend: 50 testes aprovados; build de produção com TypeScript aprovado; lint sem erros, com 4 avisos existentes.
- Wrapper psql aplicado duas vezes com sucesso no PostgreSQL 18.4 exclusivo 127.0.0.1:52639/ingarage_homologation.
- Manifesto com 61 arquivos existentes, sem suspeitas nos padrões de credenciais pesquisados. Arquivos locais de segredos permanecem ignorados. git diff --check aprovado.
- A API de criação de pagamento retorna HTTP 409 sem checkoutUrl com a flag desligada; os dois pontos de entrada falham antes de acesso ao banco/gateway. A interface não renderiza botão de pagamento com flag ausente ou false.

Logs locais (não publicar): backend/target/homologation-final/controlled-publication-*.log. Nenhum commit/push/deploy, pagamento, preferência Mercado Pago, ordem AliExpress ou acesso ao banco de produção foi executado nesta preparação. As configurações remotas ainda devem ser conferidas pelo operador; backup/restore Railway e teste no domínio são passos posteriores à preparação local.
- JAR final iniciado duas vezes com perfil controlled, Hibernate validate, SQL init never e PostgreSQL forçado a default_transaction_read_only=on: HTTP 200 nas duas execuções. Aplicações temporárias e cluster isolado encerrados; dados preservados.

**Resultado: PRONTO PARA DEPLOY CONTROLADO da cotação até o aceite, condicionado aos gates operacionais manuais deste procedimento. Pagamentos de produção não estão liberados.**
