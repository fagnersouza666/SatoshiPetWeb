#!/usr/bin/env node
/**
 * Verifica os grupos de cache do bundle e dos ícones da PWA.
 */
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(__dirname, "../..");
const ngswConfigPath = path.join(repositoryRoot, "apps/pwa/ngsw-config.json");
const iconsDirectory = path.join(repositoryRoot, "apps/pwa/public/icons");

function readNgswConfig() {
  return JSON.parse(fs.readFileSync(ngswConfigPath, "utf8"));
}

function groupNamed(config, name) {
  const group = config.assetGroups?.find(
    (candidate) => candidate.name === name,
  );
  assert.ok(group, `ngsw-config.json precisa declarar o grupo ${name}`);
  return group;
}

test("faz prefetch do shell com os bundles compilados", () => {
  const appShell = groupNamed(readNgswConfig(), "app-shell");

  assert.equal(appShell.installMode, "prefetch");
  assert.equal(appShell.updateMode, "prefetch");
  assert.deepEqual(
    new Set(appShell.resources?.files),
    new Set([
      "/favicon.ico",
      "/index.csr.html",
      "/index.html",
      "/manifest.webmanifest",
      "/*.css",
      "/*.js",
    ]),
  );
});

test("faz prefetch de todos os ícones públicos", () => {
  const icons = groupNamed(readNgswConfig(), "icons");
  const configuredFiles = new Set(icons.resources?.files);
  const publicIcons = fs
    .readdirSync(iconsDirectory)
    .filter((file) => /\.(png|svg)$/i.test(file))
    .map((file) => `/icons/${file}`);

  assert.equal(icons.installMode, "prefetch");
  assert.equal(icons.updateMode, "prefetch");
  assert.ok(publicIcons.length > 0, "a PWA precisa possuir ícones públicos");
  for (const icon of publicIcons) {
    assert.ok(
      configuredFiles.has(icon),
      `ícone não cacheado no grupo icons: ${icon}`,
    );
  }
});
