#!/usr/bin/env node
/** Gate HTTP PWA/API: exige Node/Java e npm ci em apps/pwa; não exige Docker. */
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { spawnCommand } from "../../scripts/spawn-cross-platform.mjs";

const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
if (
  !existsSync(
    path.join(root, "apps/pwa/node_modules/@angular/core/package.json"),
  )
) {
  console.error("Instale as dependências primeiro: npm --prefix apps/pwa ci");
  process.exit(1);
}
const result = spawnCommand(
  process.execPath,
  [
    path.join(root, "scripts/mvnw.mjs"),
    "-Dpwa.auth.integration=true",
    "-Dtest=PwaAuthContractTest",
    "test",
    ...process.argv.slice(2),
  ],
  { cwd: root, env: { ...process.env, SATOSHIPET_NODE: process.execPath } },
);
process.exit(result.status ?? 1);
