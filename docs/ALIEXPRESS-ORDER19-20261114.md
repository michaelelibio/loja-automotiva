# Pedido próprio #19 — candidato para criação real AliExpress

Preparação atualizada em 14/11/2026 conforme a decisão explícita do proprietário.
A primeira requisição real só será enviada pelo ADM após o clique e a confirmação.
Nenhum placeorder, pagamento, commit, push, migração de produção ou deploy foi executado.

## Comportamento pronto para publicação

O botão **Criar pedido real no AliExpress** em `/admin/pedidos/19` usa o produto,
SKU, quantidade e destinatário salvos no próprio pedido. Não depende de fixture
externa, pagamento Mercado Pago, simulação, grants individuais previamente comprovados,
homologação de comprador nem orçamento de fornecedor menor que o valor da inGarage.
O método oficial decidirá a autorização e a aceitação na tentativa confirmada.

A confirmação mostra produto/SKU/quantidade, conta conhecida do OAuth ou indicação
explícita de identidade desconhecida, endereço completo, custo do produto disponível,
fonte desse custo e valor registrado na inGarage. Frete, impostos, modalidade e total
não conhecidos continuam desconhecidos. Não são convertidos em zero. A modalidade
opcional não confirmada é omitida do payload para o AliExpress escolher ou rejeitar.
Não foi desenvolvido outro cálculo de frete, checkout ou simulador.

A consulta de produto durante a preparação é somente leitura. Seu erro ou ausência
de preço não impede a tentativa. Valor importado é identificado como possivelmente
defasado. Produto acima do valor interno é permitido. Há checkbox específico para
aceitar custos conhecidos/desconhecidos e possível custo maior, além da frase exata:
`CRIAR PEDIDO REAL DE TESTE NÃO PAGO NO ALIEXPRESS`.

O backend exige esse aceite, ADMIN, pedido #19 na lista privada, flag dedicada,
dados obrigatórios válidos e revisão persistida com prazo de 120 segundos, vinculada
ao administrador, payload e token. Mudança de dados ou token antes da compra exige
reabrir a confirmação. Os dados não são recebidos do navegador no POST de criação.

`VERIFICATION_REQUIRED` e `out_order_id=ingarage-test-19-v1` são gravados sob lock em
transação independente concluída antes da única chamada HTTP de placeorder. Não há
retry, redirect, pagamento, expiração do marcador ou reset automático. Erro de rede,
timeout, resposta parcial ou falha posterior do banco nunca liberam outro envio.

Todos os IDs válidos são mantidos como strings decimais. Erros oficiais de permissão,
validação e HTTP são salvos com código, subcódigo, mensagem sanitizada e HTTP status.
Uma resposta concreta não é substituída por NOT_VERIFIED. Credenciais, payloads,
URLs e dados pessoais são removidos ou provocam omissão segura da mensagem.

Consulta posterior usa somente IDs persistidos do #19. Identidade conhecida divergente
é recusada; identidade ausente não vira exigência de homologação. Após rotação do token,
a consulta de leitura pode ser decidida pelo próprio AliExpress. Falhas não comprovam
pagamento e não autorizam novo placeorder. Aguardar pagamento manual só é mostrado
quando observado no status oficial. Sem IDs, reconciliação em Meus pedidos/suporte.
Inclusive rejeição registrada permanece sem botão de reenvio automático nesta entrega.

## Produção conferida somente para leitura

Railway: projeto `positive-cooperation`, ID `f3ad479c-d78a-41fb-b7bb-fb2d0ebddb79`,
serviço `loja-automotiva`, ambiente `production`, raiz `/backend`.
Deployment atual: `d04c3459-a979-4a52-940e-77d9b72f3cdf`.
Commit informado pelo serviço: `97c881465a93453312ce5d1bfaeae0dd1871a05f`, igual ao HEAD local.
Domínio da API: `https://api.ingarage.com.br`.

Consulta em transação READ ONLY, sem exibir dados pessoais ou credenciais:

- #19 PENDING_PAYMENT, CATALOG_FREE, um item, quantidade 1.
- Produto `1005010687850279`, SKU `12000053193152987`, variante consistente.
- Destinatário e campos de endereço presentes.
- Nenhuma tentativa real ou ID AliExpress registrado para #19.
- A tabela administrativa existe, mas faltam supplier_sync, preflight e provider_error.
  As colunas de identidade OAuth também não existem nessa base.
- controlled, validate, SQL init never, pagamentos false, criação normal false e
  contrato normal false. As credenciais da aplicação estão presentes, sem valores expostos.
- A allowlist administrativa e a flag real estão ausentes/default false. Precisam ser
  configuradas na publicação para o botão ficar habilitado exclusivamente no #19.

OAuth conferido por hash da aplicação em transação somente leitura: uma autorização
correspondente, access token criptografado presente e expirado, refresh token
criptografado presente e ainda válido. Não foi renovado nesta auditoria. Na preparação
do botão, o serviço de tokens existente renova se necessário antes da leitura de
produto e da revisão. Só uma rejeição real dessa renovação exige nova autorização;
a ausência de identidade ou comprovação de grant não exige reconexão preventiva.

