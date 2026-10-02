import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";

const root = new URL("../../", import.meta.url);

test("contrato HTTP real é obrigatório dentro do gate da API", async () => {
  const workflow = await readFile(
    new URL(".github/workflows/ci.yml", root),
    "utf8",
  );
  const api = workflow.slice(
    workflow.indexOf("  verificar-api:"),
    workflow.indexOf("  build-imagens:"),
  );
  assert.match(api, /npm ci --prefer-offline/);
  assert.match(api, /set -o pipefail\s+\n?\s*npm run test:auth:integration/);
  assert.doesNotMatch(api, /continue-on-error:\s*true/);
  const pkg = JSON.parse(await readFile(new URL("package.json", root), "utf8"));
  assert.equal(
    pkg.scripts["test:auth:integration"],
    "node infra/scripts/check-auth-integration.mjs",
  );
});

test("harness importa AuthService e ApiClientService de produção", async () => {
  const source = await readFile(
    new URL("apps/pwa/integration/auth-api.contract.ts", root),
    "utf8",
  );
  assert.match(source, /from '\.\.\/src\/app\/core\/auth\.service'/);
  assert.match(source, /from '\.\.\/src\/app\/core\/api-client\.service'/);
  assert.doesNotMatch(source, /HttpTestingController|provideHttpClientTesting/);
  const runner = await readFile(
    new URL("infra/scripts/check-auth-integration.mjs", root),
    "utf8",
  );
  assert.match(runner, /-Dpwa\.auth\.integration=true/);
  assert.match(runner, /-Dtest=PwaAuthContractTest/);
});
