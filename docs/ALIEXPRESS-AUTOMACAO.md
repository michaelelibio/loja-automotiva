# Automação AliExpress: operação e ativação controlada

## Fluxo

1. O webhook autenticado consulta o pagamento na API do Mercado Pago e persiste
   a confirmação, vinculando pedido, pagamento, moeda e valor recebido.
2. Após o commit, o encaminhamento é enfileirado. A fila persistida também é
   varrida periodicamente para recuperar callbacks perdidos ou falhas anteriores
   à submissão ao fornecedor.
3. O serviço exige um pagamento de produção confirmado e consulta novamente a
   API do Mercado Pago antes da criação. Estorno, contestação e revisão financeira
   impedem o encaminhamento.
4. São validados produto, SKU original, quantidade, estoque e destinatário BR
   (endereço, CPF, nascimento e telefone). O payload conserva a data cadastrada
   no pedido no formato exigido pela integração existente.
5. O marcador de submissão é confirmado no banco **antes** da chamada AliExpress.
   A referência externa é estável, e a tomada do pedido usa bloqueio transacional.
6. O ID e o estado retornados ficam disponíveis no ADM. Não há pagamento
   automático ao fornecedor.

Preço do fornecedor, frete, impostos e margem são observações financeiras. Valores
elevados, desconhecidos ou margem negativa não são condições de bloqueio.
A consulta do pagamento continua exigindo que o valor recebido corresponda ao
pedido inGarage: isso valida a identidade do pagamento, não a margem da loja.

## Corte que protege pedidos antigos

A automação exige os dois parâmetros:

| Propriedade Spring | Alias em variável de ambiente | Padrão |
| --- | --- | --- |
| `aliexpress.orders.auto-dispatch-enabled` | `ALIEXPRESS_AUTO_DISPATCH_ENABLED` | `false` |
| `aliexpress.orders.auto-dispatch-created-from` | `ALIEXPRESS_AUTO_DISPATCH_CREATED_FROM` | vazio |

O segundo parâmetro é um instante UTC ISO-8601, escolhido na ativação autorizada.
Use a data/hora da ativação ou uma data futura; **não retroceda o corte para
incluir o histórico sem uma reconciliação e uma decisão explícita**.

Somente pedidos com `created_at >= corte` entram no envio automático. O mesmo
corte é aplicado ao callback pós-pagamento, às consultas das duas filas de
recuperação e à tomada transacional da criação. Um pedido antigo ainda não pago
também fica excluído caso seu pagamento seja aprovado depois da ativação.

O corte é configuração persistente: não é recalculado na inicialização, portanto
reiniciar o backend não faz perder pedidos elegíveis ainda pendentes.
Flag desligada ou corte ausente interrompem o envio automático e a varredura;
um instante malformado é rejeitado na configuração. As operações manuais
existentes conservam suas proteções e requisitos de reconciliação.

O ADM informa `automaticEnabled`, `automaticBlockReason` e
`automaticOrdersCreatedFrom`. Uma pausa não é exibida como promessa de envio;
o motivo por pedido distingue ausência do corte e exclusão do histórico.

## Antes de ativar em produção

1. Ler o estado dos pedidos, tentativas administrativas e referências existentes.
   Separar pedidos antigos pendentes, confirmados, incertos e já criados.
   Não reenviar resultados incertos nem pedidos com ID de fornecedor.
2. Apresentar ao responsável quais pedidos seriam afetados e obter autorização
   para a ativação e seu corte. Commit/push não equivalem à autorização para
   alterar essas configurações.
3. Verificar os valores **no container ativo**, incluindo `SPRING_APPLICATION_JSON`.
   Esse JSON tem precedência sobre aliases do ambiente: um booleano `false` nele
   mantém a pausa mesmo quando o alias correspondente está `true`.
4. A futura ativação requer também `aliexpress.orders.enabled=true`,
   `aliexpress.orders.contract-confirmed=true`, evidência de permissão placeorder
   vinculada ao app e OAuth válido. Reutilizar as credenciais existentes sem
   publicá-las em commits, logs ou relatórios.
5. Alterar somente as propriedades autorizadas no JSON existente. Preservar os
   demais parâmetros, inclusive pausas de CJ, consultas/sincronização, testes
   administrativos reais e recuperação de pagamentos quando não autorizadas.
6. Acompanhar inicialização e health, confirmar o corte e observar a fila.
   A varredura padrão ocorre a cada 60 segundos; submissões reais somente após a
   autorização correspondente e pagamento confirmado.

Esta correção não acrescenta colunas nem exige migração de banco. Não usar
Hibernate `update`. Configurações de banco, checkout e autenticação permanecem
as existentes.

## Falhas e recuperação

- Falha antes da submissão: até três validações, com espera crescente e estado
  persistido. Uma nova instância do worker retoma apenas pedidos elegíveis.
- Rejeição definitiva: registrar a falha e exigir revisão; webhooks repetidos
  não repetem automaticamente a compra.
- Timeout/resultado incerto ou falha ao persistir após a submissão: conservar
  `VERIFICATION_REQUIRED` e as referências obtidas; reconciliar antes de reenviar.
- Pagamento cancelado, estornado ou em revisão: não encaminhar automaticamente.
- A aprovação ou consulta do fornecedor não inicia pagamento AliExpress. Esse
  pagamento permanece exclusivamente manual.

## Validação sem efeitos reais

Os testes cobrem webhooks repetidos, concorrência, confirmação Mercado Pago,
dados inválidos, custos desconhecidos/altos, timeout, estado incerto persistido,
recuperação com nova instância e exclusão das duas filas por corte histórico.
Usar H2/PostgreSQL isolado e mocks dos provedores. Não habilitar testes de sondagem
real nem apontar os testes para o banco ou as credenciais de produção.
