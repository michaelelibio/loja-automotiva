# Etapa 1 — Destinatário AliExpress no checkout

## Contrato e persistência

O endereço da conta continua sendo selecionado por `addressId`, com propriedade conferida pelo backend. Nome completo, CEP, rua, número, complemento opcional, bairro, cidade e UF são reutilizados; não há um segundo endereço no formulário ou no payload.

O catálogo público acrescenta somente o booleano `requiresAliExpressRecipientData`, calculado pelo servidor a partir de `DROPSHIPPING` e fornecedor `ALIEXPRESS`. O checkout usa essa indicação para apresentar os três campos adicionais. A obrigatoriedade real é recalculada no servidor, considerando todos os produtos do pedido, inclusive carrinhos mistos. O navegador não controla essa regra.

`POST /api/orders` aceita o campo opcional:

```json
{
  "recipient": {
    "cpf": "CPF digitado pelo destinatário, com ou sem máscara",
    "birthDate": "DD/MM/AAAA",
    "phone": "Telefone brasileiro com DDD"
  }
}
```

O backend valida dígitos verificadores do CPF, rejeita sequências repetidas, valida a data estritamente (inclusive anos bissextos), exige data passada dentro da janela de 120 anos já utilizada na preparação AliExpress e verifica DDD/número de telefone. Nome completo e os campos do endereço também são conferidos para AliExpress. Erros 400 retornam somente nomes de campos e mensagens fixas, nunca os valores recebidos.

O snapshot do pedido reutiliza `orders.recipient_cpf` (11 dígitos), `recipient_birth_date` (`DATE`/`LocalDate`) e `recipient_phone` (DDD e número sem máscara). A conversão de nascimento para `ddMMyyyy` acontece apenas em `AliExpressOrderPreparation.logisticsAddress`, não na persistência. Alterações posteriores no endereço da conta não alteram o snapshot.

Pedidos sem fulfillment AliExpress não exigem os campos e não armazenam esses valores, mesmo se forem enviados. Pedidos antigos não são atualizados automaticamente. CPF/nascimento/telefone incompletos dos pedidos históricos aparecem apenas como pendências.

## Privacidade e autorização

- O endpoint continua autenticado e só aceita endereços pertencentes ao comprador. Outro cliente não consegue ler o pedido.
- CPF, nascimento e telefone não são incluídos em `OrderResponse`, nem no detalhe administrativo. Os getters da entidade também são ignorados pelo serializador JSON.
- `AdminOrderResponse.recipientData` retorna somente `required`, `complete` e `pendingFields`, protegido pelo acesso ADMIN já existente. A UI mostra “Dados completos: pronto para validação de frete” ou a lista de pendências, sem documentos/datas integrais.
- Os DTOs de entrada e dados normalizados têm `toString()` redigido. Logs automáticos de payload HTTP, detalhes de erros de parsing e trace de valores JDBC são suprimidos na configuração da aplicação. Não ativar captura integral de corpos ou parâmetros pessoais em proxies/APM.
- O frontend mantém os campos apenas em memória, não os grava em storage e os limpa após sucesso ou troca de endereço. O snapshot público já existente em sessionStorage continua sem os dados pessoais adicionais.

## Migração e limites desta etapa

Não existe nova migração nesta etapa: são reutilizadas as três colunas nullable de `V20261015_01__aliexpress_manual_orders.sql`, preparada na etapa anterior e registrada em `spring.sql.init.schema-locations`. Essa migração precisa estar aplicada antes de publicar o código em banco existente. Não há backfill.

Coletar os dados não confirma frete, elegibilidade ou criação de ordens. A seleção de frete existente continua sendo validada e pode recusar o pedido. As flags `ALIEXPRESS_ORDERS_ENABLED` e `ALIEXPRESS_ORDERS_CONTRACT_CONFIRMED` permanecem desabilitadas por padrão. Não foram alterados OAuth, credenciais, pagamentos, importação nem regras de produtos inativos.

Continuam pendentes a integração de cotação/seleção confirmada de frete AliExpress, a validação do contrato específico do app para `out_order_id`/`birth_date` e a reconciliação segura. O mapeamento preparado não é autorização para chamar a API real.

## Arquivos desta etapa

Backend (em `src/main/java/com/garage/garageapi/`):
- `order/dto/AliExpressRecipientRequest.java` e `CreateOrderRequest.java`.
- `order/recipient/AliExpressRecipientData.java` e `RecipientValidationException.java`.
- `order/entity/Order.java` e `order/service/OrderService.java`.
- `product/dto/ProductResponse.java`, `admin/order/dto/AdminOrderResponse.java` e `shared/exception/GlobalExceptionHandler.java`.
- `integration/aliexpress/AliExpressOrderPreparation.java` e `src/main/resources/application.properties`.
- Testes: `AliExpressCheckoutRecipientIntegrationTests.java`; ajuste da mensagem esperada em `AliExpressOrderIntegrationTests.java`.

Frontend:
- `app/checkout/CheckoutForm.tsx`, `components/AliExpressRecipientFields.tsx` e `lib/checkout/recipient.ts`.
- `components/admin/AliExpressRecipientStatus.tsx` e `app/admin/pedidos/[id]/page.tsx`.
- `lib/products.ts`, `lib/types/order.ts`, `lib/types/admin-order.ts` e `app/globals.css`.
- `tests/checkout-recipient.test.cjs`.

Testes de persistência usam H2 e uma cotação simulada isolada; isso não representa suporte real de frete AliExpress. Os testes incluem rejeição pelo serviço de frete, compatibilidade sem AliExpress, snapshots, autorização, redaction de logs/respostas e nenhuma chamada real de compra.
