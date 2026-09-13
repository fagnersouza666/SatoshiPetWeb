import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const workflowPath = path.join(raiz, '.github/workflows/ci.yml');
const gatePath = path.join(raiz, 'infra/scripts/ci-gate.mjs');

function executarGate(resultados) {
  return spawnSync(process.execPath, [gatePath], {
    cwd: raiz,
    encoding: 'utf8',
    env: {
      ...process.env,
      CI_VALIDAR_RESULT: resultados['validar-ci'],
      CI_VERIFICAR_PWA_RESULT: resultados['verificar-pwa'],
      CI_VERIFICAR_API_RESULT: resultados['verificar-api'],
      CI_BUILD_IMAGENS_RESULT: resultados['build-imagens'],
    },
  });
}

test('workflow possui um gate único dependente de todos os checks', async () => {
  const workflow = await readFile(workflowPath, 'utf8');
  const inicioBuild = workflow.indexOf('  build-imagens:');
  const inicioGate = workflow.indexOf('  gate-obrigatorio:');
  assert.notEqual(inicioBuild, -1, 'workflow deve declarar o build das imagens');
  assert.notEqual(inicioGate, -1, 'workflow deve declarar o gate obrigatório');

  const build = workflow.slice(inicioBuild, inicioGate);
  assert.match(
    build,
    /needs: \[verificar-pwa, verificar-api\]/,
    'build deve depender dos dois checks de validação',
  );

  const gate = workflow.slice(inicioGate);
  assert.match(gate, /name: "Gate obrigatório"/);
  assert.match(gate, /if: \$\{\{ always\(\) \}\}/);
  assert.match(
    gate,
    /needs: \[validar-ci, verificar-pwa, verificar-api, build-imagens\]/,
  );
  assert.match(gate, /CI_VALIDAR_RESULT: \$\{\{ needs\.validar-ci\.result \}\}/);
  assert.match(gate, /CI_VERIFICAR_PWA_RESULT: \$\{\{ needs\.verificar-pwa\.result \}\}/);
  assert.match(gate, /CI_VERIFICAR_API_RESULT: \$\{\{ needs\.verificar-api\.result \}\}/);
  assert.match(gate, /CI_BUILD_IMAGENS_RESULT: \$\{\{ needs\.build-imagens\.result \}\}/);
  assert.match(gate, /run: node infra\/scripts\/ci-gate\.mjs/);
});

test('somente checks concluídos com sucesso liberam a integração', () => {
  const base = {
    'validar-ci': 'success',
    'verificar-pwa': 'success',
    'verificar-api': 'success',
    'build-imagens': 'success',
  };

  const sucesso = executarGate(base);
  assert.equal(sucesso.status, 0, sucesso.stderr);

  for (const [nome, resultado] of [
    ['verificar-pwa', 'failure'],
    ['verificar-api', 'pending'],
    ['build-imagens', 'skipped'],
    ['validar-ci', 'cancelled'],
  ]) {
    const bloqueado = executarGate({ ...base, [nome]: resultado });
    assert.equal(bloqueado.status, 1, `${nome}=${resultado} deve bloquear`);
    assert.match(
      bloqueado.stderr,
      new RegExp(`${nome}: ${resultado}`),
      `${nome}=${resultado} deve aparecer no diagnóstico`,
    );
  }
});
