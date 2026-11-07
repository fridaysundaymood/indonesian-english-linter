import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import { validateResult, exitCodeFor } from '@android-kit/schema';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { cliArgs, toolDefinitions, configSection, configSectionSchema, supportedSchemaMajors } from '../dist/index.js';

const jar = resolve(process.env.I18N_LINT_JAR ?? fileURLToPath(new URL('../../cli/build/libs/i18n-lint.jar', import.meta.url)));
const server = fileURLToPath(new URL('../dist/bin.js', import.meta.url));
const client = new Client({ name: 'parity-test', version: '1.0.0' });
const transport = new StdioClientTransport({ command: process.execPath, args: [server], env: { ...process.env as Record<string, string>, I18N_LINT_JAR: jar } });

function direct(operation: 'lint' | 'doctor', args: string[]) {
  const child = spawnSync(process.env.JAVA ?? 'java', ['-jar', jar, operation, '--json', ...args], { encoding: 'utf8' });
  if (child.error) throw child.error;
  const result: unknown = JSON.parse(child.stdout);
  expect(validateResult(result)).toBe(true);
  if (!validateResult(result)) throw new Error('Invalid CLI envelope');
  expect(child.status).toBe(exitCodeFor(result.status));
  return result;
}

function comparable(result: Record<string, unknown>) {
  const { run_id, started_at, duration_ms, artifacts, ...stable } = result;
  return stable;
}

describe('CLI and MCP parity', () => {
  beforeAll(async () => { await client.connect(transport); }, 60000);
  afterAll(async () => { await client.close(); }, 60000);

  it('exposes the same tools and configuration through the package API', async () => {
    const listed = await client.listTools();
    expect(listed.tools.map(tool => tool.name)).toEqual(toolDefinitions().map(tool => tool.name));
    expect(configSection).toBe('i18n');
    expect(configSectionSchema.title).toBe('Localization lint configuration v1');
    expect(supportedSchemaMajors).toEqual([1]);
  });

  it('passes projectRoot to both CLI operations', async () => {
    const missing = resolve('nonexistent-i18n-project-root');
    for (const tool of toolDefinitions({ projectRoot: missing })) {
      expect((await tool.handler({})).status).toBe('error');
      expect((await tool.handler({ projectRoot: missing })).status).toBe('error');
    }
  });

  it('imports the package root without starting stdio', () => {
    const child = spawnSync(process.execPath, ['--input-type=module', '-e', "import '@android-kit/i18n-mcp';"], {
      cwd: fileURLToPath(new URL('..', import.meta.url)), encoding: 'utf8', timeout: 5000,
    });
    expect(child.error).toBeUndefined();
    expect(child.status).toBe(0);
    expect(child.stdout).toBe('');
    expect(child.stderr).toBe('');
  });

  it.each([
    { operation: 'doctor' as const, tool: 'i18n_doctor', options: {}, args: [] },
    { operation: 'lint' as const, tool: 'i18n_lint', options: { config: 'missing-i18n-config.yaml' }, args: ['--config', 'missing-i18n-config.yaml'] },
    { operation: 'lint' as const, tool: 'i18n_lint', options: { paths: ['missing-project'] }, args: ['--path', 'missing-project'] },
  ])('$tool mirrors $operation', async ({ operation, tool, options, args }) => {
    const expected = direct(operation, args);
    const response = await client.callTool({ name: tool, arguments: options });
    const content = response.content as Array<{ type: string; text?: string }>;
    const actual: unknown = JSON.parse(content[0].text!);
    expect(validateResult(actual)).toBe(true);
    if (!validateResult(actual)) throw new Error('Invalid MCP envelope');
    expect(comparable(actual as unknown as Record<string, unknown>)).toEqual(comparable(expected as unknown as Record<string, unknown>));
  }, 60000);

  it('maps every lint option to CLI arguments without shell parsing', () => {
    expect(cliArgs('lint', { config: 'a b.yaml', paths: ['a b', 'c'], rules: ['RawIdDisplay', 'MissingLocaleKey'], baseline: 'base.xml', baseline_create: 'new.xml', baseline_update: 'old.xml', reports: ['markdown', 'sarif'] })).toEqual([
      'lint', '--json', '--config', 'a b.yaml', '--path', 'a b', '--path', 'c', '--rules', 'RawIdDisplay,MissingLocaleKey', '--baseline', 'base.xml', '--baseline-create', 'new.xml', '--baseline-update', 'old.xml', '--report', 'markdown', '--report', 'sarif',
    ]);
  });
});
