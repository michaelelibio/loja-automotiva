# Frete comercial provisório nacional — R$ 24,00

Implementado em 30/10/2026 por decisão comercial explícita da inGarage. R$ 24,00 é preço cobrado ao cliente, NÃO custo comprovado do AliExpress, promoção Choice, escala inferida de shipping_fee_cent ou garantia de rentabilidade.

## Política e cobertura

Reutiliza `commercial_freight_tables.document` JSONB, com campo opcional `provisional`; documentos antigos continuam legíveis. Não há tabela, checkout, integração AliExpress ou migração nova. Exige a estrutura V20261030_02 já existente.

O documento contém elegibilidade por ID LOCAL de variante, atestado administrativo de porte pequeno/volume único, validade e três limites obrigatórios: total máximo por pedido, número máximo de pedidos em aberto e soma máxima dos totais em aberto. Não há limites financeiros escolhidos automaticamente. Preço comercial fixo: 24.00 BRL.

`provisional.nationalRates` prepara deterministicamente 81 combinações: 27 SMALL a R$ 24,00 para os SKUs elegíveis e 54 MEDIUM/LARGE como DRAFT_INACTIVE com preço nulo. A versão salva é rascunho; a cobertura SMALL só fica disponível quando publicada, com a flag comercial habilitada. Os registros nacionais pertencem à política versionada, não à lista de custos verificados `rates`. Categoria, produto e fornecedor reais continuam vinculados aos SKUs do catálogo; nenhuma origem, modalidade ou evidência de custo foi inventada.

A seleção de SKUs é explícita: a aplicação não presume pelo título/categoria que um produto é pequeno. Nenhum SKU de produção foi inscrito nem nenhuma política foi publicada no Railway nesta execução. O cadastro efetivo local foi exercitado com variantes sintéticas nos testes.

## Elegibilidade de cada pedido

- Exatamente uma unidade de um único SKU elegível, AliExpress Dropshipping ativo e com estoque. Não se concede consolidação automática para duas unidades, duas variantes, produtos de fornecedores diferentes ou remessas desconhecidas.
- CEP obrigatório, resolvido por ViaCEP; estado/endereço selecionado deve coincidir no aceite. Todas as 27 UFs têm o mesmo preço comercial.
- Subtotal calculado no servidor a partir do catálogo atual; total = produtos + 24.00. expectedTotal enviado pela interface é apenas consentimento a comparar, não fonte do cálculo.
- Não há frete grátis por atingir R$ 99 ou outro subtotal. Um item de R$ 100, por exemplo, continua com R$ 24 de frete se estiver dentro dos limites configurados.
- Ausência, expiração, retirada da política, item não elegível ou limites excedidos mantêm a cotação assistida como fallback. Não substituir automaticamente pelo preço de outro porte.

## Custos, prazos e exposição

Na decisão e no snapshot do pedido: custo do fornecedor, reserva calculada, taxa de pagamento e lucro permanecem **null**, nunca zero; policyType=PROVISIONAL_24. Não há modalidade, origem ou prazo AliExpress fabricados. O frontend mostra **Prazo a confirmar**, em vez de zero dias. O preço comercial é mostrado normalmente no produto, carrinho e checkout existentes após informar CEP válido.

Os limites agregam TODOS os pedidos comerciais em aberto, inclusive de versões anteriores ou de políticas verificadas: excluem somente CANCELED, EXPIRED e DELIVERED. Cancelar/expirar/entregar libera o respectivo compromisso; publicar outra versão não zera o acumulado. Um pedido com prazo vencido ainda conta enquanto não tiver o status alterado pelo fluxo normal (comportamento conservador).

A criação de pedidos, publicação e retirada serializam no mesmo registro comercial, com bloqueio pessimista e atualização do estado. A quota é reavaliada na transação que grava o pedido. Cotações de tela não reservam quota: se outro pedido consumir o limite, a confirmação é recusada e o cliente deve atualizar sua seleção. A idempotência existente impede repetição do mesmo pedido.