Vercel: CLI oficial 65.16.0 instalada/consultada; `whoami` retornou **Logged out**.
Não foi identificado nem alterado o projeto Production pelo CLI. Domínio a preservar:
`https://ingarage.com.br`. É necessário autenticar a conta Vercel e selecionar o projeto
existente desse domínio antes de publicar. Não criar projeto temporário ou outro site.

## Commit preparado

Mensagem: `feat: permite tentativa real AliExpress confirmada no pedido proprio 19`

O manifesto e `order19-v2.patch` contêm somente este delta sobre o HEAD conferido.
A projeção limpa é formada por `git archive HEAD` mais os arquivos do manifesto.
Artefatos de comandos antigos soltos na raiz são excluídos apenas da projeção de publicação; os originais são preservados.
O índice original não foi alterado e não há commit criado. Nenhum `.env`, secret,
fixture real, node_modules, banco, log ou artefato anterior entra no pacote de fontes.

Após autorização de commit, revisar o delta e usar a lista exata do manifesto,
sem `git add .`. Conferir auto deploys antes de push para não publicar fora da janela.
A base completa continua necessária; o ZIP de delta não é um projeto independente.

## Publicação controlada preparada

1. Registrar o deployment anterior e garantir backup do banco. Autenticar a Vercel,
   selecionar o projeto existente de ingarage.com.br e registrar sua versão atual.
2. Aplicar manualmente `backend/ops/admin-order19-migration.sql`, com ON_ERROR_STOP.
   A migração é aditiva: inclui as colunas listadas acima e não altera orders,
   payments, fulfillment normal, dados do destinatário ou tentativas anteriores.
   Não usar Hibernate update/create nem executar schema.sql em produção.
3. Preservar perfil controlled e todos os bloqueios globais atuais. Alterar somente:

```powershell
railway.cmd variable set --project f3ad479c-d78a-41fb-b7bb-fb2d0ebddb79 --service loja-automotiva --environment production --skip-deploys "ALIEXPRESS_ADMIN_TEST_ORDER_IDS=19" "ALIEXPRESS_ADMIN_TEST_REAL_ENABLED=true"
```

Não preencher grant-evidence, quote-evidence ou fixture-json: deixaram de ser requisitos.
Manter as credenciais e referências OAuth existentes. Não regenerar secrets.

4. Publicar o backend da projeção limpa, preservando a estrutura com `/backend`:

```powershell
railway.cmd up CAMINHO_DA_PROJECAO_LIMPA --path-as-root --project f3ad479c-d78a-41fb-b7bb-fb2d0ebddb79 --service loja-automotiva --environment production
```

Conferir saúde, ADMIN/403 para clientes, flags globais false e GET administrativo #19
canCreateReal=true. Preparar a confirmação faz leitura de produto e grava a revisão;
não executar POST create-real nem a confirmação do botão nesta publicação.

5. Vercel, após login e vínculo com o projeto correto: manter API apontando para
   api.ingarage.com.br e NEXT_PUBLIC_PAYMENT_CREATION_ENABLED=false. Gerar Production
   staged com `vercel deploy --prod --skip-domain`, inspecionar e promover somente o
   deployment correto com `vercel promote URL_DO_DEPLOYMENT`. Não publicar antes do
   backend compatível nem alterar o domínio para projeto temporário.
6. Conferir visualmente #19 e outro pedido, sem confirmar compra. Depois, o proprietário
   poderá clicar, aceitar os custos e digitar a frase. Guardar IDs, consultar status e
   localizar os mesmos IDs em Meus pedidos para decidir o pagamento manual.

Apenas o último POST confirmado cria a compra. Não existe pagamento automático.
Rollback: desabilitar a flag administrativa e preservar todos os registros/colunas.
Não apagar marcador, trocar referência ou restaurar banco antigo para liberar reenvio.

Executar a CLI Vercel na raiz da projeção, com Root Directory remoto
`frontend`; não duplicar a raiz executando dentro de frontend. O build `.next`
não é um artefato Vercel prebuilt.

## Validação

Backend: **129 testes, zero falhas/erros**, incluindo **16 testes em PostgreSQL**
e os mesmos 16 cenários administrativos em H2. Frontend: **73 testes, zero falhas**,
com os 16 testes da classe alterada repetidos após o ajuste final de asserções.
TypeScript, lint dos arquivos alterados e build de produção Next.js: **PASS**.
Empacotamento executável do backend: **PASS**.

Resultados finais também registrados no manifesto atualizado. A migração aditiva passou
em PostgreSQL local e foi reaplicada sem perder o marcador ou o OAuth. Testes usam
contas e endereços sintéticos e mocks de HTTP; nenhuma autorização da plataforma é
presumida a partir deles. O build frontend de produção e o lint/TypeScript passaram.

Referências operacionais oficiais:
[Railway CLI](https://docs.railway.com/cli/deploying) e
[Vercel staged Production](https://vercel.com/docs/cli/deploying-from-cli).
