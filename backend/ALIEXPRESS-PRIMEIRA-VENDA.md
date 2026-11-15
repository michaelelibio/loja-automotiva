# inGarage — primeira venda AliExpress (30/10/2026)

## Fluxo entregue

O caminho comercial implementado é a **cotação assistida**, dentro de orders, order_items, shipping_legs, payments e order_fulfillments existentes. O checkout automático continua sem autorização comercial: resposta por país/província e MAJOR_MATCH não comprovam preço para CEP/endereço completo nem escala monetária.

1. Cliente escolhe variantes e quantidades, informa endereço cadastrado completo, CPF válido, nascimento e telefone BR e clica em **Solicitar cotação sem pagamento**.
2. POST /api/orders recebe shippingCode=ASSISTED_QUOTE e requestKey UUID. Cria um pedido interno com quote.state=WAITING e snapshots imutáveis dos itens/endereço/destinatário. O frete zero provisório significa **a confirmar**, nunca frete grátis. Nenhuma chamada de criação ou pagamento ao fornecedor ocorre.
3. Administrador abre /admin/pedidos/{id}. Confere produtos, SKUs, quantidades, disponibilidade, endereço completo, frete, impostos, prazo e custo total no portal AliExpress **sem criar pedido**. Registra modalidades por remessa, código logístico, nome, origem, SKUs cobertos, frete BRL, custo completo do fornecedor, referência interna da evidência e validade futura de até 24 horas.
4. Backend exige cobertura exata dos SKUs, sem remessas duplicadas, soma os fretes e confere o total esperado. O subtotal dos produtos permanece o snapshot de catálogo: se a venda não cobrir o custo completo, a confirmação é rejeitada. Atualize o catálogo e peça uma nova solicitação; não disfarce diferença de preço como frete.
5. Cliente revisa total, remessas, prazos e validade em /conta/pedidos/{id} e aceita explicitamente revision + expectedTotal. Somente então o Mercado Pago existente pode criar preferência. Backend reconfere aceite, estado, validade e total antes da chamada; a preferência também recebe expires e expiration_date_from/to.
6. Webhook autenticado existente confirma o pagamento pelo valor BRL do snapshot. Nenhum pedido AliExpress é criado automaticamente. Aprovação fora da validade ou depois do cancelamento/expiração entra em REFUND_REQUIRED.
7. Administrador reconfere o custo atual no AliExpress. Mudança de custo, endereço, itens ou modalidades grava bloqueio durável REFUND_REQUIRED. O sistema nunca altera o total aceito nem cobra diferença. Faça cancelamento e reembolso integral no Mercado Pago, e registre a referência do comprovante. O endpoint de registro **não executa reembolso**.
8. Custo inalterado: administrador reserva o encaminhamento manual único. O fulfillment grava VERIFICATION_REQUIRED / MANUAL_IN_PROGRESS **antes** de o operador sair para criar a compra no portal. Crie uma única compra manual e registre todos os IDs externos. Repetir a reserva é bloqueado; em dúvida, reconcilie no portal e nunca compre novamente. O pagamento ao fornecedor permanece manual.
9. Registrar os IDs atualiza o fulfillment existente para CREATED e o pedido para PROCESSING. A ação é idempotente para os mesmos IDs. Todos os IDs manuais têm restrição UNIQUE no banco, inclusive os IDs adicionais de múltiplas remessas. Expedição e entrega usam as transições administrativas existentes.

Pedidos que misturem AliExpress e outros fornecedores devem ser separados. Várias remessas AliExpress são suportadas. A cotação confirmada não é reescrita: uma cotação expirada ou preço comercial a alterar requer nova solicitação e novo aceite, evitando preferências antigas com valores diferentes.

## Contratos e correções

