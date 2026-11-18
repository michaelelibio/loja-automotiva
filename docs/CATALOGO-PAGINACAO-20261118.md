# Catálogo público: filtros e paginação

## Comportamento

- Listagem pública com 12 produtos por página, números, página atual em laranja, Anterior/Próxima e reticências. Links preservam os filtros e levam ao início dos resultados.
- Aplicar filtros e alterar a ordenação retornam à primeira página. Limpar filtros abre `/produtos`, sem restringir categoria, busca ou seleção comercial anterior.
- Botão principal laranja com texto branco e secundário com borda laranja, dimensões consistentes, hover, foco visível e carregamento. Controles adaptados ao painel desktop e aos filtros recolhíveis do celular.
- Estados de carregamento, erro com nova tentativa, seleção vazia e contagem/intervalo dos resultados. URLs antigas com página além do total são redirecionadas à última página válida, preservando os filtros.
- Categoria e subcategoria continuam com os mesmos IDs, caminhos e vínculos. As opções de categoria usam a taxonomia pública completa, evitando prender a troca de categoria às facetas de uma seleção já filtrada.

## API e limites investigados

`/api/catalog` já aceitava `page`/`size`, com padrão de 24 itens, e não tinha um limite global que excluísse definitivamente os produtos posteriores. A interface oferecia somente Anterior/Próxima. O botão Aplicar filtros usava uma classe sem estilo correspondente.

O padrão passa a 12. A página, o total de registros e o total de páginas vêm de `PageRequest`/`Page` no banco. Mais vendidos também usa ordenação SQL com desempate por ID, removendo seu antigo `skip`/`limit` em memória. Ordenações explícitas por preço são respeitadas nas seleções Novidades e Mais vendidos. O limite de 100 itens por requisição da API é mantido; não limita o número de páginas.

Para compatibilidade, `page` permanece baseado em zero na API e nas URLs existentes: `page=1` representa a segunda página, identificada como **2** na interface. A listagem pública fixa 12 itens; as vitrines da página inicial preservam seu tamanho próprio.

A política pública existente continua em vigor: em produção, produtos ativos AliExpress dropshipping. A seleção agora também aplica a mesma regra de disponibilidade de SKU já usada no retorno público e no checkout, antes da paginação e contagem. Produtos sem SKU comprável ou sem estoque confirmado do fornecedor não ocupam vagas nem aumentam o total. Não há consultas ao fornecedor para navegar pelo catálogo.

As facetas continuam calculadas sobre a seleção completa, incluindo a validação existente de disponibilidade em Java. Essa etapa ainda carrega os candidatos; o conteúdo retornado e sua contagem são paginados no banco. Esta correção não introduz um limite nos primeiros candidatos.

Nenhuma migração, reclassificação, alteração de preço, estoque, SKU, vínculo AliExpress/CJ ou configuração de integração é necessária. Código de pagamentos, pedidos, clientes e automação AliExpress não foi alterado.

## Verificação

- Testes backend com 0, 7, 12, 13, 24, 25 e 121 produtos elegíveis; todas as páginas, totais, empates de ordenação, ausência de duplicatas/omissões, filtros combinados, disponibilidade e facetas completas.
- Testes adicionais de regras públicas de produção, variantes múltiplas e indisponíveis, classificação de fornecedores, Mais vendidos e ordenação explícita dentro das seleções.
- Frontend: 115 testes; URL, números/reticências, reset de página, filtros, navegação, rolagem/foco, limpeza, ordenação, carregamento, erro, vazio e redirecionamento de página inexistente.
- TypeScript sem erros; lint sem erros, com cinco avisos preexistentes; build de produção aprovado.
- Backend: `verify` aprovado, 1.048 casos, zero falhas/erros, 930 executados e 118 testes externos opt-in ignorados; inclui 23 testes de catálogo/elegibilidade. Maven executado em cópia isolada dos mesmos fontes e H2 descartável. A compilação na pasta principal sofreu interferência na geração/manutenção de classes em `target`; não houve alteração de configuração do projeto para contornar isso.
- A inspeção visual desktop/mobile não foi concluída: a ferramenta de Computer Use encerrou o acesso por não conseguir verificar a URL atual do Chrome. Foram verificados os estados e eventos por testes automatizados e a renderização HTTP local. Essa limitação permanece para a conferência visual manual.

Publicação autorizada pelo pedido: commit/push na `main`, acompanhando as integrações existentes com Vercel e Railway. Confirmar o SHA servido pelos dois provedores e percorrer as páginas públicas após a publicação, sem alterar dados ou variáveis de produção.
