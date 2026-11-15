# Primeira criação real AliExpress — pedido próprio #19

## Resultado desta tarefa: NÃO LIBERADO PARA CRIAÇÃO REAL

Não foi executado placeorder, tentativa com payload inválido, pagamento, refresh OAuth,
migração, alteração de variável, commit, push ou deploy. Consultas de produção foram
READ ONLY; houve uma única chamada autenticada de leitura `aliexpress.ds.product.get`.

O usuário confirmou nesta sessão que o pedido #19 é próprio de teste e que destinatário
e endereço têm autorização expressa. Isso autoriza preparar a operação; não autoriza
executá-la durante esta tarefa. A confirmação não equivale à permissão da plataforma.

### Bloqueios exatos

1. `AliExpressPlaceOrderPermission.verified()` continua **false**. Não há evidência
   autenticada do console/suporte demonstrando concessão de `aliexpress.trade.buy.placeorder`
   à aplicação atual. Não houve negativa do provedor a esse método, pois ele **não foi
   chamado**. O estado correto é NOT_VERIFIED, não PERMISSION_DENIED nem sucesso.
2. `AliExpressOrderQueryPermission.verified()` também continua false; a consulta real
   de pedidos deve ser homologada separadamente. O catálogo público exige OAuth;
   `authType=1` não é uma lista de grants da aplicação inGarage.
3. O Railway permanece controlled, pagamento false, criação normal false, real-test
   ausente/default false. #19 já está na lista privada administrativa, mas fixture-order-id
   não aponta para 19 e **não há fixture-json**. Não alterei esses valores.
4. Não há modalidade de entrega, frete, impostos e total final de compra confirmados
   para o endereço próprio. CATALOG_FREE é política da inGarage e não prova frete zero
   nem disponibilidade logística no AliExpress.
5. O vínculo do OAuth com a conta compradora que será usada para pagar manualmente
   ainda precisa ser cotejado no console. Consulta de produto aceita não identifica,
   por si, a conta em cujo histórico a compra aparecerá.

O código permanece bloqueado independentemente de flags. Não retirar controlled,
substituir verificadores por `return true`, usar testes mockados como prova ou tentar
placeorder para descobrir se a permissão existe. Para liberar a primeira compra será
necessária uma entrega posterior, com verificação das evidências acima; não basta
executar os comandos desta documentação ou configurar o JSON.

## Evidências obtidas sem compra

Inspeção do pedido #19 por transação READ ONLY, sem imprimir nome, endereço, CPF,
telefone ou nascimento: PENDING_PAYMENT, CATALOG_FREE, uma unidade de produto
`1005010687850279`, SKU `12000053193152987`; variante importada consistente com o item.
Campos pessoais presentes e formatos básicos preenchidos; isso não comprova que o
AliExpress aceitará o destinatário brasileiro. Não havia IDs de fornecedor no registro
administrativo de teste do #19.

Consulta pontual autenticada do produto: onSelling, SKU encontrado com `sku_attr=14:193`,
estoque retornado 118, preço regular BRL 145.56 e oferta BRL 62.59. Conferida em
`2026-11-06T00:09:15.564Z` (05/11, 21:09 em São Paulo). Esses são dados de consulta,
não preço final garantido, reserva de estoque, preço para aquele comprador ou total
com frete/impostos. Precisam de reconferência imediatamente antes da aprovação.

Essa chamada comprova que o token existente foi aceito para **consulta de produto**
com assinatura TOP atual. Não prova placeorder, consulta de pedidos ou permissão de frete.
Segredos foram mantidos somente em memória; não foram enviados ao chat, Git ou logs.

Evidência reduzida, sem dados pessoais/segredos:
[ALIEXPRESS-REAL-ORDER19-20261105.evidence.json](ALIEXPRESS-REAL-ORDER19-20261105.evidence.json).

## Contrato oficial e diferenças corrigidas localmente

Fontes oficiais atuais, obtidas por HTTP público sem login, não por exemplo de compra:

- [placeorder, docId 479](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.trade.buy.placeorder&methodType=GET%2FPOST).
- [consulta, docId 1199](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.ds.trade.order.get&methodType=GET%2FPOST).
- SDK Java/source.jar do link publicado nesses contratos, SHA-256
  `E36C92A8E6583E560CD100BE4EF23FFF30674B52E6CE1ACFDCFAE928055BB889`.
  Código-fonte lido, não executado.

