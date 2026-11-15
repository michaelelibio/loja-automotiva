# Auditoria de lançamento — inGarage v0.0.1

Data: 15/11/2026. Horários em Brasília (UTC−03). Verificações de produção entre 14:42 e 14:55.

## 1. Resumo executivo

**Veredito: NÃO APTO PARA LANÇAMENTO.** A loja atende o fluxo principal de compra e possui automação AliExpress publicada, mas há pendências de segurança, tratamento financeiro e operação que impedem recomendar a abertura oficial para novas vendas. Isso é uma avaliação de prontidão; esta auditoria não suspendeu a loja nem modificou produção.

Os principais impedimentos são:

1. Estornos e chargebacks não são representados corretamente no estado financeiro persistido. O problema foi reproduzido em memória, sem pagamento ou chamada ao provedor.
2. Dependências publicadas possuem avisos de segurança. Há caminho de entrada plausível para ReDoS de Jackson no checkout; os alertas críticos de Next/Sharp exigem atualização e avaliação da superfície residual de imagens. Não foi demonstrada execução remota, invasão ou desvio de autorização.
3. Páginas públicas de contato, identificação do fornecedor e políticas ainda contêm informações pendentes. Não é seguro lançar sem dados comerciais e procedimentos confirmados.
4. A entrega de webhooks precisa de confirmação operacional documentada. A URL por preferência não está configurada e não há reconciliação periódica de pagamentos pendentes. Existe evidência histórica de recebimento válido; isso não prova que a configuração global esteja incorreta.

**Sobre a pergunta anterior: SIM, o mecanismo automático de criação no AliExpress está publicado e habilitado.** Depois de um webhook válido, o backend consulta o pagamento no Mercado Pago, confirma a associação/valor/moeda, persiste a aprovação e encaminha os itens AliExpress. Não depende de botão administrativo no caminho normal. **Não é possível afirmar operação sem intervenção em todas as falhas:** respostas incertas de criação exigem reconciliação, erros técnicos podem exigir correção, e as tentativas temporárias são limitadas. A sincronização posterior do fornecedor está desabilitada.

Preço, frete, impostos, margem negativa e cotação financeira incompleta **não bloqueiam o envio** no código publicado. Permanecem validações técnicas, de aprovação real do pagamento e de duplicidade. O pagamento ao AliExpress continua exclusivamente manual.

Nenhuma correção de aplicação, commit, push, migração de produção, deploy, pagamento, envio de e-mail ou criação de pedido real foi executado nesta auditoria. A etapa seguinte deve corrigir os achados críticos/altos separadamente, conforme solicitado.

## 2. Escopo, versão publicada e método

### Versão efetiva

| Componente | Evidência consultada | Resultado |
|---|---|---|
| Código revisado | HEAD local e metadados dos deployments | `92d12884316fe1d542de237ee4d0068708d6af40` |
| Railway — backend de produção | Deployment ativo `5fa40d21-cb9c-4755-ac1b-328d3fcac32c` | `SUCCESS`, mesmo commit |
| Vercel — domínio público | Deployment `dpl_9ASg8wTWqjvpfnbkntmTxSDMTUHJ` | `READY`, mesmo commit |
| Bibliotecas embarcadas no backend | Listagem do JAR dentro do container ativo | 99 bibliotecas; nomes e versões coincidem com o JAR local inspecionado |
| PostgreSQL | Consultas somente leitura de schema, índices e estados | Disponível; oito colunas e duas tabelas da migração de dispatch presentes |

Foram usados revisão de código, testes locais com mocks, PostgreSQL descartável local, consultas administrativas de leitura, metadados das plataformas, GET/HEAD/OPTIONS públicos, leitura limitada de logs e consultas de vulnerabilidades. As consultas administrativas usaram credencial de auditoria efêmera em memória; segredos e dados pessoais não foram incluídos neste documento.

Não houve teste de carga, payload malicioso, exploração de CVE, repetição de webhook contra produção, alteração de destinatário ou reconciliação/criação do pedido #21. A confirmação de assinatura em produção veio de amostra histórica de logs, não de notificação fabricada.

O navegador de auditoria falhou com **“The browser access host is unavailable”**. Portanto, checkout visual desktop/celular, navegação autenticada completa, Google OAuth interativo, foco por teclado e acessibilidade visual **não foram validados em navegador**. Testes de código e respostas HTTP não substituem esses testes.

### Configuração observada

| Configuração | Produção | Interpretação |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `controlled` | Perfil publicado correto |
| `APP_PAYMENT_CREATION_ENABLED` / `PAYMENT_CREATION_ENABLED` | `true` / `true` | Criação de pagamento habilitada |
| `MERCADO_PAGO_CHECKOUT_SANDBOX` | Ausente; padrão `false` | Caminho real, não sandbox |
| `MERCADO_PAGO_DIAGNOSTIC_LOGGING` | `false` | Diagnóstico sensível desativado |
| `ALIEXPRESS_ORDERS_ENABLED` | `true` | Criação habilitada |
| `ALIEXPRESS_ORDERS_CONTRACT_CONFIRMED` | `true` | Contrato de criação confirmado |
| `ALIEXPRESS_AUTO_DISPATCH_ENABLED` | `true` | Dispatch automático habilitado |
| Autorização de `aliexpress.trade.buy.placeorder` | `EVIDENCE_CONFIGURED`; `realCreationAllowed=true` | Evidência vinculada à aplicação presente |
| `ALIEXPRESS_ORDER_SYNC_ENABLED` / `...CONTRACT_CONFIRMED` | `false` / `false` | Consulta/sincronização posterior desabilitada |
| `ALIEXPRESS_ADMIN_TEST_REAL_ENABLED` | `true` | Ferramenta administrativa de criação real habilitada; exige ADMIN |
| `ACCOUNT_EMAIL_MODE` | `resend` | Domínio do remetente confirmado como verificado por GET na Resend |
| `JPA_DDL_AUTO` | `validate` | Sem alteração automática do schema pelo Hibernate |
| `JWT_EXPIRATION` | Ausente; padrão `PT1H` | Tokens duram uma hora |
| Polling de dispatch | Ausente; padrão 60.000 ms | Recuperação a cada aproximadamente um minuto |
| URL de notificação Mercado Pago | Não configurada nas variáveis/overrides inspecionados | Novas preferências omitem `notification_url`; conferir configuração global do aplicativo |

