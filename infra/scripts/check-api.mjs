#!/usr/bin/env node
/**
 * Verificação da API: versão alinhada + mvnw verify (Testcontainers exige Docker).
 */
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnCommand } from '../../scripts/spawn-cross-platform.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const raiz = path.resolve(__dirname, '../..');
const apiDir = path.join(raiz, 'services/api');
const mvnwName = process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw';
const mvnw = path.join(apiDir, mvnwName);

function executar(comando, args, cwd) {
  const resultado =
    comando.endsWith('.mjs') || comando.endsWith('.js')
      ? spawnSync(comando, args, { cwd, stdio: 'inherit', shell: false, windowsHide: true })
      : spawnCommand(comando, args, { cwd, stdio: 'inherit' });
  if (resultado.status !== 0) {
    process.exit(resultado.status ?? 1);
  }
}

function dockerDisponivel() {
  const resultado = spawnSync('docker', ['info'], { stdio: 'ignore' });
  return resultado.status === 0;
}

executar(process.execPath, [path.join(__dirname, 'versao.mjs'), 'verificar']);

if (!dockerDisponivel()) {
  console.error('ERRO: o Docker precisa estar rodando — os testes de integração usam Testcontainers.');
  process.exit(1);
}

if (!fs.existsSync(mvnw)) {
  console.error(`ERRO: Maven Wrapper não encontrado em ${mvnw}`);
  process.exit(1);
}

console.log(`==> Verificando a API em ${apiDir}`);
executar(mvnw, ['--batch-mode', '-Pci', 'verify'], apiDir);
console.log('==> API verificada com sucesso.');
