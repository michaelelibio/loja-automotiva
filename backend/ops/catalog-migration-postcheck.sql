-- Read-only structural assertions for V20261116_01__detailing_catalog.sql.
-- Run with ON_ERROR_STOP. Safe inside the migration transaction or a READ ONLY transaction.
DO $$
DECLARE missing_count INTEGER;
BEGIN
    IF current_database() <> 'railway' OR current_schema() <> 'public' THEN
        RAISE EXCEPTION 'CATALOG_UNEXPECTED_DATABASE_OR_SCHEMA';
    END IF;
    SELECT count(*) INTO missing_count FROM (VALUES
        ('catalog_category_id','int8',NULL,'YES'),('catalog_subcategory_id','int8',NULL,'YES'),
        ('brand','varchar',100,'YES'),('catalog_usage','varchar',20,'YES'),
        ('concentrated','bool',NULL,'YES'),('refill','bool',NULL,'YES'),
        ('featured','bool',NULL,'NO'),('beginner','bool',NULL,'NO'),
        ('commercial_origin','varchar',20,'YES'),('published_at','timestamptz',NULL,'YES')
    ) expected(name,type,length,nullable)
    LEFT JOIN information_schema.columns actual ON actual.table_schema='public'
        AND actual.table_name='products' AND actual.column_name=expected.name
        AND actual.udt_name=expected.type AND actual.is_nullable=expected.nullable
        AND (expected.length IS NULL OR actual.character_maximum_length=expected.length)
    WHERE actual.column_name IS NULL;
    IF missing_count<>0 THEN RAISE EXCEPTION 'CATALOG_PRODUCT_COLUMNS_INCOMPATIBLE'; END IF;

    SELECT count(*) INTO missing_count FROM (VALUES
        ('id','int8',NULL,'NO'),('name','varchar',120,'NO'),('slug','varchar',120,'NO'),
        ('path','varchar',250,'NO'),('description','varchar',1500,'YES'),
        ('parent_id','int8',NULL,'YES'),('position','int4',NULL,'NO'),('active','bool',NULL,'NO')
    ) expected(name,type,length,nullable)
    LEFT JOIN information_schema.columns actual ON actual.table_schema='public'
        AND actual.table_name='catalog_categories' AND actual.column_name=expected.name
        AND actual.udt_name=expected.type AND actual.is_nullable=expected.nullable
        AND (expected.length IS NULL OR actual.character_maximum_length=expected.length)
    WHERE actual.column_name IS NULL;
    IF missing_count<>0 THEN RAISE EXCEPTION 'CATALOG_CATEGORY_COLUMNS_INCOMPATIBLE'; END IF;

    IF (SELECT count(*) FROM information_schema.columns WHERE table_schema='public'
        AND table_name IN ('product_catalog_surfaces','product_catalog_purposes','product_catalog_search_terms')
        AND ((column_name='product_id' AND udt_name='int8' AND is_nullable='NO')
          OR (column_name='entry_value' AND udt_name='varchar' AND character_maximum_length=100 AND is_nullable='NO')))<>6
    THEN RAISE EXCEPTION 'CATALOG_COLLECTION_COLUMNS_INCOMPATIBLE'; END IF;

    IF (SELECT count(*) FROM pg_constraint WHERE connamespace='public'::regnamespace AND contype='f'
        AND convalidated AND conrelid IN ('public.products'::regclass,'public.catalog_categories'::regclass,
            'public.product_catalog_surfaces'::regclass,'public.product_catalog_purposes'::regclass,'public.product_catalog_search_terms'::regclass)
        AND confrelid IN ('public.catalog_categories'::regclass,'public.products'::regclass))<>6
    THEN RAISE EXCEPTION 'CATALOG_FOREIGN_KEYS_INCOMPLETE'; END IF;

    IF (SELECT count(*) FROM pg_constraint WHERE contype='p' AND conrelid IN
        ('public.catalog_categories'::regclass,'public.product_catalog_surfaces'::regclass,
         'public.product_catalog_purposes'::regclass,'public.product_catalog_search_terms'::regclass))<>4
        OR NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='public.catalog_categories'::regclass
            AND contype='u' AND pg_get_constraintdef(oid)='UNIQUE (path)')
    THEN RAISE EXCEPTION 'CATALOG_KEYS_INCOMPLETE'; END IF;

    IF (SELECT count(*) FROM pg_constraint WHERE contype='f' AND confdeltype='c'
        AND conrelid IN ('public.product_catalog_surfaces'::regclass,'public.product_catalog_purposes'::regclass,'public.product_catalog_search_terms'::regclass))<>3
    THEN RAISE EXCEPTION 'CATALOG_COLLECTION_RELATIONSHIPS_INCOMPLETE'; END IF;

    IF (SELECT count(*) FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid
        WHERE c.relnamespace='public'::regnamespace AND i.indisvalid AND i.indisready AND c.relname IN
        ('ix_catalog_categories_parent','ix_products_catalog_category','ix_products_catalog_publication','ix_products_catalog_brand'))<>4
    THEN RAISE EXCEPTION 'CATALOG_INDEXES_INCOMPLETE'; END IF;

    IF (SELECT count(*) FROM public.catalog_categories WHERE parent_id IS NULL AND path IN
        ('lavagem','interior','pintura-e-protecao','rodas-e-pneus','vidros-e-acabamentos','acessorios','maquinas-e-equipamentos','kits'))<>8
        OR (SELECT count(*) FROM public.catalog_categories WHERE parent_id IS NOT NULL)<40
    THEN RAISE EXCEPTION 'CATALOG_SEED_INCOMPLETE'; END IF;

    IF EXISTS(SELECT 1 FROM public.catalog_categories child JOIN public.catalog_categories parent ON parent.id=child.parent_id
        WHERE parent.parent_id IS NOT NULL OR child.path<>parent.path||'/'||child.slug)
    THEN RAISE EXCEPTION 'CATALOG_HIERARCHY_INVALID'; END IF;
END $$;
SELECT 'CATALOG_STRUCTURE_VALIDATED' AS result;
