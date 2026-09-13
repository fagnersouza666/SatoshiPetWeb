#!/usr/bin/env node
/**
 * Gate único para a proteção de main.
 *
 * O job que chama este script usa `if: always()` para observar todos os jobs
 * necessários. Só o estado `success` libera a integração; `failure`,
 * `cancelled` e `skipped` são bloqueantes.
 */
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const gates = [
  ['validar-ci', process.env.CI_VALIDAR_RESULT],
  ['verificar-pwa', process.env.CI_VERIFICAR_PWA_RESULT],
  ['verificar-api', process.env.CI_VERIFICAR_API_RESULT],
  ['build-imagens', process.env.CI_BUILD_IMAGENS_RESULT],
];

export function avaliarGates(resultados) {
  return Object.entries(resultados)
    .filter(([, resultado]) => resultado !== 'success')
    .map(([nome, resultado]) => `${nome}: ${resultado || '<ausente>'}`);
}

function executar() {
  const resultados = Object.fromEntries(gates);
  const bloqueios = avaliarGates(resultados);

  if (bloqueios.length > 0) {
    console.error(`Gate obrigatório bloqueado: ${bloqueios.join(', ')}`);
    process.exitCode = 1;
    return;
  }

  console.log('Gate obrigatório liberado: todos os checks terminaram com success.');
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  executar();
}
