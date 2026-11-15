# inGarage — pagamento de produção e situação AliExpress

Esta revisão substitui os trechos de migração e acompanhamento do relatório anterior. Nenhuma alteração foi executada em produção; nenhuma compra, pagamento ao fornecedor, chamada real de consulta, commit, push ou deploy foi feito.

## Entrega

- `MercadoPagoCheckoutProGateway.findPayment` lê `live_mode` do JSON retornado pelo GET autenticado `/v1/payments/{id}`. Somente booleano JSON é aceito; ausência, `null`, números e strings permanecem desconhecidos.
- Depois de conferir ID, referência interna, moeda e valor, o atualizador persiste `payments.provider_live_mode` e `provider_verified_at`. O webhook apenas aciona a consulta existente. Não há campo de frontend ou ação administrativa que produza essa comprovação.
- Encaminhar ao AliExpress exige pagamento Mercado Pago `PAID`, ID e data de aprovação, `provider_live_mode=true` e data de verificação pelo backend. Pagamentos de teste e registros históricos sem prova ficam bloqueados. A confirmação não é inferida das credenciais ou de flags de sandbox.
- Todos os IDs AliExpress continuam persistidos em `supplier_order_ids`, são expostos no DTO administrativo e aparecem no ADM, como strings, preservando IDs maiores que a precisão numérica do JavaScript.
- `POST /api/admin/orders/{orderId}/aliexpress/sync` consulta apenas os IDs já vinculados ao pedido. Requer ADMIN; não recebe IDs, estado ou declaração de pagamento do navegador. Usa OAuth persistido e o método somente de leitura `aliexpress.ds.trade.order.get`.
- A consulta grava o resultado de cada divisão, horário e indicação de verificação. Não cria pedidos, não paga e não altera automaticamente o estado de entrega do pedido inGarage.
- Criação e consulta usam controles independentes. A consulta tem duas flags desabilitadas e `AliExpressOrderQueryPermission` bloqueado até homologação. Não há chamadas automáticas ao abrir a página nem agendamento em produção.

## Estados do fornecedor

| Estado recebido | Exibição |
| --- | --- |
| `PLACE_ORDER_SUCCESS` | Aguardando pagamento manual |
| `RISK_CONTROL` | Pagamento confirmado pelo fornecedor, ainda sob análise de risco |
| `WAIT_SELLER_SEND_GOODS` | Aguardando envio |
| `WAIT_BUYER_ACCEPT_GOODS` | Enviado |
| `SELLER_PART_SEND_GOODS` | Enviado parcialmente |
| `IN_CANCEL` | Falha ou cancelamento; não é confirmação de cancelamento concluído |
| Erro, timeout, resposta inválida ou estado não reconhecido | Desconhecido; não comprova pagamento |

`PAYMENT_PROCESSING` e `WAIT_SELLER_EXAMINE_MONEY` não significam pagamento confirmado. `FINISH`, `ARCHIVE` e `FUND_PROCESSING`, isoladamente, não provam envio ou conclusão normal. Combinações contraditórias conhecidas também ficam desconhecidas.

Os códigos têm tratamento conservador e precisam ser validados no contrato efetivamente concedido ao app. Resultados de pedidos divididos não são reduzidos a “todos pagos” quando diferem; o agregado é `MIXED` e o ADM mostra cada divisão.

O estado local após criar a ordem é provisório: “aguardando pagamento manual, situação ainda não consultada”. Registros manuais antigos não se tornam comprovação de pagamento. Esta entrega não acrescenta botão para marcar pagamento ao fornecedor. Depois de pagar manualmente no AliExpress, o operador consulta novamente; falha de consulta mantém a situação sem confirmação.

Os resultados são fotografias com data/hora, não garantia permanente de estado atual. Consulte novamente antes de pagar. Durante consulta concorrente, a interface identifica os resultados anteriores como possivelmente desatualizados.

## Segurança e idempotência

