#!/usr/bin/env node
/**
 * Testes de infra/scripts/versao.mjs — PWA, API e package.json da raiz juntos.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const versaoMjs = path.join(__dirname, 'versao.mjs');
let falhas = 0;

function falhar(mensagem) {
  console.error(`FALHA: ${mensagem}`);
  falhas += 1;
}

function assertEq(esperado, obtido, rotulo) {
  if (obtido !== esperado) {
    falhar(`${rotulo}: esperado '${esperado}', obtido '${obtido}'`);
  }
}

function executarVersao(cwd, ...args) {
  return spawnSync(process.execPath, [versaoMjs, ...args], {
    cwd,
    encoding: 'utf8',
  });
}

function assertExit(esperado, rotulo, cwd, ...args) {
  const resultado = executarVersao(cwd, ...args);
  if (resultado.status !== esperado) {
    falhar(`${rotulo}: esperado exit ${esperado}, obtido ${resultado.status ?? 'null'}`);
  }
}

function montarArvore(dest, verNpm, verPom) {
  fs.mkdirSync(path.join(dest, 'infra/scripts'), { recursive: true });
  fs.mkdirSync(path.join(dest, 'apps/pwa'), { recursive: true });
  fs.mkdirSync(path.join(dest, 'services/api'), { recursive: true });
  fs.writeFileSync(path.join(dest, 'package.json'), JSON.stringify({ version: verNpm }, null, 2));
  fs.writeFileSync(path.join(dest, 'apps/pwa/package.json'), JSON.stringify({ version: verNpm }, null, 2));
  fs.writeFileSync(
    path.join(dest, 'services/api/pom.xml'),
    `<project>
    <artifactId>satoshi-pet-api</artifactId>
    <version>${verPom}</version>
    <properties>
        <compiler-plugin.version>3.15.0</compiler-plugin.version>
    </properties>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <version>3.33.3.2</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
`,
  );
  fs.copyFileSync(versaoMjs, path.join(dest, 'infra/scripts/versao.mjs'));
}

function lerNpm(arquivo) {
  const conteudo = fs.readFileSync(arquivo, 'utf8');
  const match = conteudo.match(/"version"\s*:\s*"([^"]+)"/);
  return match?.[1] ?? '';
}

function lerPom(arquivo) {
  const conteudo = fs.readFileSync(arquivo, 'utf8');
  const match = conteudo.match(/<version>([0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?)<\/version>/);
  return match?.[1] ?? '';
}

if (!fs.existsSync(versaoMjs)) {
  console.error(`FALHA: ${versaoMjs} não existe.`);
  process.exit(1);
}

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'versao-test-'));

try {
  const okDir = path.join(tmp, 'ok');
  montarArvore(okDir, '1.2.3', '1.2.3-SNAPSHOT');
  assertExit(0, 'verificar alinhado', okDir, 'verificar');
  const pomBak = fs.readFileSync(path.join(okDir, 'services/api/pom.xml'), 'utf8');
  executarVersao(okDir, 'atual');
  executarVersao(okDir, 'verificar');
  const pomDepois = fs.readFileSync(path.join(okDir, 'services/api/pom.xml'), 'utf8');
  if (pomDepois !== pomBak) {
    falhar('atualizar/verificar alterou o pom (devem ser somente leitura)');
  }

  const driftDir = path.join(tmp, 'drift');
  montarArvore(driftDir, '1.2.3', '9.9.9-SNAPSHOT');
  assertExit(0, 'atual com divergência', driftDir, 'atual');
  assertExit(1, 'verificar divergente', driftDir, 'verificar');
  assertExit(1, 'corrigir com divergência', driftDir, 'corrigir');

  const patchDir = path.join(tmp, 'patch');
  montarArvore(patchDir, '1.2.3', '1.2.3-SNAPSHOT');
  executarVersao(patchDir, 'corrigir');
  assertEq('1.2.4', lerNpm(path.join(patchDir, 'package.json')), 'raiz após corrigir');
  assertEq('1.2.4', lerNpm(path.join(patchDir, 'apps/pwa/package.json')), 'pwa após corrigir');
  assertEq('1.2.4-SNAPSHOT', lerPom(path.join(patchDir, 'services/api/pom.xml')), 'pom após corrigir');
  const pomPatch = fs.readFileSync(path.join(patchDir, 'services/api/pom.xml'), 'utf8');
  if (!pomPatch.includes('<version>3.33.3.2</version>')) {
    falhar('corrigir alterou versão de dependência do pom');
  }

  const minorDir = path.join(tmp, 'minor');
  montarArvore(minorDir, '1.2.3', '1.2.3-SNAPSHOT');
  executarVersao(minorDir, 'funcionalidade');
  assertEq('1.3.0', lerNpm(path.join(minorDir, 'package.json')), 'raiz após funcionalidade');
  assertEq('1.3.0-SNAPSHOT', lerPom(path.join(minorDir, 'services/api/pom.xml')), 'pom após funcionalidade');

  const majorDir = path.join(tmp, 'major');
  montarArvore(majorDir, '1.2.3', '1.2.3-SNAPSHOT');
  executarVersao(majorDir, 'grande');
  assertEq('2.0.0', lerNpm(path.join(majorDir, 'package.json')), 'raiz após grande');
  assertEq('2.0.0', lerNpm(path.join(majorDir, 'apps/pwa/package.json')), 'pwa após grande');
  assertEq('2.0.0-SNAPSHOT', lerPom(path.join(majorDir, 'services/api/pom.xml')), 'pom após grande');

  assertExit(1, 'uso inválido', okDir, 'foobar');
} finally {
  fs.rmSync(tmp, { recursive: true, force: true });
}

if (falhas !== 0) {
  console.error(`versao.test.mjs: ${falhas} falha(s)`);
  process.exit(1);
}

console.log('versao.test.mjs: ok');
