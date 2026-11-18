# Retornos de pagamento e identidade visual — 18/11/2026

## Mercado Pago

A configuração de produção tinha `FRONTEND_URL=https://ingarage.com.br`, mas as três variáveis `MERCADO_PAGO_CHECKOUT_SUCCESS_URL`, `MERCADO_PAGO_CHECKOUT_PENDING_URL` e `MERCADO_PAGO_CHECKOUT_FAILURE_URL` ainda apontavam para `https://loja-automotiva-phi.vercel.app/pagamento/...`. O gateway usa essas variáveis diretamente em `back_urls`; não depende de `FRONTEND_URL`, do Origin do navegador nem de `VERCEL_URL`.

Os novos checkouts em modo produção fixam a origem HTTPS `ingarage.com.br`, preservando a rota e a query configuradas. Valores ausentes recebem as rotas padrão:

| Resultado | Retorno |
| --- | --- |
| Aprovado | `https://ingarage.com.br/pagamento/sucesso` |
| Pendente | `https://ingarage.com.br/pagamento/pendente` |
| Recusado | `https://ingarage.com.br/pagamento/falhou` |

O sandbox preserva suas URLs públicas configuradas. As três variáveis antigas da Railway foram alinhadas às URLs acima, sem alterar as demais configurações; a comparação de hashes exclui somente esses três valores. O webhook/notification URL permanece idêntico. O frontend da Vercel usa `NEXT_PUBLIC_API_BASE_URL=https://api.ingarage.com.br` e não utiliza `VERCEL_URL` para retornos de pagamento.

Também foi identificado um redirect 308 da Vercel de `ingarage.com.br` para `www.ingarage.com.br`. A publicação remove esse redirect do domínio raiz para que as rotas de retorno sejam servidas no domínio solicitado. `www` continua funcionando, sem introduzir um redirect inverso que possa conflitar com redirects permanentes já armazenados nos navegadores.

Nenhuma preferência ou cobrança real foi criada durante os testes. Preferências existentes não são reescritas. As telas de retorno continuam apenas informativas e oferecem acesso aos pedidos; parâmetros recebidos do navegador não aprovam pagamentos. A consulta autenticada ao provedor e a validação backend do webhook foram preservadas.

## Categorias

Os blocos pretos não eram imagens cadastradas: a home não renderizava imagens nos cards e aplicava somente fundos grafite/gradientes. Foram criadas oito ilustrações SVG originais e autocontidas para lavagem, proteção, detalhamento, polimento, cuidados internos, cuidados externos, ferramentas e acessórios. Somadas, pesam aproximadamente 13,4 KB e não usam fotografias, marcas, fontes nem recursos de terceiros.

Cards mantêm enquadramento 4:3 e carregamento lazy. Uma `imageUrl` específica válida tem prioridade; falhas de carregamento usam a ilustração correspondente. Se até o asset local falhar, uma ilustração inline evita imagem quebrada ou bloco preto. Categorias novas recebem fallback por nome/caminho, sem modificar categorias ou produtos cadastrados.

## Favicon

Letra G geométrica laranja `#ff6600` sobre grafite `#121212`, desenhada como vetor independente de fontes. O Next.js usa as convenções nativas `app/icon.svg`, `app/favicon.ico` (16/32/48 px), `app/apple-icon.png` (180 px) e `app/manifest.ts`, com PNGs de 192 e 512 px para dispositivos móveis. Metadados definem a origem oficial e a cor de tema. Não havia ícones antigos rastreados em conflito. As versões PNG/ICO podem ser reproduzidas com `node scripts/generate-brand-icons.mjs` dentro de `frontend`.

## Validação anterior à publicação

- 20 testes backend aprovados: 9 do gateway com mocks, 4 de integração de pagamento e 7 de webhook. Os três resultados, origens antigas/preview, defaults, preservação de rotas/query e consulta autenticada são cobertos.
- 121 testes frontend aprovados, incluindo cinco novos testes de categorias e recuperação de imagens.
- TypeScript aprovado; lint sem erros, com cinco avisos preexistentes fora dos arquivos alterados.
- Builds de produção frontend e backend aprovados.
- Verificação HTTP da home, das três telas de retorno, do manifest e de todos os formatos de ícone aprovada. Arquivo ICO contém os três tamanhos esperados, com G visível em 16 px.
- Inspeção visual no Chrome no desktop e em viewport móvel de 393 px, incluindo ilustrações e favicon na aba. Oito SVGs também foram inspecionados em conjunto.

Referências: [URLs de retorno Mercado Pago](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro-preferences/configure-back-urls), [configuração de domínio Vercel](https://vercel.com/docs/rest-api/projects/update-a-project-domain).
