#!/usr/bin/env node
/** Compila os serviços reais e executa o contrato sem servidor Angular/browser. */
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { spawnSync } from 'node:child_process';

const directory = dirname(fileURLToPath(import.meta.url));
const require = createRequire(resolve(directory, '../package.json'));
const { build } = require('esbuild');
const temporary = await mkdtemp(resolve(tmpdir(), 'satoshi-pet-auth-contract-'));
try {
  const output = resolve(temporary, 'contract.mjs');
  await build({
    entryPoints: [resolve(directory, 'auth-api.contract.ts')],
    outfile: output,
    bundle: true,
    platform: 'node',
    format: 'esm',
    target: 'node22',
    tsconfig: resolve(directory, '../tsconfig.json'),
    logLevel: 'warning',
  });
  const result = spawnSync(process.execPath, [output, ...process.argv.slice(2)], {
    stdio: 'inherit',
    timeout: 120_000,
  });
  if (result.error) console.error(result.error);
  process.exitCode = result.status ?? 1;
} finally {
  await rm(temporary, { recursive: true, force: true });
}
