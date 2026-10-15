# Normalização dos atributos de produto AliExpress

O contrato `attributes: Map<String,String>` do preview foi preservado.
Estas regras se aplicam somente a `ae_item_properties`, não a propriedades de variantes ou IDs de SKU:

- ID numérico positivo único: mantém o ID como chave.
- ID ausente, zero, negativo, não numérico ou repetido: usa `name:<attr_name>` quando o nome é único e cabe no limite de chave.
- Nome ausente, repetido ou que excederia o limite de chave com o prefixo: usa `property:<índice original>`.
- Mesmo nomes e IDs repetidos com valores diferentes são preservados em entradas distintas.
- `source:<índice>:key` aponta para a chave do valor normalizado.
- `source:<índice>:attr_name_id`, `attr_name` e `attr_value_id` preservam os campos originais presentes, após a conversão escalar e trim já existentes.

Os namespaces são separados para impedir colisões entre nomes do fornecedor, IDs e metadados. A mesma resposta produz as mesmas chaves e fingerprint. A posição é a posição original no array do fornecedor, iniciando em zero. Atributos presentes sem valor útil são rejeitados; limites de tamanho e rejeição de objetos/arrays como valores continuam ativos.

A correção anterior (`ce91ad2`) adicionou logs diagnósticos, mas manteve a seleção de qualquer `attr_name_id` não nulo como chave. Vários atributos com ID `-1` continuavam colidindo em `put`, que rejeita valores conflitantes.

`src/test/resources/aliexpress-product-1005010661577657.json` é uma resposta simulada: reproduz os campos confirmados do produto, IDs e preços numéricos, cinco atributos com ID `-1` e país `Br`. Título e imagem são sintéticos; não é uma captura integral do fornecedor. Testes cobrem o cliente HTTP simulado, normalização e endpoint ADMIN, sem gravar produtos.

Não houve mudança em OAuth, assinatura, credenciais, checkout, métodos de importação/publicação ou validações das variantes. Logs continuam limitados a estágio, campo, condição, índices e tipos, sem valores do fornecedor.
