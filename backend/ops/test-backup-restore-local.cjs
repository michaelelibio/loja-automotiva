/* A synthetic, local-only PostgreSQL 18 restore drill. Never loads application variables or credentials. */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const net = require('node:net');
const crypto = require('node:crypto');
const { spawnSync } = require('node:child_process');

const workspace = path.resolve(__dirname, '../..');
const target = path.resolve(workspace, 'backend/target/launch-fixes-backup-postgres');
const postgresBin = 'C:/Program Files/PostgreSQL/18/bin';
const host = '127.0.0.1'; const port = '55450'; const user = 'audit_restore_test';
const runId = `${Date.now()}_${crypto.randomBytes(3).toString('hex')}`;
const sourceDb = `audit_restore_source_${runId}`;
const restoreDb = `audit_restore_target_${runId}`;
const runDirectory = path.join(target, runId);
const cluster = path.join(runDirectory, 'cluster');
const evidenceFile = path.join(runDirectory, 'evidence.json');
const migrationsDirectory = path.join(workspace, 'backend/src/main/resources/db/migration');
const prerequisites = ['V20260820_03__order_fulfillment.sql', 'V20261015_01__aliexpress_manual_orders.sql', 'V20261030_03__payment_production_and_supplier_sync.sql'];
const migrations = ['V20261115_01__aliexpress_automatic_dispatch.sql', 'V20261115_02__payment_reversals.sql', 'V20261115_03__payment_reconciliation.sql'];
const evidence = { version: 1, syntheticOnly: true, host, port: Number(port), sourceDb, restoreDb, startedAt: new Date().toISOString(), migrationFiles: [], checks: {}, timingsMs: {} };
let attemptedStart = false;

function requireLocalDatabase(database) {
  if (host !== '127.0.0.1' || port !== '55450' || !/^audit_restore_(?:source|target)_\d+_[a-f0-9]{6}$/.test(database)) throw Error('Local database allowlist rejected the operation.');
}
function requireWorkspacePath(filename) {
  const resolved = path.resolve(filename);
  if (!resolved.startsWith(`${target}${path.sep}`)) throw Error('Artifact path is outside the disposable workspace directory.');
  return resolved;
}
const environment = {};
// Deliberately exclude DB_URL, DATABASE_URL, application secrets and every inherited PG* setting.
for (const name of ['SystemRoot', 'WINDIR', 'PATH', 'Path', 'PATHEXT', 'COMSPEC', 'TEMP', 'TMP']) if (process.env[name]) environment[name] = process.env[name];
Object.assign(environment, { PGCONNECT_TIMEOUT: '5', PGCLIENTENCODING: 'UTF8', PGPASSFILE: path.join(runDirectory, 'empty.pgpass'), PGSERVICEFILE: path.join(runDirectory, 'empty.pgservice'), PGSYSCONFDIR: runDirectory });

