#!/usr/bin/env node
/**
 * Lê e incrementa a versão do produto — PWA e API juntos.
 * Funciona em Linux, macOS e Windows (sem bash).
 *
 * Uso:
 *   node infra/scripts/versao.mjs atual
 *   node infra/scripts/versao.mjs verificar
 *   node infra/scripts/versao.mjs corrigir
 *   node infra/scripts/versao.mjs funcionalidade
 *   node infra/scripts/versao.mjs grande
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/** Raiz do monorepo: cwd quando contém os três manifestos (testes), senão relativo ao script. */
function resolveRaiz() {
  const candidatos = [
    process.env.SATOSHI_PET_ROOT,
    process.cwd(),
    path.resolve(__dirname, '../..'),
  ].filter(Boolean);

  for (const candidato of candidatos) {
    const abs = path.resolve(candidato);
    if (
      fs.existsSync(path.join(abs, 'package.json')) &&
      fs.existsSync(path.join(abs, 'apps/pwa/package.json')) &&
      fs.existsSync(path.join(abs, 'services/api/pom.xml'))
    ) {
      return abs;
    }
  }

  return path.resolve(__dirname, '../..');
}

const raiz = resolveRaiz();
const npmRaiz = path.join(raiz, 'package.json');
const npmPwa = path.join(raiz, 'apps/pwa/package.json');
const pom = path.join(raiz, 'services/api/pom.xml');

for (const arquivo of [npmRaiz, npmPwa, pom]) {
  if (!fs.existsSync(arquivo)) {
    console.error(`ERRO: arquivo não encontrado: ${arquivo}`);
    process.exit(1);
  }
}

function versaoDoNpm(arquivo) {
  const conteudo = fs.readFileSync(arquivo, 'utf8');
  const match = conteudo.match(/"version"\s*:\s*"([^"]+)"/);
  if (!match) {
    throw new Error(`versão não encontrada em ${arquivo}`);
  }
  return match[1];
}

function versaoBrutaDoBackend() {
  const conteudo = fs.readFileSync(pom, 'utf8');
  const match = conteudo.match(/<version>([0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?)<\/version>/);
  if (!match) {
    throw new Error('versão do projeto não encontrada no pom.xml');
  }
  return match[1];
}

function versaoDoBackend() {
  return versaoBrutaDoBackend().replace(/-SNAPSHOT$/, '');
}

function exigirSemver(versao) {
  if (!/^[0-9]+\.[0-9]+\.[0-9]+$/.test(versao)) {
    console.error(`ERRO: versão '${versao}' não está no formato maior.menor.correção`);
    process.exit(1);
  }
}

function versoesAlinhadas() {
  const raizV = versaoDoNpm(npmRaiz);
  const pwaV = versaoDoNpm(npmPwa);
  const apiV = versaoDoBackend();
  return raizV === pwaV && pwaV === apiV;
}

function mostrar() {
  console.log(`  ${'workspace (package.json)'.padEnd(42)} ${versaoDoNpm(npmRaiz)}`);
  console.log(`  ${'PWA (apps/pwa/package.json)'.padEnd(42)} ${versaoDoNpm(npmPwa)}`);
  console.log(`  ${'API (services/api/pom.xml)'.padEnd(42)} ${versaoBrutaDoBackend()}`);
  return versoesAlinhadas();
}

function gravarNpm(arquivo, nova) {
  const conteudo = fs.readFileSync(arquivo, 'utf8');
  let substituido = false;
  const atualizado = conteudo.replace(/"version"\s*:\s*"[^"]+"/, () => {
    if (substituido) {
      return '"version": "ignored"';
    }
    substituido = true;
    return `"version": "${nova}"`;
  });
  if (!substituido) {
    throw new Error(`campo version não encontrado em ${arquivo}`);
  }
  fs.writeFileSync(arquivo, atualizado);
}

function gravarPom(nova) {
  const conteudo = fs.readFileSync(pom, 'utf8');
  let substituido = false;
  const atualizado = conteudo.replace(
    /<version>[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?<\/version>/,
    () => {
      if (substituido) {
        return '<version>ignored</version>';
      }
      substituido = true;
      return `<version>${nova}-SNAPSHOT</version>`;
    },
  );
  if (!substituido) {
    throw new Error('versão do projeto não encontrada no pom.xml');
  }
  fs.writeFileSync(pom, atualizado);
}

function incrementar(versao, tipo) {
  const [maior, menor, correcao] = versao.split('.').map(Number);
  switch (tipo) {
    case 'corrigir':
      return `${maior}.${menor}.${correcao + 1}`;
    case 'funcionalidade':
      return `${maior}.${menor + 1}.0`;
    case 'grande':
      return `${maior + 1}.0.0`;
    default:
      throw new Error(`tipo de incremento inválido: ${tipo}`);
  }
}

const comando = process.argv[2] ?? 'atual';

switch (comando) {
  case 'atual': {
    console.log('Versão do produto:');
    const alinhado = mostrar();
    console.log();
    if (alinhado) {
      console.log('==> PWA e API estão na mesma versão.');
    } else {
      console.error('AVISO: PWA e API estão em versões diferentes.');
    }
    break;
  }

  case 'verificar': {
    if (versoesAlinhadas()) {
      console.log(`==> Versão coerente entre PWA e API: ${versaoDoNpm(npmRaiz)}`);
    } else {
      console.error(
        `FALHA: raiz (${versaoDoNpm(npmRaiz)}), PWA (${versaoDoNpm(npmPwa)}) e API (${versaoDoBackend()}) divergem.`,
      );
      console.error('       Os três sobem juntos — use npm run versao para incrementar.');
      mostrar();
      process.exit(1);
    }
    break;
  }

  case 'corrigir':
  case 'funcionalidade':
  case 'grande': {
    const atualRaiz = versaoDoNpm(npmRaiz);
    const atualPwa = versaoDoNpm(npmPwa);
    const atualApi = versaoDoBackend();
    exigirSemver(atualRaiz);
    exigirSemver(atualPwa);
    exigirSemver(atualApi);

    if (atualRaiz !== atualPwa || atualPwa !== atualApi) {
      console.error(
        `ERRO: raiz (${atualRaiz}), PWA (${atualPwa}) e API (${atualApi}) já estão divergentes.`,
      );
      console.error('      Alinhe os três à mão antes de incrementar, para não escolher por você.');
      process.exit(1);
    }

    const nova = incrementar(atualRaiz, comando);
    gravarNpm(npmRaiz, nova);
    gravarNpm(npmPwa, nova);
    gravarPom(nova);

    console.log(`${atualRaiz} -> ${nova}`);
    console.log();
    if (!mostrar()) {
      console.error('ERRO: a substituição deixou os arquivos divergentes — revise o diff.');
      process.exit(1);
    }
    console.log();
    console.log('Lembre de incluir a mudança de versão NO MESMO commit da alteração');
    console.log('que a motivou: versão em commit separado desgarra do que ela descreve.');
    break;
  }

  default:
    console.error('Uso: versao.mjs [atual|verificar|corrigir|funcionalidade|grande]');
    process.exit(1);
}
