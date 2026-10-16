# Pedidos AliExpress: envio administrativo manual

## Estado atual e bloqueios obrigatórios

O fluxo está em `/admin/pedidos`, na linha de pedidos que contêm itens AliExpress. O backend é a fonte dos estados de pagamento, pedido, fulfillment e entrega. Não existe envio automático AliExpress após o webhook de pagamento: o dispatcher de CJ/DSers continua sem chamar o cliente AliExpress.

**Criação real permanece desabilitada por padrão.** A etapa 1 agora coleta e valida CPF, nascimento e telefone para novos pedidos AliExpress; detalhes em `ALIEXPRESS-CHECKOUT-RECIPIENT.md`. Pedidos históricos continuam com snapshots incompletos. Ainda não existe um provedor de cotação AliExpress que grave frete confirmado. Pedidos sem dados ou frete válido permanecem bloqueados, inclusive se as flags forem alteradas. Não usar dados fictícios nem substituir frete genérico/local por um nome presumido.

Antes de liberar chamadas reais, é necessário:

1. Coleta validada concluída para novos pedidos na etapa 1 pelo `POST /api/orders` existente. O endereço é reutilizado por ID e os dados pessoais adicionais não são retornados ao cliente/ADM. Pedidos antigos não são preenchidos automaticamente.
2. Implementar cotação/seleção confirmada de frete AliExpress e persistir a modalidade, custo, moeda, origem e IDs das variantes no `shipping_legs` existente. A implementação atual só aceita pedidos exclusivamente AliExpress e uma única modalidade em BRL cobrindo exatamente suas variantes.
3. Validar com a documentação e permissões específicas do app o contrato de `aliexpress.trade.buy.placeorder`, inclusive `out_order_id`, o campo de nascimento BR (`birth_date` no adaptador preparado), o transporte TOP `/sync` e a reconciliação por referência. **Esses campos não foram confirmados na referência pública consultada. Não basta ativar as flags para considerar o contrato validado.**
4. Somente após essa validação, revisar o adaptador conforme o contrato efetivo e configurar `ALIEXPRESS_ORDERS_ENABLED=true` e `ALIEXPRESS_ORDERS_CONTRACT_CONFIRMED=true`. Ambas são `false` por padrão; o cliente HTTP também aplica o bloqueio.

Referência oficial consultada: https://open.alitrip.com/docs/api.htm?apiId=35446 . Ela documenta `param_place_order_request4_open_api_d_t_o`, endereço, CPF, `product_id`, quantidade, `sku_attr`, `logistics_service_name`, resultado `is_success` e `order_list.number`; não documenta `out_order_id` ou nascimento. A referência também classifica `REPEATED_ORDER_ERROR` como possivelmente já processado: nunca liberar reenvio nessa situação.

## API administrativa

- `GET /api/admin/orders/{orderId}/aliexpress`: consulta elegibilidade e estados persistidos. Requer ADMIN.
- `POST /api/admin/orders/{orderId}/aliexpress/send`: envio manual, sem corpo. Requer ADMIN. O frontend exige confirmação em diálogo antes dessa chamada.
- Pagamento é verificado no banco: `PaymentStatus.PAID`, timestamp de aprovação e ID do pagamento no gateway, além do estado interno compatível. Um `OrderStatus.PAID` isolado não autoriza compras.
- IDs do produto e variante são conferidos contra o snapshot do item e a variante importada. A chamada usa a chave `raw_variant_key` original em `sku_attr`, nunca rótulos comerciais como identificadores. Quantidades devem ser positivas e de até 1000 unidades por linha.

## Concorrência, idempotência e resultado desconhecido

O `external_reference` existente (`INGARAGE-<id>`) é o `out_order_id` estável para todas as tentativas do mesmo pedido. Não há referência nova por clique ou timeout, nem repetição automática de HTTP.

Antes do envio, uma transação independente bloqueia o pedido, revalida pagamento e pré-requisitos e grava `VERIFICATION_REQUIRED` com token, contador e timestamp. Só então o cliente externo pode ser chamado. Solicitações concorrentes encontram o marcador e são bloqueadas.

- Sucesso: todos os IDs retornados são persistidos em `supplier_order_ids` e o primeiro também no campo legado `supplier_order_id`. Fulfillment `CREATED`, fornecedor `AWAITING_MANUAL_PAYMENT`, pedido interno `PROCESSING`. Não há pagamento ao fornecedor nem alteração de `shipped_at`/`delivered_at`.
- Rejeição inequívoca sem IDs: fulfillment `FAILED`, fornecedor `REJECTED`. Nova tentativa exige ação manual e revalidação, usando a mesma referência.
- Timeout, HTTP sem resultado confiável, duplicidade no fornecedor, resposta parcial/desconhecida, queda do processo ou falha local após a criação: `VERIFICATION_REQUIRED` permanece durável. Nunca reenviar automaticamente nem considerar que a ausência de resposta significa ausência de compra.

**Reconciliação automática ainda não implementada:** depende do contrato confirmado de consulta por `out_order_id`. Até lá, o operador deve verificar no portal AliExpress e guardar a evidência relacionando referência estável e todos os IDs externos. Esta entrega não oferece endpoint para informar um ID arbitrário, apagar o marcador ou liberar reenvio. Uma futura rotina deverá comprovar o vínculo no fornecedor antes de atualizar os estados; ausência em uma consulta isolada não prova que é seguro repetir a compra.

## Compatibilidade, migração e testes

`V20261015_01__aliexpress_manual_orders.sql` adiciona campos nullable de destinatário no pedido e `supplier_order_ids`/`supplier_status` no fulfillment; amplia a constraint de status. Não faz backfill nem altera status de pedidos antigos. Foi adicionada ao mecanismo existente `spring.sql.init.schema-locations` (o projeto não usa Flyway).

O fulfillment legado `NONE/NOT_REQUIRED` de um pedido exclusivamente AliExpress só pode ser preparado na ação administrativa explícita, depois de passar por todos os bloqueios. Fulfillments de outros fornecedores não são sobrescritos. Pedidos mistos ficam bloqueados.

A área do cliente continua consultando `OrderResponse.status`; `PROCESSING` é apresentado como “Preparando pedido”. Informações de CPF/nascimento, tokens, respostas completas e erros internos não entram nas respostas administrativas/cliente ou em novos logs.

Os testes usam clientes HTTP simulados e não fazem compras reais. Cobrem pagamento pendente/aprovado, ADMIN, campos obrigatórios, frete, IDs, concorrência, referência estável, sucesso, rejeição, timeout, crash e estados do ADM/cliente. Há testes separados da assinatura TOP e de respostas da API.
