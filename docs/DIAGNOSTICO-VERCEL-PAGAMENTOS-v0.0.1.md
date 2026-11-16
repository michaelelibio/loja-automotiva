# Diagnóstico da Vercel, pagamentos e pedido #21

Verificações em 15/11/2026, aproximadamente 18h26–18h34 (America/Sao_Paulo).
Alterações preparadas localmente na branch `main`, baseada em
`2678e899a79b32a3b49fd82d0ae9c9d33ceb6aa1`. Nenhum deploy, push, pagamento,
reprocessamento, restauração ou alteração de dados/configurações de produção foi executado.

## 1. Falha comprovada do frontend e correção local

Os logs completos dos dois builds mais recentes com erro foram consultados:

| Deployment Vercel | Commit | Resultado |
| --- | --- | --- |
| `dpl_GCf9aFkW2x5x9NPL5tNxy6eBnJv7` | `2678e89` / main | ERROR |
| `dpl_3uMV8YMRsCtn6etLUPVVUqGAtu43` | `2678e89` / main | ERROR |

Em ambos, a instalação e a compilação Turbopack terminam, mas a verificação
TypeScript interrompe `npm run build` com `lint_or_type_error`:

```text
lib/store/business-profile.ts(94,3): error TS2322
Types of property 'taxIdType' are incompatible.
Type 'string | null' is not assignable to type '"CPF" | "CNPJ" | null'.
```

O objeto intermediário `values`, sem tipo contextual, alargava o tipo do campo
mutável `taxIdType` para `string | null`. `tsc --noEmit --incremental false`
reproduziu exatamente o erro localmente.

Correção: declarar `values` como `Omit<BusinessProfile, 'pendingFields' | 'ready'>`.
Isso preserva a união de literais aceita pelo contrato, sem cast, sem ignorar
erros do build e sem modificar validações ou informações comerciais.

A execução da suíte também revelou dois testes quebrados por um mock de
`InstitutionalPage`: o mock fornecia uma função diretamente, enquanto as páginas
importam a exportação nomeada. O mock agora fornece `{ InstitutionalPage: ... }`.
Não foi necessário alterar as páginas nem o componente real.

## 2. Configuração e versão da Vercel

| Configuração consultada | Valor |
| --- | --- |
| Root Directory | `frontend` |
| Framework | `nextjs` |
| Node.js | `24.x` |
| Build Command | padrão do framework; logs confirmam `npm run build` → `next build` |
| Install Command | padrão; logs confirmam instalação npm |
| Output Directory | padrão do Next.js |
| Next.js do build com erro | `16.4.6` |
| `NEXT_PUBLIC_PAYMENT_CREATION_ENABLED` em Production | `true` |
| `NEXT_PUBLIC_API_BASE_URL` em Production | `https://api.ingarage.com.br` |

Os valores públicos foram lidos individualmente pela API da Vercel; nenhum
segredo foi incluído neste relatório.

`www.ingarage.com.br` continua associado ao deployment Ready/Production
`dpl_9ASg8wTWqjvpfnbkntmTxSDMTUHJ`, commit
`92d12884316fe1d542de237ee4d0068708d6af40`.

Além da configuração atual do projeto, o bundle JavaScript efetivamente servido
na página `/conta/pedidos/21` foi consultado por GET. Ele contém o botão
“Ir para pagamento”, o endpoint `/payments` e a URL correta da API, sem o ramo
de indisponibilidade incondicional. Portanto, a habilitação do pagamento já está
presente no frontend servido, não apenas na configuração de um build futuro.

## 3. Bloqueio independente da API

O Railway tem como único deployment Active/SUCCESS
`52b0c0f5-eba5-402f-b0c0-8b1631e708a2`, commit
`2678e899a79b32a3b49fd82d0ae9c9d33ceb6aa1`, branch `main`.

A leitura do ambiente do próprio container, e não somente das variáveis do
serviço, confirmou:

```text
SPRING_PROFILES_ACTIVE=controlled
APP_PAYMENT_CREATION_ENABLED=true
PAYMENT_CREATION_ENABLED=true
SPRING_APPLICATION_JSON → app.payment.creation-enabled=false
```

O valor canônico de `SPRING_APPLICATION_JSON` tem precedência sobre as variáveis
de ambiente. A propriedade efetiva continua desabilitada. O perfil `controlled`
também usa `false` como padrão; ativar apenas o alias legado não altera esse padrão.

O endpoint é `POST /api/orders/{orderId}/payments`. O caminho
`PaymentController` → `PaymentService.create` → `PaymentAttemptService.prepare`
consulta essa propriedade antes de criar a tentativa ou acessar o Mercado Pago.
Quando desabilitada, lança `ResourceConflictException` (HTTP 409) com a mensagem
“Pagamentos temporariamente indisponíveis durante a validação da loja.”

Os logs HTTP existentes mostram:

| Horário BRT em 15/11 | Endpoint | Status |
| --- | --- | --- |
| 18:20:55 e 18:21:02 | `/api/orders/22/payments` | 409 |
| 18:21:46 | `/api/orders/23/payments` | 409 |
| 18:22:39 | `/api/orders/20/payments` | 409 |