Credenciais necessárias estavam presentes; a chave JWT satisfaz o tamanho mínimo verificado. Nenhum valor de credencial foi publicado. `FRONTEND_URL` usa `https://ingarage.com.br`, origem válida; CORS permite explicitamente o domínio com e sem `www`. A diferença entre essas origens não foi tratada como defeito.

## 3. Achados classificados e correções recomendadas

As severidades abaixo consideram impacto e aplicabilidade na inGarage. A classificação de um scanner é registrada separadamente: pacote afetado não equivale a exploração comprovada.

### CRÍTICA — nenhum comprometimento crítico confirmado

Não foi demonstrada falha crítica explorável da aplicação. O `npm audit` contém pacote classificado como crítico e o OSV contém avisos críticos; suas precondições estão discriminadas na seção de dependências. Esses resultados não autorizam afirmar que houve RCE, vazamento ou acesso administrativo indevido.

### A01 — ALTA: estorno e chargeback não atualizam corretamente o pagamento

**Evidências:**

- [MercadoPagoCheckoutProGateway.java](../backend/src/main/java/com/garage/garageapi/payment/gateway/MercadoPagoCheckoutProGateway.java), linhas 216–224: `mapStatus` não aceita `refunded` nem `charged_back`; lança exceção de status não suportado.
- [PaymentStatus.java](../backend/src/main/java/com/garage/garageapi/payment/entity/PaymentStatus.java), linha 3: não representa esses estados.
- [Payment.java](../backend/src/main/java/com/garage/garageapi/payment/entity/Payment.java), linhas 174–180: um pagamento `PAID` permanece pago ao receber outro estado. O eventual preenchimento de `paidAt` não altera esse comportamento.
- [CheckoutProWebhookUpdater.java](../backend/src/main/java/com/garage/garageapi/payment/service/CheckoutProWebhookUpdater.java), linhas 31–56: não persiste o valor estornado no estado financeiro; aprovação com estorno parcial também demanda tratamento específico.

**Impacto:** ADM e processo financeiro podem continuar indicando pagamento integral após reversão. A validação AliExpress consulta novamente o Mercado Pago e exige estorno zero antes de uma nova criação, o que é uma proteção positiva, mas não corrige o livro financeiro nem pedidos já enviados.