- shipping_fee_cent aceita sintaticamente inteiros e decimais bounded com **BigDecimal**. O parser não divide nem interpreta automaticamente valores não zero. MONEY_SCALE_UNCONFIRMED distingue sintaxe válida de unidade sem confirmação; campos inválidos continuam INVALID_RESPONSE.
- monetaryStructure mantém valor observado, texto de apresentação normalizado, documentedScale e comparação MAJOR_MATCH / MINOR_MATCH / NEITHER_MATCH separados. syntacticallyValidNumber identifica o escalar numérico; commercialAuthorization declara que esse diagnóstico não autoriza cobrança.
- Mesmo zero inequívoco não prova cobertura do endereço: a avaliação COUNTRY_ONLY continua impedida de autorizar checkout automático.
- logistics_service_name recebe o **código** da modalidade da remessa que cobre o SKU, em vez do rótulo comercial. Cada SKU deve pertencer a uma remessa, com cobertura exata do pedido.
- O contrato oficial extraído em ALIEXPRESS-MONEY-ADDRESS-EVIDENCE-20261027.json (snapshot completo em target/review-20261027-aliexpress.trade.buy.placeorder.json) documenta **ds_extend_request.trade_extra_param.birthday**, formato ddMMyyyy. Foi removido o campo incorreto logistics_address.birth_date. CPF, full_name, mobile_no e phone_country=+55 são preservados; province usa o nome do estado, sem fingir que UF/IBGE sejam códigos territoriais AliExpress.
- A evidência pública consultada está vinculada em ALIEXPRESS-MONEY-ADDRESS-REVIEW-20261027.md. A releitura pela ferramenta web em 30/10 não conseguiu abrir o serviço oficial; esta entrega usa os snapshots públicos já presentes e não inventa nova comprovação de escala, território ou concessões do app.
- Referência Mercado Pago para vigência: https://www.mercadopago.com.br/developers/pt/docs/checkout-pro-preferences/additional-settings/term-of-preference

As flags ALIEXPRESS_ORDERS_ENABLED e ALIEXPRESS_ORDERS_CONTRACT_CONFIRMED continuam false por padrão. Pedidos assistidos usam encaminhamento manual mesmo se essas flags forem alteradas. Nenhum método de pagamento AliExpress foi adicionado.

## API reutilizada e extensões

- POST /api/orders: solicitação com itens, addressId, recipient, shippingCode e requestKey.
- GET /api/orders/{id}: quote resumida (estado, revisão, validade e aceite), remessas e total. Não retorna custo do fornecedor, evidência interna nem CPF.
- POST /api/orders/{id}/quote/accept: proprietário autenticado; revision e expectedTotal.
- GET /api/admin/orders/{id}/quote: auditoria da confirmação administrativa.
- POST /api/admin/orders/{id}/quote: confirmação, com supplierTotalBrl, expectedTotal, validUntil, evidenceReference, fullAddressAndTaxesChecked e legs[].
- POST /api/admin/orders/{id}/quote/recheck: supplierTotalBrl e sameAddressItemsAndMethods; false ou custo diferente bloqueia envio e exige reembolso.
- POST /api/admin/orders/{id}/quote/manual-claim: reserva durável de encaminhamento manual.
- POST /api/admin/orders/{id}/quote/manual-complete: supplierOrderIds, após conferência no portal.
- POST /api/admin/orders/{id}/quote/refund-record: reference do comprovante de reembolso integral já realizado.
- GET /api/admin/orders/{id}/recipient-for-fulfillment: consulta explícita, somente ADMIN e após pagamento, com Cache-Control: no-store. Destinatário não é copiado para listas ou logs.

