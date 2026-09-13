import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { validateBuildArtifacts } from "./pwa-installation-smoke.mjs";

const repositoryRoot = path.resolve(import.meta.dirname, "../..");

function readNgswConfig() {
  return JSON.parse(
    fs.readFileSync(
      path.join(repositoryRoot, "apps/pwa/ngsw-config.json"),
      "utf8",
    ),
  );
}

function createValidFixture() {
  const directory = fs.mkdtempSync(
    path.join(os.tmpdir(), "satoshi-pet-pwa-contract-"),
  );
  fs.mkdirSync(path.join(directory, "icons"));
  fs.writeFileSync(
    path.join(directory, "index.html"),
    '<html><head><link rel="manifest" href="manifest.webmanifest"></head></html>',
  );
  fs.writeFileSync(
    path.join(directory, "manifest.webmanifest"),
    JSON.stringify({
      name: "Satoshi Pet",
      short_name: "Satoshi Pet",
      lang: "pt-BR",
      display: "standalone",
      start_url: "./",
      scope: "./",
      icons: [
        { src: "icons/icon-192x192.png", sizes: "192x192", type: "image/png" },
        { src: "icons/icon-512x512.png", sizes: "512x512", type: "image/png" },
      ],
    }),
  );
  for (const file of ["favicon.ico", "ngsw-worker.js"])
    fs.writeFileSync(path.join(directory, file), "fixture");
  fs.writeFileSync(
    path.join(directory, "ngsw.json"),
    JSON.stringify({ assetGroups: [{ installMode: "prefetch" }] }),
  );
  fs.writeFileSync(
    path.join(directory, "main.js"),
    "register('ngsw-worker.js')",
  );
  const pngSignature = Buffer.from("89504e470d0a1a0a", "hex");
  fs.writeFileSync(
    path.join(directory, "icons/icon-192x192.png"),
    pngSignature,
  );
  fs.writeFileSync(
    path.join(directory, "icons/icon-512x512.png"),
    pngSignature,
  );
  return directory;
}

test("valida o contrato mínimo dos artefatos instaláveis", (t) => {
  const directory = createValidFixture();
  t.after(() => fs.rmSync(directory, { recursive: true, force: true }));

  const result = validateBuildArtifacts(directory);

  assert.equal(result.manifest.display, "standalone");
  assert.deepEqual(result.resources, [
    "/",
    "/manifest.webmanifest",
    "/ngsw-worker.js",
    "/ngsw.json",
  ]);
});

test("rejeita manifest sem ícone PNG de 192x192", (t) => {
  const directory = createValidFixture();
  t.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  const manifestPath = path.join(directory, "manifest.webmanifest");
  const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
  manifest.icons.shift();
  fs.writeFileSync(manifestPath, JSON.stringify(manifest));

  assert.throws(() => validateBuildArtifacts(directory), /ícone PNG 192x192/);
});

test("faz prefetch do ovo padrão em atualizações versionadas", () => {
  const eggGroup = readNgswConfig().assetGroups?.find(
    (group) => group.name === "egg-default",
  );

  assert.ok(eggGroup, "ngsw-config.json precisa declarar o grupo egg-default");
  assert.equal(eggGroup.installMode, "prefetch");
  assert.equal(eggGroup.updateMode, "prefetch");
  assert.deepEqual(eggGroup.resources?.files, ["/assets/egg.svg"]);
});
