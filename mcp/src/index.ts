#!/usr/bin/env node
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { checkSchemaMajor, createResult, exitCodeFor, newRunId, validateResult, type Result } from '@android-kit/schema';
import { z } from 'zod';

checkSchemaMajor(1);

const lintOptions = {
  config: z.string().optional().describe('Configuration file'),
  paths: z.array(z.string()).optional().describe('Project, source or resource paths'),
  rules: z.array(z.string()).optional().describe('Selected lint issue IDs'),
  baseline: z.string().optional(),
  baseline_create: z.string().optional(),
  baseline_update: z.string().optional(),
  reports: z.array(z.enum(['markdown', 'sarif'])).optional(),
};
type LintOptions = {
  config?: string; paths?: string[]; rules?: string[]; baseline?: string;
  baseline_create?: string; baseline_update?: string; reports?: ('markdown' | 'sarif')[];
};

export function cliArgs(operation: 'lint' | 'doctor', options: LintOptions = {}): string[] {
  const args = [operation, '--json'];
  if (operation === 'doctor') return args;
  if (options.config !== undefined) args.push('--config', options.config);
  for (const path of options.paths ?? []) args.push('--path', path);
  if (options.rules !== undefined) args.push('--rules', options.rules.join(','));
  if (options.baseline !== undefined) args.push('--baseline', options.baseline);
  if (options.baseline_create !== undefined) args.push('--baseline-create', options.baseline_create);
  if (options.baseline_update !== undefined) args.push('--baseline-update', options.baseline_update);
  for (const report of options.reports ?? []) args.push('--report', report);
  return args;
}

export async function runCli(operation: 'lint' | 'doctor', options: LintOptions = {}): Promise<Result> {
  const startedAt = new Date();
  const jar = process.env.I18N_LINT_JAR ?? fileURLToPath(new URL('../../cli/build/libs/i18n-lint.jar', import.meta.url));
  try {
    const { promise, resolve: accept, reject } = Promise.withResolvers<{stdout: string; stderr: string; code: number | null}>();
    const child = spawn(process.env.JAVA ?? 'java', ['-jar', resolve(jar), ...cliArgs(operation, options)], { windowsHide: true });
    let stdout = '', stderr = '';
    child.stdout.setEncoding('utf8').on('data', (chunk: string) => { stdout += chunk; });
    child.stderr.setEncoding('utf8').on('data', (chunk: string) => { stderr += chunk; });
    child.on('error', reject);
    child.on('close', code => accept({ stdout, stderr, code }));
    const completed = await promise;
    const { stdout: output, stderr: errors, code } = completed;
    let result: unknown;
    try { result = JSON.parse(output); } catch { throw new Error(`CLI did not return JSON (exit ${code}): ${errors.trim()}`); }
    if (!validateResult(result) || result.tool !== 'i18n' || result.operation !== operation)
      throw new Error('CLI returned an invalid i18n result envelope');
    if (code !== exitCodeFor(result.status)) throw new Error(`CLI exit code ${code} does not match status ${result.status}`);
    return result;
  } catch (error) {
    return createResult({ tool: 'i18n', operation, run_id: newRunId(), status: 'error', classification: 'environment', summary: `Unable to run i18n CLI: ${error instanceof Error ? error.message : String(error)}`, evidence: [], artifacts: [], startedAt });
  }
}

export function createServer(): McpServer {
  const server = new McpServer({ name: 'i18n-lint', version: '1.0.0' });
  server.registerTool('i18n_lint', { description: 'Run localization lint checks', inputSchema: lintOptions }, async (options) => {
    const result = await runCli('lint', options);
    return { content: [{ type: 'text', text: JSON.stringify(result) }] };
  });
  server.registerTool('i18n_doctor', { description: 'Check localization lint prerequisites', inputSchema: {} }, async () => {
    const result = await runCli('doctor');
    return { content: [{ type: 'text', text: JSON.stringify(result) }] };
  });
  return server;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await createServer().connect(new StdioServerTransport());
}
