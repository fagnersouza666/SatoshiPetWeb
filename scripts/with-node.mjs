#!/usr/bin/env node
/**
 * Executa comandos do workspace com Node local ou versão do .nvmrc.
 * Funciona em Windows, Linux e macOS (sem bash).
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnCommand } from './spawn-cross-platform.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(__dirname, '..');
const nvmrcPath = path.join(repositoryRoot, '.nvmrc');
const args = process.argv.slice(2);

if (args.length === 0) {
  console.error('Uso: node scripts/with-node.mjs <comando> [argumentos...]');
  process.exit(64);
}

function executar(comando, cmdArgs, cwd = repositoryRoot) {
  const resultado = spawnCommand(comando, cmdArgs, { cwd, stdio: 'inherit' });
  process.exit(resultado.status ?? 1);
}

/** Angular CLI local — evita npm exec e funciona com caminhos com espaço no Windows. */
if (args[0] === 'ng') {
  const pwaRoot = path.join(repositoryRoot, 'apps/pwa');
  const ngJs = path.join(pwaRoot, 'node_modules/@angular/cli/bin/ng.js');
  if (fs.existsSync(ngJs)) {
    const resultado = spawnCommand(process.execPath, [ngJs, ...args.slice(1)], {
      cwd: pwaRoot,
      stdio: 'inherit',
    });
    process.exit(resultado.status ?? 1);
  }
}

if (!fs.existsSync(nvmrcPath)) {
  console.error('A versão do Node não está definida em .nvmrc.');
  process.exit(1);
}

const nodeVersion = fs.readFileSync(nvmrcPath, 'utf8').trim().split(/\s+/)[0];
const npmCmd = process.platform === 'win32' ? 'npm.cmd' : 'npm';
executar(npmCmd, ['exec', '--yes', `--package=node@${nodeVersion}`, '--', ...args]);
