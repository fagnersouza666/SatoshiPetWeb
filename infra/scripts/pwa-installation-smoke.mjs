#!/usr/bin/env node
/**
 * Smoke test dos artefatos instaláveis da PWA.
 *
 * A validação combina o build de produção com uma checagem HTTPS dos recursos
 * que o navegador precisa para oferecer a instalação: shell, manifesto,
 * service worker e ícones. Um endereço HTTPS já publicado pode ser informado
 * por PWA_SMOKE_URL; sem ele, o script cria um servidor HTTPS efêmero local.
 */
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { createServer } from "node:https";
import { fileURLToPath } from "node:url";
import { request as httpsRequest } from "node:https";
import { spawnCommand } from "../../scripts/spawn-cross-platform.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(__dirname, "../..");
const defaultOutputDirectory = path.join(
  repositoryRoot,
  "apps/pwa/dist/pwa/browser",
);
const manifestOrigin = "https://satoshi-pet-smoke.invalid";

const requiredBuildFiles = [
  "index.html",
  "manifest.webmanifest",
  "ngsw-worker.js",
  "ngsw.json",
  "favicon.ico",
];

const contentTypes = {
  ".css": "text/css; charset=utf-8",
  ".html": "text/html; charset=utf-8",
  ".ico": "image/x-icon",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".webmanifest": "application/manifest+json; charset=utf-8",
};

