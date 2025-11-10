import { join } from 'path';

// Bun.argv contains command-line arguments
// bun .github/scripts/update-version.ts 1.2.3
// argv[0] is 'bun'
// argv[1] is the script path
// argv[2] is the version
const newVersion = Bun.argv[2];

if (!newVersion) {
  console.error('Usage: bun update-version.ts <new-version>');
  process.exit(1);
}

interface PackageJson {
  version: string;
  [key: string]: unknown;
}

interface AppJson {
  expo: {
    version: string;
    [key: string]: unknown;
  };
  [key: string]: unknown;
}

async function updateFile<T>(
  filePath: string,
  updateFn: (content: T, version: string) => void
): Promise<void> {
  // import.meta.dir is the directory of the current file
  // We go two levels up to the project root from .github/scripts
  const absolutePath = join(import.meta.dir, '../..', filePath);
  const file = Bun.file(absolutePath);

  const content: T = await file.json();
  updateFn(content, newVersion);

  await Bun.write(absolutePath, JSON.stringify(content, null, 2) + '\n');
}

console.log(`Updating package.json and app.json to version ${newVersion}...`);

await Promise.all([
  updateFile<PackageJson>('./package.json', (pkg, version) => {
    pkg.version = version;
  }),
  updateFile<AppJson>('./app.json', (appConfig, version) => {
    appConfig.expo.version = version;
  }),
]);

console.log('Version update complete.');
