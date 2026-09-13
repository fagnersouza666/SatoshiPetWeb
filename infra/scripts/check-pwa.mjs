#!/usr/bin/env node
/**
 * Aceitação local da PWA: versão alinhada + testes Angular (Vitest).
 */
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnCommand } from '../../scripts/spawn-cross-platform.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const raiz = path.resolve(__dirname, '../..');

function executar(comando, args, cwd = raiz) {
  const resultado =
    comando.endsWith('.mjs') || comando.endsWith('.js') || comando === process.execPath
      ? spawnSync(comando, args, { cwd, stdio: 'inherit', shell: false, windowsHide: true })
      : spawnCommand(comando, args, { cwd, stdio: 'inherit' });
  if (resultado.status !== 0) {
    process.exit(resultado.status ?? 1);
  }
}

executar(process.execPath, [path.join(__dirname, 'versao.mjs'), 'verificar']);
executar(process.execPath, [path.join(__dirname, 'ngsw-pet-assets.test.mjs')]);

console.log(`==> Verificando a PWA em ${path.join(raiz, 'apps/pwa')}`);
executar(process.platform === 'win32' ? 'npm.cmd' : 'npm', ['run', 'test:pwa'], raiz);
executar(process.platform === 'win32' ? 'npm.cmd' : 'npm', ['run', 'test:pwa:contract'], raiz);
executar(process.execPath, [path.join(__dirname, 'pwa-installation-smoke.mjs')], raiz);
console.log('==> PWA verificada com sucesso.');