function assertCondition(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function readJson(filePath, description) {
  try {
    return JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch (error) {
    throw new Error(`Não foi possível ler ${description}: ${error.message}`);
  }
}

function resolveLocalManifestPath(outputDirectory, resource, description) {
  let resourceUrl;
  try {
    resourceUrl = new URL(resource, `${manifestOrigin}/`);
  } catch (error) {
    throw new Error(`${description} inválido: ${error.message}`);
  }

  assertCondition(
    resourceUrl.origin === manifestOrigin,
    `${description} deve apontar para um recurso da própria PWA: ${resource}`,
  );

  let decodedPath;
  try {
    decodedPath = decodeURIComponent(resourceUrl.pathname);
  } catch (error) {
    throw new Error(
      `${description} contém uma URL codificada inválida: ${error.message}`,
    );
  }

  const resolvedOutput = path.resolve(outputDirectory);
  const resolvedResource = path.resolve(resolvedOutput, `.${decodedPath}`);
  const relativeResource = path.relative(resolvedOutput, resolvedResource);
  assertCondition(
    relativeResource !== ".." &&
      !relativeResource.startsWith(`..${path.sep}`) &&
      !path.isAbsolute(relativeResource),
    `${description} não pode sair do diretório publicado: ${resource}`,
  );

  return resolvedResource;
}

function hasManifestIcon(manifest, expectedSize) {
  return manifest.icons.some((icon) => {
    const sizes = typeof icon.sizes === "string" ? icon.sizes.split(/\s+/) : [];
    return sizes.includes(expectedSize) && icon.type === "image/png";
  });
}

/**
 * Valida os contratos que o build precisa produzir para uma PWA instalável.
 * Exportada para permitir testes unitários sem executar Angular ou Docker.
 */
export function validateBuildArtifacts(
  outputDirectory = defaultOutputDirectory,
) {
  const resolvedOutput = path.resolve(outputDirectory);
  assertCondition(
    fs.existsSync(resolvedOutput),
    `Diretório do build não encontrado: ${resolvedOutput}`,
  );

  for (const relativeFile of requiredBuildFiles) {
    const filePath = path.join(resolvedOutput, relativeFile);
    assertCondition(
      fs.existsSync(filePath),
      `Artefato obrigatório ausente: ${relativeFile}`,
    );
    assertCondition(
      fs.statSync(filePath).size > 0,
      `Artefato obrigatório vazio: ${relativeFile}`,
    );
  }

  const indexHtml = fs.readFileSync(
    path.join(resolvedOutput, "index.html"),
    "utf8",
  );
  const linkTags = indexHtml.match(/<link\b[^>]*>/gi) ?? [];
  const manifestLink = linkTags.find(
    (tag) =>
      /\brel\s*=\s*["']manifest["']/i.test(tag) &&
      /\bhref\s*=\s*["'][^"']+manifest[^"']*["']/i.test(tag),
  );
  assertCondition(
    manifestLink,
    "index.html não referencia o manifest.webmanifest",
  );

  const javascriptFiles = fs
    .readdirSync(resolvedOutput)
    .filter((file) => file.endsWith(".js"));
  assertCondition(
    javascriptFiles.some((file) =>
      fs
        .readFileSync(path.join(resolvedOutput, file), "utf8")
        .includes("ngsw-worker.js"),
    ),
    "O bundle da PWA não registra o service worker ngsw-worker.js",
  );

  const manifest = readJson(
    path.join(resolvedOutput, "manifest.webmanifest"),
    "manifest.webmanifest",
  );
  assertCondition(manifest.name, "manifest.webmanifest precisa declarar name");
  assertCondition(
    manifest.short_name,
    "manifest.webmanifest precisa declarar short_name",
  );
  assertCondition(
    manifest.lang === "pt-BR",
    "manifest.webmanifest precisa declarar lang pt-BR",
  );
  assertCondition(
    manifest.display === "standalone",
    "manifest.webmanifest precisa usar display standalone",
  );
  assertCondition(
    typeof manifest.start_url === "string" && manifest.start_url.length > 0,
    "manifest precisa declarar start_url",
  );
  assertCondition(
    typeof manifest.scope === "string" && manifest.scope.length > 0,
    "manifest precisa declarar scope",
  );
  assertCondition(
    Array.isArray(manifest.icons) && manifest.icons.length > 0,
    "manifest precisa declarar ícones",
  );

  for (const [field, value] of [
    ["start_url", manifest.start_url],
    ["scope", manifest.scope],
  ]) {
    let fieldUrl;
    try {
      fieldUrl = new URL(value, `${manifestOrigin}/`);
    } catch (error) {
      throw new Error(`manifest.${field} inválido: ${error.message}`);
    }
    assertCondition(
      fieldUrl.origin === manifestOrigin,
      `manifest.${field} deve ser relativo à própria PWA`,
    );
  }

  for (const [index, icon] of manifest.icons.entries()) {
    assertCondition(
      icon && typeof icon.src === "string" && icon.src.length > 0,
      `Ícone ${index} sem src`,
    );
    const iconPath = resolveLocalManifestPath(
      resolvedOutput,
      icon.src,
      `Ícone ${index}`,
    );
    assertCondition(
      fs.existsSync(iconPath),
      `Ícone declarado não encontrado: ${icon.src}`,
    );
    assertCondition(
      fs.statSync(iconPath).size > 0,
      `Ícone declarado vazio: ${icon.src}`,
    );
    if (icon.type === "image/png") {
      const pngSignature = Buffer.from("89504e470d0a1a0a", "hex");
      const actualSignature = fs
        .readFileSync(iconPath)
        .subarray(0, pngSignature.length);
      assertCondition(
        actualSignature.equals(pngSignature),
        `Ícone PNG inválido: ${icon.src}`,
      );
    }
  }

  assertCondition(
    hasManifestIcon(manifest, "192x192"),
    "manifest precisa de um ícone PNG 192x192",
  );
  assertCondition(
    hasManifestIcon(manifest, "512x512"),
    "manifest precisa de um ícone PNG 512x512",
  );

  const ngsw = readJson(path.join(resolvedOutput, "ngsw.json"), "ngsw.json");
  assertCondition(
    Array.isArray(ngsw.assetGroups) && ngsw.assetGroups.length > 0,
    "ngsw.json precisa declarar assetGroups",
  );
  assertCondition(
    ngsw.assetGroups.some((group) => group.installMode === "prefetch"),
    "ngsw.json precisa declarar um assetGroup prefetch para o app shell",
  );

  return {
    manifest,
    iconPaths: manifest.icons.map((icon) =>
      resolveLocalManifestPath(resolvedOutput, icon.src, "Ícone"),
    ),
    resources: ["/", "/manifest.webmanifest", "/ngsw-worker.js", "/ngsw.json"],
  };
}

function runProductionBuild() {
  const npmCommand = process.platform === "win32" ? "npm.cmd" : "npm";
  const result = spawnCommand(npmCommand, ["run", "build:pwa"], {
    cwd: repositoryRoot,
    stdio: "inherit",
  });
  assertCondition(
    result.status === 0,
    `Build da PWA falhou com código ${result.status ?? "desconhecido"}`,
  );
}

function normalizeBaseUrl(rawUrl) {
  let url;
  try {
    url = new URL(rawUrl);
  } catch (error) {
    throw new Error(`PWA_SMOKE_URL inválida: ${error.message}`);
  }
  assertCondition(
    url.protocol === "https:",
    "O smoke test de instalação exige uma URL HTTPS",
  );
  if (!url.pathname.endsWith("/")) {
    url.pathname += "/";
  }
  url.search = "";
  url.hash = "";
  return url;
}

function requestHttps(url, allowInsecure) {
  return new Promise((resolve, reject) => {
    const request = httpsRequest(
      url,
      {
        rejectUnauthorized: !allowInsecure,
        headers: { accept: "*/*" },
      },
      (response) => {
        const chunks = [];
        response.on("data", (chunk) => chunks.push(chunk));
        response.on("end", () => {
          resolve({
            statusCode: response.statusCode ?? 0,
            headers: response.headers,
            body: Buffer.concat(chunks),
          });
        });
      },
    );
    request.setTimeout(10_000, () =>
      request.destroy(new Error(`Timeout ao acessar ${url}`)),
    );
    request.on("error", reject);
    request.end();
  });
}

function expectedContentType(resourcePath) {
  if (resourcePath === "/") return "text/html";
  if (resourcePath.endsWith(".webmanifest")) return "manifest";
  if (resourcePath.endsWith(".js")) return "javascript";
  if (resourcePath.endsWith(".json")) return "json";
  if (resourcePath.endsWith(".png")) return "image/png";
  return undefined;
}

/**
 * Consulta os recursos de instalação através de HTTPS, sem depender de um
 * navegador instalado no host.
 */
export async function probeHttps(
  baseUrl,
  resources,
  { allowInsecure = false } = {},
) {
  const normalizedBase = normalizeBaseUrl(baseUrl);
  const checkedResources = [];

  for (const resourcePath of resources) {
    const resourceUrl = new URL(
      resourcePath.replace(/^\//, ""),
      normalizedBase,
    );
    const response = await requestHttps(resourceUrl, allowInsecure);
    assertCondition(
      response.statusCode === 200,
      `${resourcePath} retornou HTTP ${response.statusCode}`,
    );

    const contentType = String(
      response.headers["content-type"] ?? "",
    ).toLowerCase();
    const expectedType = expectedContentType(resourcePath);
    if (expectedType) {
      assertCondition(
        contentType.includes(expectedType),
        `${resourcePath} retornou Content-Type incompatível: ${contentType || "(ausente)"}`,
      );
    }

    checkedResources.push(resourcePath);
  }

  return checkedResources;
}

function createEphemeralCertificate() {
  const directory = fs.mkdtempSync(
    path.join(os.tmpdir(), "satoshi-pet-pwa-smoke-"),
  );
  const keyPath = path.join(directory, "key.pem");
  const certificatePath = path.join(directory, "certificate.pem");
  const result = spawnSync(
    process.env.OPENSSL_BIN || "openssl",
    [
      "req",
      "-x509",
      "-newkey",
      "rsa:2048",
      "-nodes",
      "-days",
      "1",
      "-subj",
      "/CN=localhost",
      "-keyout",
      keyPath,
      "-out",
      certificatePath,
    ],
    { stdio: "ignore", windowsHide: true },
  );

  if (
    result.error ||
    result.status !== 0 ||
    !fs.existsSync(keyPath) ||
    !fs.existsSync(certificatePath)
  ) {
    fs.rmSync(directory, { recursive: true, force: true });
    return undefined;
  }

  return {
    directory,
    key: fs.readFileSync(keyPath),
    certificate: fs.readFileSync(certificatePath),
  };
}

function serveBuildOverHttps(outputDirectory, certificate) {
  const resolvedOutput = path.resolve(outputDirectory);
  const server = createServer(
    { key: certificate.key, cert: certificate.certificate },
    (request, response) => {
      let pathname;
      try {
        pathname = decodeURIComponent(
          new URL(request.url || "/", "https://localhost").pathname,
        );
      } catch {
        response.writeHead(400);
        response.end();
        return;
      }

      const relativePath = pathname === "/" ? "index.html" : pathname.slice(1);
      const filePath = path.resolve(resolvedOutput, relativePath);
      const relativeFile = path.relative(resolvedOutput, filePath);
      const isInsideOutput =
        relativeFile !== ".." &&
        !relativeFile.startsWith(`..${path.sep}`) &&
        !path.isAbsolute(relativeFile);

      if (
        !isInsideOutput ||
        !fs.existsSync(filePath) ||
        !fs.statSync(filePath).isFile()
      ) {
        response.writeHead(404);
        response.end();
        return;
      }

      const extension = path.extname(filePath).toLowerCase();
      response.writeHead(200, {
        "content-type": contentTypes[extension] || "application/octet-stream",
      });
      fs.createReadStream(filePath).pipe(response);
    },
  );

  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      if (!address || typeof address === "string") {
        server.close();
        reject(
          new Error(
            "Não foi possível descobrir a porta do servidor HTTPS efêmero",
          ),
        );
        return;
      }
      resolve({
        server,
        url: `https://127.0.0.1:${address.port}/`,
      });
    });
  });
}

function closeServer(server) {
  return new Promise((resolve) => server.close(resolve));
}

async function runSmokeTest({
  skipBuild = false,
  outputDirectory = defaultOutputDirectory,
} = {}) {
  if (!skipBuild) {
    console.log("==> Gerando build de produção da PWA");
    runProductionBuild();
  }

  console.log(`==> Validando artefatos instaláveis em ${outputDirectory}`);
  const artifactContract = validateBuildArtifacts(outputDirectory);
  const iconResources = artifactContract.manifest.icons
    .filter((icon) =>
      ["192x192", "512x512"].some((size) =>
        icon.sizes?.split(/\s+/).includes(size),
      ),
    )
    .map(
      (icon) =>
        new URL(icon.src, "https://satoshi-pet-smoke.invalid/").pathname,
    );
  const resources = [...artifactContract.resources, ...new Set(iconResources)];
  const configuredUrl = process.env.PWA_SMOKE_URL?.trim();

  if (configuredUrl) {
    console.log(`==> Verificando instalação sobre HTTPS em ${configuredUrl}`);
    await probeHttps(configuredUrl, resources, {
      allowInsecure: process.env.PWA_SMOKE_ALLOW_INSECURE === "1",
    });
  } else {
    const certificate = createEphemeralCertificate();
    if (!certificate) {
      const message =
        "SKIP: HTTPS local não foi verificado porque o openssl não está disponível; " +
        "informe PWA_SMOKE_URL=https://... em staging ou instale o openssl para executar esta etapa.";
      if (process.env.PWA_SMOKE_REQUIRE_HTTPS === "1") {
        throw new Error(message.replace(/^SKIP: /, ""));
      }
      console.warn(message);
    } else {
      let server;
      try {
        const localServer = await serveBuildOverHttps(
          outputDirectory,
          certificate,
        );
        server = localServer.server;
        console.log(
          `==> Verificando instalação sobre HTTPS local em ${localServer.url}`,
        );
        await probeHttps(localServer.url, resources, { allowInsecure: true });
      } finally {
        if (server) await closeServer(server);
        fs.rmSync(certificate.directory, { recursive: true, force: true });
      }
    }
  }

  console.log("PWA installation smoke test concluído com sucesso.");
}

const isMainModule =
  process.argv[1] &&
  path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMainModule) {
  runSmokeTest({
    skipBuild: process.argv.includes("--skip-build"),
    outputDirectory: process.env.PWA_DIST_DIR || defaultOutputDirectory,
  }).catch((error) => {
    console.error(`PWA installation smoke test falhou: ${error.message}`);
    process.exitCode = 1;
  });
}