- A criação mantém sua reivindicação persistida antes da rede e o bloqueio de reenvio após resultado ambíguo.
- A sincronização possui uma reivindicação própria e não modifica o token, tentativas ou estado de criação. Uma consulta concorrente é bloqueada; uma reivindicação interrompida pode ser retomada após 120 segundos porque consultar é uma operação de leitura.
- A rede é chamada fora das transações de banco. O resultado só é aplicado se o token e a lista de IDs ainda corresponderem à consulta atual. Respostas antigas não sobrescrevem uma observação mais recente.
- Falhas individuais não removem os IDs nem desbloqueiam criação. Não há retry automático do cliente HTTP, redirecionamento ou registro do corpo do fornecedor.
- São armazenados apenas estados permitidos e códigos fixos de falha, sem tokens, endereço ou corpo bruto do fornecedor. Não foi implementada nenhuma API de pagamento AliExpress.
- `controlled` continua fixando pagamentos e criação reais como desabilitados, e agora também fixa as consultas reais como desabilitadas. As classes de comprovação de permissão permanecem bloqueadas; ligar flags isoladamente não libera operações.
- A estrutura, cálculos e interfaces de frete não foram alterados nesta revisão.

## Migração e publicação — roteiro, não executado

Nova migração: `backend/src/main/resources/db/migration/V20261030_03__payment_production_and_supplier_sync.sql`.

Ela acrescenta, com `IF NOT EXISTS`, somente:

- `payments.provider_live_mode BOOLEAN`, anulável;
- `payments.provider_verified_at TIMESTAMP WITH TIME ZONE`, anulável;
- `order_fulfillments.supplier_sync JSONB`, anulável.

Não há exclusão, backfill de `true`, mudança de preço, migração de frete ou alteração de dados históricos. Registros antigos sem comprovação permanecem `NULL`.

1. Identificar a revisão realmente implantada no Railway e fazer backup do PostgreSQL. O esquema de produção não foi acessado nesta verificação.
2. Comparar o esquema existente com os mapeamentos atuais. Conferir as migrações anteriores necessárias, especialmente `V20261015_01` e `V20261030_01/02`, preservando seus dados e dependências. Não executar todos os scripts indiscriminadamente.
3. Em banco de homologação, validar a aplicação aditiva de `V20261030_03`, inclusive repetição e registros históricos, antes de autorizar sua aplicação no Railway. Aplicar somente o script ausente, em transação e com parada em erro; não usar `ddl-auto=update` como migração de produção.
4. Confirmar `SPRING_PROFILES_ACTIVE=controlled`, `spring.jpa.hibernate.ddl-auto=validate` e `spring.sql.init.mode=never`. Conferir que as duas novas colunas de pagamento e o JSONB de sincronização existem antes de iniciar a versão nova.
5. Manter `PAYMENT_CREATION_ENABLED=false`, `ALIEXPRESS_ORDERS_ENABLED=false`, `ALIEXPRESS_ORDERS_CONTRACT_CONFIRMED=false`, `ALIEXPRESS_ORDER_SYNC_ENABLED=false` e `ALIEXPRESS_ORDER_SYNC_CONTRACT_CONFIRMED=false`. O perfil `controlled` também fixa esses bloqueios diretamente.
6. Preservar App Key/Secret, OAuth persistido e `ALIEXPRESS_TOKEN_ENCRYPTION_KEY`. Não rotacionar a chave de criptografia sem um procedimento de recriptografia/reautorização.
7. Conferir `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS`, `NEXT_PUBLIC_API_BASE_URL`/`NEXT_PUBLIC_API_URL`, URLs HTTPS de retorno/notificação Mercado Pago e configuração de entrega grátis já aprovada. Não ativar `local-freight`.
8. Coordenar frontend/backend com o esquema já atualizado. Validar leituras e bloqueios, sem usar compras ou cobranças reais como teste de publicação.
9. Eventual reversão de aplicação preserva as colunas novas e os dados. Não executar rollback destrutivo do banco.

## Verificações que ainda faltam para homologação

**Mercado Pago:** comprovar que token, aplicação e vendedor correspondem à operação; validar o webhook assinado e a consulta autenticada no ambiente correto; comprovar que a resposta real contém `live_mode=true`, `approved`, ID, referência, BRL, valor e data corretos e que essa evidência chega ao PostgreSQL; validar rejeição de sandbox, campo ausente e notificações repetidas/fora de ordem; definir e validar cancelamento, reembolso e aprovação após vencimento. Nenhum recebimento real foi executado nesta revisão. A comprovação operacional real requer autorização posterior separada.