Os exemplos Java dos dois métodos usam IopClient/Protocol.TOP. TopExecutor e IopUtils
indicam /sync, session, epoch em milissegundos, HMAC-SHA256, concatenação ordenada dos
parâmetros sem sign e sem prefixo de API e hexadecimal maiúsculo. A região api-sg é
a já usada pela aplicação e aceita na consulta de produto; autorização da compra
nessa região permanece pendente. Não misturar o curl genérico GOP/access_token com TOP.

O cliente de criação já usava esse transporte. A consulta ainda usava Taobao legado,
router/rest, HMAC-MD5 e data GMT+8; foi adaptada ao contrato atual, sem fallback/retry.
Ela exige rsp_code 200, rejeita envelope de erro e não publica mensagens pessoais do
fornecedor. As permissões e flags continuam desabilitadas.

O contrato atual também documenta out_order_id e o nascimento brasileiro, ausentes
na referência legada anteriormente consultada. O **payload próprio de teste** passou a
incluir esses campos; snapshots e checkout normal não foram alterados. A preparação
normal de clientes ainda precisa de homologação própria antes da futura automação.

| Dado de teste revisado | Representação enviada |
|---|---|
| ID de produto | product_items[].product_id, inteiro sem conversão JavaScript |
| SKU 12000053193152987 | Conferir na leitura atual; enviar o sku_attr original 14:193, não o ID numérico no campo sku_attr |
| Quantidade | product_items[].product_count = 1 |
| Serviço disponível e confirmado | product_items[].logistics_service_name, sem inferir CATALOG_FREE |
| Endereço próprio | logistics_address: country BR, province, city, address, zip e demais campos revisados |
| Identificação BR | cpf, mobile_no, phone_country +55, locale pt_BR; validação local não substitui aceitação pelo fornecedor |
| Nascimento próprio | ds_extend_request.trade_extra_param.birthday, ddMMyyyy |
| Referência estável | out_order_id = id da fixture, sem gerar nova referência a cada clique |

Não existe campo de pagamento ou instrução de pagar no payload. Os valores de custo
ficam na revisão/digest; não inventar campo de limite de preço não documentado na API.

## Ação administrativa preparada, mantendo o bloqueio

Rotas exclusivas ADMIN, sob `/api/admin/orders/{id}/aliexpress-tests`:

- GET `/payload-review`: retorna exclusivamente o payload da fixture privada autorizada,
  com digest e referência de autorização. Requer ID allowlisted e fixture completa ligada
  ao ID. Não copia dados automaticamente de orders, do navegador ou de clientes.
  Resposta Cache-Control no-store e toString redigido para evitar logs de dados pessoais.
- POST `/create-real`: operação já separada do Mercado Pago. Exige frase explícita,
  identificador e digest exatos; todos os bloqueios de homologação permanecem.
- POST `/sync`: consulta somente IDs persistidos no registro de teste, com permissão
  própria e flags de consulta; bloqueada em controlled. Não altera Order, Payment ou
  fulfillment normal. Sem IDs, exige reconciliação manual, nunca novo placeorder.

No ADM, revisar o payload antes de abrir a confirmação. A interface não permite
confirmar criação antes dessa revisão. O backend exige digest e frase da operação;
isso representa a declaração administrativa de revisão, não uma prova de leitura humana.
Custos e endereço devem ser conferidos visualmente contra o pedido próprio #19.
Não criar uma fixture de outro produto/endereço apenas para fazê-la passar na validação.

A fixture agora exige birthDate ISO (AAAA-MM-DD), CPF/telefone/CEP válidos localmente,
phone_country e locale. Valores antigos sem nascimento são bloqueados antes da rede.
Não colocar fixture real em arquivo versionado, evidência pública ou mensagem de chat.

O teste pode ser independente de Mercado Pago porque é próprio e explicitamente
autorizado. Isso não altera o caminho normal: PAID, método Mercado Pago, ID/paidAt,
live_mode=true e verificação do provedor continuam obrigatórios. A futura criação
automática após esse pagamento é uma etapa posterior, não habilitada nesta entrega.

## Duplicações, IDs e estados

A reserva VERIFICATION_REQUIRED é confirmada no banco antes da única chamada de
criação. Lock por pedido e real_key global única sobrevivem a clique duplicado,
concorrência, timeout, queda e reinício. Nenhum desbloqueio por tempo ou retry automático.
out_order_id acrescenta correlação com o fornecedor, mas não substitui a reserva local
nem prova que o provedor aplicou idempotência da forma esperada para esta aplicação.

Sucesso confirmado guarda todos os IDs retornados. IDs numéricos de resposta parcial
ou ambígua também são preservados no registro de teste, **sem declarar criação
confirmada** ou liberar reenvio. Consultá-los primeiro. Resposta totalmente incerta,
sem IDs: comparar conta compradora, horário, item e referência com histórico/suporte.
O contrato de order.get recebe ID AliExpress, não uma referência externa; não há
reconciliação automática por out_order_id implementada ou comprovada aqui.

