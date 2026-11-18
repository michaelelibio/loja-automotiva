// Raster derivatives of the original, font-independent inGarage vector mark.
// Run from frontend: node scripts/generate-brand-icons.mjs
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

async function main() {
  const vector = await fs.readFile(path.join(root, 'app/icon.svg'));
  const png = (size) => sharp(vector, { density: 384 }).resize(size, size).png().toBuffer();
  await fs.mkdir(path.join(root, 'public/brand'), { recursive: true });
  for (const [file, size] of [['app/apple-icon.png', 180], ['public/brand/icon-192.png', 192], ['public/brand/icon-512.png', 512]]) {
    await fs.writeFile(path.join(root, file), await png(size));
  }
  // ICO directory entries point to PNG images, supported by current browsers and Windows.
  const sizes = [16, 32, 48];
  const images = await Promise.all(sizes.map(png));
  const header = Buffer.alloc(6 + sizes.length * 16);
  header.writeUInt16LE(1, 2);
  header.writeUInt16LE(sizes.length, 4);
  let offset = header.length;
  images.forEach((image, index) => {
    const entry = 6 + index * 16;
    header[entry] = sizes[index];
    header[entry + 1] = sizes[index];
    header.writeUInt16LE(1, entry + 4);
    header.writeUInt16LE(32, entry + 6);
    header.writeUInt32LE(image.length, entry + 8);
    header.writeUInt32LE(offset, entry + 12);
    offset += image.length;
  });
  await fs.writeFile(path.join(root, 'app/favicon.ico'), Buffer.concat([header, ...images]));
}

main().catch((error) => { console.error(error.message); process.exitCode = 1; });