**AliExpress:** conferir, na aplicação já vinculada, permissão concedida tanto para `aliexpress.trade.buy.placeorder` quanto para `aliexpress.ds.trade.order.get`; homologar OAuth, gateway e assinatura correspondentes ao app. O cliente de criação anterior ainda usa `/sync`/SHA-256; a consulta nova segue o contrato TOP publicado, com HTTPS `/router/rest`, HMAC e timestamp GMT+8. Não presumir equivalência ou permissão entre métodos. Implementar a fonte de comprovação efetiva nas classes de permissão somente após evidência operacional. Validar, em pedidos legítimos e com autorização separada, os códigos reais antes/depois do pagamento manual, risco, divisão, cancelamento e envio parcial; comparar todos os IDs; comprovar reconciliação de timeout sem recriação e sem pagamento automático.

Nenhuma chamada de `placeorder` deve ser usada para sondar permissão. Documentação disponível, permissão concedida e execução real comprovada continuam sendo evidências distintas.

## Referências oficiais consultadas

- [Consulta DS de pedido, API 60455](https://developer.alibaba.com/docs/doc.htm?articleId=60455&docType=2&treeId=762).
- [Estados de pedidos AliExpress — documentação histórica; validar no contrato atual](https://developer.alibaba.com/docs/doc.htm?articleId=108126&docType=1&treeId=502).
- [Assinatura TOP](https://developer.alibaba.com/docs/doc.htm?articleId=118416&docType=1&treeId=684).
- [Notificações Mercado Pago e consulta do pagamento pelo backend](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro-preferences/payment-notifications).

## Testes

- Suíte completa backend: **807 cenários, zero falhas/erros, 35 opcionais ignorados**. Os ignorados incluem verificações externas que não foram habilitadas.
- Repetição adicional: **33 cenários de operação AliExpress em PostgreSQL 18 local**, todos aprovados, com criação/consulta simuladas. Inclui pagamentos não produtivos, persistência JSONB das divisões, idempotência, concorrência, timeout e resposta atrasada. O conjunto final de relatórios contém **840 cenários: 805 executados com sucesso e 35 ignorados**.
- Checkout e migração nova: **7 cenários em H2 e os mesmos 7 em PostgreSQL**, todos aprovados; a migração foi executada duas vezes apenas nos bancos descartáveis de teste.
- Frontend: **65 testes aprovados**, build Next.js e TypeScript aprovados. Lint sem erros, com 5 avisos existentes de imagens, dependências de hook e exportações.
- `git diff --check`: aprovado.

O PostgreSQL descartável usou somente `127.0.0.1:55449`, usuário de teste e bancos `clean_checkout`/`clean_operation`, sem `DB_URL` ou credenciais de produção. Foi encerrado ao final. O Maven usou o cache local e o diretório de build separado já preparado; não foi necessário modificar o `pom.xml`.

Os testes usam respostas simuladas e renderização de componentes. Não comprovam concessão de permissão, aceitação do contrato pelo app real ou liquidação financeira real; também não houve validação manual autenticada em navegador.

## Arquivos principais desta revisão

- Pagamento: `Payment.java`, `CheckoutProGateway.java`, `MercadoPagoCheckoutProGateway.java`, `CheckoutProWebhookUpdater.java` e bloqueio em `AliExpressOrderService.java`.
- Fornecedor: `OrderFulfillment.java`, novos `AliExpressSupplierSnapshot.java`, `AliExpressOrderQueryClient.java`, `AliExpressOrderQueryPermission.java` e `AliExpressOrderSyncService.java`.
- ADM: `AdminAliExpressOrderController.java`, `AdminOrderFulfillmentResponse.java`, `AliExpressOrderAction.tsx`, detalhe administrativo, tipos e cliente API administrativo.
- Migração `V20261030_03__payment_production_and_supplier_sync.sql`, configurações `application.properties`/`application-controlled.properties` e exemplo de variáveis.
- Testes de pagamento, consulta, sincronização, PostgreSQL e interface; fixtures ajustadas para declarar explicitamente a evidência simulada de produção.

As alterações locais anteriores de checkout/frete foram preservadas. Esta revisão não alterou seus algoritmos, tabelas, dados nem reabriu suas interfaces de produção.
