#!/usr/bin/env node
/**
 * Invoca o Maven Wrapper de services/api de forma cross-platform (Windows/Linux/macOS).
 */
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnCommand } from './spawn-cross-platform.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const apiDir = path.resolve(__dirname, '../services/api');
const mvnw = path.join(apiDir, process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw');
const args = process.argv.slice(2);

const resultado = spawnCommand(mvnw, args, { cwd: apiDir });
process.exit(resultado.status ?? 1);