Consulta de teste persiste uma observação por ID, horário, código seguro e estado
verificado/desconhecido. Timeout/erro substitui a observação por UNKNOWN; resultados
anteriores em consulta em andamento são identificados como potencialmente desatualizados.
Uma conclusão atrasada não sobrescreve outra consulta mais recente.

PLACE_ORDER_SUCCESS é exibido como aguardando pagamento manual; IDs de criação
isolados não comprovam pagamento ao fornecedor. RISK_CONTROL passou a UNKNOWN,
conservando o código seguro: não há evidência suficiente aqui para apresentá-lo como
pagamento confirmado. Código desconhecido/erro jamais implica pagamento.

## Homologação antes de autorizar o único clique real

1. No console da aplicação, cotejar App Key e conta compradora sem divulgar secrets.
   Guardar evidência privada datada dos métodos concedidos para placeorder e order.get,
   ambiente/região e estado da aplicação. Se faltar grant, solicitar ao suporte AliExpress;
   não regenerar credenciais nem executar compra de sondagem.
2. Confirmar OAuth da mesma conta compradora e método de consulta com um ID **já
   existente e pertencente a essa conta**, se houver; nunca criar um pedido para testar
   a autorização de consulta. Sem ID existente, obter evidência de grant e planejar a
   primeira consulta apenas após uma criação posteriormente autorizada.
3. Reconferir o produto/SKU, estoque, logística/origem disponíveis para o endereço
   próprio completo e custo discriminado de produto, frete, impostos e total na conta.
   Registrar moeda e validade dessa revisão. Oferta de produto e frete zero na loja
   não satisfazem esta etapa. Não pagar nem finalizar compra no portal durante a revisão.
4. Implementar/revisar a verificação dessas evidências antes de liberar o endpoint.
   Nesta versão os verificadores concretos retornam false e readiness bloqueia sempre:
   **não há comando para habilitar a compra com segurança ainda**.
5. Só após essa homologação, preparar em gerenciador privado a fixture-order-id=19,
   produto/SKU acima, quantidade=1 e endereço/nascimento próprios autorizados. Referência
   idempotente proposta: `ingarage-test-19-v1`. Não usar SIM-AE-* como ID real.
   A lista administrativa deve conter apenas os pedidos próprios autorizados.
6. ADMIN abre #19, revisa a configuração, custos e `/payload-review`, confere digest e
   out_order_id e digita `CRIAR PEDIDO REAL DE TESTE NÃO PAGO NO ALIEXPRESS`. Esse clique
   futuro precisa de autorização operacional nova e explícita. Não clicar nesta tarefa.
7. Guardar IDs; consultar cada um; abrir a mesma conta compradora → Meus pedidos →
   Aguardando pagamento. Comparar produto, variante, endereço e total. Só então o
   operador poderá decidir pagar manualmente. Nunca pagar somente porque o ADM exibe ID.
8. Qualquer timeout/erro ambíguo encerra a tentativa. Não trocar referência, excluir
   marcador, reutilizar outro pedido interno ou editar IDs no banco para tentar novamente.
   Sem reconciliação suficiente, manter bloqueado e acionar suporte.

O planejamento de pagamentos manuais noturnos é operacional. Não foi criado job,
scheduler, chamada de pagamento ou compra automática nesta tarefa.

## Publicação futura, sem execução automática

O HEAD local 97c8814 contém frete e arquivos fora da seleção antiga. Não publicar essa
árvore inteira. O [manifesto combinado](ALIEXPRESS-REAL-ORDER19-20261105.files.txt) aplica
somente base `a67f880636e86557f9e20f190fddb6b002798a1f` + arquivos da release administrativa
anterior + esta preparação. Não alterar índice/histórico automaticamente.

Preparação manual de uma fonte separada, sem reset/rewrite da árvore atual:

```powershell
# SOMENTE OPERADOR. Origem: raiz da arvore que contem os 30 arquivos revisados.
$sourceRoot = (Get-Location).Path
$releaseDir = Read-Host 'Caminho absoluto NOVO para worktree isolada da release'
if (Test-Path -LiteralPath $releaseDir) { throw 'Destino ja existe; escolher outro, sem apagar arquivos' }
git worktree add --detach $releaseDir a67f880636e86557f9e20f190fddb6b002798a1f
if ($LASTEXITCODE -ne 0) { throw 'Worktree falhou' }
$files = @(Get-Content -LiteralPath docs/ALIEXPRESS-REAL-ORDER19-20261105.files.txt)
foreach ($file in $files) {
  $destination = Join-Path $releaseDir $file
  New-Item -ItemType Directory -Force -Path (Split-Path $destination) | Out-Null
  Copy-Item -LiteralPath (Join-Path $sourceRoot $file) -Destination $destination
}
git -C $releaseDir status --short
git -C $releaseDir diff --check
```

