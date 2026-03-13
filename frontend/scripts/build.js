import fs from 'node:fs';
import path from 'node:path';

const rootDir = path.resolve(process.cwd());
const srcDir = path.join(rootDir, 'src');
const distDir = path.join(rootDir, 'dist');

if (!fs.existsSync(srcDir)) {
  console.error('Missing src directory:', srcDir);
  process.exit(1);
}

fs.rmSync(distDir, { recursive: true, force: true });
fs.mkdirSync(distDir, { recursive: true });

fs.cpSync(srcDir, distDir, { recursive: true });

console.log('Build complete:', distDir);