Os logs de aplicação consultados não registram o corpo dessas respostas nem uma
exceção do provedor. A identificação do bloqueio decorre do valor confirmado no
container e da condição incondicional executada primeiro no código publicado;
nenhum novo POST foi executado para reproduzi-lo em produção.

O cliente `lib/api/payments.ts`, o componente `MercadoPagoPayment` e o contrato
de pagamento do frontend não mudaram entre `92d1288` e `2678e89`. No backend, os
novos campos de `PaymentResponse` são aditivos. A URL, autenticação e corpo
`{ "method": "MERCADO_PAGO" }` continuam compatíveis para essa operação.
Isso não significa que o frontend antigo exiba todas as novas informações da auditoria.

### Correção mínima proposta para futura autorização

Na variável **existente** `SPRING_APPLICATION_JSON` do Railway, alterar somente
o booleano `app.payment.creation-enabled` de `false` para `true`, preservando
todas as demais propriedades e pausas. Alternativamente, remover somente essa
propriedade permitiria que `APP_PAYMENT_CREATION_ENABLED=true` prevalecesse.
Não substituir o JSON inteiro por um fragmento: isso perderia as demais pausas.

Depois, seria necessário aplicar a configuração em um novo deployment/reinício
controlado do backend. Essa alteração **não foi executada**. A publicação do
frontend corrigido, por si só, não remove o bloqueio da API. O envio automático
AliExpress e a recuperação Mercado Pago também permanecem pausados; não foram
reativados durante este diagnóstico.

## 4. Investigação independente do pedido #21

A consulta foi feita em transação `READ ONLY`, com timeout, no serviço PostgreSQL
`56346743-a9d0-4f07-a6c0-99f533afbb70` do ambiente Production do projeto
`f3ad479c-d78a-41fb-b7bb-fb2d0ebddb79`.

O hostname e o nome do banco da conexão `DB_URL` do container ativo coincidem
com esse serviço. Não existe override `spring.datasource.url` no ambiente/JSON
consultado. O banco é `railway`, o schema é `public`, e a única tabela `orders`
encontrada no catálogo consultado é `public.orders`.

Resultado confirmado novamente às 18h33 BRT:

- `public.orders.id=21` **existe**, status logístico `PROCESSING`.
- Seu pagamento persistido está `PAID`, com identificação do provedor presente.
- Fulfillment AliExpress `CREATED`, com referência do fornecedor preservada.
- Estado local do fornecedor: `CREATED_UNPAID_UNVERIFIED`; não houve consulta
  externa ao AliExpress para atualizar esse estado.
- Contagens atuais: 23 pedidos, 16 pagamentos e 11 fulfillments.

As leituras autenticadas da API retornam:

| GET | Resultado |
| --- | --- |
| `/api/admin/health` | 200 |
| `/api/admin/orders/21` | 200, `PROCESSING` |
| `/api/admin/orders/21/aliexpress` | 200, `CREATED`, referência presente |
| `/api/admin/orders?page=0&size=20` | 200, 23 pedidos, inclui #21 |
| `/api/admin/orders?page=0&size=20&status=PROCESSING` | 200, inclui #21 |
| `/api/admin/orders?page=0&size=20&status=PAID` | 200, não inclui #21 |

Logo, não foi constatado desaparecimento do registro nesse banco. O filtro
`PAID` do ADM usa o status logístico do pedido e realmente o exclui, embora o
pagamento continue aprovado. Não foi observada a seleção/filtro da interface
Railway usada pelo proprietário; não é possível atribuir sua visualização a uma
causa específica sem essa evidência. Não há justificativa para restauração.

Consulta segura para conferir o destino e o registro no editor SQL:

```sql
BEGIN READ ONLY;
SELECT current_database(), current_schema();
SELECT id, status, created_at FROM public.orders WHERE id = 21;
COMMIT;
```

## 5. Validação local e alterações preparadas

| Verificação | Resultado |
| --- | --- |
| TypeScript antes da correção | TS2322 reproduzido |
| `tsc --noEmit --incremental false` após correção | aprovado |
| `npm test` | 87 aprovados, zero falhas, APIs simuladas |
| `npm run lint` | zero erros, cinco avisos preexistentes |
| `npm run build` | aprovado; TypeScript e geração de 35 páginas concluídos |

O primeiro build local foi bloqueado pelo acesso de rede às fontes Google no
sandbox. O mesmo comando passou com acesso permitido às fontes, sem alterar
fontes, configuração Next.js ou verificações TypeScript. Durante o build local,
as duas variáveis de URL de API apontaram para `http://127.0.0.1:9`, evitando
acesso à API de produção; `NEXT_PUBLIC_PAYMENT_CREATION_ENABLED=true` e
`NEXT_TELEMETRY_DISABLED=1` foram definidos apenas no processo local.
Esse artefato local não deve ser enviado a produção; um build futuro usará as
variáveis Production confirmadas da Vercel.

Arquivos preparados:

1. `frontend/lib/store/business-profile.ts`: tipo contextual do objeto.
2. `frontend/tests/business-profile.test.cjs`: exportação nomeada no mock.
3. Este relatório.

Sem mudanças em pagamentos, integração AliExpress, dependências, schema ou
configurações de produção. Commit/push e publicação ficam pendentes de autorização;
um push para `main` pode acionar publicação automática e não foi realizado.