Revisar todos os arquivos completos e revalidar essa fonte. Se houver autorização
posterior para staging/commit/publicação, usar o manifesto **combinado de 30**, não o
manifesto antigo de 19. Os comandos de commit/push do roteiro antigo não devem ser
executados por esta tarefa, nem reutilizados com a seleção antiga para esta mudança.

Antes do backend: backup concluído, schema inspecionado e janela autorizada. A tabela
de testes já existia no Railway nesta consulta. A nova coluna supplier_sync precisa da
migração aditiva abaixo. Não reaplicar V02/V03, não usar ddl-auto=update e não promover
o novo JAR antes de a coluna existir, pois controlled usa Hibernate validate.

```powershell
# SOMENTE OPERADOR, em conexao psql autorizada, depois de backup e aprovacao.
# Este comando NAO foi executado nesta tarefa.
psql -X -W -v ON_ERROR_STOP=1 --single-transaction -f backend/src/main/resources/db/migration/V20261105_01__admin_aliexpress_test_sync.sql
if ($LASTEXITCODE -ne 0) { throw 'Migracao falhou; nao publicar' }
```

O banco atual é privado: usar sessão operacional autorizada na rede Railway ou
expandir/transmitir o SQL pelo mesmo procedimento SSH documentado em
[release anterior](RELEASE-ADM-SIMULACAO-20261105.md), com transação e timeouts.
Não criar proxy nem mudar credenciais automaticamente.

O snippet psql acima pressupõe PGHOST/PGPORT/PGDATABASE/PGUSER já conferidos para o
alvo correto; não colocar senha na linha de comando. Para executar pelo Railway SSH,
o operador pode usar o conteúdo do novo arquivo, envolvido em BEGIN/COMMIT, pelo
mesmo transporte base64 do roteiro anterior. Não transmitir o wrapper antigo que
reaplica a migração anterior por engano.

Publicar primeiro backend, depois frontend, de seleção limpa e validada, com os
comandos Railway/Vercel do roteiro anterior. Nesta etapa manter controlled, pagamentos
false, criação/sync reais false e real-test false; não mudar o perfil para desbloquear.
Root Railway /backend e Root Vercel frontend; build frontend Webpack conforme validado.
Revalidar 401/403, IDs de teste e motivos de bloqueio depois de cada promoção.

Rollback: voltar ao par de deployments protegidos anterior e conservar a coluna
aditiva, IDs e chaves idempotentes. Não apagar a tabela, não restaurar dump sobre
produção nem permitir cobranças ao voltar variáveis/imagens. A nova migração só adiciona
supplier_sync; o binário anterior pode ignorá-la.

## Testes locais

Os testes usam H2, clientes HTTP mockados e dados sintéticos. Cobrem nascimento BR,
referência idempotente, ausência de dados de cliente no payload, confirmação/digest,
grants ausentes, controlled, timeout/concurrência, IDs parciais, consulta e isolamento
de estados financeiros. Não comprovam permissão ou aceitação real de placeorder.

Validação da seleção combinada limpa (a67f880 + 30 arquivos do manifesto):

- Maven verify: **811 casos reportados; 755 executados/aprovados, 56 ignorados,
  zero falhas e zero erros; BUILD SUCCESS**. Probes externos e suítes opcionais
  PostgreSQL permanecem ignorados. JAR executável de produção gerado.
- Frontend: **68 testes aprovados**, TypeScript e build Production Next.js/Webpack
  aprovados; lint com zero erros e cinco avisos preexistentes em arquivos da base.
- A árvore de trabalho completa também passou sua suíte, mas seus números não são
  atribuídos à release: ela contém frete provisório fora do manifesto combinado.
- Java local JDK 25, target Java 17; não alegada execução local no JDK 21 do Railway.
- Checkout, frete, configurações controlled e os verificadores de permissão não foram
  alterados. Índice Git permanece vazio; nenhuma seleção foi commitada/publicada.
- A nova migração supplier_sync não foi aplicada em banco externo. Validar schema e
  backup na janela autorizada antes de publicar o novo binário.

Logs e cópia de validação ficam em `backend/target/aliexpress-real19/`, fora do Git.
Nenhuma resposta de testes mockados é apresentada como criação real do #19.
