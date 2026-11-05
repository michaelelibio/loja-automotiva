# Checkout com entrega grátis por seleção administrativa

Esta decisão substitui a exigência de evidências de entrega nos relatórios anteriores.

- O controle **Ativo**, já existente no ADM, habilita os produtos AliExpress selecionados pela equipe. Produtos inativos não aparecem no catálogo público e não podem ser comprados.
- A entrega desses produtos custa **R$ 0,00**. O total continua sendo calculado pelo backend, a partir dos preços e quantidades persistidos.
- `VERIFIED_FREE_DELIVERY_EVIDENCE` não é mais uma configuração obrigatória, nem é lida no fluxo ativo. O helper antigo fica isolado, sem bean Spring ou dependências no checkout.
- Nenhuma cotação, tabela estadual ou prazo de validade de evidências é necessário para criar o pedido, iniciar o pagamento ou preparar seu encaminhamento.
- O checkout usa `CATALOG_FREE`. Pedidos históricos `VERIFIED_FREE` mantêm seus snapshots, sem depender novamente da configuração de evidências.
- A validação de destinatário/endereço, preço, variante, estoque, aceite do total, snapshots e idempotência permanece. Prazo desconhecido aparece como “A confirmar”, nunca como zero dias.
- A política de entrega da loja não é enviada como código logístico ao fornecedor. Quando não existe modalidade no snapshot, o payload não inventa `logistics_service_name`, campo opcional do contrato já revisado.
- A exigência de pagamento Mercado Pago real confirmado (`live_mode=true` verificado pelo backend), as permissões AliExpress e as flags de homologação não foram removidas. Pagamento ao fornecedor continua exclusivamente manual.
- O perfil `controlled` e os bloqueios de operações reais permanecem intactos.

Não é necessária nova migração ou variável de ambiente para este ajuste. O esquema e as migrações aditivas da revisão anterior continuam necessários para a versão completa. Frontend e backend devem ser publicados em conjunto, pois os novos pedidos usam `CATALOG_FREE`.

Nenhum produto foi ativado automaticamente, nenhum dado histórico foi excluído e não houve compra, cobrança real, commit, push ou deploy.

Os testes do checkout agora usam configuração de evidências deliberadamente inválida, sem impedir a inicialização ou a compra simulada. Cobrem ativação/inativação administrativa, frete zero, cálculo do total, endereço, estoque, idempotência, pedido interno e vínculo AliExpress simulado. As regressões de pagamentos, webhooks e bloqueios AliExpress também foram executadas.
