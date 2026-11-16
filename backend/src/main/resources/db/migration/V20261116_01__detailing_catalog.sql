-- Additive migration. Run inside a reviewed transaction; never reclassifies existing products.
CREATE TABLE IF NOT EXISTS catalog_categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    path VARCHAR(250) NOT NULL UNIQUE,
    description VARCHAR(1500),
    parent_id BIGINT REFERENCES catalog_categories(id),
    position INTEGER NOT NULL DEFAULT 0 CHECK (position >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE INDEX IF NOT EXISTS ix_catalog_categories_parent ON catalog_categories(parent_id, position);
ALTER TABLE products ADD COLUMN IF NOT EXISTS catalog_category_id BIGINT REFERENCES catalog_categories(id);
ALTER TABLE products ADD COLUMN IF NOT EXISTS catalog_subcategory_id BIGINT REFERENCES catalog_categories(id);
ALTER TABLE products ADD COLUMN IF NOT EXISTS brand VARCHAR(100);
ALTER TABLE products ADD COLUMN IF NOT EXISTS catalog_usage VARCHAR(20) CHECK (catalog_usage IN ('DOMESTIC','PROFESSIONAL','BOTH'));
ALTER TABLE products ADD COLUMN IF NOT EXISTS concentrated BOOLEAN;
ALTER TABLE products ADD COLUMN IF NOT EXISTS refill BOOLEAN;
ALTER TABLE products ADD COLUMN IF NOT EXISTS featured BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE products ADD COLUMN IF NOT EXISTS beginner BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE products ADD COLUMN IF NOT EXISTS commercial_origin VARCHAR(20) CHECK (commercial_origin IN ('NATIONAL','INTERNATIONAL'));
ALTER TABLE products ADD COLUMN IF NOT EXISTS published_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS ix_products_catalog_category ON products(catalog_category_id, catalog_subcategory_id);
CREATE INDEX IF NOT EXISTS ix_products_catalog_publication ON products(active, published_at);
CREATE INDEX IF NOT EXISTS ix_products_catalog_brand ON products(brand);
CREATE TABLE IF NOT EXISTS product_catalog_surfaces (
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    entry_value VARCHAR(100) NOT NULL, PRIMARY KEY(product_id,entry_value)
);
CREATE TABLE IF NOT EXISTS product_catalog_purposes (
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    entry_value VARCHAR(100) NOT NULL, PRIMARY KEY(product_id,entry_value)
);
CREATE TABLE IF NOT EXISTS product_catalog_search_terms (
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    entry_value VARCHAR(100) NOT NULL, PRIMARY KEY(product_id,entry_value)
);

-- ON CONFLICT never overwrites later administrative edits or activation decisions.
INSERT INTO catalog_categories(name,slug,path,position) VALUES
 ('Lavagem','lavagem','lavagem',10),
 ('Interior','interior','interior',20),
 ('Pintura e proteção','pintura-e-protecao','pintura-e-protecao',30),
 ('Rodas e pneus','rodas-e-pneus','rodas-e-pneus',40),
 ('Vidros e acabamentos','vidros-e-acabamentos','vidros-e-acabamentos',50),
 ('Acessórios','acessorios','acessorios',60),
 ('Máquinas e equipamentos','maquinas-e-equipamentos','maquinas-e-equipamentos',70),
 ('Kits','kits','kits',80)
ON CONFLICT(path) DO NOTHING;

INSERT INTO catalog_categories(name,slug,path,parent_id,description,position)
SELECT seed.name,seed.slug,parent.path || '/' || seed.slug,parent.id,seed.description,seed.position
FROM (VALUES
 ('lavagem','Lavagem automotiva','lavagem-automotiva','Shampoos neutros, com cera, pré-lavagem, espuma ativa, lavagem a seco e sem enxágue.',10),
 ('lavagem','Limpeza pesada','limpeza-pesada','Desengraxantes, APC e removedores de insetos, piche, cola e seiva.',20),
 ('lavagem','Descontaminação','descontaminacao','Descontaminantes ferrosos, clay bars, luvas descontaminantes e lubrificantes.',30),
 ('interior','Painel e acabamentos','painel-e-acabamentos','Limpadores de painel, console, portas e telas.',10),
 ('interior','Tecidos e carpetes','tecidos-e-carpetes','Higienizadores, tira-manchas, limpadores de estofados e impermeabilizantes.',20),
 ('interior','Couro e materiais sintéticos','couro-e-materiais-sinteticos','Limpadores, condicionadores e protetores específicos.',30),
 ('interior','Aromatização','aromatizacao','Aromatizantes, neutralizadores de odores e refis.',40),
 ('pintura-e-protecao','Polimento e correção','polimento-e-correcao','Corte, refino, lustro, produtos tudo em um e removedores de marcas d’água.',10),
 ('pintura-e-protecao','Ceras e selantes','ceras-e-selantes','Naturais, sintéticos, líquidos, em pasta e spray.',20),
 ('pintura-e-protecao','Proteção cerâmica','protecao-ceramica','Coatings, sprays cerâmicos, preparadores e manutenção.',30),
 ('pintura-e-protecao','Brilho e acabamento','brilho-e-acabamento','Detalhadores rápidos, realçadores e auxiliares de secagem.',40),
 ('pintura-e-protecao','Envelopamento e PPF','envelopamento-e-ppf','Limpeza e proteção para películas e superfícies foscas.',50),
 ('rodas-e-pneus','Limpa-rodas e limpa-pneus','limpa-rodas-e-limpa-pneus',NULL,10),
 ('rodas-e-pneus','Revitalizadores e pretinhos','revitalizadores-e-pretinhos',NULL,20),
 ('rodas-e-pneus','Selantes e coatings para rodas','selantes-e-coatings',NULL,30),
 ('vidros-e-acabamentos','Vidros','vidros','Limpa-vidros, removedores de marcas d’água, polidores e repelentes de água.',10),
 ('vidros-e-acabamentos','Plásticos, borrachas e frisos','plasticos-borrachas-e-frisos','Limpadores, revitalizadores, restauradores e protetores.',20),
 ('vidros-e-acabamentos','Faróis e lanternas','farois-e-lanternas','Restauração, lixas, polidores e proteção UV.',30),
 ('vidros-e-acabamentos','Metais e cromados','metais-e-cromados','Limpeza e polimento de cromados, inox, alumínio e ponteiras.',40),
 ('vidros-e-acabamentos','Motor e cofre','motor-e-cofre','Limpeza, desengraxe e proteção de acabamento.',50),
 ('vidros-e-acabamentos','Capotas e conversíveis','capotas-e-conversiveis','Limpeza, impermeabilização de tecido e proteção de vinil.',60),
 ('acessorios','Microfibras e secagem','microfibras-e-secagem','Panos, toalhas de secagem e de vidros, luvas de lavagem.',10),
 ('acessorios','Escovas, pincéis e aplicadores','escovas-pinceis-e-aplicadores','Detalhamento, rodas, pneus, bancos e aplicadores.',20),
 ('acessorios','Lavagem','lavagem','Baldes, separadores de sujeira, pulverizadores, borrifadores e acessórios de espuma.',30),
 ('acessorios','Polimento','polimento','Boinas, suportes, interfaces, fitas de mascaramento e escovas de limpeza.',40),
 ('acessorios','Organização e proteção','organizacao-e-protecao','Bolsas, organizadores, suportes, carrinhos, luvas e óculos.',50),
 ('acessorios','Refis e dosagem','refis-e-dosagem','Frascos de diluição e dosadores.',60),
 ('maquinas-e-equipamentos','Politrizes','politrizes',NULL,10),
 ('maquinas-e-equipamentos','Lavadoras de alta pressão','lavadoras-de-alta-pressao',NULL,20),
 ('maquinas-e-equipamentos','Aspiradores e extratoras','aspiradores-e-extratoras',NULL,30),
 ('maquinas-e-equipamentos','Sopradores','sopradores',NULL,40),
 ('maquinas-e-equipamentos','Equipamentos a vapor','equipamentos-a-vapor',NULL,50),
 ('maquinas-e-equipamentos','Canhões e geradores de espuma','canhoes-e-geradores-de-espuma',NULL,60),
 ('maquinas-e-equipamentos','Iluminação e inspeção','iluminacao-e-inspecao','Lanternas, luminárias e medidores de espessura.',70),
 ('kits','Primeira lavagem','primeira-lavagem',NULL,10),
 ('kits','Limpeza interna','limpeza-interna',NULL,20),
 ('kits','Rodas e pneus','rodas-e-pneus',NULL,30),
 ('kits','Cuidados com couro','cuidados-com-couro',NULL,40),
 ('kits','Proteção da pintura','protecao-da-pintura',NULL,50),
 ('kits','Kits profissionais','kits-profissionais',NULL,60)
) AS seed(parent_path,name,slug,description,position)
JOIN catalog_categories parent ON parent.path=seed.parent_path
ON CONFLICT(path) DO NOTHING;
