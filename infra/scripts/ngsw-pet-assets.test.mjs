#!/usr/bin/env node
/** Contrato da arte pública versionada (FUND-11, PRD §14.6). */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createRequire } from 'node:module';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const pwaRoot = path.join(repositoryRoot, 'apps/pwa');
const requirePwa = createRequire(path.join(pwaRoot, 'package.json'));
const { Generator } = await import(pathToFileURL(requirePwa.resolve('@angular/service-worker/config')));
const config = JSON.parse(fs.readFileSync(path.join(pwaRoot, 'ngsw-config.json'), 'utf8'));
const schema = JSON.parse(
  fs.readFileSync(requirePwa.resolve('@angular/service-worker/config/schema.json'), 'utf8'),
);
const manifest = await new Generator(
  {
    list: async () => [],
    read: async () => '',
    hash: async () => '',
    write: async () => {},
  },
  '/',
).process(config);
const artwork = manifest.dataGroups.find((group) => group.name === 'pet-artwork');

function matches(group, url) {
  return group.patterns.some((pattern) => new RegExp(pattern).test(url));
}

test('usa somente estratégias aceitas pelo Angular instalado', () => {
  const supported = schema.properties.dataGroups.items.properties.cacheConfig.properties.strategy.enum;
  for (const group of config.dataGroups) {
    assert.ok(
      supported.includes(group.cacheConfig.strategy),
      `${group.name}: estratégia inválida ${group.cacheConfig.strategy}`,
    );
  }
});

test('gera cache-first limitado para a arte pública antes do fallback geral da API', () => {
  assert.ok(artwork, 'o manifesto gerado precisa declarar pet-artwork');
  assert.equal(artwork.strategy, 'performance');
  assert.equal(artwork.maxSize, 50);
  assert.equal(artwork.maxAge, 365 * 24 * 60 * 60 * 1000);
  assert.equal(artwork.cacheQueryOptions.ignoreSearch ?? false, false);
  const urls = [
    'https://satoshi.pet/api/v1/public/addresses/criatura-a/artwork/1/atlas.png',
    'https://satoshi.pet/api/v1/public/addresses/criatura-a/artwork/2/atlas.png',
    'https://satoshi.pet/api/v1/public/addresses/criatura-b/artwork/1/atlas.png',
  ];
  for (const url of urls) {
    assert.equal(manifest.dataGroups.find((group) => matches(group, url))?.name, 'pet-artwork');
  }
});

test('não inclui previews privados nem URLs diretas do storage no grupo público', () => {
  assert.ok(artwork);
  for (const url of [
    'https://satoshi.pet/api/v1/account/pet/artwork/preview/atlas.png',
    'https://satoshi.pet/api/v1/auth/session',
    'https://storage.example/pet-artwork/pets/criatura-a/v1/atlas.png',
    'https://storage.example/pet-artwork-staging/pets/criatura-a/v1/atlas.png',
  ]) {
    assert.equal(matches(artwork, url), false, `arte pública não pode incluir ${url}`);
  }
  assert.equal(config.assetGroups.some((group) => group.name === 'pet-artwork'), false);
});
