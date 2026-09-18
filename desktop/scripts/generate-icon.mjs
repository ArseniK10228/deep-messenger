import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import pngToIco from 'png-to-ico';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const png = path.join(root, 'build', 'icon.png');
const ico = path.join(root, 'build', 'icon.ico');

if (!fs.existsSync(png)) {
  console.error('Missing build/icon.png');
  process.exit(1);
}

const buf = await pngToIco(png);
fs.writeFileSync(ico, buf);
console.log('Wrote', ico);
