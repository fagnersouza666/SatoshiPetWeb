#!/usr/bin/env node
/**
 * spawnSync seguro em Windows (paths com espaço, .cmd/.bat).
 */
import { spawnSync } from 'node:child_process';

function quoteCmd(part) {
  return `"${String(part).replace(/"/g, '""')}"`;
}

/**
 * @param {string} executable
 * @param {string[]} args
 * @param {import('node:child_process').SpawnSyncOptions} [options]
 */
export function spawnCommand(executable, args, options = {}) {
  const { cwd, stdio = 'inherit', env, windowsHide = true } = options;
  const batchNoWindows = process.platform === 'win32' && /\.(cmd|bat)$/i.test(executable);

  if (batchNoWindows && executable.includes(' ')) {
    const linha = [executable, ...args].map(quoteCmd).join(' ');
    return spawnSync(linha, { cwd, stdio, env, shell: true, windowsHide });
  }

  if (batchNoWindows) {
    return spawnSync(executable, args, { cwd, stdio, env, shell: true, windowsHide });
  }

  return spawnSync(executable, args, { cwd, stdio, env, shell: false, windowsHide });
}