Esses limites controlam a quantidade e o valor de compromissos assumidos pela loja; NÃO demonstram limite do custo do fornecedor ou prejuízo máximo, pois esse custo ainda é desconhecido. Pagamento e encaminhamento continuam indisponíveis. Mesmo se alguém futuramente habilitar o gate global de pagamento, a revalidação recusa um snapshot PROVISIONAL_24 sem custo completo: será necessária outra cotação completa e novo aceite. Não há aumento unilateral do total de pedidos já aceitos.

## Operação administrativa

1. Em ambiente local isolado, abrir `/admin/fretes`, seção **Frete comercial provisório: R$ 24,00**.
2. Conferir os produtos/variantes no catálogo e informar os IDs locais das variantes, não os IDs externos AliExpress. Confirmar explicitamente que são pequenos e de volume único.
3. Definir validade, máximo por pedido, máximo de pedidos abertos e soma máxima aberta. Campos ficam em branco até escolha administrativa.
4. Salvar como rascunho. A cobertura nacional SMALL/24 e os portes maiores inativos já estão preparados na mesma versão.
5. Para ativar em ambiente autorizado, é necessário `COMMERCIAL_FREIGHT_ENABLED=true` e clicar **Ativar política provisória**. Preservar `SPRING_PROFILES_ACTIVE=controlled`, `PAYMENT_CREATION_ENABLED=false` e `NEXT_PUBLIC_PAYMENT_CREATION_ENABLED=false`. Nenhuma dessas configurações foi alterada nesta execução.
6. **Desativar política provisória** retira a versão. Para mudar elegibilidade/limites, copiar e revisar, salvar nova versão e publicar. Publicação substitui a política comercial vigente por inteiro; versões anteriores não são reativadas automaticamente.
7. Não alterar pedidos anteriores. Cancelamento e eventual nova cotação são operações explícitas; não reaplicar R$ 24 retroativamente.

Endpoint novo, somente ADMIN: POST `/api/admin/shipping-tables/provisional`. Publicar/retirar e listar reutilizam os endpoints existentes. POST recebe `eligibleVariantIds`, `singleSmallUnitParcelConfirmed`, `validUntil`, `maximumOrderTotalBrl`, `maximumOpenOrders`, `maximumOpenSalesBrl`. O cliente não escolhe outro preço neste contrato; o servidor fixa 24.00.

Ambos backend e frontend precisarão ser publicados futuramente para esta entrega. Não há autorização de deploy nesta tarefa. Manter os gates de pagamento e pedidos AliExpress desabilitados. Em rollback, retirar a política ou desabilitar a flag comercial, mantendo snapshots e valores históricos; não apagar tabelas.

## Validação local

Testes usam exclusivamente produtos, pessoas e endereços sintéticos, gateways simulados e PostgreSQL local `ingarage_homologation` em loopback. A cópia restaurada de produção não foi usada nesta etapa. Nenhum código em integration/aliexpress foi modificado, nenhum OAuth foi lido e nenhuma requisição autenticada real foi enviada.

Casos cobertos: 27 UFs, 27 preços SMALL + 54 portes maiores sem preço, custo/lucro desconhecidos, pedido com total 74 para produto sintético de 50, idempotência, consentimento divergente, múltiplas unidades/SKUs, ausência de gratuidade acima de 99, teto por pedido, teto agregado, contagem entre versões, liberação após cancelamento, retirada da política, preservação de total anterior, serialização JSONB e pagamento HTTP 409. Os números dos limites e dos produtos nos testes são apenas fixtures, não recomendação comercial.

Resultado final: **217 testes backend, zero falhas/erros/ignorados, BUILD SUCCESS**, incluindo 75 testes comerciais PostgreSQL e 72 H2; **60 testes frontend aprovados**, lint sem erros (quatro avisos existentes) e build Next.js/TypeScript aprovado. Perfil controlled idêntico ao HEAD; git diff --check aprovado. Cluster isolado encerrado após os testes, dados preservados. Sem banco de produção, pagamentos reais, pedidos AliExpress, commit, push ou deploy. Logs ignorados em target/homologation-final/provisional-{backend-final,frontend-tests,frontend-lint,frontend-build}.log.
