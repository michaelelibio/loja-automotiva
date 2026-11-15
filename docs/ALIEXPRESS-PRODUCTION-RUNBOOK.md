# Publicação da criação automática AliExpress

Um pagamento Mercado Pago aprovado em produção encaminha os itens AliExpress para a criação de pedidos no fornecedor. Preço, frete, impostos, total desconhecido e margem negativa geram alertas; a validação técnica de pagamento, destinatário, produto, variante e estoque continua obrigatória. Nenhum caminho desta integração paga o fornecedor.

O payload utiliza o CPF e o nascimento do snapshot do destinatário do pedido. O nascimento é enviado em `ds_extend_request.trade_extra_param.birthday`, no formato `ddMMyyyy`. Correções administrativas atualizam somente esse snapshot, com auditoria sem dados pessoais. A consulta administrativa retorna CPF mascarado e a presença do nascimento.

## Ordem de publicação

1. Conferir os commits ativos, inventariar pedidos pagos, tentativas anteriores e referências existentes. Conservar estados incertos em retenção.
2. Manter `ALIEXPRESS_AUTO_DISPATCH_ENABLED=false`. Aplicar explicitamente `V20261115_01__aliexpress_automatic_dispatch.sql` antes do backend; o perfil `controlled` valida o schema e não executa migrações automaticamente. A migração é aditiva e não recria tentativas.
3. Publicar o backend. Validar autenticação ADMIN e endpoints de consulta, sem criar pedidos como teste de fumaça.
4. Publicar o frontend após o backend estar saudável.
5. Ativar `ALIEXPRESS_ORDERS_ENABLED`, `ALIEXPRESS_ORDERS_CONTRACT_CONFIRMED` e `ALIEXPRESS_AUTO_DISPATCH_ENABLED`, com evidência da permissão de criação vinculada ao SHA-256 da chave da aplicação (`ALIEXPRESS_PERMISSION_APP_KEY_SHA256` e `ALIEXPRESS_PERMISSION_EVIDENCE`). Reiniciar a versão publicada e verificar o estado efetivo.
6. As flags `ALIEXPRESS_ORDER_SYNC_ENABLED` e `ALIEXPRESS_ORDER_SYNC_CONTRACT_CONFIRMED` e a evidência `ALIEXPRESS_QUERY_PERMISSION_*` são independentes. Habilitar somente após uma consulta real autorizada retornar dados consistentes de um pedido conhecido. Consultar por ID não demonstra ausência de pedidos cujo ID é desconhecido.

## Retomada de falhas

Webhooks repetidos e o worker compartilham o marcador durável anterior ao HTTP. Uma resposta incerta, timeout, retorno parcial ou falha de persistência conserva a proteção contra novo envio. Não limpar IDs, tentativas ou estados incertos para destravar um pedido.

O ADM permite corrigir dados, conferir o histórico completo do fornecedor e reconciliar explicitamente a ausência após todas as tentativas anteriores. A reconciliação libera somente uma tentativa manual por quinze minutos, vinculada à revisão atual; o pedido continua fora da automação. Alterar os dados invalida a liberação. Se existir pedido no fornecedor ou a conferência for inconclusiva, não reenviar a criação.

Para uma reversão, desligar primeiro a automação e restaurar o código anterior. Preservar as colunas aditivas, referências e auditorias. Não remover dados persistidos durante a publicação.

Não registrar tokens, CPF completo, nascimento ou payloads do destinatário nos logs e relatórios de publicação.