**Reprodução local:** o gateway rejeitou os dois estados; um pagamento com estado e data de aprovação preenchidos permaneceu `PAID` após sincronização com `CANCELED`. Nenhum estorno real foi feito. O Mercado Pago documenta estados de reembolso/chargeback e distingue cancelamento de reembolso após captura. [Estados](https://www.mercadopago.com.br/developers/en/docs/checkout-api-orders/resources/migrate-payments-to-orders), [reembolsos e cancelamentos](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro-preferences/additional-settings/refunds-and-cancellations).

**Correção:** modelar reversões totais/parciais, chargebacks e seus valores; persistir eventos idempotentes, aceitar notificações fora de ordem e reconciliar com o provedor. Definir tratamento administrativo quando o fornecedor já tiver pedido criado. Cobrir transições e duplicatas em testes.

### A02 — ALTA: Jackson vulnerável com entrada plausível no checkout

**Evidências:** `jackson-core` e `jackson-databind` `3.1.4` estão no JAR de produção. [CreateOrderRequest.java](../backend/src/main/java/com/garage/garageapi/order/dto/CreateOrderRequest.java), linha 17, recebe `expectedTotal` como `BigDecimal`; [OrderController.java](../backend/src/main/java/com/garage/garageapi/order/controller/OrderController.java), linhas 28–30, desserializa o corpo autenticado de `/api/orders`.

No source JAR do Jackson, `NumberDeserializers.java:1126` chama `NumberInput.looksLikeValidNumber` para string numérica antes do limite de ponto flutuante. Não foi encontrada configuração que proíba essa coerção. Bean Validation ocorre depois da desserialização.

**Impacto:** caminho plausível para consumo excessivo de CPU por entrada autenticada, conforme [GHSA-p6pp-m3f8-5c89](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-p6pp-m3f8-5c89). Não houve payload de DoS nem medição de exploração.

**Correção:** atualizar o conjunto Jackson de forma compatível com o BOM, no mínimo para a correção `3.1.7` dessa linha, e revisar limites/coerção de corpos. Repetir auditoria e testes; não considerar esse número suficiente para avisos futuros.

### A03 — ALTA: dependências frontend afetadas e superfície residual de imagens

**Evidências:** [package-lock.json](../frontend/package-lock.json) fixa Next `16.3.0`, Sharp `0.35.3` e libvips Linux `1.3.2`. `npm audit`: **14 pacotes afetados — 1 crítico, 5 altos, 8 moderados**. [next.config.mjs](../frontend/next.config.mjs), linhas 2–10, não desabilita globalmente o otimizador.

Os componentes encontrados usam `next/image` com `unoptimized`, ou `<img>` nativo. Isso reduz o uso normal do otimizador, mas não prova remoção de `/_next/image`. AVIF/SVG têm precondições de processamento de imagem não confiável; o padrão `dangerouslyAllowSVG=false` restringe o caminho ordinário de SVG. Não foi confirmada entrada maliciosa alcançável nem inspecionado o binário privado do serviço gerenciado de imagens da Vercel.

**Correção:** atualizar Next para versão suportada corrigida, no mínimo `16.4.1` para os avisos consultados, garantir Sharp ≥`0.35.5` no lock e avaliar/desabilitar superfícies de imagens não utilizadas. Atualizar também ferramentas de build e repetir a matriz de validação. Não executar `audit fix --force` sem revisão. [Next/AVIF](https://github.com/vercel/next.js/security/advisories/GHSA-2xp9-vwfh-vxw4), [Sharp/AVIF](https://github.com/lovell/sharp/security/advisories/GHSA-rgj7-g3m4-5g8c), [Sharp/SVG](https://github.com/lovell/sharp/security/advisories/GHSA-wq5f-xc86-pv6w).

### A04 — ALTA, operacional: identificação, contato e políticas incompletos

**Evidências:**

- [contato/page.tsx](../frontend/app/contato/page.tsx), linhas 9–12: identificação/canal/endereço pendentes.
- [termos/page.tsx](../frontend/app/termos/page.tsx), linha 17: identificação comercial pendente.
- [trocas-e-devolucoes/page.tsx](../frontend/app/trocas-e-devolucoes/page.tsx), linhas 10–14: canal, logística de devolução e procedimentos ainda não definidos.
- [privacidade/page.tsx](../frontend/app/privacidade/page.tsx), linhas 12–20: texto genérico, sem contato do controlador confirmado e sem explicação suficiente sobre CPF/nascimento e compartilhamento com fornecedor.
- [entrega/page.tsx](../frontend/app/entrega/page.tsx), linha 12: informação de prazo/contagem ainda incompleta.

As rotas retornam HTTP 200; isso não valida seu conteúdo. A identificação e o atendimento eletrônico são relevantes à operação de comércio eletrônico, assim como transparência sobre tratamento de dados. [Decreto 7.962/2013 — fonte oficial](https://bibliotecadigital.mj.gov.br/bitstream/1/8424/2/DEC_PR_2013_7962.htm), [LGPD atualizada — Câmara](https://www2.camara.leg.br/legin/fed/lei/2018/lei-13709-14-agosto-2018-787077-normaatualizada-pl.html).

**Correção:** preencher dados reais confirmados de identificação, inscrição fiscal quando aplicável, contato/endereço, entrega, devolução, arrependimento, reembolso e privacidade. Não inventar CNPJ, endereço, prazo ou base legal. Validar os procedimentos com o responsável pelo negócio. Este é um impedimento operacional de lançamento, não uma exploração de software.

### M01 — MÉDIA: JWT permanece válido após reset/troca de senha e logout

**Evidências:** [AccountSecurityService.java](../backend/src/main/java/com/garage/garageapi/auth/service/AccountSecurityService.java), linhas 128–147; [AuthController.java](../backend/src/main/java/com/garage/garageapi/auth/controller/AuthController.java), linhas 74–76; [SecurityConfig.java](../backend/src/main/java/com/garage/garageapi/auth/config/SecurityConfig.java), linhas 57–60 e 79–88; [JwtService.java](../backend/src/main/java/com/garage/garageapi/auth/service/JwtService.java), linha 32.

Assinatura, emissor e expiração são validados, mas tokens anteriores não são revogados. A autorização administrativa usa o papel do token sem revalidar em geral a situação/papel atual do usuário. TTL efetivo padrão: uma hora.

**Correção:** versão de credenciais/sessão ou revogação adequada, com revalidação de permissões administrativas e testes após reset, logout, desativação e retirada de papel. Não foi demonstrado roubo de token. [OWASP Session Management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).

### M02 — MÉDIA: autenticação e recuperação sem limite implementado no backend

**Evidências:** `SecurityConfig.java:72–74`, `AuthController.java:36–63`, `AuthService.java:77–94` e `AccountSecurityService.java:102–113,150–153`, nos diretórios de autenticação acima.

Não foi encontrado rate limit/cooldown nessas rotas. Recuperação repetida pode enviar mensagens e invalidar links anteriores. Proteções externas de WAF não foram confirmadas; não houve tentativa de força bruta.

**Correção:** limites por IP/conta, cooldown, respostas neutras e métricas, preservando recuperação legítima. [OWASP Authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html).

### M03 — MÉDIA: token persistente no navegador e cabeçalhos incompletos

**Evidências:** [tokenStorage.ts](../frontend/lib/auth/tokenStorage.ts), linhas 5 e 13, guarda JWT em `localStorage`. `next.config.mjs:2–10` não configura cabeçalhos. No frontend publicado, HEAD confirmou ausência de CSP, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` e `Permissions-Policy`; HSTS está presente. Não foi encontrado sink evidente de HTML arbitrário nesta revisão.

Na resposta HEAD da API observada, `nosniff` e `DENY` estavam presentes, HSTS ausente; esse HEAD recebeu 401 porque a liberação pública revisada é para GET. O GET de produtos recebeu 200. Não confundir esse 401 com indisponibilidade do catálogo.

**Correção:** política de cabeçalhos/CSP testada, reduzir exposição de tokens e revisar proteção CSRF se a estratégia migrar para cookies. Hoje os fluxos revisados usam Bearer e API stateless; CSRF desabilitado precisa ser reavaliado se cookies autenticadores forem introduzidos.

### M04 — MÉDIA: usuário da aplicação é superusuário do banco

**Evidência de produção:** consulta de `pg_roles` para o usuário configurado retornou `superuser=true`, `createRole=true`, `createDb=true`.

**Impacto:** uma eventual falha de aplicação teria alcance desnecessário no banco. Não houve tentativa de usar esses privilégios.

**Correção:** conta restrita às operações necessárias, separar conta de migração, revisar ownership/permissões e validar acesso após a mudança.

### M05 — MÉDIA: entrega de webhook sem configuração por preferência confirmada e sem recuperação de pagamentos pendentes

**Evidências:** [MercadoPagoCheckoutProGateway.java](../backend/src/main/java/com/garage/garageapi/payment/gateway/MercadoPagoCheckoutProGateway.java), linhas 108–109, só inclui `notification_url` quando configurada. Variáveis canônicas/legadas e overrides inspecionados não a definem. [PaymentWebhookService.java](../backend/src/main/java/com/garage/garageapi/payment/service/PaymentWebhookService.java), linha 18, consulta o provedor ao receber notificação; não foi encontrado job periódico que descubra aprovação dos pagamentos ainda pendentes.

Amostra histórica de deployment anterior contém validação de assinatura aceita. A configuração global de webhooks no painel Mercado Pago **não foi lida independentemente**. Portanto, não há prova de que a entrega esteja quebrada; há dependência não documentada e lacuna de recuperação se todas as notificações forem perdidas. Os 11 registros pendentes no banco não foram considerados pagos nem diagnosticados como erro sem consulta individual.

**Correção:** confirmar/documentar aplicação, ambiente e URL global ou configurar explicitamente a URL por preferência; adicionar reconciliação limitada e idempotente de pendências. Validar entrega em ambiente de teste sem gerar venda real nesta auditoria.

### M06 — MÉDIA: recuperação AliExpress limitada e status posterior incompleto

**Evidências:** [AliExpressDispatchWorker.java](../backend/src/main/java/com/garage/garageapi/integration/aliexpress/AliExpressDispatchWorker.java), linhas 21–30; [OrderFulfillment.java](../backend/src/main/java/com/garage/garageapi/order/fulfillment/OrderFulfillment.java), linhas 150–161 e 199–207; [AliExpressOrderService.java](../backend/src/main/java/com/garage/garageapi/integration/aliexpress/AliExpressOrderService.java), linhas 115–126; [AliExpressOrderSyncService.java](../backend/src/main/java/com/garage/garageapi/integration/aliexpress/AliExpressOrderSyncService.java), linha 34.

Falhas temporárias antes do envio têm no máximo três verificações, com reagendamento de 60/120 segundos. Depois disso, não há próximo horário automático. Resposta incerta após envio, timeout ou interrupção entre envio e persistência exige reconciliação e não pode ser reenviada cegamente. Esse bloqueio protege contra duplicidade; o ponto incompleto é a operação de resolução/alerta.

Flags de sincronização de status estão desligadas. Na publicação anterior, consulta do fornecedor retornou HTTP 200 com código de aplicação `401`, sem dados; essa chamada não foi repetida nesta auditoria. [AliExpressOrderQueryClient.java](../backend/src/main/java/com/garage/garageapi/integration/aliexpress/AliExpressOrderQueryClient.java), linha 67, não entrega rastreio/transportadora no modelo revisado; [AliExpressSupplierSnapshot.java](../backend/src/main/java/com/garage/garageapi/integration/aliexpress/AliExpressSupplierSnapshot.java), linhas 20–39, não fornece uma projeção completa do pós-venda. Não há fluxo automático completo até `SHIPPED/DELIVERED` da loja.

**Correção:** alertas e procedimento de reconciliação; validar contrato/permissão de consulta antes de habilitar flags, mapear estados, envio e rastreio. Nunca substituir reconciliação por repetição insegura da criação.

### M07 — MÉDIA: chamadas externas sem timeout explícito e efeitos síncronos

**Evidências:** `MercadoPagoCheckoutProGateway.java:50`, [ResendEmailClient.java](../backend/src/main/java/com/garage/garageapi/shared/email/ResendEmailClient.java), linha 25, usam `RestClient.builder` sem limites explícitos de conexão/leitura; [PaymentAttemptService.java](../backend/src/main/java/com/garage/garageapi/payment/service/PaymentAttemptService.java), linhas 91–106, faz chamada externa dentro de transação com bloqueio; [OrderEmailNotificationService.java](../backend/src/main/java/com/garage/garageapi/order/email/OrderEmailNotificationService.java), linha 17, envia após commit de forma síncrona.

**Impacto:** dependência lenta pode reter recursos/atrasar resposta e callback de dispatch. O polling persistido é uma recuperação parcial, não elimina retenção de conexão/thread.

**Correção:** timeouts explícitos por cliente, limites de concorrência e separação dos efeitos externos por mecanismo durável, com política de retry compatível com cada idempotência. Não foi provocado timeout em produção.

### M08 — MÉDIA: observabilidade, migrações e restauração precisam de comprovação

**Evidências:** [AdminHealthController.java](../backend/src/main/java/com/garage/garageapi/admin/AdminHealthController.java), linhas 12–14, retorna estado simples sem verificar DB/fila/provedores. Não foi encontrado fluxo completo de métricas/alertas para fila parada. A amostra limitada de logs atuais teve zero erros; isso não comprova ausência de incidentes.

Produção usa validação de schema e não inicializa SQL automaticamente; o perfil padrão ainda permite `ddl-auto=update`/inicialização, criando risco se o perfil errado for usado. Migrações são SQL manuais, sem ledger Flyway/Liquibase encontrado. Backup/PITR, retenção e restauração não foram comprovados; não se conclui que backups inexistem.

**Correção:** readiness com dependências essenciais, alertas de pagamento aprovado sem fornecedor e falhas de fila, migrações versionadas/aplicação rastreável, evidência de backup e ensaio de restore isolado. Reinicialização de produção não foi feita.

### M09 — MÉDIA, condicional: catálogo paginado tratado como catálogo completo

**Evidências:** [products.ts](../frontend/lib/products.ts), linhas 240–247, solicita `/api/products` sem percorrer páginas. [CheckoutForm.tsx](../frontend/app/checkout/CheckoutForm.tsx), linha 58, e [CartView.tsx](../frontend/app/carrinho/CartView.tsx), linha 15, reconciliam itens com esse conjunto. [ProductController.java](../backend/src/main/java/com/garage/garageapi/product/controller/ProductController.java), linha 24, usa tamanho padrão 20.

**Impacto:** com mais de 20 produtos, um item válido fora da primeira página pode desaparecer da reconciliação do carrinho/checkout. Na consulta atual há quatro produtos públicos; não foi observada perda atual por essa condição.

**Correção:** buscar explicitamente os produtos do carrinho ou tratar paginação completa, sem remover itens por resposta incompleta. Testar item além da primeira página e falha de rede.

### M10 — MÉDIA: retenção e governança de dados pessoais não demonstradas

**Evidências:** [Order.java](../backend/src/main/java/com/garage/garageapi/order/entity/Order.java), linhas 108–117, persiste CPF e nascimento em campos de negócio convencionais. O endpoint administrativo mascara CPF e retorna presença do nascimento, com auditoria de correções e sanitização; isso é positivo. Tokens OAuth usam AES-GCM.

Não foi comprovada política de retenção/exclusão, controle de acesso aos backups ou processo de atendimento ao titular. Armazenamento em campos convencionais, sozinho, não demonstra descumprimento da LGPD nem ausência de criptografia no volume gerenciado.

**Correção:** documentar finalidade, acesso, compartilhamento internacional aplicável, retenção, descarte e atendimento ao titular; avaliar proteção adicional de campos/backups conforme risco. [Direitos do titular — gov.br](https://www.gov.br/mcti/pt-br/acesso-a-informacao/lei-geral-de-protecao-de-dados-pessoais-lgpd/direito-dos-titulares).

### B01 — BAIXA: falhas de rede confundidas com vazio/404

**Evidências:** `CartView.tsx:16–26` não diferencia adequadamente carregamento/erro de carrinho vazio; [produtos/[slug]/page.tsx](../frontend/app/produtos/[slug]/page.tsx), linhas 15–16, converte exceções de API em `notFound`.

**Correção:** apresentar carregamento, falha recuperável e nova tentativa; reservar 404 para produto realmente inexistente. Isso evita ocultar indisponibilidade como ausência de produto.

### B02 — BAIXA: acessibilidade, SEO e crescimento de consultas

**Evidências:** modais de endereço/veículo/importação precisam de verificação de foco, Escape e retorno de foco; metadata em [layout.tsx](../frontend/app/layout.tsx), linhas 11–14, é genérica. Não foram encontrados sitemap/robots/canonical/metadata individual de produto completos.

[OrderService.java](../backend/src/main/java/com/garage/garageapi/order/service/OrderService.java), linhas 161–165, lista histórico do cliente sem paginação. [PaymentRepository.java](../backend/src/main/java/com/garage/garageapi/payment/repository/PaymentRepository.java), linhas 21–32, consulta histórico/estado recente; o índice composto correspondente a `(order_id, status, created_at, id)` não existe entre os índices consultados. Há índices únicos de pagamento equivalentes redundantes a revisar.

**Correção:** teste manual e automatizado de acessibilidade/SEO, paginação e análise de plano com volume representativo antes de criar/remover índices. Não foi medida query lenta; `pg_stat_statements` não estava habilitado. Acessibilidade visual permanece não validada.

### B03 — BAIXA, latente: diagnóstico pode registrar material de autenticação

**Evidências:** [MercadoPagoWebhookSignatureVerifier.java](../backend/src/main/java/com/garage/garageapi/payment/webhook/MercadoPagoWebhookSignatureVerifier.java), linhas 68–81, pode registrar assinatura/HMAC no modo diagnóstico. `application.properties:36` habilita por padrão; `application-controlled.properties:11` e a variável efetiva desabilitam em produção. [LoginRequest.java](../backend/src/main/java/com/garage/garageapi/auth/dto/LoginRequest.java), linha 8, é record sem redação de senha em `toString`, relevante caso logging detalhado seja ativado.

**Correção:** remover conteúdo sensível mesmo em diagnóstico e redigir DTOs; manter logs de corpo/bind desativados. Não foi constatado vazamento atual de CPF, nascimento ou senha real por esses caminhos.

## 4. Controles verificados nos fluxos críticos

### Segurança e autenticação

- `/api/admin/**` exige ADMIN no backend. Em produção, leitura sem autenticação retornou 401; token sem ADMIN recebeu 403; leitura administrativa autorizada recebeu 200.
- Pedidos, pagamentos, endereços e veículos usam consultas por `userId`: `OrderService.java:171`, `PaymentService.java:65`, `AddressService.java:87`, `VehicleService.java:81`. Não foi identificado IDOR nesses fluxos; nenhum dado de outra cliente foi alterado para testar.
- Repositórios revisados usam parâmetros/JPA, sem caminho demonstrado de SQL Injection. Entradas recebem validação; não foi encontrado sink evidente de XSS arbitrário no frontend. Isso não constitui prova universal de ausência de falhas.
- CORS real: origem pública permitida recebeu 200; origem externa de auditoria recebeu 403 e nenhum cabeçalho de permissão. HTTPS público redireciona com 308; frontend apresenta HSTS.
- Importação AliExpress restringe hosts/IDs; clientes usam destinos fixos e não seguem redirecionamentos nos caminhos revisados (`AliExpressProductInput.java:18`, `AliExpressProductClient.java:88`, `AliExpressOrderClient.java:96`). Não foi identificado SSRF nesses caminhos.
- Recuperação usa token armazenado por hash e consumo sob bloqueio. Validador Google verifica emissor, audiência, expiração e `email_verified` usando JWKS fixo. Login Google real e entrega de e-mail ao cliente não foram exercitados.
- A ferramenta administrativa de criação real está habilitada, mas não pública: passa por proteção ADMIN. Recomenda-se limitar essa capacidade operacional conforme necessidade; não foi invocada.

### Mercado Pago

- A notificação não define o pagamento como aprovado por seu próprio corpo: o backend valida assinatura e consulta diretamente a API do Mercado Pago.
- `CheckoutProWebhookUpdater.java:60–70` confere método, ID notificado/retornado, referência exata `garage_order_<orderId>_payment_<paymentId>`, moeda BRL e valor do pedido.
- O cliente não escolhe o preço final persistido: criação do pedido usa produtos/variantes do servidor, comparação do total e bloqueios de estoque. Não existe endpoint público revisado para marcar um pedido como pago.
- Atualização de pagamento usa bloqueio; a transição de pedido e o dispatch são idempotentes nos cenários repetidos testados. Pagamentos pendentes, recusados e cancelados possuem mapeamento inicial. Reversões posteriores são a lacuna A01.
- O dispatch AliExpress ainda faz uma consulta atual ao Mercado Pago e exige ambiente real, aprovação datada, valor/moeda/referência corretos e estorno zero (`AliExpressDispatchValidation.java:67–74`). Nenhum pedido pendente deve criar fornecedor apenas por receber um corpo de webhook falsificado.

### AliExpress: criação, fila e prevenção de duplicidade

1. `CheckoutProWebhookUpdater` confirma e persiste o pagamento. O encaminhamento é registrado como efeito após commit por `OrderFulfillmentNotificationService`.
2. `SupplierFulfillmentService` encaminha o fornecedor AliExpress ao worker. O caminho rápido usa executor de 1–2 threads, fila 50; a fila durável está no PostgreSQL, não apenas em memória.
3. `AliExpressDispatchWorker` recupera até 20 candidatos de cada armazenamento por ciclo de aproximadamente 60 segundos. Não havia candidato legado elegível vencido nem registro na fila secundária na leitura de produção.
4. `AliExpressOrderService.java:94–109` adquire o bloqueio comum do pedido, revalida contexto/destinatário/produto, registra token de tentativa e hash antes de chamar a API. O armazenamento separado preserva compatibilidade com fulfillments legados; ambos usam o mesmo bloqueio de ciclo de vida.
5. IDs retornados são persistidos e impedem novo envio. Webhooks repetidos e disputa entre envio manual/automático foram cobertos localmente. Timeout/resposta ambígua após submissão fica em verificação, sem retry automático da criação.

Essas proteções reduzem duplicação de chamadas sob controle da inGarage. Não é tecnicamente honesto prometer risco zero em toda combinação de falha distribuída ou criação externa manual: a conferência no fornecedor continua necessária quando a resposta é incerta. Não foi confirmada garantia externa de exactly-once do AliExpress.

`AliExpressOrderPreparation.java:35` valida nascimento; `:102` inclui `ds_extend_request.trade_extra_param.birthday`, formatado com o nascimento cadastrado. CPF/telefone/endereço vêm do snapshot do pedido. `AliExpressRecipientAdminService.java:35–88` apresenta máscara/presença, permite correção por administrador, preserva o cadastro geral e exige reconciliação antes de reenviar tentativa anterior. A auditoria não alterou nem inventou dado pessoal e não revalidou sua titularidade independentemente.

`AliExpressDispatchValidation.java:35–58` verifica conta compradora, produto, variante, quantidade/estoque. Custos são coletados quando disponíveis e expostos como alertas por `AliExpressOrderService.java:155–160`; não há comparação de margem que impeça criar. Ausência de total, frete ou imposto também não impõe aprovação financeira manual no caminho normal.

OAuth AliExpress possui armazenamento cifrado AES-GCM, expiração e refresh no código. Em produção havia access token vigente até **14/12/2026 18:28, horário de Brasília**, refresh e comprador presentes. Não foi forçada renovação nesta auditoria; concorrência/erros de integração foram avaliados por código e testes com simulação.

Não foi encontrado fluxo que pague automaticamente o fornecedor. O ADM registra ID, tentativas, estado e erros sanitizados; a confirmação de pagamento manual e o acompanhamento completo de envio ainda não são automatizados.

### Pedido #21 — somente leitura

| Campo | Resultado observado |
|---|---|
| ID AliExpress persistido | `8216810077868933` |
| Fulfillment | `CREATED` |
| Estado técnico persistido do fornecedor | `CREATED_UNPAID_UNVERIFIED` |
| Tentativas acumuladas | 2 |
| Reenvio disponível na API administrativa | `false` |
| Proteção manual registrada | `manualHold=true` |

A captura enviada anteriormente pelo usuário mostrava “Esperando pagamento”. Ela confirma o estado naquele momento, não uma sincronização ao vivo nesta auditoria. O código de estado local não foi atualizado com base na imagem. Nenhum novo pedido foi criado e nenhum pagamento foi efetuado.

## 5. Dependências, segredos e dados

### Dependências frontend

O resultado de `npm audit` tem 14 pacotes afetados, não 14 explorações demonstradas. Next `16.3.0`/Sharp `0.35.3` demandam atualização. React/React DOM estão em `19.2.8`; ESLint em `9.39.5` e eslint-config-next em `16.3.1` no lock consultado.

| Família de aviso | Aplicabilidade observada |
|---|---|
| RCE em Windows (`GHSA-p293-qw3h-jr36`) | Relevante ao desenvolvimento local Windows sob as condições do aviso; não fundamenta afirmar RCE no deploy Linux da Vercel. [Fonte](https://github.com/vercel/next.js/security/advisories/GHSA-p293-qw3h-jr36). |
| AVIF/SVG Next/Sharp | Versões afetadas; processamento de mídia não confiável e runtime importam. Superfície residual não foi demonstrada como explorável nem totalmente excluída. Ver A03. |
| SSRF do otimizador (`GHSA-cjq9-62q9-8jv4`) | Pré-condição `remotePatterns` ausente. [Fonte](https://github.com/vercel/next.js/security/advisories/GHSA-cjq9-62q9-8jv4). |
| `next/og`, `use cache`, Server Actions | Funcionalidades/precondições não encontradas; manifesto do build contém zero Server Actions Node/Edge. [next/og](https://github.com/vercel/next.js/security/advisories/GHSA-vcvr-r3jv-pc5j), [use cache](https://github.com/vercel/next.js/security/advisories/GHSA-673q-mjm6-r5xr). |
| Cache/SSG/ISR/metadata específicas | Pré-condições não encontradas nos caminhos revisados; aviso de Pages Router/self-hosting exclui Vercel. [Fonte](https://github.com/vercel/next.js/security/advisories/GHSA-4jqv-mc3x-m676). |
| Dev MCP (`GHSA-39w2-rjm5-chcv`) | Afeta somente `next dev`, não o deployment de produção. Relevante ao ambiente de desenvolvimento. [Fonte](https://github.com/vercel/next.js/security/advisories/GHSA-39w2-rjm5-chcv). |
| Ferramentas de lint/build | Majoritariamente desenvolvimento/build; não equivalem a um endpoint público explorável confirmado. Atualizar também a cadeia de desenvolvimento. |

### Dependências backend

OSV consultado com **147 coordenadas Maven**, correlacionando **98 das 99 bibliotecas runtime** do JAR. Foram retornados **24 advisories** nos pacotes runtime. `spring-boot-jarmode-tools-4.1.0.jar` foi a biblioteca sem coordenada correspondente nesse levantamento. O scanner não é exaustivo.

| Grupo publicado | Resultado e encaminhamento |
|---|---|
| Jackson core/databind `3.1.4` | A02 tem entrada plausível. Outros avisos dependem de DataInput, identidade/forward references, polimorfismo/default typing, JsonView/Unwrapped, InetAddress, Path ou tipos XML não encontrados como entradas nos fluxos revisados. |
| Tomcat `11.0.22` | Avisos de DIGEST/FORM, constraints de servlet, RewriteValve, SNI e geração de web.xml dependem de mecanismos não identificados/habilitados. Usa-se Spring Security/JWT; SSL e HTTP/2 do container não estão habilitados nas variáveis consultadas. A página do mantenedor já lista outros avisos além do OSV, com correções até `11.0.27`; revisar conjunto completo e BOM. [Fonte](https://tomcat.apache.org/security-11.html). |
| Spring MVC `7.0.8` | Avisos SSE/views e XsltView têm precondições ausentes na API revisada. Mantenedor classifica os casos consultados como baixa/média, divergindo de rótulos críticos do agregado. [SSE](https://spring.io/security/cve-2026-47890/), [XsltView](https://spring.io/security/cve-2026-47884/). |
| Commons BeanUtils `1.9.4` | `GHSA-wxr5-93ph-8wr9`; dependência transitiva a atualizar/reduzir. Nenhum caminho de exploração específico demonstrado. |
| HttpClient5 `5.6.1`, HttpCore5/H2 `5.4.2` | `GHSA-hjcp-jmpx-g3qm`, `GHSA-hf6x-8p5f-cgmf`, `GHSA-v3jc-474w-2wm6`; avaliar transporte utilizado e atualizar conjunto compatível. |
| Log4j API `2.25.4` | `GHSA-qv9r-c865-cp47`; não confundir com demonstração de Log4Shell nesta loja. |
| PostgreSQL JDBC `42.7.11` | `GHSA-j92g-9f8w-j867`; revisar precondições do driver e atualizar versão compatível. Nenhum teste destrutivo ou exploração executado. |

A comparação de bibliotecas confirma nomes/versões no container, não identidade byte a byte de todo artefato. Atualizar apenas para remover um subconjunto de alertas não basta: usar fontes dos mantenedores e repetir scan/testes no artefato final.

### Varredura de segredos

Varredura local de **57 commits alcançáveis**, **1.265 blobs**, **12.602.493 bytes**, sem exclusão por tamanho. Foram comparados em memória oito tipos de credenciais atuais e padrões de tokens/chaves privadas. **Zero correspondências de credenciais atuais e zero candidatos pelos padrões usados.** Nenhum valor foi incluído no resultado.

Foi scanner delimitado por padrões, não garantia de ausência de qualquer segredo histórico: credenciais desconhecidas, codificadas, arquivos fora do Git e objetos não alcançáveis/reflogs não estão cobertos. Gitleaks/TruffleHog não estavam disponíveis. Não foi constatada exposição que justificasse rotação emergencial nesta auditoria.

## 6. Testes e evidências executados

| Verificação | Resultado | Limite |
|---|---|---|
| Suite backend Maven | **963 casos reportados; 0 falhas, 0 erros, 102 ignorados; BUILD SUCCESS** | Probes reais externos desabilitados. Casos ignorados não contam como aprovação. |
| PostgreSQL local — AliExpressOperationPostgresTests | **58; 0 falhas/erros/ignorados** | Banco descartável local, concorrência/persistência e idempotência; nenhum fornecedor real. |
| PostgreSQL local — CleanCheckoutPostgresTests | **7; 0 falhas/erros/ignorados** | Checkout/transações locais. Não somar como 65 casos únicos novos à suite geral: há sobreposição de classes. |
| Probe financeiro local | **Defeito A01 reproduzido** | Classes atuais em memória; sem API/DB/pagamento. |
| Migração de dispatch | **Aplicada duas vezes com sucesso; 8 colunas/2 tabelas; ROLLBACK** | Schema isolado em transação no PostgreSQL local; produção apenas consultada. |
| Frontend `npm test` | **80 testes aprovados** | Testes existentes usam simulação; não são E2E em navegador. |
| TypeScript `tsc --noEmit` | **Aprovado** | Sem erros. |
| ESLint completo | **0 erros, 5 avisos** | Hook em CheckoutForm, imagens em CheckoutForm/ProductCard e exports em favorites/tokenStorage. |
| Build de produção Next | **Aprovado** | Compilação não assegura ausência de vulnerabilidades. |
| `npm audit --json` | **14 pacotes afetados** | Saída não zero esperada pelos alertas; nenhuma atualização aplicada. |
| OSV Maven | **24 advisories** | Consulta de versões e análise estática de aplicabilidade; não exploração. |
| Histórico Git/credenciais | **Sem correspondências nos critérios usados** | Cobertura descrita na seção anterior. |
| HTTP público | **14 rotas GET 200** | Home, catálogo, autenticação/recuperação, carrinho/checkout e políticas; sem submeter formulários. |
| Segurança de leitura/CORS | **401/403/200 conforme papel; CORS externo negado** | Nenhuma mutação administrativa. |
| Resend | **Domínio do remetente verificado** | GET somente; entrega efetiva e reputação não testadas. |
| Desktop/celular/E2E visual | **Não executado: navegador indisponível** | Não declarar responsividade, foco ou checkout integral aprovados. |

Os testes backend abrangem autenticação/autorização, associação de pedidos, assinatura/webhooks repetidos, concorrência, idempotência, falhas de comunicação e mocks AliExpress, conforme as suites disponíveis. Casos com integração real opt-in ficaram desativados. O PostgreSQL descartável iniciado para a auditoria foi encerrado ao final.

Evidências locais ficam em `backend/target/launch-audit-20261115/`, ignorado pelo Git. Não devem ser publicadas indiscriminadamente: logs de testes são volumosos e contêm fixtures sintéticas. Principais arquivos:

- [Produção — somente leitura](../backend/target/launch-audit-20261115/production-readonly.json) e [checagem operacional complementar](../backend/target/launch-audit-20261115/operation-followup.json).
- [Configuração de callback e origens](../backend/target/launch-audit-20261115/configuration-followup.json).
- [Suite backend](../backend/target/launch-audit-20261115/backend-tests.log) e [PostgreSQL local](../backend/target/launch-audit-20261115/postgres-tests.log).
- [Reprodução financeira](../backend/target/launch-audit-20261115/refund-probe.json) e [migração local](../backend/target/launch-audit-20261115/local-migration-proof.json).
- [Testes frontend](../backend/target/launch-audit-20261115/frontend-tests.log), [lint](../backend/target/launch-audit-20261115/frontend-lint.log), [build](../backend/target/launch-audit-20261115/frontend-build.log).
- [npm audit](../backend/target/launch-audit-20261115/npm-audit-normalized.json), [OSV Maven](../backend/target/launch-audit-20261115/java-osv-audit.json) e [varredura Git](../backend/target/launch-audit-20261115/git-secret-scan.json).

As evidências são uma fotografia datada. Este relatório é o artefato versionável; links para `target` exigem os arquivos locais desta execução.

## 7. Arquitetura, integridade e funcionalidades incompletas

Há separação útil entre entrada de webhook, consulta ao provedor, aplicação transacional, preparação de destinatário/produto, worker e cliente AliExpress. Bloqueios de ciclo de vida e constraints únicas são parte central da proteção; devem ser preservados em qualquer refatoração.

O armazenamento legado em `order_fulfillments` e o secundário em `aliexpress_dispatch_records` aumentam caminhos a manter, embora o bloqueio comum e os testes PostgreSQL reduzam disputa. Consolidar abstrações e estados em mudanças pequenas; não há evidência que justifique reescrever a arquitetura para lançar.

A migração `V20261115_01__aliexpress_automatic_dispatch.sql` é reaplicável no ensaio local e seus objetos estão presentes em produção. Nenhuma migração da automação ficou pendente na inspeção. Isso não substitui ledger/controle de execução de todas as futuras migrações.

Permanecem incompletos: contabilidade de reversões, recuperação de pagamento sem webhook, pós-venda/rastreio automático, alertas de pendências, prova de restore, políticas finais, paginação coerente do catálogo/carrinho e validação E2E/visual. A renovação OAuth tem implementação, mas não foi exercitada contra a conta real nesta auditoria.

## 8. Riscos operacionais e procedimento de recuperação

| Cenário | Comportamento atual | Ação necessária |
|---|---|---|
| Webhook repetido | Atualização/dispatch protegidos por bloqueios e estado | Monitorar; não reenviar manualmente por ansiedade. |
| Todas as notificações de um pagamento perdidas | Pedido pode continuar pendente; worker AliExpress não descobre sozinho aprovação ainda desconhecida | Conferir webhook e reconciliar pagamento com fonte autoritativa. Implementar job próprio. |
| Fila em memória cheia/restart antes do dispatch | Fulfillment pago persistido permite polling posterior | Acompanhar atraso da fila e saúde do worker. |
| Erro temporário técnico antes de submeter criação | Reagendamento limitado | Após limite, tratar falha no ADM; custo desconhecido não deve virar bloqueio financeiro. |
| Timeout/crash após submissão | Verificação obrigatória; reenvio bloqueado | Consultar fornecedor/reconciliar referências antes de liberar tentativa. |
| Destinatário inválido | Falha técnica; correção administrativa no snapshot | Usar dados confirmados e reconciliar antes de reenviar. |
| Pedido criado e pago manualmente no fornecedor | ID persiste, consulta automática desabilitada | Operação acompanha pagamento/envio/rastreio manualmente até concluir sincronização. |
| Estorno/chargeback após criação | Estado financeiro incompleto | Corrigir A01 e definir procedimento humano para fornecedor/reembolso. |
| Indisponibilidade de banco/restore | Não validado por ensaio de restauração | Comprovar backup/PITR e recuperação isolada antes de depender da continuidade. |

## 9. Checklist final e impedimentos para novas vendas

### Concluído nesta auditoria

- [x] Identificar commit publicado no Railway e na Vercel.
- [x] Confirmar flags, autorização de criação e objetos da migração.
- [x] Confirmar automação pós-aprovação, proteção de estado e ausência de pagamento automático ao fornecedor.
- [x] Verificar que custos desconhecidos/negativos são alertas, sem bloqueio financeiro.
- [x] Consultar #21 sem reenviar; ID e estado administrativo preservados.
- [x] Executar testes locais backend/frontend, PostgreSQL, tipos, lint, build e auditorias de dependências.
- [x] Manter produção sem alterações e não criar pedidos/pagamentos reais.

### Impedimentos para recomendar lançamento oficial

- [ ] Corrigir A01: reembolsos, estornos parciais, chargebacks e eventos fora de ordem.
- [ ] Corrigir A02/A03 e atualizar demais dependências afetadas; repetir auditoria do artefato final e revisar alertas residuais com aplicabilidade documentada.
- [ ] Concluir A04 com informações comerciais e procedimentos reais confirmados.
- [ ] Confirmar/documentar entrega do webhook de produção e estratégia de recuperação de pagamentos sem notificação; ausência de URL por preferência continua pendente de decisão/configuração.
- [ ] Executar checkout desktop/celular, autenticação/Google/recuperação, carrinho e ADM em ambiente seguro, sem criar compra real adicional, com navegador funcional.
- [ ] Comprovar restauração isolada e responsabilidade por monitorar pagamentos/fila e pós-venda manual.

### Recomendações médias antes da abertura ou com mitigação explícita

- [ ] Revogação de sessão, revalidação administrativa e rate limits.
- [ ] CSP/cabeçalhos e revisão do armazenamento de tokens.
- [ ] Privilégios mínimos do banco, governança de dados e migrações rastreáveis.
- [ ] Timeouts, alertas e processo de reconciliação; corrigir paginação antes de ampliar catálogo além da primeira página.
- [ ] Completar consulta/rastreio do fornecedor ou assumir e documentar operação manual no lançamento. Habilitar flags sem corrigir contrato não resolve a lacuna.

## 10. Veredito e próxima etapa

**NÃO APTO PARA LANÇAMENTO oficial da inGarage v0.0.1 nas condições auditadas.** O sucesso do #21 demonstra o caminho principal real, mas não valida reversões, resiliência completa, dependências seguras nem operação de atendimento/pós-venda.

A criação AliExpress em si **não tem deploy nem migração pendente identificados**: está ativa no commit auditado. A entrega global dos webhooks exige confirmação; consulta/rastreio e recuperação integral continuam pendentes. Não é correto confundir “automação de criação ativa” com “toda operação resolvida sem intervenção”.

Próxima etapa separada: corrigir os achados altos, manter a regra de custos como alertas, preservar bloqueios de duplicidade e pagamento exclusivamente manual ao fornecedor, repetir os testes afetados e submeter a publicação à autorização do usuário. Esta etapa de auditoria alterou apenas este relatório e gerou evidências locais ignoradas; não publicou correções.
