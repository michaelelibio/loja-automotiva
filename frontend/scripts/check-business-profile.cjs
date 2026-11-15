/* eslint-disable @typescript-eslint/no-require-imports -- Release CLI loads the same pure TypeScript validator as the pages. */
const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const ts = require('typescript');
const { loadEnvConfig } = require('@next/env');

const root = path.resolve(__dirname, '..');
loadEnvConfig(root, false, { info() {}, error() {} });
const filename = path.join(root, 'lib/store/business-profile.ts');
const loaded = new Module(filename, module);
loaded.filename = filename;
loaded.paths = Module._nodeModulePaths(root);
loaded._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2017 },
}).outputText, filename);
const profile = loaded.exports.readBusinessProfile(process.env);
if (!profile.ready) {
  console.error('PUBLICACAO COMERCIAL BLOQUEADA: dados ausentes, invalidos ou nao confirmados.');
  for (const key of profile.pendingFields) console.error(`- ${key}`);
  // Never print personal details, configured text or contact values.
  process.exitCode = 1;
} else {
  console.log('Configuracao comercial completa e marcada como confirmada. Revisar a evidencia e o HTML antes da publicacao.');
}
