#!/usr/bin/env node
/**
 * Verifica a política network-first dos dados da API da PWA.
 */
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(__dirname, "../..");
const ngswConfigPath = path.join(repositoryRoot, "apps/pwa/ngsw-config.json");

function readNgswConfig() {
  return JSON.parse(fs.readFileSync(ngswConfigPath, "utf8"));
}

function apiDataGroup() {
  const group = readNgswConfig().dataGroups?.find((candidate) =>
    candidate.urls?.includes("/api/**"),
  );
  assert.ok(group, "ngsw-config.json precisa declarar o grupo da API");
  return group;
}

test("consulta a API pela rede antes de usar o cache", () => {
  const group = apiDataGroup();

  assert.equal(group.cacheConfig?.strategy, "freshness");
});

test("mantém fallback de leitura da API limitado e explícito", () => {
  const group = apiDataGroup();

  assert.equal(group.cacheConfig?.timeout, "10s");
  assert.equal(group.cacheConfig?.maxSize, 100);
  assert.equal(group.cacheConfig?.maxAge, "1d");
});
