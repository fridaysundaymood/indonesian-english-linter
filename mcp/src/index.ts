import { spawn } from 'node:child_process';
import { lstatSync, readFileSync, realpathSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path';
import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { CallToolRequestSchema, ListToolsRequestSchema, McpError, ErrorCode } from '@modelcontextprotocol/sdk/types.js';
import { checkSchemaMajor, createResult, exitCodeFor, newRunId, validateResult, type Result } from '@budaknyenyel/kit-schema';
import { z } from 'zod/v4';

checkSchemaMajor(1);

export const configSection = 'i18n';
export const supportedSchemaMajors = [1];
export const configSectionSchema: Record<string, unknown> = JSON.parse(
  readFileSync(new URL('./i18n.v1.json', import.meta.url), 'utf8'),
);

const lintSchema = z.object({
  config: z.string().optional().describe('Configuration file'),
  paths: z.array(z.string()).optional().describe('Project, source or resource paths'),
  rules: z.array(z.string().regex(/^[A-Za-z][A-Za-z0-9_]*$/)).optional().describe('Selected lint issue IDs'),
  baseline: z.string().optional(),
  baseline_create: z.string().optional(),
  baseline_update: z.string().optional(),
  reports: z.array(z.enum(['markdown', 'sarif'])).optional(),
}).strict();
const doctorSchema = z.object({}).strict();
type LintOptions = {
  projectRoot?: string;
  config?: string; paths?: string[]; rules?: string[]; baseline?: string;
  baseline_create?: string; baseline_update?: string; reports?: ('markdown' | 'sarif')[];
};

function containedPath(root: string, path: string): void {
  if (!path || path.startsWith('-')) throw new Error('Invalid project path');
  const target = resolve(root, path);
  const inside = (candidate: string) => {
    const rel = relative(root, candidate);
    return !isAbsolute(rel) && rel !== '..' && !rel.startsWith(`..${sep}`);
  };
  if (!inside(target)) throw new Error('Path is outside the project root');
  let existing = target;
  for (;;) {
    try { lstatSync(existing); break; } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error;
      const parent = dirname(existing);
      if (parent === existing) throw new Error('Path has no existing ancestor');
      existing = parent;
    }
  }
  if (!inside(realpathSync(existing))) throw new Error('Path resolves outside the project root');
}

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
  const jar = process.env.I18N_LINT_JAR ?? fileURLToPath(new URL('./i18n-lint.jar', import.meta.url));
  try {
    const root = realpathSync(options.projectRoot ?? process.cwd());
    for (const path of [options.config, ...(options.paths ?? []), options.baseline, options.baseline_create, options.baseline_update]) {
      if (path !== undefined) containedPath(root, path);
    }
    const { promise, resolve: accept, reject } = Promise.withResolvers<{stdout: string; stderr: string; code: number | null}>();
    const child = spawn(process.env.JAVA ?? 'java', ['-jar', resolve(jar), ...cliArgs(operation, options)], { windowsHide: true, cwd: root });
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

export async function doctor(options: { projectRoot?: string } = {}): Promise<Result> {
  return runCli('doctor', options);
}

export function toolDefinitions(options: { projectRoot?: string } = {}) {
  return [
    {
      name: 'i18n_lint',
      description: 'Run localization lint checks',
      inputSchema: z.toJSONSchema(lintSchema),
      handler: (input: unknown) => runCli('lint', { ...lintSchema.parse(input), ...options }),
    },
    {
      name: 'i18n_doctor',
      description: 'Check localization lint prerequisites',
      inputSchema: z.toJSONSchema(doctorSchema),
      handler: (input: unknown) => doctor({ ...doctorSchema.parse(input), ...options }),
    },
  ];
}

export function createServer(): Server {
  const server = new Server({ name: 'i18n-lint', version: '0.1.0' }, { capabilities: { tools: {} } });
  const tools = toolDefinitions();
  server.setRequestHandler(ListToolsRequestSchema, async () => ({
    tools: tools.map(({ name, description, inputSchema }) => ({ name, description, inputSchema })),
  }));
  server.setRequestHandler(CallToolRequestSchema, async ({ params }) => {
    const tool = tools.find(candidate => candidate.name === params.name);
    if (!tool) throw new McpError(ErrorCode.MethodNotFound, `Unknown tool: ${params.name}`);
    const result = await tool.handler(params.arguments ?? {});
    return { content: [{ type: 'text', text: JSON.stringify(result) }] };
  });
  return server;
}
