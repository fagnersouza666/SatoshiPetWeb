import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const env = {
  PATH: process.env.PATH,
  SYSTEMROOT: process.env.SYSTEMROOT,
  APP_VERSION: '1.9.1',
  IMAGE_REGISTRY: 'registry.invalid/test',
  DOMAIN: 'satoshi.example.test',
  POSTGRES_PASSWORD: 'test-postgres-password',
  MINIO_ROOT_PASSWORD: 'test-minio-password',
  MAIL_HOST: 'smtp.example.test',
  MAIL_USER: 'test-mail-user',
  MAIL_PASSWORD: 'test-mail-password',
  CORS_ALLOWED_ORIGINS: 'https://satoshi.example.test',
  QUARKUS_HTTP_PROXY_TRUSTED_PROXIES: 'caddy,pwa',
  OBJECT_STORAGE_ENDPOINT: 'https://s3.example.test',
  OBJECT_STORAGE_BUCKET: 'approved-test',
  OBJECT_STORAGE_STAGING_BUCKET: 'staging-test',
  AWS_ACCESS_KEY_ID: 'test-storage-key',
  AWS_SECRET_ACCESS_KEY: 'test-storage-secret',
};

function compose(production = false, devProfile = false) {
  const args = ['compose', '--env-file', process.platform === 'win32' ? 'NUL' : '/dev/null',
    '-f', 'infra/docker-compose.yml'];
  if (production) args.push('-f', 'infra/docker-compose.prod.yml');
  if (devProfile) args.push('--profile', 'dev');
  args.push('config', '--format', 'json');
  const result = spawnSync('docker', args, { cwd: root, env, encoding: 'utf8' });
  assert.equal(result.status, 0, result.error?.message ?? result.stderr);
  return JSON.parse(result.stdout);
}

test('PostgreSQL 18 persiste no diretório pai versionado em dev e produção', () => {
  for (const config of [compose(false, true), compose(true)]) {
    assert.ok(config.services.postgres.volumes.some(v =>
      v.source === 'postgres-data' && v.target === '/var/lib/postgresql'));
  }
});

test('produção injeta URL pública, proxy confiável e destino de storage consumido pela API', () => {
  const config = compose(true);
  const apiEnv = config.services.api.environment;
  assert.equal(apiEnv.MAGIC_LINK_BASE_URL, 'https://satoshi.example.test');
  assert.equal(apiEnv.QUARKUS_HTTP_PROXY_TRUSTED_PROXIES, 'caddy,pwa');
  assert.equal(apiEnv.SESSION_COOKIE_SECURE, 'true');
  assert.equal(apiEnv.MINIO_ENDPOINT, 'https://s3.example.test');
  assert.equal(apiEnv.MINIO_ACCESS_KEY, env.AWS_ACCESS_KEY_ID);
  assert.equal(apiEnv.MINIO_SECRET_KEY, env.AWS_SECRET_ACCESS_KEY);
  assert.equal(apiEnv.MINIO_BUCKET, 'approved-test');
  assert.equal(apiEnv.MINIO_STAGING_BUCKET, 'staging-test');
  assert.equal(apiEnv.QUARKUS_S3_ENDPOINT_OVERRIDE, undefined);
  assert.equal(apiEnv.QUARKUS_S3_AWS_REGION, undefined);
  assert.equal(config.services.api.depends_on.minio, undefined);
});

test('serviços locais só iniciam com perfil dev e produção mantém somente a borda publicada', () => {
  const production = compose(true);
  for (const name of ['mailpit', 'bitcoind', 'minio-init', 'minio']) {
    assert.equal(production.services[name], undefined, `${name} não deve iniciar em produção`);
  }
  for (const [name, service] of Object.entries(production.services)) {
    if (name !== 'caddy') assert.deepEqual(service.ports ?? [], [], `${name} expôs porta`);
  }
  const development = compose(false, true);
  for (const name of ['mailpit', 'bitcoind', 'minio-init', 'minio']) {
    assert.ok(development.services[name], `${name} deve estar no perfil local`);
  }
  assert.equal(development.services.api.environment.QUARKUS_HTTP_PROXY_TRUSTED_PROXIES, 'caddy,pwa');
  assert.equal(development.services.api.environment.SESSION_COOKIE_SECURE, 'false');
});

test('produção recusa configuração sem allowlist ou destino externo de storage', () => {
  for (const missing of ['QUARKUS_HTTP_PROXY_TRUSTED_PROXIES', 'OBJECT_STORAGE_ENDPOINT',
    'OBJECT_STORAGE_BUCKET', 'OBJECT_STORAGE_STAGING_BUCKET', 'AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY']) {
    const incompleteEnv = { ...env };
    delete incompleteEnv[missing];
    const result = spawnSync('docker', ['compose', '--env-file',
      process.platform === 'win32' ? 'NUL' : '/dev/null', '-f', 'infra/docker-compose.yml',
      '-f', 'infra/docker-compose.prod.yml', 'config', '--format', 'json'],
    { cwd: root, env: incompleteEnv, encoding: 'utf8' });
    assert.notEqual(result.status, 0, `produção aceitou ausência de ${missing}`);
    assert.ok(result.stderr.includes(missing), result.stderr);
  }
});

test('Caddy expande domínio antes do parsing dos endereços e remove headers forjados', () => {
  const source = readFileSync(path.join(root, 'infra/caddy/Caddyfile.prod'), 'utf8');
  const siteAddresses = source.split('\n').filter(line => /^\S+\s*\{$/.test(line));
  assert.ok(siteAddresses.includes('{$DOMAIN} {'));
  assert.ok(siteAddresses.includes('http://{$DOMAIN} {'));
  assert.equal(siteAddresses.some(line => line.includes('{env.')), false);
  assert.match(source, /header_up -Forwarded/);
  assert.match(source, /header_up -X-Forwarded-\*/);
  assert.match(source, /header_up X-Forwarded-Host \{host\}/);
});

test('nginx protege API PNG contra regex estática e encaminha o caminho real do WebSocket', () => {
  const source = readFileSync(path.join(root, 'apps/pwa/nginx.conf'), 'utf8');
  assert.match(source, /location \^~ \/api\/\s*\{/);
  const socket = source.match(/location \^~ \/api\/ws\/\s*\{([^}]+)\}/s)?.[1];
  assert.ok(socket, 'canal /api/ws/ deve ter location protegido');
  assert.match(socket, /proxy_set_header\s+Upgrade\s+\$http_upgrade/);
  assert.match(socket, /proxy_set_header\s+Connection\s+"upgrade"/);
  assert.doesNotMatch(source, /\$proxy_add_x_forwarded_for/);
});
