import { copyFile, readdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';

const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? '';

// The approved 3D hero keeps its source poster URL unchanged. Prefix this one public
// asset in the exported HTML and client chunks so GitHub Pages resolves it below basePath.
async function prefixPoster(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) await prefixPoster(file);
    else if (/\.(html|js|css)$/.test(entry.name)) {
      const source = await readFile(file, 'utf8');
      const result = source.replaceAll('/app-screens/phone-poster.webp', `${basePath}/app-screens/phone-poster.webp`);
      if (result !== source) await writeFile(file, result);
    }
  }
}

await prefixPoster(path.resolve(new URL('../out/', import.meta.url).pathname));
await writeFile(new URL('../out/.nojekyll', import.meta.url), '');
await copyFile(new URL('../NOTICE-REACT-BITS.md', import.meta.url), new URL('../out/NOTICE-REACT-BITS.md', import.meta.url));
console.log('GitHub Pages post-build: .nojekyll and React Bits notice written to out/.');
