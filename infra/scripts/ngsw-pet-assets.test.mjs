#!/usr/bin/env node
/**
 * Verifica o isolamento dos assets aprovados do pet no service worker.
 *
 * O object storage entrega URLs versionadas. O PWA apenas direciona as URLs
 * do bucket de produção para um grupo próprio, sem impor namespace de objetos
 * ou TTL que não façam parte do contrato do storage.
 */
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";

const repositoryRoot = path.resolve(import.meta.dirname, "../..");
const ngswConfigPath = path.join(repositoryRoot, "apps/pwa/ngsw-config.json");
const petArtworkUrlPattern = "https://**/pet-artwork/**";

function readNgswConfig() {
  return JSON.parse(fs.readFileSync(ngswConfigPath, "utf8"));
}

function petArtworkGroup() {
  const group = readNgswConfig().assetGroups?.find(
    (candidate) => candidate.name === "pet-artwork",
  );
  assert.ok(group, "ngsw-config.json precisa declarar o grupo pet-artwork");
  return group;
}

test("mantém assets do pet em grupo próprio e carregado sob demanda", () => {
  const group = petArtworkGroup();

  assert.equal(group.installMode, "lazy");
  assert.equal(group.updateMode, "prefetch");
  assert.deepEqual(group.resources?.urls, [petArtworkUrlPattern]);
  assert.equal(group.resources?.files, undefined);
});

test("não impõe TTL aos assets versionados pelo object storage", () => {
  const group = petArtworkGroup();
  const urls = group.resources?.urls;

  assert.equal(group.cacheConfig, undefined);
  assert.ok(Array.isArray(urls), "o grupo pet-artwork precisa usar URLs");
  assert.equal(urls.length, 1);
  assert.match(urls[0], /^https:\/\/\*\*\/pet-artwork\/\*\*$/);
});