function run(binary, args, input, allowFailure = false) {
  const result = spawnSync(path.join(postgresBin, `${binary}.exe`), args, { input, encoding: 'utf8', env: environment, timeout: 60000, maxBuffer: 8 * 1024 * 1024, windowsHide: true });
  if (!allowFailure && (result.error || result.status !== 0)) throw Error(`${binary} failed: ${result.error?.message || result.stderr || 'nonzero exit'}`);
  return result;
}
function connection(database) {
  requireLocalDatabase(database);
  return ['--host', host, '--port', port, '--username', user, '--no-password', '--dbname', database];
}
function sql(database, statement) {
  const guard = `DO $$ BEGIN IF host(inet_server_addr()) <> '127.0.0.1' OR current_setting('port') <> '55450' OR current_database() !~ '^audit_restore_(source|target)_[0-9]+_[a-f0-9]{6}$' THEN RAISE EXCEPTION 'Local restore guard rejected connection'; END IF; END $$;`;
  return run('psql', [...connection(database), '-X', '-q', '-A', '-t', '-v', 'ON_ERROR_STOP=1'], `${guard}\n${statement}\n`).stdout.trim();
}
function apply(database, files) {
  // Use the exact repository SQL, without filtering out constraints or changing statements.
  const contents = files.map((file) => {
    const value = fs.readFileSync(path.join(migrationsDirectory, file), 'utf8');
    const hash = crypto.createHash('sha256').update(value).digest('hex');
    if (!evidence.migrationFiles.some((row) => row.name === file)) evidence.migrationFiles.push({ name: file, sha256: hash });
    return value;
  });
  sql(database, `BEGIN; SET LOCAL lock_timeout='5s'; SET LOCAL statement_timeout='30s';\n${contents.join('\n')}\nCOMMIT;`);
}
function snapshot(database) {
  return JSON.parse(sql(database, `
    CREATE TEMP TABLE drill_fingerprints(name text, rows bigint, digest text);
    DO $$ DECLARE relation record; n bigint; digest text; BEGIN
      FOR relation IN SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename LOOP
        EXECUTE format('SELECT count(*), md5(coalesce(string_agg(md5(to_jsonb(r)::text), '''' ORDER BY to_jsonb(r)::text), '''')) FROM public.%I r', relation.tablename) INTO n,digest;
        INSERT INTO drill_fingerprints VALUES(relation.tablename,n,digest);
      END LOOP;
    END $$;
    SELECT jsonb_build_object(
      'tables',(SELECT jsonb_agg(to_jsonb(f) ORDER BY name) FROM drill_fingerprints f),
      'constraints',(SELECT jsonb_agg(jsonb_build_object('table',rel.relname,'name',c.conname,'definition',pg_get_constraintdef(c.oid),'validated',c.convalidated) ORDER BY rel.relname,c.conname) FROM pg_constraint c JOIN pg_class rel ON rel.oid=c.conrelid JOIN pg_namespace n ON n.oid=rel.relnamespace WHERE n.nspname='public'),
      'indexes',(SELECT jsonb_agg(jsonb_build_object('name',indexname,'definition',indexdef) ORDER BY indexname) FROM pg_indexes WHERE schemaname='public'),
      'sequences',(SELECT jsonb_agg(jsonb_build_object('name',sequencename,'lastValue',last_value,'increment',increment_by) ORDER BY sequencename) FROM pg_sequences WHERE schemaname='public')
    );
  `));
}
function equal(first, second, reason) {
  if (JSON.stringify(first) !== JSON.stringify(second)) throw Error(reason);
}
function measure(name, operation) {
  const started = performance.now(); const result = operation(); evidence.timingsMs[name] = Math.round(performance.now() - started); return result;
}
async function requireFreePort() {
  await new Promise((resolve, reject) => {
    const server = net.createServer(); server.once('error', () => reject(Error('Port 55450 is already occupied; no existing service will be touched.')));
    server.listen({ host, port: Number(port), exclusive: true }, () => server.close(resolve));
  });
}

