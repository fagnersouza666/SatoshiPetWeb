import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

const politica =
  "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
  "img-src 'self' data: https:; font-src 'self'; media-src 'self'; " +
  "connect-src 'self' wss:; worker-src 'self'; manifest-src 'self'; " +
  "object-src 'none'; frame-src 'none'; frame-ancestors 'none'; " +
  "base-uri 'self'; form-action 'self'";

function normalizar(valor) {
  return valor.replace(/\s+/g, ' ').trim();
}

async function ler(caminho) {
  return readFile(path.join(raiz, caminho), 'utf8');
}

function extrairCaddy(conteudo) {
  const match = conteudo.match(/Content-Security-Policy\s+"([^"]+)"/);
  assert.ok(match, 'Caddy deve declarar Content-Security-Policy');
  return match[1];
}

function extrairNginx(conteudo) {
  const match = conteudo.match(/set\s+\$csp_policy\s+"([^"]+)"/);
  assert.ok(match, 'nginx deve declarar a variável da política CSP');
  return match[1];
}

function extrairJava(conteudo) {
  const inicio = conteudo.indexOf('private static final String CSP');
  const fimDiretiva = conteudo.indexOf('form-action', inicio);
  const fim = conteudo.indexOf(';', fimDiretiva);
  assert.ok(
    inicio >= 0 && fimDiretiva >= 0 && fim >= 0,
    'SecurityHeadersFilter deve declarar a política CSP',
  );
  const bloco = conteudo.slice(inicio, fim);
  return [...bloco.matchAll(/"([^"]*)"/g)].map((match) => match[1]).join('');
}

test('mantém a mesma CSP no Caddy, nginx e API', async () => {
  const [caddy, caddyProd, nginx, java] = await Promise.all([
    ler('infra/caddy/Caddyfile'),
    ler('infra/caddy/Caddyfile.prod'),
    ler('apps/pwa/nginx.conf'),
    ler('services/api/src/main/java/br/com/satoshipet/api/platform/SecurityHeadersFilter.java'),
  ]);

  for (const [nome, atual] of [
    ['Caddy local', extrairCaddy(caddy)],
    ['Caddy produção', extrairCaddy(caddyProd)],
    ['nginx da PWA', extrairNginx(nginx)],
    ['filtro da API', extrairJava(java)],
  ]) {
    assert.equal(normalizar(atual), normalizar(politica), `${nome} divergiu da CSP canônica`);
  }
});
