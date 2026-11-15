Implementado localmente: seção “Destinatário AliExpress” no detalhe administrativo do pedido. A consulta mostra CPF mascarado e a presença de nascimento/telefone, sem devolver CPF completo ou nascimento. A edição requer administrador autenticado e confirmação dos dois valores nos campos vazios. Os dados são salvos somente no snapshot de entrega de `orders`; `User` e `Address` não são alterados.

Endpoints protegidos por `ROLE_ADMIN`:

- `GET /api/admin/orders/{id}/aliexpress/recipient`: representação mascarada, revisão, campos pendentes e elegibilidade da correção/reconciliação. Resposta `Cache-Control: no-store`.
- `PATCH` no mesmo caminho: CPF, nascimento `DD/MM/AAAA` e digest do contexto lido. CPF exige dígitos verificadores válidos; nascimento usa calendário estrito, data passada e idade inferior a 120 anos. Telefone e endereço obrigatórios do snapshot também são validados. Mensagens fixas não refletem valores rejeitados.
- `POST .../recipient/reconcile`: revisão atual, contexto, número de tentativas e confirmações explícitas de dados conferidos e ausência de pedido após todas as tentativas anteriores. Não consulta um histórico fictício nem cria ou paga pedidos. O administrador precisa conferir a conta, todas as páginas, o período e os dados anteriores à correção. Encontrando correspondência ou dúvida, não deve declarar ausência.

Salvar mantém retenção manual persistente. Não altera tentativas já realizadas, IDs retornados ou o marcador de resultado incerto. Pedidos já vinculados, enviados, entregues, cancelados ou com criação ainda em andamento não admitem essa correção. A reconciliação exige o término da janela da tentativa anterior, os dados completos, a revisão atual e ausência de IDs. A liberação vale por 15 minutos, uma tentativa, e mantém a retenção contra worker/webhook. O envio futuro ainda depende da confirmação no ADM, nova validação de pagamento/produto e do lock comum antes da chamada. Nova correção, contexto alterado, expiração ou início de envio invalidam a liberação anterior.

O payload usa exclusivamente CPF e nascimento do snapshot corrigido do pedido, com `birthday` em `ddMMyyyy`. Não há fallback para os dados da cliente nem para a fixture de teste. A auditoria `aliexpress_recipient_admin_audits` registra administrador, ação, horários, número de tentativas e hashes dos contextos; não armazena CPF, nascimento, endereço, payload ou texto livre. DTOs de correção têm `toString()` redigido. Os testes capturam os logs de validação e verificam ausência dos valores pessoais sintéticos.

O fluxo de pedidos mistos mantém as mesmas regras de retenção e revisão no snapshot separado do despacho AliExpress. O fulfillment dos demais fornecedores é preservado. Uma aceitação pelo fornecedor também não pode perder seu ID por causa de uma antiga exigência financeira da cotação assistida.

Validação executada após essa implementação:

- Backend afetado: 607 casos, 539 aprovados, 68 opt-in ignorados, zero falhas/erros. Inclui autorização, mascaramento, dados inválidos, logs/erros sem valores, preservação de User/Address, formato do payload, retenção automática, revisão obsoleta, expiração, resultados incertos e duplicidade.
- Frontend: 80 testes aprovados; TypeScript, lint e build aprovados. Inclui edição sem persistência dos campos no navegador, limpeza após salvar, bloqueio de clique repetido e reconciliação sem chamada de criação.
- Quatro testes do executor pontual garantem que ele recuse qualquer tentativa anterior, inclusive o erro de nascimento do #21. Esse executor não é um caminho alternativo para nova tentativa.
- Migração aditiva final executada duas vezes no PostgreSQL 18 local, com rollback; histórico preservado. Nenhuma migração de produção foi aplicada.
- JAR local preparado em `backend/target/order21-validation/garage-api-0.0.1-SNAPSHOT.jar`; frontend em `frontend/.next`. Nenhum deploy, push ou pagamento ao fornecedor foi executado.

Situação real #21: a única chamada anterior retornou `B_DROPSHIPPER_DELIVERY_ADDRESS_CPF_BIRTHDAY_NO_MATCH`, sem ID. O erro está registrado no ADM e a tentativa permanece bloqueada para reconciliação. Após a última instrução do proprietário, não houve nova criação real nem alteração dos dados pessoais. A seção nova ainda depende de publicação manual autorizada para aparecer no ADM de produção.

Arquivos desta entrega: [manifesto completo](ALIEXPRESS-AUTOMACAO-REGRA-DEFINITIVA-20261115.files.txt). Preparação de publicação e contexto operacional: [relatório da integração](ALIEXPRESS-AUTOMACAO-REGRA-DEFINITIVA-20261115.md).
