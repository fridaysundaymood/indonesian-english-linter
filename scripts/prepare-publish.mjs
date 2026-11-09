import { cp, mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const source = join(root, 'mcp');
const [destination, tag] = process.argv.slice(2);
if (!destination || !tag?.startsWith('v')) {
  throw new Error('Usage: node scripts/prepare-publish.mjs <destination> <version tag>');
}
const manifest = JSON.parse(await readFile(join(source, 'package.json'), 'utf8'));
if (`v${manifest.version}` !== tag) throw new Error(`${manifest.name}: version ${manifest.version} does not match tag ${tag}`);
for (const section of ['dependencies', 'optionalDependencies', 'peerDependencies']) {
  for (const [name, spec] of Object.entries(manifest[section] ?? {})) {
    if (!spec.startsWith('file:')) continue;
    const dependency = JSON.parse(await readFile(resolve(source, spec.slice(5), 'package.json'), 'utf8'));
    if (dependency.name !== name || dependency.private || !dependency.version) throw new Error(`Invalid publish dependency ${name}`);
    manifest[section][name] = `^${dependency.version}`;
  }
}
// The staged copy ships already-built output: no rebuild here, only a check that every shipped file exists.
manifest.scripts = {
  prepublishOnly: "node -e \"const fs=require('node:fs'); for (const f of ['dist/bin.js','dist/index.js','dist/i18n.v1.json','dist/i18n-lint.jar','README.md','LICENSE','CHANGELOG.md']) if (!fs.existsSync(f)) throw new Error('missing ' + f)\"",
};
delete manifest.devDependencies;
const target = resolve(destination);
await mkdir(target, { recursive: true });
await cp(join(source, 'dist'), join(target, 'dist'), { recursive: true });
await mkdir(join(target, 'schema'), { recursive: true });
await cp(join(root, 'schema/i18n.v1.json'), join(target, 'schema/i18n.v1.json'));
for (const file of ['README.md', 'LICENSE', 'CHANGELOG.md']) await cp(join(root, file), join(target, file));
await cp(join(root, 'cli/build/libs/i18n-lint.jar'), join(target, 'dist/i18n-lint.jar'));
await writeFile(join(target, 'package.json'), `${JSON.stringify(manifest, null, 2)}\n`);