async function main() {
  if (process.platform !== 'win32') throw Error('This drill is pinned to the Windows PostgreSQL 18 tools.');
  requireLocalDatabase(sourceDb); requireLocalDatabase(restoreDb);
  if (sourceDb === restoreDb) throw Error('Restore target must differ from source.');
  requireWorkspacePath(cluster); requireWorkspacePath(evidenceFile);
  await requireFreePort();
  fs.mkdirSync(runDirectory, { recursive: true });
  fs.writeFileSync(environment.PGPASSFILE, ''); fs.writeFileSync(environment.PGSERVICEFILE, '');
  evidence.postgresTools = run('pg_dump', ['--version']).stdout.trim();
  if (!/PostgreSQL\) 18\./.test(evidence.postgresTools)) throw Error('PostgreSQL 18 is required.');
  measure('initializeCluster', () => run('initdb', ['--pgdata', cluster, '--username', user, '--auth-local=trust', '--auth-host=trust', '--encoding=UTF8', '--locale=C']));
  attemptedStart = true;
  run('pg_ctl', ['--pgdata', cluster, '--log', path.join(runDirectory, 'postgres.log'), '--options', '-h 127.0.0.1 -p 55450 -c max_connections=15 -c shared_buffers=16MB', '--wait', 'start']);
  for (const database of [sourceDb, restoreDb]) {
    requireLocalDatabase(database);
    run('createdb', ['--host', host, '--port', port, '--username', user, '--no-password', '--maintenance-db=postgres', database]);
  }
  sql(sourceDb, `BEGIN;
    CREATE TABLE audit_restore_environment (id integer PRIMARY KEY CHECK(id=1), synthetic_only boolean NOT NULL CHECK(synthetic_only), run_id text NOT NULL);
    INSERT INTO audit_restore_environment VALUES(1,true,'${runId}');
    CREATE TABLE users(id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, name varchar(150) NOT NULL);
    CREATE TABLE orders(id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, user_id bigint NOT NULL REFERENCES users(id), status varchar(30) NOT NULL, total numeric(12,2) NOT NULL, created_at timestamptz NOT NULL);
    CREATE TABLE payments(id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, order_id bigint NOT NULL REFERENCES orders(id), method varchar(30) NOT NULL,
      status varchar(30) NOT NULL CONSTRAINT payments_status_check CHECK(status IN('PENDING','PAID','EXPIRED','CANCELED','FAILED')),
      external_reference varchar(255) UNIQUE, provider_preference_id varchar(255) UNIQUE, provider_payment_id varchar(255) UNIQUE, idempotency_key varchar(36) UNIQUE, created_at timestamptz NOT NULL);
    INSERT INTO users(id,name) VALUES(101,'SYNTHETIC BACKUP FIXTURE');
    INSERT INTO orders(id,user_id,status,total,created_at) VALUES(501,101,'PROCESSING',25.00,'2026-01-01T00:00:00Z'),(502,101,'PENDING_PAYMENT',12.00,'2026-01-01T00:00:00Z');
    INSERT INTO payments(id,order_id,method,status,external_reference,provider_preference_id,provider_payment_id,idempotency_key,created_at) VALUES
      (701,501,'MERCADO_PAGO','PAID','audit-payment-701','audit-preference-701','audit-provider-701','audit-idempotency-701','2026-01-01T00:00:00Z'),
      (702,502,'MERCADO_PAGO','PENDING','audit-payment-702','audit-preference-702',NULL,'audit-idempotency-702','2026-01-01T00:00:00Z');
    SELECT setval(pg_get_serial_sequence('users','id'),101,true);
    SELECT setval(pg_get_serial_sequence('orders','id'),502,true);
    SELECT setval(pg_get_serial_sequence('payments','id'),702,true);
    COMMIT;`);
  apply(sourceDb, prerequisites);
  sql(sourceDb, `INSERT INTO order_fulfillments(order_id,status,provider,supplier_order_id,supplier_order_ids,supplier_status,external_reference,attempt_count,created_at,updated_at)
    VALUES(501,'CREATED','ALIEXPRESS','900000000000000001','["900000000000000001"]','CREATED_UNPAID_UNVERIFIED','audit-supplier-501',2,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z'),
    (502,'PENDING','ALIEXPRESS',NULL,'[]',NULL,'audit-supplier-502',0,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z');`);
  const historical = sql(sourceDb, "SELECT jsonb_agg(jsonb_build_object('order',order_id,'attempts',attempt_count,'id',supplier_order_id,'ids',supplier_order_ids) ORDER BY order_id) FROM order_fulfillments;");
  measure('migrations', () => apply(sourceDb, migrations));
  equal(historical, sql(sourceDb, "SELECT jsonb_agg(jsonb_build_object('order',order_id,'attempts',attempt_count,'id',supplier_order_id,'ids',supplier_order_ids) ORDER BY order_id) FROM order_fulfillments;"), 'Migrations changed supplier IDs or attempts.');
  evidence.checks.historicalSupplierIdsAndAttemptsPreserved = true;
  sql(sourceDb, `BEGIN;
    UPDATE payments SET status='PARTIALLY_REFUNDED',refunded_amount=5.00,provider_updated_at='2026-01-02T00:00:00Z',provider_status_detail='synthetic_refund' WHERE id=701;
    UPDATE orders SET financial_payment_id=701,payment_financial_status='PARTIALLY_REFUNDED',payment_refunded_amount=5.00,payment_net_revenue=20.00,payment_review_required=true WHERE id=501;
    UPDATE order_fulfillments SET dispatch_manual_hold=true,supplier_costs='{"source":"SUPPLIER_ORDER_API","currency":"BRL","total":29.00}' WHERE order_id=501;
    INSERT INTO aliexpress_dispatch_records(order_id,status,automatic_eligible,state) VALUES(502,'PENDING',true,'{"version":1,"status":"PENDING","attemptCount":0,"supplierOrderIds":[]}');
    INSERT INTO aliexpress_recipient_admin_audits(order_id,actor_id,action,previous_context,current_context,attempt_count,created_at) VALUES(502,'synthetic_operator','RECIPIENT_CORRECTED',repeat('a',64),repeat('b',64),0,'2026-01-02T00:00:00Z');
    INSERT INTO payment_reconciliations(payment_id,next_check_at,failure_count,last_failure_code,review_required) VALUES(701,'2026-01-03T00:00:00Z',2,'SYNTHETIC_NETWORK',true),(702,'2026-01-03T00:00:00Z',0,NULL,false);
    COMMIT;`);
  const original = snapshot(sourceDb);
  apply(sourceDb, migrations);
  equal(original, snapshot(sourceDb), 'Repeating migrations changed synthetic records, constraints or sequences.');
  evidence.checks.migrationsIdempotentWithData = true;
  const dumpFile = requireWorkspacePath(path.join(runDirectory, 'synthetic-backup.custom'));
  measure('dump', () => run('pg_dump', [...connection(sourceDb), '--format=custom', '--no-owner', '--no-privileges', '--file', dumpFile]));
  const dump = fs.readFileSync(dumpFile);
  if (dump.subarray(0,5).toString('ascii') !== 'PGDMP') throw Error('Backup is not PostgreSQL custom format.');
  evidence.archive = { format: 'custom', bytes: dump.length, sha256: crypto.createHash('sha256').update(dump).digest('hex') };
  measure('restore', () => run('pg_restore', [...connection(restoreDb), '--exit-on-error', '--no-owner', '--no-privileges', dumpFile]));
  equal(original, snapshot(restoreDb), 'Restored rows, IDs, references, constraints, indexes or sequences differ.');
  evidence.checks.customDumpRestoredIntoDifferentEmptyDatabase = true;
  evidence.checks.allTableCountsAndRowFingerprintsMatch = true;
  evidence.checks.constraintsIndexesAndSequencesMatch = true;
  evidence.snapshot = original;
  const constraintProof = sql(restoreDb, `DO $$ BEGIN
    BEGIN INSERT INTO payment_reconciliations(payment_id,next_check_at) VALUES(999999,now()); RAISE EXCEPTION 'Missing FK rejection'; EXCEPTION WHEN foreign_key_violation THEN NULL; END;
    BEGIN INSERT INTO aliexpress_dispatch_records(order_id,status,state) VALUES(999999,'PENDING','{}'); RAISE EXCEPTION 'Missing order FK rejection'; EXCEPTION WHEN foreign_key_violation THEN NULL; END;
    BEGIN INSERT INTO payment_reconciliations(payment_id,next_check_at) VALUES(701,now()); RAISE EXCEPTION 'Missing reconciliation duplicate rejection'; EXCEPTION WHEN unique_violation THEN NULL; END;
    BEGIN INSERT INTO order_fulfillments(order_id,status,provider,external_reference,attempt_count,created_at,updated_at) VALUES(501,'PENDING','ALIEXPRESS','audit-duplicate-501',0,now(),now()); RAISE EXCEPTION 'Missing supplier dispatch duplicate rejection'; EXCEPTION WHEN unique_violation THEN NULL; END;
    BEGIN UPDATE payments SET refunded_amount=-1 WHERE id=701; RAISE EXCEPTION 'Missing refund constraint'; EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN UPDATE payments SET status='INVALID_SYNTHETIC' WHERE id=701; RAISE EXCEPTION 'Missing status constraint'; EXCEPTION WHEN check_violation THEN NULL; END;
    IF (SELECT nextval(pg_get_serial_sequence('orders','id'))) <> 503 THEN RAISE EXCEPTION 'Order identity sequence not restored'; END IF;
  END $$; SELECT 'RESTORE_CONSTRAINTS_AND_IDENTITY_VERIFIED';`);
  if (constraintProof !== 'RESTORE_CONSTRAINTS_AND_IDENTITY_VERIFIED') throw Error('Constraint proof was not confirmed.');
  evidence.checks.foreignKeysUniqueKeysRefundAndStatusConstraintsEnforced = true;
  evidence.checks.restoredIdentityGeneratesNextExpectedOrderId = true;
  evidence.status = 'PASSED';
}

main().catch((error) => { evidence.status = 'FAILED'; evidence.failure = error.message; process.exitCode = 1; }).finally(() => {
  if (attemptedStart) {
    const stopped = run('pg_ctl', ['--pgdata', cluster, '--mode', 'fast', '--wait', 'stop'], undefined, true);
    const status = run('pg_ctl', ['--pgdata', cluster, 'status'], undefined, true);
    evidence.clusterStopped = stopped.status === 0 && status.status === 3;
    if (!evidence.clusterStopped) { evidence.status = 'FAILED'; process.exitCode = 1; }
  } else evidence.clusterStopped = null;
  evidence.completedAt = new Date().toISOString();
  if (fs.existsSync(runDirectory)) fs.writeFileSync(evidenceFile, `${JSON.stringify(evidence,null,2)}\n`);
  console.log(JSON.stringify({ status: evidence.status, clusterStopped: evidence.clusterStopped, checks: evidence.checks, timingsMs: evidence.timingsMs, evidenceFile, failure: evidence.failure },null,2));
});