As ações administrativas herdam a regra /api/admin/** ADMIN existente. Locks de pedido serializam confirmação, aceite e encaminhamento. requestKey + índice UNIQUE + locks de produtos impedem duplicação da solicitação; referências e IDs externos são persistidos na estrutura existente. Registros internos de confirmação/reembolso são declarações administrativas auditáveis, não verificações automáticas do portal.

## Testar sem cobrança real

Não carregue backend/.env.local.ps1 para executar testes. Os testes usam H2 e credenciais sintéticas, MockMvc para HTTP interno e MockRestServiceServer/Mockito para HTTP de fornecedores.

No backend: mvn test (ou .\mvnw.cmd test). O cenário dedicado é mvn -Dtest=AssistedQuoteIntegrationTests test. Ele solicita a compra por HTTP, confirma/aceita, simula a preferência e o webhook, reconfere custo, reserva encaminhamento e registra IDs **sintéticos**, sem criar compra no portal. Há cenários de total forjado, usuário diferente, validade, custo alterado, reembolso, repetição, snapshots e múltiplas remessas.

No frontend: npm test, npx tsc --noEmit e npm run build. Os testes de formulário e painel administrativo não efetuam requisições reais. Para ensaio visual, use ambiente separado com usuários/credenciais de teste Mercado Pago e não efetue a etapa de compra no portal AliExpress; o cenário HTTP automatizado cobre essa etapa com IDs sintéticos. Não introduzimos endpoint de simulação de pagamento em produção.

## Antes de publicar e vender

1. Revisar/aplicar a migração idempotente V20261030_01__assisted_quote.sql no PostgreSQL de homologação; ela já está na lista spring.sql.init.schema-locations usada pelo projeto. Não houve acesso ao banco de produção nesta entrega.
2. Homologar login cliente/ADMIN, importação com variantes, solicitação, conferência administrativa, aceite e Mercado Pago sandbox com webhook acessível; testar aprovação dentro/fora da validade.
3. Configurar Mercado Pago, URLs HTTPS e segredo do webhook para o ambiente pretendido. Usar credenciais de teste em homologação. Conferir margem, impostos, política de prazo/reembolso e disponibilidade operacional para atender solicitações e encaminhamentos manuais.
4. Revisar as mudanças e publicar backend/frontend por procedimento autorizado. Nenhum commit, push ou deploy foi executado.

Para automatizar: obter confirmação oficial da escala de todos os formatos de shipping_fee_cent, contrato/grant de pré-cotação/reserva para endereço completo antes de criar pedido, vínculo oficial dos IDs territoriais BR com a consulta/criação e reconciliação por referência externa com comportamento de idempotência confirmado no app. BR + Santa Catarina igual a BR e origem BR isolada não resolvem essas lacunas. Os snapshots existentes não documentam uma pré-cotação definitiva por CEP, portanto não habilitamos o caminho automático por inferência.

## Arquivos modificados/criados

- backend/ALIEXPRESS-PRIMEIRA-VENDA.md
- backend/src/main/java/com/garage/garageapi/admin/order/dto/AdminOrderResponse.java
- backend/src/main/java/com/garage/garageapi/admin/order/dto/AdminOrderSummaryResponse.java
- backend/src/main/java/com/garage/garageapi/integration/aliexpress/AliExpressDsFreightClient.java
- backend/src/main/java/com/garage/garageapi/integration/aliexpress/AliExpressFreightDiagnosticResult.java
- backend/src/main/java/com/garage/garageapi/integration/aliexpress/AliExpressFreightService.java
- backend/src/main/java/com/garage/garageapi/integration/aliexpress/AliExpressOrderPreparation.java
- backend/src/main/java/com/garage/garageapi/order/dto/CreateOrderRequest.java
- backend/src/main/java/com/garage/garageapi/order/dto/OrderResponse.java
- backend/src/main/java/com/garage/garageapi/order/entity/Order.java
- backend/src/main/java/com/garage/garageapi/order/fulfillment/OrderFulfillment.java
- backend/src/main/java/com/garage/garageapi/order/quote/AssistedQuote.java
- backend/src/main/java/com/garage/garageapi/order/quote/AssistedQuoteController.java
- backend/src/main/java/com/garage/garageapi/order/quote/AssistedQuoteService.java
- backend/src/main/java/com/garage/garageapi/order/repository/OrderRepository.java
- backend/src/main/java/com/garage/garageapi/order/service/OrderService.java
- backend/src/main/java/com/garage/garageapi/payment/gateway/CheckoutProGateway.java
- backend/src/main/java/com/garage/garageapi/payment/gateway/MercadoPagoCheckoutProGateway.java
- backend/src/main/java/com/garage/garageapi/payment/service/CheckoutProWebhookUpdater.java
- backend/src/main/java/com/garage/garageapi/payment/service/PaymentAttemptService.java
- backend/src/main/java/com/garage/garageapi/payment/service/PaymentService.java
- backend/src/main/resources/application.properties
- backend/src/main/resources/db/migration/V20261030_01__assisted_quote.sql
- backend/src/test/java/com/garage/garageapi/integration/aliexpress/AliExpressCheckoutRecipientIntegrationTests.java
- backend/src/test/java/com/garage/garageapi/integration/aliexpress/AliExpressDsFreightGatewayTests.java
- backend/src/test/java/com/garage/garageapi/integration/aliexpress/AliExpressFreightDiagnosticClientTests.java
- backend/src/test/java/com/garage/garageapi/integration/aliexpress/AliExpressShippingHttpIntegrationTests.java
- backend/src/test/java/com/garage/garageapi/order/quote/AssistedQuoteIntegrationTests.java
- backend/src/test/java/com/garage/garageapi/payment/gateway/MercadoPagoCheckoutProGatewayTests.java
- frontend/app/admin/pedidos/[id]/page.tsx
- frontend/app/admin/pedidos/page.tsx
- frontend/app/checkout/CheckoutForm.tsx
- frontend/app/conta/pedidos/[id]/page.tsx
- frontend/app/globals.css
- frontend/components/OrdersPanel.tsx
- frontend/components/admin/AliExpressFreightDiagnostic.tsx
- frontend/components/admin/AssistedQuotePanel.tsx
- frontend/lib/admin/aliexpress-freight.ts
- frontend/lib/api/admin/orders.ts
- frontend/lib/api/orders.ts
- frontend/lib/types/admin-order.ts
- frontend/lib/types/order.ts
- frontend/tests/assisted-quote.test.cjs
- frontend/tests/checkout-recipient.test.cjs

A declaração de pacote ausente no teste AliExpressFreightQuoteTests, detectada no estado inicial do workspace, foi restaurada ao conteúdo original. Arquivos gerados pelo build não fazem parte da entrega.

## Validação executada

- Backend: regressão completa com 639 casos, zero falhas/erros e um probe real ignorado por segurança. Os sete cenários AssistedQuoteIntegrationTests passaram. Maven verify gerou o JAR Spring Boot.
- Frontend: 49 testes aprovados, TypeScript sem erros, lint dos arquivos alterados sem erros (um aviso preexistente de img no checkout) e build Next.js aprovado.
- A verificação Maven usou target/assisted-verification-pom.xml, gerado a partir do pom.xml original, com os mesmos fontes, recursos e dependências e diretório de saída isolado; a compilação no target/classes comum estava sofrendo interferência do ambiente de desenvolvimento. O pom.xml do projeto não foi alterado.
- Para repetir o fluxo dedicado nesta sessão, no backend: .\mvnw.cmd -f target/assisted-verification-pom.xml -Dtest=AssistedQuoteIntegrationTests test. Para toda a regressão, substitua a seleção de teste por verify. O POM de verificação e os logs são artefatos locais em target; não são componentes de produção.
- Evidências locais: target/assisted-final-regression.log, target/assisted-final-wire-format.log, target/frontend-final-tests.log, target/frontend-final-types.log, target/frontend-final-lint.log e target/frontend-final-build.log. Nenhuma variável de .env.local.ps1 foi carregada.

Após o ajuste final do zero monetário para representação 0.00, os 77 testes de transporte/parser/gateway/frete HTTP foram repetidos e aprovados, e o JAR final foi novamente empacotado com sucesso. A alteração preserva a ausência de conversão de valores não zero.
